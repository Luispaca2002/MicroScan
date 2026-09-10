package com.bioscanlab.server

import java.io.File

/**
 * Un fragmento indexable del corpus del laboratorio.
 *
 * La granularidad es "una seccion de un equipo": suficientemente chica para que
 * el contexto que se le manda al LLM sea acotado, y suficientemente grande para
 * que un procedimiento no quede partido a la mitad.
 */
data class Chunk(
    /** Archivo de origen, tal como se muestra al usuario. */
    val document: String,
    /** Equipo al que pertenece, del titulo de nivel 2. */
    val equipment: String,
    /** Apartado dentro del equipo, del titulo de nivel 3. */
    val section: String,
    val text: String,
) {
    /** Etiqueta legible que la app muestra como fuente. */
    val sectionLabel: String
        get() = if (section.isBlank()) equipment else "$equipment > $section"
}

/**
 * Carga y trocea los documentos del laboratorio.
 *
 * Formato esperado (ver server/corpus/):
 *   # Titulo del documento
 *   ## NOMBRE DEL EQUIPO
 *   ### APARTADO
 *   texto...
 *
 * Se eligio Markdown en vez de leer los .docx directamente por dos razones:
 * el corpus queda versionado y revisable en el repositorio, y el servidor no
 * necesita una libreria de ofimatica para arrancar.
 */
object Corpus {

    /** Secciones administrativas del documento que no describen ningun equipo. */
    private val IGNORED = setOf(
        "TECNICO DE LABORATORIO",
        "LABORATORIO DE BIOLOGIA Y MICROBIOLOGIA",
        "INDICE",
        "ÍNDICE",
    )

    /** Los titulos vienen numerados ("## 1. Balanza Analitica"); el numero sobra. */
    private val LEADING_NUMBER = Regex("""^\d+[.)]\s*""")

    fun load(directory: File): List<Chunk> {
        if (!directory.isDirectory) {
            error("No existe el directorio del corpus: ${directory.absolutePath}")
        }

        val files = directory.listFiles { f -> f.extension.equals("md", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()

        if (files.isEmpty()) {
            error("El corpus esta vacio: ${directory.absolutePath}")
        }

        return files.flatMap { parse(it) }
    }

    private fun parse(file: File): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        var documentTitle = file.nameWithoutExtension
        var equipment = ""
        var section = ""
        val buffer = StringBuilder()

        fun flush() {
            val text = buffer.toString().trim()
            buffer.setLength(0)
            if (text.isEmpty() || equipment.isEmpty()) return
            if (equipment.uppercase() in IGNORED) return
            chunks += Chunk(documentTitle, equipment, section, text)
        }

        file.forEachLine { raw ->
            val line = raw.trim()
            when {
                line.startsWith("### ") -> {
                    flush()
                    section = line.removePrefix("### ").trim()
                }
                line.startsWith("## ") -> {
                    flush()
                    equipment = line.removePrefix("## ").trim().replace(LEADING_NUMBER, "")
                    section = ""
                }
                line.startsWith("# ") -> {
                    flush()
                    documentTitle = line.removePrefix("# ").trim()
                    equipment = ""
                    section = ""
                }
                line == "---" -> Unit
                else -> if (line.isNotEmpty()) buffer.append(line).append('\n')
            }
        }
        flush()
        return chunks
    }
}
