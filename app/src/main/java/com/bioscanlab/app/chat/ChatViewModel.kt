package com.bioscanlab.app.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bioscanlab.app.nav.Routes
import com.bioscanlab.app.rag.RagClient
import com.bioscanlab.app.rag.RagProvider
import com.bioscanlab.app.rag.SourceRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val fromUser: Boolean,
    val sources: List<SourceRef> = emptyList(),
    /** false marca las respuestas en las que el corpus no alcanzo o hay error. */
    val grounded: Boolean = true,
    val isOfficialGuide: Boolean = true,
    val disclaimer: String? = null,
)

data class ChatUiState(
    val equipmentName: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val isResponding: Boolean = false,
)

/**
 * Chat con el asistente del laboratorio.
 * Soporta tanto el modo servidor como el modo autónomo inteligente integrado.
 */
class ChatViewModel @JvmOverloads constructor(
    savedStateHandle: SavedStateHandle,
    private val ragClient: RagClient = RagProvider.get(),
) : ViewModel() {

    private val equipmentName: String =
        savedStateHandle.get<String>(Routes.ARG_EQUIPMENT).orEmpty()

    private val _uiState = MutableStateFlow(
        ChatUiState(
            equipmentName = equipmentName,
            messages = listOf(
                ChatMessage(
                    text = "Equipo detectado: $equipmentName.\n\nPuedo responder sobre su función, componentes, procedimiento de uso, equipo de protección personal (EPP), riesgos y mantenimiento según los documentos oficiales del laboratorio.",
                    fromUser = false,
                    isOfficialGuide = true,
                ),
            ),
        ),
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun send(text: String) {
        val question = text.trim()
        if (question.isEmpty() || _uiState.value.isResponding) return

        _uiState.update {
            it.copy(
                messages = it.messages + ChatMessage(question, fromUser = true),
                isResponding = true,
            )
        }

        viewModelScope.launch {
            val answer = runCatching { ragClient.ask(equipmentName, question) }.getOrNull()

            val reply = if (answer == null) {
                ChatMessage(
                    text = "No pude conectarme con el asistente del laboratorio. " +
                        "Verifica tu conexión a internet o la configuración del servidor.",
                    fromUser = false,
                    grounded = false,
                    isOfficialGuide = false,
                )
            } else {
                ChatMessage(
                    text = answer.text,
                    fromUser = false,
                    sources = answer.sources,
                    grounded = answer.hasEnoughInfo,
                    isOfficialGuide = answer.isOfficialGuide,
                    disclaimer = answer.disclaimer,
                )
            }

            _uiState.update {
                it.copy(messages = it.messages + reply, isResponding = false)
            }
        }
    }
}
