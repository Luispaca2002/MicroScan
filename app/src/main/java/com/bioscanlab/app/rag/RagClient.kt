package com.bioscanlab.app.rag

/**
 * Fragmento recuperado del corpus del laboratorio.
 *
 * La rubrica exige que "las respuestas deberan mostrar la fuente consultada",
 * asi que la fuente NO es opcional: viaja pegada a cada respuesta y la UI la
 * muestra siempre.
 */
data class SourceRef(
    /** Documento de origen, p.ej. "Instructivo basico de equipos - Microbiologia". */
    val document: String,
    /** Seccion dentro del documento, p.ej. "AUTOCLAVE > PASOS PARA SU CORRECTO USO". */
    val section: String,
    /** Fragmento textual usado. Sirve para que el usuario verifique la cita. */
    val snippet: String,
)

/**
 * Respuesta del asistente.
 *
 * [hasEnoughInfo] = false modela el caso que la rubrica pide explicitamente:
 * "Si la informacion no se encuentra en los documentos, el asistente debera
 * indicar que no dispone de informacion suficiente y recomendar consultar al
 * docente o responsable del laboratorio."
 *
 * Es un estado propio y no un simple texto, para que la UI pueda distinguirlo
 * visualmente de una respuesta fundamentada.
 */
data class RagAnswer(
    val text: String,
    val sources: List<SourceRef> = emptyList(),
    val hasEnoughInfo: Boolean = true,
    val isOfficialGuide: Boolean = true,
    val disclaimer: String? = null,
)

/** Ficha tecnica de un equipo, armada por el backend a partir del corpus. */
data class EquipmentSheet(
    val className: String,
    val funcion: String,
    val componentes: List<String> = emptyList(),
    val procedimiento: List<String> = emptyList(),
    val proteccionPersonal: List<String> = emptyList(),
    val riesgos: List<String> = emptyList(),
    val mantenimiento: List<String> = emptyList(),
    val sources: List<SourceRef> = emptyList(),
) {
    /** true si el corpus no tenia material suficiente para armar la ficha. */
    val isEmpty: Boolean
        get() = funcion.isBlank() && componentes.isEmpty() && procedimiento.isEmpty() &&
            proteccionPersonal.isEmpty() && riesgos.isEmpty() && mantenimiento.isEmpty()
}

/**
 * Puerta de entrada al backend RAG.
 *
 * IMPORTANTE - lo que esta interfaz deliberadamente NO permite:
 * la app nunca manda documentos ni arma el prompt. Solo envia el nombre del
 * equipo detectado y la pregunta del usuario. La recuperacion de fragmentos y
 * la construccion del contexto ocurren en el backend, tal como exige la rubrica
 * ("No se deben enviar documentos completos al LLM desde Android").
 */
interface RagClient {

    /** Ficha tecnica del equipo, recuperada del corpus. */
    suspend fun sheet(className: String): EquipmentSheet

    /** Pregunta libre sobre el equipo, respondida solo con el corpus. */
    suspend fun ask(className: String, question: String): RagAnswer
}
