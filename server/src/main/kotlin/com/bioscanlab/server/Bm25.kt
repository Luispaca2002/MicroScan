package com.bioscanlab.server

import java.text.Normalizer
import kotlin.math.ln

/**
 * Buscador BM25 sobre los fragmentos del corpus.
 *
 * Se eligio BM25 y no embeddings porque el corpus son ~20-40k caracteres sobre
 * un vocabulario muy acotado y muy literal (nombres de equipos, marcas y
 * modelos). En ese escenario la coincidencia lexica recupera igual de bien, no
 * necesita descargar un modelo de embeddings, y el criterio de recuperacion es
 * explicable en el informe.
 */
class Bm25(private val chunks: List<Chunk>) {

    private val documents: List<List<String>> = chunks.map { tokenize(it.equipment + " " + it.section + " " + it.text) }
    private val averageLength: Double = documents.sumOf { it.size }.toDouble() / documents.size.coerceAtLeast(1)

    /** Cuantos documentos contienen cada termino. */
    private val documentFrequency: Map<String, Int> =
        documents.flatMap { it.toSet() }.groupingBy { it }.eachCount()

    private val termFrequencies: List<Map<String, Int>> =
        documents.map { doc -> doc.groupingBy { it }.eachCount() }

    /**
     * Devuelve los [limit] fragmentos mas relevantes con puntaje mayor a cero.
     *
     * [equipment] pesa mas que la pregunta: si el usuario pregunta "como lo
     * limpio", lo unico que ancla la busqueda al equipo correcto es el nombre
     * que vino de la deteccion.
     */
    fun search(equipment: String, question: String, limit: Int = 5): List<Scored> {
        val query = tokenize(equipment).flatMap { listOf(it, it) } + tokenize(question)
        if (query.isEmpty()) return emptyList()

        return candidatesFor(equipment)
            .map { i -> Scored(chunks[i], score(query, i)) }
            .filter { it.score > 0.0 }
            .sortedByDescending { it.score }
            .take(limit)
    }

    /** Todos los fragmentos de un equipo, para armar la ficha tecnica. */
    fun byEquipment(equipment: String, limit: Int = 12): List<Scored> {
        val query = tokenize(equipment)
        if (query.isEmpty()) return emptyList()

        return candidatesFor(equipment)
            .map { i -> Scored(chunks[i], score(query, i)) }
            .filter { it.score > 0.0 }
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * Restringe la busqueda a los fragmentos cuyo TITULO de equipo se
     * corresponde con la clase detectada.
     *
     * Sin este filtro, BM25 puntua por palabras sueltas del cuerpo del texto y
     * termina atribuyendo contenido de un equipo a otro: la clase "Balanza
     * analitica PR Series Analytical" matcheaba con la seccion de la INCUBADORA
     * porque su descripcion menciona "investigacion clinica y analitica". Servir
     * el procedimiento de otro equipo es peor que no responder.
     *
     * El criterio es la cobertura del titulo: al menos el 60% de los terminos
     * del encabezado del corpus deben aparecer en el nombre de la clase. Asi
     * "Microscopio Olympus CX22 LED" alcanza "MICROSCOPIO BIOLOGICO OLYMPUS
     * CX22" (3 de 4), pero "Estereo Microscopio Binocular Modelo BS-80" no
     * alcanza a ninguno de los dos microscopios documentados (1 de 4), que es
     * lo correcto porque ese equipo no esta en el corpus.
     */
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

    /**
     * Un termino del titulo se considera cubierto si coincide, o si uno de los
     * dos contiene al otro y el termino compartido es largo.
     *
     * Hace falta porque el corpus escribe "Estereomicroscopio" en una palabra y
     * el data.yaml lo separa en "Estereo Microscopio": sin esto, esa clase
     * quedaba justo en el limite del umbral y cualquier retoque del titulo la
     * hubiera dejado sin ficha.
     */
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

    data class Scored(val chunk: Chunk, val score: Double)

    companion object {
        private const val K1 = 1.5
        private const val B = 0.75

        /** Fraccion minima del titulo del corpus que debe cubrir la clase detectada. */
        private const val HEADING_COVERAGE = 0.6

        /** Palabras vacias del espanol que solo agregan ruido al puntaje. */
        private val STOP_WORDS = setOf(
            "de", "la", "el", "los", "las", "un", "una", "unos", "unas", "y", "o", "que",
            "en", "con", "por", "para", "del", "al", "se", "su", "sus", "es", "son", "lo",
            "como", "mas", "pero", "sin", "sobre", "este", "esta", "estos", "estas",
        )

        /**
         * Normaliza a minusculas y sin tildes. Es imprescindible aca: el corpus
         * escribe "Baño maria" y las clases del modelo llegan como "Bano maria"
         * (el data.yaml no lleva tildes). Sin esta normalizacion, esos terminos
         * nunca coincidirian.
         */
        fun tokenize(text: String): List<String> =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length > 1 && it !in STOP_WORDS }
    }
}
