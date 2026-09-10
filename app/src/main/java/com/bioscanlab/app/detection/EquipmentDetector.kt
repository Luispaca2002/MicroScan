package com.bioscanlab.app.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Motor de inferencia YOLO11 sobre LiteRT para best_int8.tflite.
 *
 * Caracteristicas del modelo, verificadas inspeccionando el .tflite (no asumidas):
 *  - input : [1, 3, 640, 640] float32 en layout NCHW (channel-first, no NHWC).
 *            Es el export nuevo de Ultralytics 8.4.x via ai-edge-torch, que
 *            conserva el layout de PyTorch. Por eso NO se puede usar
 *            TensorImage/ImageProcessor de tflite-support, que son NHWC-only.
 *  - output: [1, 18, 8400] float32, tambien channel-first: 18 = 4 (cx,cy,w,h) + 14 clases.
 *  - Pese al nombre "int8" la interfaz es float32: el modelo lleva quantize /
 *    dequantize internos, asi que aca solo hay que normalizar /255 y listo.
 *  - metadata end2end = false  ->  el modelo NO trae NMS, hay que aplicarlo aca.
 *  - Las cajas salen ya normalizadas a [0..1] respecto del tensor 640x640
 *    (Ultralytics inserta _NormalizeCoords en el grafo).
 *
 * NO es thread-safe: un Interpreter debe usarse desde un solo hilo a la vez.
 * DetectionRepository se encarga de serializar los accesos.
 */
