package com.bioscanlab.app.rag

import com.bioscanlab.app.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Cliente del backend RAG.
 *
 * Solo manda el nombre del equipo detectado y la pregunta. No conoce el corpus,
 * no arma prompts y no habla con ningun LLM: todo eso vive en el servidor, como
 * exige la rubrica.
 */
class HttpRagClient(private val baseUrl: String? = null) : RagClient {

    private fun resolveUrl(): String = baseUrl ?: ServerConfig.getUrl()

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            // Un LLM local o llamada por tunel puede requerir tiempo de conexion
            // mayor en redes moviles (4G/5G).
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 60_000
        }
    }

    override suspend fun sheet(className: String): EquipmentSheet =
        client.get("${resolveUrl()}/sheet") { parameter("equipment", className) }
            .body<SheetDto>()
            .toDomain()

    override suspend fun ask(className: String, question: String): RagAnswer =
        client.post("${resolveUrl()}/ask") {
            contentType(ContentType.Application.Json)
            setBody(AskRequestDto(equipment = className, question = question))
        }.body<AnswerDto>().toDomain()

    companion object {
        val DEFAULT_BASE_URL: String
            get() = ServerConfig.getUrl()
    }
}

@Serializable
private data class AskRequestDto(val equipment: String, val question: String)

@Serializable
private data class SourceDto(
    val document: String = "",
    val section: String = "",
    val snippet: String = "",
)

@Serializable
private data class AnswerDto(
    val text: String = "",
    val sources: List<SourceDto> = emptyList(),
    val hasEnoughInfo: Boolean = true,
)

@Serializable
private data class SheetDto(
    val className: String = "",
    val funcion: String = "",
    val componentes: List<String> = emptyList(),
    val procedimiento: List<String> = emptyList(),
    val proteccionPersonal: List<String> = emptyList(),
    val riesgos: List<String> = emptyList(),
    val mantenimiento: List<String> = emptyList(),
    val sources: List<SourceDto> = emptyList(),
)

private fun SourceDto.toDomain() = SourceRef(document, section, snippet)

private fun AnswerDto.toDomain() =
    RagAnswer(text = text, sources = sources.map { it.toDomain() }, hasEnoughInfo = hasEnoughInfo)

private fun SheetDto.toDomain() = EquipmentSheet(
    className = className,
    funcion = funcion,
    componentes = componentes,
    procedimiento = procedimiento,
    proteccionPersonal = proteccionPersonal,
    riesgos = riesgos,
    mantenimiento = mantenimiento,
    sources = sources.map { it.toDomain() },
)
