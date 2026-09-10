package com.bioscanlab.app.rag

import kotlinx.coroutines.delay

/**
 * Implementacion temporal mientras no existe el backend.
 *
 * Devuelve deliberadamente "sin informacion suficiente" en vez de inventar
 * contenido: asi la UI se prueba contra el caso que la rubrica exige manejar, y
 * no queda una demo que parece funcionar pero miente.
 *
 * ============================ TODO (Carlos) ============================
 * Reemplazar por HttpRagClient cuando exista el backend:
 *
 *   class HttpRagClient(private val baseUrl: String) : RagClient {
 *       override suspend fun sheet(className: String): EquipmentSheet   // GET  /sheet?equipment=...
 *       override suspend fun ask(className, question): RagAnswer        // POST /ask
 *   }
 *
 * El backend hace: BM25 sobre los fragmentos del corpus -> arma el contexto ->
 * lo manda al LLM con la instruccion de responder SOLO con ese contexto ->
 * devuelve texto + fuentes. La API key vive ahi, nunca en el APK.
 *
 * Para la demo local, baseUrl es la IP de tu maquina en la red del laboratorio
 * (p.ej. "http://192.168.1.50:8000"). Acordate de habilitar trafico en claro
 * para esa IP en res/xml/network_security_config.xml: Android bloquea HTTP
 * plano por defecto desde API 28.
 * =======================================================================
 */
class StubRagClient : RagClient {

    override suspend fun sheet(className: String): EquipmentSheet {
        delay(300)
        return EquipmentSheet(className = className, funcion = "")
    }

    override suspend fun ask(className: String, question: String): RagAnswer {
        delay(500)
        return RagAnswer(
            text = "No dispongo de informacion suficiente en los documentos del " +
                "laboratorio para responder sobre $className. Te recomiendo consultar " +
                "al docente o al responsable del laboratorio.\n\n" +
                "(Backend RAG todavia no conectado: ver TODO en StubRagClient.kt)",
            sources = emptyList(),
            hasEnoughInfo = false,
        )
    }
}
