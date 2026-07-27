package com.example.accessibility.imo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Message Bubble Detection Engine.
 */
sealed class MessageBubbleUiState {
    object Idle : MessageBubbleUiState()
    object Detecting : MessageBubbleUiState()
    data class Success(val result: MessageBubbleDetectionResult) : MessageBubbleUiState()
    data class Error(val message: String) : MessageBubbleUiState()
}

/**
 * ViewModel for Message Bubble Detection interactions following MVVM architecture.
 */
class MessageBubbleViewModel(
    private val detectionEngine: MessageBubbleDetectionEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<MessageBubbleUiState>(MessageBubbleUiState.Idle)
    val uiState: StateFlow<MessageBubbleUiState> = _uiState.asStateFlow()

    fun detectBubbles(scanReport: UiScanReport? = null) {
        viewModelScope.launch {
            _uiState.value = MessageBubbleUiState.Detecting
            try {
                val result = detectionEngine.detectAndClassifyBubbles(scanReport)
                _uiState.value = MessageBubbleUiState.Success(result)
            } catch (e: Exception) {
                _uiState.value = MessageBubbleUiState.Error("Bubble detection failed: ${e.message}")
            }
        }
    }
}
