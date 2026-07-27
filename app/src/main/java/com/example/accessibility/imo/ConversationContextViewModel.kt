package com.example.accessibility.imo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Conversation Context Engine.
 */
sealed class ConversationContextUiState {
    object Idle : ConversationContextUiState()
    object Building : ConversationContextUiState()
    data class Success(val contextModel: ConversationContextModel) : ConversationContextUiState()
    data class Empty(val contextModel: ConversationContextModel) : ConversationContextUiState()
    data class Error(val message: String) : ConversationContextUiState()
}

/**
 * ViewModel for Conversation Context operations following MVVM pattern.
 */
class ConversationContextViewModel(
    private val contextEngine: ConversationContextEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConversationContextUiState>(ConversationContextUiState.Idle)
    val uiState: StateFlow<ConversationContextUiState> = _uiState.asStateFlow()

    fun buildConversationContext(
        extractedMessages: List<ExtractedTextModel>,
        maxMessagesToInclude: Int = 20
    ) {
        viewModelScope.launch {
            _uiState.value = ConversationContextUiState.Building
            try {
                val model = contextEngine.buildContext(extractedMessages, maxMessagesToInclude)
                _uiState.value = if (model.totalMessages > 0) {
                    ConversationContextUiState.Success(model)
                } else {
                    ConversationContextUiState.Empty(model)
                }
            } catch (e: Exception) {
                _uiState.value = ConversationContextUiState.Error("Failed to build conversation context: ${e.message}")
            }
        }
    }
}
