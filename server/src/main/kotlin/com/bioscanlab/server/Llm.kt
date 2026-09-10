package com.bioscanlab.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Redacta la respuesta final a partir del contexto recuperado.
 *
 * Es lo unico intercambiable del pipeline: la recuperacion (BM25), el armado
 * del contexto y las reglas de grounding son iguales para cualquier proveedor.
 */
interface Llm {
    val name: String
    suspend fun complete(systemPrompt: String, userMessage: String): String
}

/**
 * encodeDefaults es imprescindible aca: kotlinx.serialization omite los campos
 * que valen su default, asi que "stream = false" no se enviaba nunca y Ollama
 * respondia en streaming (un JSON por token). Lo mismo con "temperature".
 */
private val httpJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * El timeout por defecto de Ktor es de 15 segundos, y no alcanza: un modelo
 * local grande puede tardar minutos solo en cargarse en memoria la primera vez,
 * y mas todavia si no entra entero en la VRAM y parte corre en CPU.
 */
private fun httpClient() = HttpClient {
    install(ContentNegotiation) { json(httpJson) }
    install(HttpTimeout) {
        requestTimeoutMillis = LLM_TIMEOUT_MS
        socketTimeoutMillis = LLM_TIMEOUT_MS
        connectTimeoutMillis = 10_000
    }
}

private const val LLM_TIMEOUT_MS = 10 * 60 * 1000L

/**
 * LLM local via Ollama. Es el proveedor por defecto: no necesita API key ni
 * conexion a internet, asi que la demo funciona aunque la red del laboratorio
 * no tenga salida.
 */
class OllamaLlm(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "qwen2.5:3b",
) : Llm {

    override val name = "ollama:$model"

    @Serializable
    private data class Request(
        val model: String,
        val prompt: String,
        val system: String,
        val stream: Boolean = false,
        val options: Options = Options(),
    )

    @Serializable
    private data class Options(val temperature: Double = 0.1)

    @Serializable
    private data class Response(val response: String = "")

    /**
     * Ollama responde con Content-Type "application/x-ndjson" incluso con
     * stream=false, y la negociacion de contenido de Ktor solo reconoce
     * "application/json". Se lee el cuerpo como texto y se parsea a mano para
     * no depender del encabezado.
     */
    override suspend fun complete(systemPrompt: String, userMessage: String): String =
        httpClient().use { client ->
            val raw = client.post("$baseUrl/api/generate") {
                contentType(ContentType.Application.Json)
                setBody(Request(model = model, prompt = userMessage, system = systemPrompt))
            }.bodyAsText()
            httpJson.decodeFromString<Response>(raw).response.trim()
        }
}

/**
 * LLM remoto via la Messages API de Claude. Mas confiable para cenirse al
 * contexto y para admitir cuando no sabe, que es justo lo que se evalua.
 *
 * La API key se lee de la variable de entorno ANTHROPIC_API_KEY y vive solo en
 * este servidor: nunca viaja a la app Android.
 */
class ClaudeLlm(
    private val apiKey: String,
    private val model: String = "claude-sonnet-5",
) : Llm {

    override val name = "claude:$model"

    @Serializable
    private data class Message(val role: String, val content: String)

    @Serializable
    private data class Request(
        val model: String,
        @SerialName("max_tokens") val maxTokens: Int,
        val system: String,
        val messages: List<Message>,
        val temperature: Double = 0.0,
    )

    @Serializable
    private data class ContentBlock(val type: String = "", val text: String = "")

    @Serializable
    private data class Response(val content: List<ContentBlock> = emptyList())

    override suspend fun complete(systemPrompt: String, userMessage: String): String =
        httpClient().use { client ->
            client.post("https://api.anthropic.com/v1/messages") {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(
                    Request(
                        model = model,
                        maxTokens = 1024,
                        system = systemPrompt,
                        messages = listOf(Message("user", userMessage)),
                    ),
                )
            }.body<Response>()
                .content
                .filter { it.type == "text" }
                .joinToString("\n") { it.text }
                .trim()
        }
}

/**
 * Adaptador generico para cualquier proveedor con API compatible con OpenAI.
 *
 * Un solo adaptador cubre las opciones gratuitas o casi gratuitas que alojan
 * Qwen y DeepSeek: OpenRouter (variantes ":free"), Groq, la API de DeepSeek, y
 * tambien LM Studio si se prefiere correr local con otra interfaz. Solo cambian
 * la URL base y el nombre del modelo.
 *
 * La key se lee del entorno del servidor y nunca llega a la app Android.
 */
class OpenAiCompatibleLlm(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) : Llm {

    override val name = "openai-compat:$model"

    @Serializable
    private data class Message(val role: String, val content: String)

    @Serializable
    private data class Request(
        val model: String,
        val messages: List<Message>,
        val temperature: Double = 0.1,
    )

    @Serializable
    private data class Choice(val message: Message? = null)

    @Serializable
    private data class Response(val choices: List<Choice> = emptyList())

    override suspend fun complete(systemPrompt: String, userMessage: String): String =
        httpClient().use { client ->
            client.post("$baseUrl/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(
                    Request(
                        model = model,
                        messages = listOf(
                            Message("system", systemPrompt),
                            Message("user", userMessage),
                        ),
                    ),
                )
            }.body<Response>()
                .choices
                .firstOrNull()
                ?.message
                ?.content
                .orEmpty()
                .trim()
        }
}

/**
 * LLM remoto via la API de Google Gemini.
 *
 * La key se lee de la variable de entorno GEMINI_API_KEY y vive solo en este
 * servidor: nunca viaja a la app Android.
 */
