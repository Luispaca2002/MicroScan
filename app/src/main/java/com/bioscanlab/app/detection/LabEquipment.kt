package com.bioscanlab.app.detection

/**
 * Clases del modelo, en el orden exacto de indice del data.yaml con el que se
 * entreno best_int8.tflite. Verificado contra el metadata.json embebido en el
 * propio .tflite (Ultralytics 8.4.138, YOLO11n).
 *
 * El orden de esta lista ES el contrato con el modelo: no reordenar ni insertar.
 * Las cadenas se transcriben LITERALES del data.yaml, incluido el doble espacio
 * de "Bano maria  Memmert WNB-14": es como el modelo declara la clase, y
 * cualquier busqueda por nombre exacto tiene que encontrarla igual.
 */
object LabEquipment {

    val CLASS_NAMES: List<String> = listOf(
        "Agitador Orbital JOANLAB OS-20",                                   // 0
        "Autoclave ALL AMERICAN 25X-1",                                     // 1
        "Balanza analitica PR Series Analytical",                           // 2
        "Bano maria  Memmert WNB-14",                                       // 3
        "Cabina de flujo laminar horizontal -PIVAS- BBS-H1500B BBS-H1800B", // 4
        "Centrifugadora Sigma 201",                                         // 5
        "Espectofotometro UV-5100B",                                        // 6
        "Estereo Microscopio Binocular Modelo BS-80",                       // 7
        "Estereo Microscopio Thomas Scientific",                            // 8
        "Estufa de secado memmert ULE 600",                                 // 9
        "Incubadora de laboratorio SMI6",                                   // 10
        "Microscopio Motic RED 220",                                        // 11
        "Microscopio Olympus CX22 LED",                                     // 12
        "incubadora memmert in110",                                         // 13
    )

    val NUM_CLASSES: Int = CLASS_NAMES.size

    fun nameOf(classId: Int): String =
        CLASS_NAMES.getOrElse(classId) { "Desconocido ($classId)" }
}
