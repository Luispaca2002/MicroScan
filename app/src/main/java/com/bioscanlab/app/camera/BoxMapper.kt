package com.bioscanlab.app.camera

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.bioscanlab.app.detection.Detection

/**
 * Traduce entre coordenadas normalizadas del frame y pixeles del overlay.
 *
 * PreviewView esta configurado con FILL_CENTER, o sea que recorta el frame para
 * llenar la vista. El overlay tiene que aplicar exactamente la misma
 * transformacion, o las cajas quedarian desplazadas respecto de lo que se ve.
 *
 * Dibujado y hit-testing usan este mismo mapper (uno la ida, el otro la vuelta),
 * asi es imposible que se desincronicen.
 */
class BoxMapper(
    frameWidth: Int,
    frameHeight: Int,
    private val viewSize: Size,
) {
    private val valid = frameWidth > 0 && frameHeight > 0 &&
        viewSize.width > 0f && viewSize.height > 0f

    // FILL_CENTER = escalar por el factor MAYOR y recortar el sobrante centrado.
    private val scale = if (valid) {
        maxOf(viewSize.width / frameWidth, viewSize.height / frameHeight)
    } else 1f

    private val scaledWidth = frameWidth * scale
    private val scaledHeight = frameHeight * scale
    private val offsetX = (viewSize.width - scaledWidth) / 2f
    private val offsetY = (viewSize.height - scaledHeight) / 2f

    /** Normalizado [0..1] -> pixeles del overlay. */
    fun toViewRect(detection: Detection): Rect = Rect(
        left = offsetX + detection.left * scaledWidth,
        top = offsetY + detection.top * scaledHeight,
        right = offsetX + detection.right * scaledWidth,
        bottom = offsetY + detection.bottom * scaledHeight,
    )

    /** Pixeles del overlay -> normalizado [0..1]. Inversa exacta de [toViewRect]. */
    fun toNormalized(point: Offset): Offset = Offset(
        x = (point.x - offsetX) / scaledWidth,
        y = (point.y - offsetY) / scaledHeight,
    )

    /**
     * Elige que deteccion selecciono el usuario al tocar [point].
     *
     * Heuristica: entre todas las cajas que contienen el punto, gana la de MENOR
     * AREA; si dos tienen area casi identica (dentro del 5%), desempata la de
     * mayor confianza.
     *
     * El motivo de priorizar la mas chica: cuando una caja esta contenida en
     * otra (un microscopio encuadrado dentro de una cabina de flujo laminar, por
     * ejemplo), la grande cubre a la chica por completo. Si ganara la grande,
     * el equipo de adentro seria literalmente imposible de seleccionar. Al reves
     * no pasa: la caja grande siempre conserva area propia fuera de la chica,
     * donde el usuario puede tocarla.
     */
    fun pick(detections: List<Detection>, point: Offset): Detection? {
        if (!valid) return null
        val normalized = toNormalized(point)
        val hits = detections.filter { it.contains(normalized.x, normalized.y) }
        if (hits.isEmpty()) return null

        val smallest = hits.minBy { it.area }
        return hits
            .filter { it.area <= smallest.area * AREA_TIE_TOLERANCE }
            .maxBy { it.score }
    }

    private companion object {
        const val AREA_TIE_TOLERANCE = 1.05f
    }
}