class GeminiLlm(
    private val apiKey: String,
    private val model: String = "gemini-3.5-flash-lite",
) : Llm {

    override val name = "gemini:$model"

    @Serializable
    private data class TextPart(val text: String = "")

    @Serializable
    private data class Content(val parts: List<TextPart>, val role: String? = null)

    @Serializable
    private data class GenerationConfig(val temperature: Double = 0.1)

    @Serializable
    private data class Request(
        @SerialName("system_instruction") val systemInstruction: Content? = null,
        val contents: List<Content>,
        val generationConfig: GenerationConfig = GenerationConfig(),
    )

    @Serializable
    private data class Candidate(val content: Content? = null)

    @Serializable
    private data class Response(val candidates: List<Candidate> = emptyList())

    override suspend fun complete(systemPrompt: String, userMessage: String): String =
        httpClient().use { client ->
            val modelsToTry = if (model != "gemini-3.5-flash-lite") {
                listOf(model, "gemini-3.5-flash-lite")
            } else {
                listOf("gemini-3.5-flash-lite")
            }

            var lastError = ""
            for (m in modelsToTry) {
                val response = client.post("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent?key=$apiKey") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        Request(
                            systemInstruction = Content(parts = listOf(TextPart(systemPrompt))),
                            contents = listOf(Content(parts = listOf(TextPart(userMessage)))),
                            generationConfig = GenerationConfig(temperature = 0.1),
                        ),
                    )
                }
                val status = response.status
                val raw = response.bodyAsText()
                if (status.value in 200..299) {
                    val parsed = httpJson.decodeFromString<Response>(raw)
                    return parsed.candidates
                        .firstOrNull()
                        ?.content
                        ?.parts
                        ?.joinToString("\n") { it.text }
                        ?.trim()
                        .orEmpty()
                } else {
                    lastError = "Gemini API error ($status on $m): $raw"
                }
            }
            error(lastError)
        }
}

/**
 * Elige el proveedor segun el entorno.
 *
 *   LLM_PROVIDER=gemini  (default si GEMINI_API_KEY esta definida)   GEMINI_MODEL=gemini-3.5-flash-lite
 *   LLM_PROVIDER=ollama                                              OLLAMA_MODEL=qwen2.5:3b   OLLAMA_URL=...
 *   LLM_PROVIDER=openai                                              OPENAI_BASE_URL=...  OPENAI_API_KEY=...  OPENAI_MODEL=...
 *   LLM_PROVIDER=claude                                              ANTHROPIC_API_KEY=sk-...
 */
object LlmFactory {
    fun fromEnvironment(): Llm {
        val localProps = java.util.Properties().apply {
            listOf(java.io.File("local.properties"), java.io.File("../local.properties"))
                .firstOrNull { it.isFile }
                ?.inputStream()
                ?.use { load(it) }
        }

        val geminiKey = System.getenv("GEMINI_API_KEY")
            ?: System.getProperty("gemini.apiKey")
            ?: localProps.getProperty("gemini.apiKey")
            ?: localProps.getProperty("bioscanlab.geminiApiKey")

        val provider = System.getenv("LLM_PROVIDER")?.lowercase()
            ?: localProps.getProperty("llm.provider")?.lowercase()
            ?: if (!geminiKey.isNullOrBlank()) "gemini" else "ollama"

        return when (provider) {
            "gemini", "google" -> {
                val key = geminiKey
                    ?: error("LLM_PROVIDER=gemini pero falta GEMINI_API_KEY (puedes definirla en variable de entorno o en local.properties como gemini.apiKey=...)")
                val model = System.getenv("GEMINI_MODEL")
                    ?: localProps.getProperty("gemini.model")
                    ?: "gemini-3.5-flash-lite"
                GeminiLlm(key, model)
            }
            "claude" -> {
                val key = System.getenv("ANTHROPIC_API_KEY")
                    ?: localProps.getProperty("anthropic.apiKey")
                    ?: error("LLM_PROVIDER=claude pero falta ANTHROPIC_API_KEY")
                ClaudeLlm(key, System.getenv("CLAUDE_MODEL") ?: localProps.getProperty("claude.model") ?: "claude-sonnet-5")
            }
            "openai", "openrouter", "groq", "deepseek", "qwen" -> {
                val key = System.getenv("OPENAI_API_KEY")
                    ?: localProps.getProperty("openai.apiKey")
                    ?: error("LLM_PROVIDER=$provider pero falta OPENAI_API_KEY")
                OpenAiCompatibleLlm(
                    baseUrl = System.getenv("OPENAI_BASE_URL")
                        ?: localProps.getProperty("openai.baseUrl")
                        ?: "https://openrouter.ai/api/v1",
                    apiKey = key,
                    model = System.getenv("OPENAI_MODEL")
                        ?: localProps.getProperty("openai.model")
                        ?: "deepseek/deepseek-chat-v3-0324:free",
                )
            }
            "ollama" -> OllamaLlm(
                baseUrl = System.getenv("OLLAMA_URL")
                    ?: localProps.getProperty("ollama.url")
                    ?: "http://localhost:11434",
                model = System.getenv("OLLAMA_MODEL")
                    ?: localProps.getProperty("ollama.model")
                    ?: "qwen2.5:3b",
            )
            else -> error(
                "LLM_PROVIDER desconocido: $provider (usa 'gemini', 'ollama', 'openai' o 'claude')"
            )
        }
    }
}