class EquipmentDetector private constructor(
    private val interpreter: Interpreter,
    private val delegate: Delegate?,
    val accelerator: Accelerator,
) : Closeable {

    enum class Accelerator { GPU, NNAPI, CPU }

    // Buffers reutilizados entre frames: asignarlos por frame generaria varios
    // MB/s de basura y provocaria GC visible en el preview.
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(1 * 3 * INPUT_SIZE * INPUT_SIZE * 4).order(ByteOrder.nativeOrder())
    private val outputBuffer: Array<Array<FloatArray>> =
        Array(1) { Array(CHANNELS) { FloatArray(NUM_ANCHORS) } }
    private val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
    private val letterboxed: Bitmap =
        Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
    private val letterboxCanvas = Canvas(letterboxed)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dstRect = Rect()

    /**
     * Corre una inferencia completa sobre [source].
     *
     * Devuelve cajas en coordenadas normalizadas [0..1] respecto de [source]
     * (el padding del letterbox ya esta revertido), listas para dibujarse sobre
     * el preview sin mas correcciones de aspecto.
     */
    fun detect(
        source: Bitmap,
        confidenceThreshold: Float = DEFAULT_CONFIDENCE,
        iouThreshold: Float = DEFAULT_IOU,
    ): DetectionResult {
        val started = SystemClock.elapsedRealtime()

        // Letterbox: escalar manteniendo aspect ratio y rellenar con gris 114
        // (el mismo valor de padding que usa Ultralytics al entrenar/validar).
        // Deformar la imagen a 640x640 degradaria el mAP en equipos altos y
        // angostos como los microscopios.
        val scale = minOf(
            INPUT_SIZE.toFloat() / source.width,
            INPUT_SIZE.toFloat() / source.height,
        )
        val scaledW = (source.width * scale).toInt()
        val scaledH = (source.height * scale).toInt()
        val padX = (INPUT_SIZE - scaledW) / 2
        val padY = (INPUT_SIZE - scaledH) / 2

        letterboxCanvas.drawColor(PAD_COLOR)
        dstRect.set(padX, padY, padX + scaledW, padY + scaledH)
        letterboxCanvas.drawBitmap(source, null, dstRect, bitmapPaint)

        writeNchwInput(letterboxed)

        interpreter.run(inputBuffer, outputBuffer)

        val raw = decode(
            out = outputBuffer[0],
            confidenceThreshold = confidenceThreshold,
            scale = scale,
            padX = padX,
            padY = padY,
            srcWidth = source.width,
            srcHeight = source.height,
        )
        val kept = nonMaxSuppression(raw, iouThreshold)

        return DetectionResult(
            detections = kept,
            inferenceMs = SystemClock.elapsedRealtime() - started,
            frameWidth = source.width,
            frameHeight = source.height,
        )
    }

    /**
     * Vuelca el bitmap al buffer de entrada en orden NCHW: primero el plano R
     * completo, despues el G, despues el B. Este es el punto donde es facil
     * equivocarse: si se escribe intercalado (RGBRGB..., que es NHWC) el modelo
     * no falla, simplemente devuelve detecciones sin sentido.
     */
    private fun writeNchwInput(bitmap: Bitmap) {
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        inputBuffer.rewind()
        val floats = inputBuffer.asFloatBuffer()
        val plane = INPUT_SIZE * INPUT_SIZE

        for (i in 0 until plane) {
            val p = pixels[i]
            floats.put(i, ((p shr 16) and 0xFF) / 255f)              // plano R
            floats.put(plane + i, ((p shr 8) and 0xFF) / 255f)       // plano G
            floats.put(2 * plane + i, (p and 0xFF) / 255f)           // plano B
        }
        inputBuffer.rewind()
    }

    /**
     * Recorre las 8400 anclas del tensor channel-first y se queda con las que
     * superan el umbral. YOLO11 no tiene score de objectness: la confianza es
     * directamente el maximo entre las 14 probabilidades de clase.
     */
    private fun decode(
        out: Array<FloatArray>,
        confidenceThreshold: Float,
        scale: Float,
        padX: Int,
        padY: Int,
        srcWidth: Int,
        srcHeight: Int,
    ): MutableList<Detection> {
        val result = mutableListOf<Detection>()
        val cxRow = out[0]
        val cyRow = out[1]
        val wRow = out[2]
        val hRow = out[3]

        for (a in 0 until NUM_ANCHORS) {
            var bestClass = -1
            var bestScore = confidenceThreshold
            for (c in 0 until LabEquipment.NUM_CLASSES) {
                val s = out[BOX_CHANNELS + c][a]
                if (s > bestScore) {
                    bestScore = s
                    bestClass = c
                }
            }
            if (bestClass < 0) continue

            // De normalizado-al-tensor a pixeles del tensor 640x640...
            val cx = cxRow[a] * INPUT_SIZE
            val cy = cyRow[a] * INPUT_SIZE
            val w = wRow[a] * INPUT_SIZE
            val h = hRow[a] * INPUT_SIZE

            // ...y de ahi, quitando padding y escala, a normalizado del frame original.
            val left = ((cx - w / 2f) - padX) / scale / srcWidth
            val top = ((cy - h / 2f) - padY) / scale / srcHeight
            val right = ((cx + w / 2f) - padX) / scale / srcWidth
            val bottom = ((cy + h / 2f) - padY) / scale / srcHeight

            result += Detection(
                classId = bestClass,
                score = bestScore,
                left = left.coerceIn(0f, 1f),
                top = top.coerceIn(0f, 1f),
                right = right.coerceIn(0f, 1f),
                bottom = bottom.coerceIn(0f, 1f),
            )
        }
        return result
    }

    /**
     * NMS por clase: dos equipos distintos pueden solaparse legitimamente en el
     * encuadre (un microscopio delante de una incubadora), asi que suprimir
     * entre clases distintas perderia detecciones validas.
     */
    private fun nonMaxSuppression(
        candidates: MutableList<Detection>,
        iouThreshold: Float,
    ): List<Detection> {
        if (candidates.isEmpty()) return emptyList()

        val kept = mutableListOf<Detection>()
        candidates.groupBy { it.classId }.forEach { (_, group) ->
            val pool = group.sortedByDescending { it.score }.toMutableList()
            while (pool.isNotEmpty()) {
                val best = pool.removeAt(0)
                kept += best
                pool.removeAll { iou(best, it) > iouThreshold }
            }
        }
        return kept
            .sortedByDescending { it.score }
            .take(MAX_DETECTIONS)
    }

    private fun iou(a: Detection, b: Detection): Float {
        val interLeft = maxOf(a.left, b.left)
        val interTop = maxOf(a.top, b.top)
        val interRight = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)
        val interW = interRight - interLeft
        val interH = interBottom - interTop
        if (interW <= 0f || interH <= 0f) return 0f
        val inter = interW * interH
        return inter / (a.area + b.area - inter)
    }

    override fun close() {
        interpreter.close()
        (delegate as? Closeable)?.close()
        letterboxed.recycle()
    }

    companion object {
        private const val TAG = "EquipmentDetector"

        const val MODEL_ASSET = "best_int8.tflite"
        const val INPUT_SIZE = 640
        const val NUM_ANCHORS = 8400
        private const val BOX_CHANNELS = 4
        private const val CHANNELS = BOX_CHANNELS + 14
        private const val MAX_DETECTIONS = 20
        private val PAD_COLOR = Color.rgb(114, 114, 114)

        const val DEFAULT_CONFIDENCE = 0.40f
        const val DEFAULT_IOU = 0.45f

        private val NUM_THREADS: Int
            get() = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

        /**
         * Intenta GPU -> NNAPI -> CPU/XNNPACK, quedandose con el primero que
         * logre inicializar. Este modelo viene de un export stablehlo NCHW, y
         * hay drivers en los que el GPU delegate no puede tomar el grafo entero:
         * por eso el fallback es explicito y se loguea el acelerador elegido en
         * lugar de asumir que la GPU siempre funciona.
         */
        fun create(context: Context): EquipmentDetector {
            val model = loadModel(context)

            CompatibilityList().use { compat ->
                if (compat.isDelegateSupportedOnThisDevice) {
                    try {
                        val gpu = GpuDelegate(compat.bestOptionsForThisDevice)
                        val options = Interpreter.Options().addDelegate(gpu)
                        val interpreter = Interpreter(model, options)
                        Log.i(TAG, "Acelerador: GPU delegate")
                        return EquipmentDetector(interpreter, gpu, Accelerator.GPU)
                    } catch (t: Throwable) {
                        Log.w(TAG, "GPU delegate no disponible, probando NNAPI", t)
                    }
                } else {
                    Log.i(TAG, "GPU delegate no soportado en este dispositivo")
                }
            }

            try {
                val options = Interpreter.Options().apply {
                    setUseNNAPI(true)
                    numThreads = NUM_THREADS
                }
                val interpreter = Interpreter(model, options)
                Log.i(TAG, "Acelerador: NNAPI")
                return EquipmentDetector(interpreter, null, Accelerator.NNAPI)
            } catch (t: Throwable) {
                Log.w(TAG, "NNAPI no disponible, usando CPU/XNNPACK", t)
            }

            val options = Interpreter.Options().apply {
                setUseXNNPACK(true)
                numThreads = NUM_THREADS
            }
            Log.i(TAG, "Acelerador: CPU/XNNPACK")
            return EquipmentDetector(Interpreter(model, options), null, Accelerator.CPU)
        }

        /**
         * Mapea el asset en memoria en lugar de copiarlo al heap. Requiere que
         * el .tflite quede sin comprimir en el APK, cosa que garantiza el
         * noCompress += "tflite" de app/build.gradle.kts.
         */
        private fun loadModel(context: Context): ByteBuffer {
            context.assets.openFd(MODEL_ASSET).use { fd ->
                FileInputStream(fd.fileDescriptor).use { stream ->
                    return stream.channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        fd.startOffset,
                        fd.declaredLength,
                    )
                }
            }
        }
    }
}
