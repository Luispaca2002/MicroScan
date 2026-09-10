package com.bioscanlab.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

@Serializable
data class HealthDto(
    val status: String,
    val llm: String,
    val chunks: Int,
    val equipment: List<String>,
)

/**
 * Backend RAG del Laboratorio de Microbiologia.
 *
 * Corre en la maquina del desarrollador; el telefono le pega por la red local.
 *
 *   ./gradlew :server:run
 *
 * Variables de entorno:
 *   LLM_PROVIDER=gemini|ollama|claude|openai (default: gemini si hay key, sino ollama)
 *   GEMINI_API_KEY=AQ.Ab8...
 *   GEMINI_MODEL=gemini-3.5-flash
 *   OLLAMA_MODEL=qwen2.5:3b
 *   ANTHROPIC_API_KEY=sk-...     (solo si LLM_PROVIDER=claude)
 *   PORT=8080
 *   CORPUS_DIR=server/corpus
 */
fun main() {
    // ./gradlew :server:run usa server/ como directorio de trabajo, pero al
    // ejecutar el jar desde la raiz del repo el corpus queda en server/corpus.
    // Se aceptan ambas ubicaciones para que arranque igual en los dos casos.
    val corpusDir = System.getenv("CORPUS_DIR")?.let(::File)
        ?: listOf(File("corpus"), File("server/corpus")).firstOrNull { it.isDirectory }
        ?: File("corpus")
    val chunks = Corpus.load(corpusDir)
    val llm = LlmFactory.fromEnvironment()
    val rag = RagService(chunks, llm)
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    val equipment = chunks.map { it.equipment }.distinct().sorted()

    println("BioScanLab RAG")
    println("  corpus : ${corpusDir.absolutePath}")
    println("  chunks : ${chunks.size} sobre ${equipment.size} equipos")
    println("  llm    : ${llm.name}")
    println("  url    : http://${localIpAddress()}:$port   <- usa esta IP en la app")
    println()

    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }

        routing {
            // Sirve para verificar desde el telefono que la red llega al backend
            // antes de culpar a la app.
            get("/health") {
                call.respond(HealthDto("ok", llm.name, chunks.size, equipment))
            }

            get("/sheet") {
                val name = call.request.queryParameters["equipment"].orEmpty()
                call.respond(rag.sheet(name))
            }

            post("/ask") {
                val request = call.receive<AskRequest>()
                call.respond(rag.ask(request.equipment, request.question))
            }
        }
    }.start(wait = true)
}

/** IP de la maquina en la red local, para no tener que buscarla a mano. */
private fun localIpAddress(): String =
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress }
        ?.hostAddress
        ?: "localhost"
