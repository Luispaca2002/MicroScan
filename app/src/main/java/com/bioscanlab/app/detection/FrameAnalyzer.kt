package com.bioscanlab.app.detection

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * Analyzer de CameraX que corre la deteccion fuera del UI thread.
 *
 * CameraX ya entrega los frames en su propio executor, asi que la inferencia
 * nunca toca el hilo principal. El throttling real lo da la combinacion de:
 *  - STRATEGY_KEEP_ONLY_LATEST en el ImageAnalysis (descarta los frames que
 *    llegan mientras este analyzer sigue ocupado), y
 *  - [minIntervalMs], que pone un techo a la frecuencia de inferencia para no
 *    dejar el SoC al 100% cuando el modelo corre mas rapido de lo necesario.
 *
 * Esto es preferible a "procesar 1 de cada N frames" con un contador: con un
 * contador fijo, en un telefono lento igual se encolan frames y el preview se
 * atrasa; aca siempre se procesa el frame mas reciente disponible.
 */
class FrameAnalyzer(
    private val detector: EquipmentDetector,
    private val minIntervalMs: Long = 100L,
    private val onResult: (DetectionResult) -> Unit,
) : ImageAnalysis.Analyzer {

    private var lastRunAt = 0L
    private val rotation = Matrix()

    override fun analyze(image: ImageProxy) {
        image.use { proxy ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastRunAt < minIntervalMs) return
            lastRunAt = now

            val bitmap = proxy.toUprightBitmap() ?: return
            val result = detector.detect(bitmap)
            bitmap.recycle()
            onResult(result)
        }
    }

    /**
     * Convierte el frame a un Bitmap ya rotado a la orientacion en que el
     * usuario lo ve. Sin esta rotacion el modelo veria la escena acostada y las
     * cajas caerian en el lugar equivocado del overlay.
     */
    private fun ImageProxy.toUprightBitmap(): Bitmap? {
        val source = try {
            toBitmap()
        } catch (_: IllegalArgumentException) {
            return null
        }

        val degrees = imageInfo.rotationDegrees
        if (degrees == 0) return source

        rotation.reset()
        rotation.postRotate(degrees.toFloat())
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, rotation, true)
        if (rotated !== source) source.recycle()
        return rotated
    }
}
