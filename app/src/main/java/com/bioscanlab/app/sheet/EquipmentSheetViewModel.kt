package com.bioscanlab.app.sheet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bioscanlab.app.nav.Routes
import com.bioscanlab.app.rag.EquipmentSheet
import com.bioscanlab.app.rag.RagClient
import com.bioscanlab.app.rag.RagProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SheetUiState(
    val className: String = "",
    val sheet: EquipmentSheet? = null,
    val isLoading: Boolean = true,
    val isConnectionError: Boolean = false,
)

class EquipmentSheetViewModel @JvmOverloads constructor(
    savedStateHandle: SavedStateHandle,
    private val ragClient: RagClient = RagProvider.get(),
) : ViewModel() {

    private val className: String =
        savedStateHandle.get<String>(Routes.ARG_EQUIPMENT).orEmpty()

    private val _uiState = MutableStateFlow(SheetUiState(className = className))
    val uiState: StateFlow<SheetUiState> = _uiState.asStateFlow()

    init {
        loadSheet()
    }

    fun loadSheet() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, isConnectionError = false)
            val result = runCatching { ragClient.sheet(className) }
            val sheet = result.getOrNull()
            _uiState.value = SheetUiState(
                className = className,
                sheet = sheet,
                isLoading = false,
                isConnectionError = result.isFailure,
            )
        }
    }
}
