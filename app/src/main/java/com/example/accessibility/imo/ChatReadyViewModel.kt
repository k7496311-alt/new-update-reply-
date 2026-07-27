package com.example.accessibility.imo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Chat Ready Detection.
 */
sealed class ChatReadyUiState {
    object Idle : ChatReadyUiState()
    object Waiting : ChatReadyUiState()
    data class Ready(val result: ChatReadyResult) : ChatReadyUiState()
    data class Timeout(val result: ChatReadyResult) : ChatReadyUiState()
}

/**
 * ViewModel for Chat Ready Detection interactions following MVVM pattern.
 */
class ChatReadyViewModel(
    private val detectorEngine: ChatReadyDetectorEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatReadyUiState>(ChatReadyUiState.Idle)
    val uiState: StateFlow<ChatReadyUiState> = _uiState.asStateFlow()

    fun startDetection(timeoutMs: Long = 5000L) {
        viewModelScope.launch {
            _uiState.value = ChatReadyUiState.Waiting
            val result = detectorEngine.detectChatReady(timeoutMs)
            _uiState.value = when (result.status) {
                ChatReadyStatus.CHAT_READY -> ChatReadyUiState.Ready(result)
                ChatReadyStatus.CHAT_TIMEOUT -> ChatReadyUiState.Timeout(result)
            }
        }
    }
}
