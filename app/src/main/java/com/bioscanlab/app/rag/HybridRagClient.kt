package com.bioscanlab.app.rag

import android.content.Context

/**
 * Cliente RAG inteligente que combina el modo servidor con el motor autónomo:
 * 1. Para la Ficha Técnica: La resuelve directamente en el dispositivo (100% offline, 0 ms de espera).
 * 2. Para Preguntar a la IA:
 *    - Si hay un servidor remoto configurado y activo, lo consulta.
 *    - Si el servidor no responde (PC apagada, fuera de la red local o túnel cerrado),
 *      automáticamente recurre al motor autónomo integrado con Gemini en la nube.
 *
 * De este modo, la app funciona SIEMPRE, para cualquier persona y en cualquier lugar,
 * sin depender de tener la PC encendida ni de estar en la misma red Wi-Fi.
 */
class HybridRagClient(
    private val context: Context,
    private val httpClient: HttpRagClient = HttpRagClient(),
    private val autonomousEngine: AutonomousRagEngine = AutonomousRagEngine(context),
) : RagClient {

    override suspend fun sheet(className: String): EquipmentSheet {
        // La ficha técnica siempre se puede armar localmente de forma instantánea y determinista
        val local = autonomousEngine.sheet(className)
        if (!local.isEmpty) {
            return local
        }
        return try {
            httpClient.sheet(className)
        } catch (e: Exception) {
            local
        }
    }

    override suspend fun ask(className: String, question: String): RagAnswer {
        val serverUrl = ServerConfig.getUrl()
        val isCustomServer = serverUrl.isNotBlank() && !serverUrl.contains("10.0.2.2")

        if (isCustomServer) {
            try {
                return httpClient.ask(className, question)
            } catch (e: Exception) {
                // Si el servidor falla (PC apagada o túnel cerrado), fallback automático al motor autónomo
            }
        }

        // Modo autónomo directo (Cloud Gemini + RAG local)
        return try {
            autonomousEngine.ask(className, question)
        } catch (e: Exception) {
            if (!isCustomServer) {
                try {
                    httpClient.ask(className, question)
                } catch (ex: Exception) {
                    RagAnswer(
                        text = "No fue posible conectar con el asistente de IA: ${e.localizedMessage}",
                        hasEnoughInfo = false,
                    )
                }
            } else {
                RagAnswer(
                    text = "No fue posible conectar con el asistente de IA: ${e.localizedMessage}",
                    hasEnoughInfo = false,
                )
            }
        }
    }
}
