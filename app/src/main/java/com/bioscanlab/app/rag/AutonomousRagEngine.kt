package com.bioscanlab.app.rag

import android.content.Context
import com.bioscanlab.app.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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
 * Motor RAG autónomo integrado directamente dentro de la app Android.
 * - Extrae fichas técnicas localmente con 0 latencia y 100% offline.
 * - Responde preguntas libres consultando directamente el corpus local y
 *   conectando con Google Gemini en la nube sin requerir una PC encendida.
 */
class AutonomousRagEngine(private val context: Context) : RagClient {

    private val chunks by lazy { LocalCorpus.getChunks(context) }
    private val index by lazy { LocalBm25(chunks) }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 35_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 35_000
        }
    }

    override suspend fun sheet(className: String): EquipmentSheet {
        val hits = index.byEquipment(className)
        if (hits.isEmpty()) return EquipmentSheet(className = className, funcion = "")

        val best = hits.first().chunk.equipment
        val own = hits.filter { it.chunk.equipment == best }.map { it.chunk }

        fun bucketOf(section: String): String {
            val s = section.uppercase()
            return when {
                s.contains("PASO") || s.contains("PROCEDIMIENTO") -> "procedimiento"
                s.contains("PRECAUCI") || s.contains("RIESGO") || s.contains("SEGURIDAD") -> "riesgos"
                s.contains("PROTECCI") || s.contains("EPP") -> "epp"
                s.contains("MANTENIMIENTO") || s.contains("LIMPIEZA") -> "mantenimiento"
                s.contains("COMPONENTE") || s.contains("PARTES") -> "componentes"
                else -> "funcion"
            }
        }

        val grouped = own.groupBy { bucketOf(it.section) }

        fun lines(bucket: String): List<String> =
            grouped[bucket].orEmpty()
                .flatMap { it.text.lines() }
                .map(::cleanMarkdown)
                .filter { it.isNotEmpty() }

        return EquipmentSheet(
            className = className,
            funcion = lines("funcion").joinToString(separator = "\n"),
            componentes = lines("componentes"),
            procedimiento = lines("procedimiento"),
            proteccionPersonal = lines("epp"),
            riesgos = lines("riesgos"),
            mantenimiento = lines("mantenimiento"),
            sources = own.map { it.toDomain() }.distinctBy { it.section },
        )
    }

    override suspend fun ask(className: String, question: String): RagAnswer {
        val hits = index.search(className, question, limit = 12)
        val allEquipmentHits = index.byEquipment(className, limit = 12)
        if (hits.isEmpty() && allEquipmentHits.isEmpty()) return notEnoughInfo(className)

        val contextChunks = (hits + allEquipmentHits).distinctBy { it.chunk.section }

        val contextText = contextChunks.joinToString("\n\n") { (chunk, _) ->
            "[${chunk.document} | ${chunk.sectionLabel}]\n${chunk.text}"
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return RagAnswer(
                text = "No se configuró la clave de Gemini en el APK. Por favor revisa local.properties.",
                sources = emptyList(),
                hasEnoughInfo = false,
            )
        }

        val prompt = systemPrompt(className)
        val userContent = "CONTEXTO:\n$contextText\n\nPREGUNTA: $question"

        val modelsToTry = listOf("gemini-3.5-flash-lite", "gemini-flash-latest", "gemini-flash-lite-latest", "gemini-2.5-flash")
        var rawAnswer = ""
        var lastError = ""

        for (model in modelsToTry) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val response = client.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(
                        GeminiRequest(
                            systemInstruction = GeminiContent(parts = listOf(GeminiTextPart(prompt))),
                            contents = listOf(
                                GeminiContent(
                                    parts = listOf(GeminiTextPart(userContent)),
                                ),
                            ),
                            generationConfig = GeminiGenConfig(temperature = 0.1),
                        ),
                    )
                }

                val status = response.status.value
                val body = response.bodyAsText()
                if (status in 200..299) {
                    val parsed = json.decodeFromString<GeminiResponse>(body)
                    rawAnswer = parsed.candidates
                        .firstOrNull()
                        ?.content
                        ?.parts
                        ?.joinToString("\n") { it.text }
                        ?.trim()
                        .orEmpty()
                    if (rawAnswer.isNotBlank()) break
                } else {
                    lastError = "Error $status: $body"
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "Error de red"
            }
        }

        if (rawAnswer.isBlank()) {
            return RagAnswer(
                text = "No pude contactar al asistente inteligente: $lastError",
                hasEnoughInfo = false,
            )
        }

        if (rawAnswer.contains(OUT_OF_SCOPE_SENTINEL)) {
            return RagAnswer(
                text = "No se encuentra información sobre este tema. Como asistente técnico del laboratorio de microbiología, únicamente puedo resolver dudas sobre los equipos del laboratorio, bioseguridad, EPP y procedimientos técnicos.",
                sources = emptyList(),
                hasEnoughInfo = false,
                isOfficialGuide = false,
            )
        }

        val sourcesToReturn = if (hits.isNotEmpty()) {
            hits.map { it.chunk.toDomain() }
        } else {
            allEquipmentHits.take(3).map { it.chunk.toDomain() }
        }.distinctBy { it.document to it.section }

        val isUnverified = rawAnswer.contains(UNVERIFIED_INFO_SENTINEL)
        val cleanedText = rawAnswer.replace(UNVERIFIED_INFO_SENTINEL, "").trim()

        return RagAnswer(
            text = cleanedText,
            sources = if (!isUnverified) sourcesToReturn else emptyList(),
            hasEnoughInfo = true,
            isOfficialGuide = !isUnverified,
            disclaimer = if (isUnverified) {
                "Información complementaria técnica (no verificada en el instructivo oficial del laboratorio)"
            } else null,
        )
    }

    private fun cleanMarkdown(line: String): String =
        line.trim()
            .removePrefix("- ")
            .removePrefix("* ")
            .replace("**", "")
            .replace("__", "")
            .trim()

    private fun notEnoughInfo(equipment: String) = RagAnswer(
        text = "No se encuentra información suficiente sobre este aspecto de $equipment en los documentos " +
            "del laboratorio. Te recomiendo consultar al docente o al responsable del " +
            "laboratorio.",
        sources = emptyList(),
        hasEnoughInfo = false,
        isOfficialGuide = false,
    )

    private fun systemPrompt(equipment: String) = """
        Eres el asistente técnico del Laboratorio de Microbiología de la UTEQ.
        El usuario está consultando sobre el equipo identificado: $equipment.

        REGLAS ESTRICTAS DE RESPUESTA:
        1. SALUDOS: Si el usuario saluda, responde amablemente en 1 sola frase recordando que estás listo para ayudar con el equipo $equipment.
        2. ÁMBITO EXCLUSIVO DEL LABORATORIO: Solo respondes sobre microbiología, equipos de laboratorio, bioseguridad, reactivos y procedimientos técnicos. Si la pregunta NO TIENE RELACIÓN con el laboratorio ni con este equipo técnico (por ejemplo: cocina, recetas, fútbol, deportes, videojuegos, política, películas o temas ajenos), responde EXACTAMENTE Y SIN NADA MÁS: $OUT_OF_SCOPE_SENTINEL
        3. INFORMACIÓN DEL INSTRUCTIVO OFICIAL:
           - Si la respuesta se encuentra en el CONTEXTO del instructivo oficial proporcionado, responde de forma clara, directa, precisa y profesional basándote en dicho texto.
        4. INFORMACIÓN COMPLEMENTARIA EXTERNA (NO EN EL INSTRUCTIVO):
           - Si la pregunta trata sobre el equipo ($equipment) o procedimientos de laboratorio pero el CONTEXTO NO contiene el dato específico, responde utilizando conocimiento técnico general de laboratorio o especificaciones comunes del fabricante.
           - En este caso, DEBES iniciar tu respuesta OBLIGATORIAMENTE con la etiqueta exacta: $UNVERIFIED_INFO_SENTINEL
           - Y debes indicar al inicio de dónde proviene la información y advertir que no está en el instructivo oficial, por ejemplo:
             "Aviso: Esta información no se encuentra en el instructivo oficial del laboratorio (información no verificada por la institución). Se proporciona según referencias técnicas generales de fabricante o literatura especializada:"
        5. FORMATO:
           - Redacta de forma clara y concisa en español.
           - Emplea viñetas ordenadas cuando enumeres pasos o partes.
    """.trimIndent()

    private fun CorpusChunk.toDomain() = SourceRef(
        document = document,
        section = sectionLabel,
        snippet = text.take(200),
    )

    private companion object {
        const val OUT_OF_SCOPE_SENTINEL = "FUERA_DE_AMBITO_LABORATORIO"
        const val UNVERIFIED_INFO_SENTINEL = "[INFO_GENERAL_NO_VERIFICADA]"
    }
}

@Serializable
private data class GeminiTextPart(val text: String = "")

@Serializable
private data class GeminiContent(val parts: List<GeminiTextPart>, val role: String? = null)

@Serializable
private data class GeminiGenConfig(val temperature: Double = 0.1)

@Serializable
private data class GeminiRequest(
    @SerialName("system_instruction") val systemInstruction: GeminiContent? = null,
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenConfig = GeminiGenConfig(),
)

@Serializable
private data class GeminiCandidate(val content: GeminiContent? = null)

@Serializable
private data class GeminiResponse(val candidates: List<GeminiCandidate> = emptyList())
