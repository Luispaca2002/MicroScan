package com.bioscanlab.app.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.bioscanlab.app.detection.Detection
import com.bioscanlab.app.detection.DetectionResult
import kotlin.math.roundToInt

/**
 * Dibuja TODAS las detecciones del frame actual y resuelve el tap del usuario.
 *
 * Va encima del PreviewView; el PreviewView no recibe toques porque este Canvas
 * los consume, que es justo lo que se quiere.
 */
@Composable
fun DetectionOverlay(
    result: DetectionResult,
    onDetectionSelected: (Detection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    // El lambda del pointerInput se captura una sola vez: rememberUpdatedState
    // evita reiniciar el detector de gestos en cada recomposicion por frame.
    val currentResult = rememberUpdatedState(result)
    val currentOnSelected = rememberUpdatedState(onDetectionSelected)

    val labelStyle = remember {
        TextStyle(color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val snapshot = currentResult.value
                val mapper = BoxMapper(snapshot.frameWidth, snapshot.frameHeight, size.toSize())
                mapper.pick(snapshot.detections, offset)?.let(currentOnSelected.value)
            }
        },
    ) {
        val mapper = BoxMapper(result.frameWidth, result.frameHeight, Size(size.width, size.height))
        val strokeWidth = 3.dp.toPx()
        val padding = 4.dp.toPx()
        val corner = 6.dp.toPx()

        result.detections.forEach { detection ->
            val rect = mapper.toViewRect(detection)
            val color = colorForClass(detection.classId)

            drawRect(
                color = color,
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(width = strokeWidth),
            )

            val text = "${detection.label}  ${(detection.score * 100).roundToInt()}%"
            val layout = textMeasurer.measure(
                text = text,
                style = labelStyle,
                maxLines = 2,
                constraints = Constraints(
                    maxWidth = (size.width - 2 * padding).toInt().coerceAtLeast(1),
                ),
            )

            // La etiqueta va arriba de la caja, salvo que no entre (caja pegada
            // al borde superior), en cuyo caso cae adentro.
            val chipHeight = layout.size.height + padding
            val chipTop = if (rect.top - chipHeight >= 0f) rect.top - chipHeight else rect.top
            val chipWidth = (layout.size.width + 2 * padding)
                .coerceAtMost(size.width - rect.left)

            drawRoundRect(
                color = color,
                topLeft = Offset(rect.left, chipTop),
                size = Size(chipWidth, chipHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(rect.left + padding, chipTop + padding / 2f),
            )
        }
    }
}

/**
 * Color estable por clase: la misma clase siempre se dibuja del mismo color en
 * todos los frames, asi el usuario puede seguir visualmente un equipo aunque
 * haya varios en pantalla.
 */
private fun colorForClass(classId: Int): Color = CLASS_COLORS[classId % CLASS_COLORS.size]

private val CLASS_COLORS = listOf(
    Color(0xFF4DD0E1), Color(0xFF81C784), Color(0xFFFFB74D), Color(0xFFE57373),
    Color(0xFFBA68C8), Color(0xFF64B5F6), Color(0xFFFFF176), Color(0xFF4DB6AC),
    Color(0xFFF06292), Color(0xFFAED581), Color(0xFF9575CD), Color(0xFFFF8A65),
    Color(0xFF7986CB), Color(0xFFDCE775),
)
