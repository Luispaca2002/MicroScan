package com.bioscanlab.app.rag

import java.text.Normalizer
import kotlin.math.ln

/**
 * Buscador BM25 autónomo optimizado para correr directamente en el dispositivo móvil.
 */
class LocalBm25(private val chunks: List<CorpusChunk>) {

    private val documents: List<List<String>> =
        chunks.map { tokenize(it.equipment + " " + it.section + " " + it.text) }
    private val averageLength: Double =
        documents.sumOf { it.size }.toDouble() / documents.size.coerceAtLeast(1)

    private val documentFrequency: Map<String, Int> =
        documents.flatMap { it.toSet() }.groupingBy { it }.eachCount()

    private val termFrequencies: List<Map<String, Int>> =
        documents.map { doc -> doc.groupingBy { it }.eachCount() }

    fun search(equipment: String, question: String, limit: Int = 5): List<Scored> {
        val query = tokenize(equipment).flatMap { listOf(it, it) } + tokenize(question)
        if (query.isEmpty()) return emptyList()

        return candidatesFor(equipment)
            .map { i -> Scored(chunks[i], score(query, i)) }
            .filter { it.score > 0.0 }
            .sortedByDescending { it.score }
            .take(limit)
    }

    fun byEquipment(equipment: String, limit: Int = 12): List<Scored> {
        val query = tokenize(equipment)
        if (query.isEmpty()) return emptyList()

        return candidatesFor(equipment)
            .map { i -> Scored(chunks[i], score(query, i)) }
            .filter { it.score > 0.0 }
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun candidatesFor(equipment: String): List<Int> {
        val queryTokens = tokenize(equipment).toSet()
        if (queryTokens.isEmpty()) return emptyList()

        return chunks.indices.filter { i ->
            val heading = tokenize(chunks[i].equipment)
            if (heading.isEmpty()) return@filter false
            val covered = heading.count { token -> queryTokens.any { covers(token, it) } }
            covered.toDouble() / heading.size >= HEADING_COVERAGE
        }
    }

    private fun covers(headingToken: String, queryToken: String): Boolean =
        headingToken == queryToken ||
            (headingToken.length >= 6 && queryToken.length >= 6 &&
                (headingToken.contains(queryToken) || queryToken.contains(headingToken)))

    private fun score(query: List<String>, docIndex: Int): Double {
        val frequencies = termFrequencies[docIndex]
        val length = documents[docIndex].size.toDouble()
        var total = 0.0

        for (term in query) {
            val tf = frequencies[term] ?: continue
            val df = documentFrequency[term] ?: continue
            val idf = ln(1 + (documents.size - df + 0.5) / (df + 0.5))
            val numerator = tf * (K1 + 1)
            val denominator = tf + K1 * (1 - B + B * length / averageLength)
            total += idf * numerator / denominator
        }
        return total
    }

    data class Scored(val chunk: CorpusChunk, val score: Double)

    companion object {
        private const val K1 = 1.5
        private const val B = 0.75
        private const val HEADING_COVERAGE = 0.6

        private val STOP_WORDS = setOf(
            "de", "la", "el", "los", "las", "un", "una", "unos", "unas", "y", "o", "que",
            "en", "con", "por", "para", "del", "al", "se", "su", "sus", "es", "son", "lo",
            "como", "mas", "pero", "sin", "sobre", "este", "esta", "estos", "estas",
        )

        fun tokenize(text: String): List<String> =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length > 1 && it !in STOP_WORDS }
    }
}
