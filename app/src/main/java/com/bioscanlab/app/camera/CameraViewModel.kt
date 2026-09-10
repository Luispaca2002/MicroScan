package com.bioscanlab.app.camera

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bioscanlab.app.detection.DetectionResult
import com.bioscanlab.app.detection.EquipmentDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DetectorState {
    data object Loading : DetectorState
    data class Ready(val accelerator: EquipmentDetector.Accelerator) : DetectorState
    data class Failed(val message: String) : DetectorState
}

/**
 * Duena del ciclo de vida del [EquipmentDetector]: se crea una sola vez y
 * sobrevive a los cambios de configuracion, para no pagar la carga del modelo
 * (~3 MB + init del delegate) en cada rotacion de pantalla.
 */
class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _detectorState = MutableStateFlow<DetectorState>(DetectorState.Loading)
    val detectorState: StateFlow<DetectorState> = _detectorState.asStateFlow()

    private val _detections = MutableStateFlow(DetectionResult.EMPTY)
    val detections: StateFlow<DetectionResult> = _detections.asStateFlow()

    @Volatile
    var detector: EquipmentDetector? = null
        private set

    init {
        viewModelScope.launch {
            // La carga del modelo hace I/O y compila el delegate: fuera del UI thread.
            val state = withContext(Dispatchers.IO) {
                runCatching { EquipmentDetector.create(getApplication()) }
            }
            state
                .onSuccess {
                    detector = it
                    _detectorState.value = DetectorState.Ready(it.accelerator)
                }
                .onFailure {
                    _detectorState.value =
                        DetectorState.Failed(it.message ?: "No se pudo cargar el modelo")
                }
        }
    }

    /** Llamado desde el hilo de analisis de CameraX, no desde el UI thread. */
    fun publish(result: DetectionResult) {
        _detections.value = result
    }

    override fun onCleared() {
        super.onCleared()
        detector?.close()
        detector = null
    }
}
