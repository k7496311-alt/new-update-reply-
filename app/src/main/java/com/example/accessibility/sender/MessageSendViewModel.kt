package com.example.accessibility.sender

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Message Send operations.
 */
sealed class MessageSendUiState {
    object Idle : MessageSendUiState()
    object Sending : MessageSendUiState()
    data class Success(val result: MessageSendResult) : MessageSendUiState()
    data class Failed(val result: MessageSendResult) : MessageSendUiState()
    data class Error(val message: String) : MessageSendUiState()
}

/**
 * ViewModel for Message Send Engine following MVVM architecture.
 */
class MessageSendViewModel(
    private val sendEngine: MessageSendEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<MessageSendUiState>(MessageSendUiState.Idle)
    val uiState: StateFlow<MessageSendUiState> = _uiState.asStateFlow()

    fun performSend(criteria: MessageSendCriteria) {
        viewModelScope.launch {
            _uiState.value = MessageSendUiState.Sending
            try {
                val result = sendEngine.executeSend(criteria)
                _uiState.value = if (result.isSuccess) {
                    MessageSendUiState.Success(result)
                } else {
                    MessageSendUiState.Failed(result)
                }
            } catch (e: Exception) {
                _uiState.value = MessageSendUiState.Error("Message send failed: ${e.message}")
            }
        }
    }
}
