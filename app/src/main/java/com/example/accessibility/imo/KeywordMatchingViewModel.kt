package com.example.accessibility.imo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Keyword Matching Engine.
 */
sealed class KeywordMatchingUiState {
    object Idle : KeywordMatchingUiState()
    object Matching : KeywordMatchingUiState()
    data class Success(val result: KeywordMatchResult) : KeywordMatchingUiState()
    data class NoMatch(val result: KeywordMatchResult) : KeywordMatchingUiState()
    data class Error(val message: String) : KeywordMatchingUiState()
}

/**
 * ViewModel for Keyword Matching operations following MVVM architecture.
 */
class KeywordMatchingViewModel(
    private val matchingEngine: KeywordMatchingEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<KeywordMatchingUiState>(KeywordMatchingUiState.Idle)
    val uiState: StateFlow<KeywordMatchingUiState> = _uiState.asStateFlow()

    fun matchKeywords(
        conversationContext: ConversationContextModel,
        rules: List<KeywordMatchRule>? = null
    ) {
        viewModelScope.launch {
            _uiState.value = KeywordMatchingUiState.Matching
            try {
                val result = matchingEngine.match(conversationContext, rules)
                _uiState.value = when (result.status) {
                    KeywordMatchStatus.MATCHED -> KeywordMatchingUiState.Success(result)
                    KeywordMatchStatus.NO_MATCH -> KeywordMatchingUiState.NoMatch(result)
                }
            } catch (e: Exception) {
                _uiState.value = KeywordMatchingUiState.Error("Keyword matching failed: ${e.message}")
            }
        }
    }
}
