package com.bioscanlab.server

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RagEngineTest {

    private val corpusDir: File
        get() = listOf(File("corpus"), File("server/corpus"), File("../server/corpus"))
            .firstOrNull { it.isDirectory }
            ?: error("No se encontró el directorio de corpus")

    @Test
    fun `corpus carga fragmentos correctamente y excluye secciones administrativas`() {
        val chunks = Corpus.load(corpusDir)
        assertTrue(chunks.isNotEmpty(), "El corpus no debería estar vacío")

        val equipments = chunks.map { it.equipment }.distinct()
        // No debe contener el índice
        assertFalse(equipments.any { it.contains("INDICE", ignoreCase = true) })
        // Debe contener los equipos documentados
        assertTrue(equipments.any { it.contains("Balanza", ignoreCase = true) })
        assertTrue(equipments.any { it.contains("Baño María", ignoreCase = true) })
        assertTrue(equipments.any { it.contains("Incubadora Memmert", ignoreCase = true) })
        assertTrue(equipments.any { it.contains("Olympus CX22", ignoreCase = true) })
    }

    @Test
    fun `bm25 cubre las 14 clases del modelo y rechaza equipos no existentes`() {
        val chunks = Corpus.load(corpusDir)
        val bm25 = Bm25(chunks)

        // Las 14 clases del modelo YOLO deben encontrar fragmentos (byEquipment)
        val all14Classes = listOf(
            "Agitador Orbital JOANLAB OS-20",
            "Autoclave ALL AMERICAN 25X-1",
            "Balanza analitica PR Series Analytical",
            "Bano maria  Memmert WNB-14",
            "Cabina de flujo laminar horizontal -PIVAS- BBS-H1500B BBS-H1800B",
            "Centrifugadora Sigma 201",
            "Espectofotometro UV-5100B",
            "Estereo Microscopio Binocular Modelo BS-80",
            "Estereo Microscopio Thomas Scientific",
            "Estufa de secado memmert ULE 600",
            "Incubadora de laboratorio SMI6",
            "Microscopio Motic RED 220",
            "Microscopio Olympus CX22 LED",
            "incubadora memmert in110",
        )

        for (className in all14Classes) {
            val hits = bm25.byEquipment(className)
            assertTrue(hits.isNotEmpty(), "La clase del modelo '$className' debe tener fragmentos")
        }

        // Equipos no existentes en el laboratorio NO deben vincularse por error (anti-alucinación de grounding)
        val nonExistent = listOf(
            "Secuenciador de ADN Illumina MiniSeq",
            "Ultracongelador Thermo Scientific -80C",
            "Robot pipeteador Opentrons OT-2",
            "Liofilizador de laboratorio Labconco FreeZone",
        )

        for (className in nonExistent) {
            val hits = bm25.byEquipment(className)
            assertTrue(hits.isEmpty(), "El equipo no existente '$className' NO debe vincular fragmentos por error")
        }
    }

    @Test
    fun `ragService arma la ficha tecnica determinista sin pasar por el LLM`() {
        val chunks = Corpus.load(corpusDir)
        val dummyLlm = object : Llm {
            override val name = "dummy"
            override suspend fun complete(systemPrompt: String, userMessage: String): String {
                error("No se debe invocar el LLM para la ficha técnica")
            }
        }
        val rag = RagService(chunks, dummyLlm)

        val sheet = rag.sheet("Balanza analitica PR Series Analytical")
        assertEquals("Balanza analitica PR Series Analytical", sheet.className)
        assertTrue(sheet.funcion.isNotBlank(), "Debe tener descripción/función")
        assertTrue(sheet.procedimiento.isNotEmpty(), "Debe tener pasos de procedimiento")
        assertTrue(sheet.riesgos.isNotEmpty(), "Debe tener precauciones/riesgos")
        assertTrue(sheet.sources.isNotEmpty(), "Debe incluir fuentes")

        // Verificar también uno de los nuevos equipos añadidos (ej. Agitador Orbital)
        val agitadorSheet = rag.sheet("Agitador Orbital JOANLAB OS-20")
        assertEquals("Agitador Orbital JOANLAB OS-20", agitadorSheet.className)
        assertTrue(agitadorSheet.funcion.isNotBlank(), "Agitador debe tener función")
        assertTrue(agitadorSheet.procedimiento.isNotEmpty(), "Agitador debe tener procedimiento")
        assertTrue(agitadorSheet.riesgos.isNotEmpty(), "Agitador debe tener riesgos")
    }

    @Test
    fun `ragService ask rechaza preguntas de equipos sin documentacion sin llamar al LLM`() = runBlocking {
        val chunks = Corpus.load(corpusDir)
        var llmCalled = false
        val dummyLlm = object : Llm {
            override val name = "dummy"
            override suspend fun complete(systemPrompt: String, userMessage: String): String {
                llmCalled = true
                return "Respuesta inventada"
            }
        }
        val rag = RagService(chunks, dummyLlm)

        val answer = rag.ask("Secuenciador de ADN Illumina MiniSeq", "¿Cómo se enciende?")
        assertFalse(llmCalled, "No se debe llamar al LLM si no hay fragmentos en el corpus")
        assertFalse(answer.hasEnoughInfo)
        assertTrue(answer.text.contains("No se encuentra información"))
    }

    @Test
    fun `ragService ask maneja el centinela de falta de informacion devuelto por el LLM`() = runBlocking {
        val chunks = Corpus.load(corpusDir)
        val sentinelLlm = object : Llm {
            override val name = "sentinel-mock"
            override suspend fun complete(systemPrompt: String, userMessage: String): String {
                return "SIN_INFORMACION_SUFICIENTE"
            }
        }
        val rag = RagService(chunks, sentinelLlm)

        val answer = rag.ask("Bano maria  Memmert WNB-14", "¿Cuál es el precio de compra?")
        assertFalse(answer.hasEnoughInfo)
        assertTrue(answer.text.contains("No se encuentra información"))
    }

    @Test
    fun `ragService ask maneja preguntas fuera del ambito del laboratorio`() = runBlocking {
        val chunks = Corpus.load(corpusDir)
        val outOfScopeLlm = object : Llm {
            override val name = "out-of-scope-mock"
            override suspend fun complete(systemPrompt: String, userMessage: String): String {
                return "FUERA_DE_AMBITO_LABORATORIO"
            }
        }
        val rag = RagService(chunks, outOfScopeLlm)

        val answer = rag.ask("Agitador Orbital JOANLAB OS-20", "¿Quién ganó el último partido de fútbol?")
        assertFalse(answer.hasEnoughInfo)
        assertTrue(answer.text.contains("Solo puedo responder preguntas relacionadas con el laboratorio"))
    }

    @Test
    fun `llmFactory resuelve Gemini desde local properties`() {
        val llm = LlmFactory.fromEnvironment()
        assertNotNull(llm)
        assertTrue(llm.name.startsWith("gemini:"), "Debe resolver proveedor Gemini por defecto con la clave configurada")
    }

    @Test
    fun `gemini llm responde pregunta tecnica fundamentada usando el corpus real`() = runBlocking {
        val key = java.util.Properties().apply {
            listOf(File("local.properties"), File("../local.properties"))
                .firstOrNull { it.isFile }
                ?.inputStream()
                ?.use { load(it) }
        }.getProperty("gemini.apiKey") ?: return@runBlocking

        val llm = GeminiLlm(key, "gemini-3.5-flash-lite")
        val chunks = Corpus.load(corpusDir)
        val rag = RagService(chunks, llm)

        val answer = rag.ask("Balanza analitica PR Series Analytical", "¿Cual es la capacidad maxima o limite de masa?")
        println("Respuesta Gemini Balanza: ${answer.text}")
        assertTrue(answer.hasEnoughInfo, "Debe tener información suficiente")
        assertTrue(answer.text.isNotBlank(), "La respuesta no debe estar vacía")
        assertTrue(answer.sources.isNotEmpty(), "Debe incluir fuentes")

        val agitadorAns = rag.ask("Agitador Orbital JOANLAB OS-20", "¿Cómo enciendo esta máquina?")
        println("Respuesta Gemini Agitador: ${agitadorAns.text}")
        assertTrue(agitadorAns.hasEnoughInfo, "Debe responder con información sobre encendido")
        assertTrue(agitadorAns.text.lowercase().contains("interruptor") || agitadorAns.text.lowercase().contains("encender") || agitadorAns.text.lowercase().contains("start") || agitadorAns.text.lowercase().contains("inicio"))

        val outOfScopeAns = rag.ask("Agitador Orbital JOANLAB OS-20", "¿Cómo preparo una pizza margarita?")
        println("Respuesta Gemini OutOfScope: ${outOfScopeAns.text}")
        assertFalse(outOfScopeAns.hasEnoughInfo, "Preguntas ajenas no deben considerarse dentro de ámbito")
        assertTrue(outOfScopeAns.text.contains("Solo puedo responder preguntas relacionadas con el laboratorio"))
    }
}
