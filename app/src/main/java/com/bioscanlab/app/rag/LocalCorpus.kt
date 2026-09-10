package com.bioscanlab.app.rag

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

data class CorpusChunk(
    val document: String,
    val equipment: String,
    val section: String,
    val text: String,
) {
    val sectionLabel: String
        get() = if (section.isBlank()) equipment else "$equipment > $section"
}

/**
 * Carga y parsea el corpus del laboratorio directamente desde los assets de la app.
 * Permite que las fichas técnicas y el motor RAG funcionen de forma 100% autónoma
 * en cualquier teléfono sin requerir un servidor o PC encendida.
 */
object LocalCorpus {
    private val IGNORED = setOf(
        "TECNICO DE LABORATORIO",
        "LABORATORIO DE BIOLOGIA Y MICROBIOLOGIA",
        "INDICE",
        "ÍNDICE",
    )
    private val LEADING_NUMBER = Regex("""^\d+[.)]\s*""")

    @Volatile
    private var cachedChunks: List<CorpusChunk>? = null

    fun getChunks(context: Context): List<CorpusChunk> {
        cachedChunks?.let { return it }
        synchronized(this) {
            cachedChunks?.let { return it }
            val chunks = loadFromAssets(context)
            cachedChunks = chunks
            return chunks
        }
    }

    private fun loadFromAssets(context: Context): List<CorpusChunk> {
        val assetManager = context.assets
        val list = try {
            assetManager.list("corpus") ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }

        val files = if (list.isNotEmpty()) {
            list.filter { it.endsWith(".md", ignoreCase = true) }.map { "corpus/$it" }
        } else {
            listOf("corpus/instructivo_equipos_microbiologia.md")
        }

        val chunks = mutableListOf<CorpusChunk>()
        for (filePath in files) {
            try {
                assetManager.open(filePath).use { stream ->
                    val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
                    val docTitle = filePath.substringAfterLast("/").removeSuffix(".md")
                    chunks.addAll(parse(reader, docTitle))
                }
            } catch (e: Exception) {
                // Ignore missing file
            }
        }
        return chunks
    }

    private fun parse(reader: BufferedReader, defaultTitle: String): List<CorpusChunk> {
        val chunks = mutableListOf<CorpusChunk>()
        var documentTitle = defaultTitle
        var equipment = ""
        var section = ""
        val buffer = StringBuilder()

        fun flush() {
            val text = buffer.toString().trim()
            buffer.setLength(0)
            if (text.isEmpty() || equipment.isEmpty()) return
            if (equipment.uppercase() in IGNORED) return
            chunks += CorpusChunk(documentTitle, equipment, section, text)
        }

        reader.forEachLine { raw ->
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
