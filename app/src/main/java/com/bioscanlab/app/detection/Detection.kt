package com.bioscanlab.app.detection

/**
 * Una deteccion en coordenadas NORMALIZADAS [0..1] relativas al frame original
 * de la camara (no al tensor 640x640: el letterbox ya fue revertido).
 *
 * left/top/right/bottom son esquinas, no centro+tamano.
 */
data class Detection(
    val classId: Int,
    val score: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val label: String get() = LabEquipment.nameOf(classId)

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = (width.coerceAtLeast(0f)) * (height.coerceAtLeast(0f))

    fun contains(x: Float, y: Float): Boolean =
        x in left..right && y in top..bottom
}

/** Resultado de un frame completo, con timings para poder diagnosticar performance. */
data class DetectionResult(
    val detections: List<Detection>,
    val inferenceMs: Long,
    /** Ancho/alto del frame de camara ya rotado a orientacion de display. */
    val frameWidth: Int,
    val frameHeight: Int,
) {
    companion object {
        val EMPTY = DetectionResult(emptyList(), 0L, 0, 0)
    }
}
