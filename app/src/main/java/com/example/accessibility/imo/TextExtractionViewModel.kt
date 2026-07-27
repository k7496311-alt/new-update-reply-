package com.example.accessibility.imo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Chat Text Extraction operations.
 */
sealed class TextExtractionUiState {
    object Idle : TextExtractionUiState()
    object Extracting : TextExtractionUiState()
    data class Success(val result: TextExtractionResult) : TextExtractionUiState()
    data class Empty(val result: TextExtractionResult) : TextExtractionUiState()
    data class Error(val message: String) : TextExtractionUiState()
}

/**
 * ViewModel for Chat Text Extraction Engine following MVVM architecture.
 */
class TextExtractionViewModel(
    private val extractionEngine: TextExtractionEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<TextExtractionUiState>(TextExtractionUiState.Idle)
    val uiState: StateFlow<TextExtractionUiState> = _uiState.asStateFlow()

    fun extractText(bubbles: List<MessageBubbleModel>) {
        viewModelScope.launch {
            _uiState.value = TextExtractionUiState.Extracting
            try {
                val result = extractionEngine.extractText(bubbles)
                _uiState.value = when (result.status) {
                    TextExtractionStatus.SUCCESS -> TextExtractionUiState.Success(result)
                    TextExtractionStatus.EMPTY_RESULT -> TextExtractionUiState.Empty(result)
                }
            } catch (e: Exception) {
                _uiState.value = TextExtractionUiState.Error("Text extraction failed: ${e.message}")
            }
        }
    }
}
