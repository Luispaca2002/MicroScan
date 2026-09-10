package com.bioscanlab.server

import kotlinx.serialization.Serializable

@Serializable
data class SourceRefDto(val document: String, val section: String, val snippet: String)

@Serializable
data class AnswerDto(
    val text: String,
    val sources: List<SourceRefDto> = emptyList(),
    val hasEnoughInfo: Boolean = true,
)

@Serializable
data class SheetDto(
    val className: String,
    val funcion: String = "",
    val componentes: List<String> = emptyList(),
    val procedimiento: List<String> = emptyList(),
    val proteccionPersonal: List<String> = emptyList(),
    val riesgos: List<String> = emptyList(),
    val mantenimiento: List<String> = emptyList(),
    val sources: List<SourceRefDto> = emptyList(),
)

@Serializable
data class AskRequest(val equipment: String, val question: String)

/**
 * El nucleo del RAG: recupera, arma el contexto, y solo entonces llama al LLM.
 */
class RagService(
    private val chunks: List<Chunk>,
    private val llm: Llm,
) {
    private val index = Bm25(chunks)

    /**
     * La ficha tecnica se arma DE FORMA DETERMINISTA a partir de los fragmentos,
     * sin pasar por el LLM.
     *
     * Es a proposito: una ficha es una transcripcion estructurada de los
     * documentos, no una interpretacion. Al no mediar un modelo generativo, no
     * hay forma de que aparezca un dato que no este en el corpus. Los apartados
     * que el corpus no cubre quedan vacios, y la app los muestra como
     * "sin informacion suficiente" en lugar de rellenarlos.
     */
    fun sheet(equipment: String): SheetDto {
        val hits = index.byEquipment(equipment)
        if (hits.isEmpty()) return SheetDto(className = equipment)

        // Nos quedamos con el equipo mejor puntuado y descartamos fragmentos de otros.
        val best = hits.first().chunk.equipment
        val own = hits.filter { it.chunk.equipment == best }.map { it.chunk }

        // Cada seccion cae en UN solo campo, por prioridad. El orden importa:
        // "Pasos de Uso" contiene la palabra "Uso", asi que si se evaluara
        // primero el criterio de funcion, el procedimiento entero aparecia
        // duplicado dentro de la descripcion.
        fun bucketOf(section: String): String {
            val s = section.uppercase()
            return when {
                s.contains("PASO") || s.contains("PROCEDIMIENTO") -> "procedimiento"
                s.contains("PRECAUCI") || s.contains("RIESGO") || s.contains("SEGURIDAD") -> "riesgos"
                s.contains("PROTECCI") || s.contains("EPP") -> "epp"
                s.contains("MANTENIMIENTO") || s.contains("LIMPIEZA") -> "mantenimiento"
                s.contains("COMPONENTE") || s.contains("PARTES") -> "componentes"
                // Todo lo demas ("Informacion General", "Uso Principal", y el
                // texto sin subtitulo) describe que es y para que sirve.
                else -> "funcion"
            }
        }

        val grouped = own.groupBy { bucketOf(it.section) }

        fun lines(bucket: String): List<String> =
            grouped[bucket].orEmpty()
                .flatMap { it.text.lines() }
                .map(::cleanMarkdown)
                .filter { it.isNotEmpty() }

        return SheetDto(
            className = equipment,
            funcion = lines("funcion").joinToString(separator = "\n"),
            componentes = lines("componentes"),
            procedimiento = lines("procedimiento"),
            proteccionPersonal = lines("epp"),
            riesgos = lines("riesgos"),
            mantenimiento = lines("mantenimiento"),
            sources = own.map { it.toDto() }.distinctBy { it.section },
        )
    }

    /**
     * La app renderiza texto plano, no Markdown: sin esto se verian los
     * asteriscos y los guiones de vineta tal cual en pantalla.
     */
    private fun cleanMarkdown(line: String): String =
        line.trim()
            .removePrefix("- ")
            .removePrefix("* ")
            .replace("**", "")
            .replace("__", "")
            .trim()

    /**
     * Pregunta libre. Si la recuperacion no trae nada, ni siquiera se llama al
     * LLM: sin contexto no hay respuesta posible que este fundamentada, y
     * llamarlo igual seria justamente invitarlo a inventar.
     */
    suspend fun ask(equipment: String, question: String): AnswerDto {
        val hits = index.search(equipment, question, limit = 12)
        val allEquipmentHits = index.byEquipment(equipment, limit = 12)
        if (hits.isEmpty() && allEquipmentHits.isEmpty()) return notEnoughInfo(equipment)

        // Combinamos los fragmentos encontrados con todos los del equipo para asegurar
        // que secciones como "Pasos de Uso", "Componentes", "EPP", etc. siempre estén
        // disponibles en el contexto del LLM.
        val contextChunks = (hits + allEquipmentHits)
            .distinctBy { it.chunk.section }

        val context = contextChunks.joinToString("\n\n") { (chunk, _) ->
            "[${chunk.document} | ${chunk.sectionLabel}]\n${chunk.text}"
        }

        val answer = runCatching {
            llm.complete(systemPrompt(equipment), "CONTEXTO:\n$context\n\nPREGUNTA: $question")
        }.getOrElse {
            return AnswerDto(
                text = "No pude contactar al modelo de lenguaje (${llm.name}): ${it.message}",
                hasEnoughInfo = false,
            )
        }

        // Si la pregunta es ajena al ámbito del laboratorio, el LLM emite el centinela correspondiente
        if (answer.contains(OUT_OF_SCOPE_SENTINEL)) {
            return AnswerDto(
                text = "No se encuentra información. Solo puedo responder preguntas relacionadas con el laboratorio y sus equipos técnicos.",
                sources = emptyList(),
                hasEnoughInfo = false,
            )
        }

        // El modelo declara el vacío con un centinela en vez de improvisar.
        if (answer.isBlank() || answer.contains(NO_INFO_SENTINEL)) return notEnoughInfo(equipment)

        val sourcesToReturn = if (hits.isNotEmpty()) {
            hits.map { it.chunk.toDto() }
        } else {
            allEquipmentHits.take(3).map { it.chunk.toDto() }
        }.distinctBy { it.document to it.section }

        return AnswerDto(
            text = answer,
            sources = sourcesToReturn,
            hasEnoughInfo = true,
        )
    }

    private fun notEnoughInfo(equipment: String) = AnswerDto(
        text = "No se encuentra información suficiente sobre este aspecto de $equipment en los documentos " +
            "del laboratorio. Te recomiendo consultar al docente o al responsable del " +
            "laboratorio.",
        sources = emptyList(),
        hasEnoughInfo = false,
    )

    /**
     * Reglas estrictas:
     * - Atender solo temas del laboratorio y del equipo detectado.
     * - Responder con el contexto sin completar con conocimiento propio.
     * - Declarar fuera de ámbito o falta de información con centinelas específicos.
     */
    private fun systemPrompt(equipment: String) = """
        Sos el asistente técnico del Laboratorio de Microbiología de la UTEQ.
        El usuario apuntó la cámara a un equipo y el detector lo identificó como: $equipment.

        REGLAS ESTRICTAS:
        1. Puedes responder saludos cordiales brevemente indicando tu función de asistencia para el equipo $equipment.
        2. Responde consultas relacionadas con el laboratorio de microbiología y el equipo técnico identificado ($equipment): su función, componentes, procedimiento de uso, precauciones de seguridad, EPP y mantenimiento.
        3. Si la pregunta NO TIENE RELACIÓN con el laboratorio ni con este equipo técnico (por ejemplo: recetas de cocina, deportes, programación general, chistes, opiniones o temas ajenos al ámbito técnico del laboratorio), responde EXACTAMENTE: $OUT_OF_SCOPE_SENTINEL
        4. Para consultas del laboratorio: responde basándote en la información del CONTEXTO que se te entrega.
        5. Si el CONTEXTO no contiene información para responder la pregunta sobre este equipo, responde EXACTAMENTE: $NO_INFO_SENTINEL
        6. No inventes nombres de documentos ni datos inexistentes. Responde siempre en español, de forma clara, profesional y concisa.
    """.trimIndent()

    private fun Chunk.toDto() = SourceRefDto(
        document = document,
        section = sectionLabel,
        snippet = text.take(200),
    )

    private companion object {
        const val NO_INFO_SENTINEL = "SIN_INFORMACION_SUFICIENTE"
        const val OUT_OF_SCOPE_SENTINEL = "FUERA_DE_AMBITO_LABORATORIO"
    }
}
