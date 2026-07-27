package com.example.reply

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AutoReplyRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Final Reply Generation.
 */
sealed class FinalReplyUiState {
    object Idle : FinalReplyUiState()
    object Generating : FinalReplyUiState()
    data class Success(val result: FinalReplyResult) : FinalReplyUiState()
    data class Empty(val result: FinalReplyResult) : FinalReplyUiState()
    data class Error(val message: String) : FinalReplyUiState()
}

/**
 * ViewModel for Final Reply Engine following MVVM architecture.
 */
class FinalReplyViewModel(
    private val replyEngine: FinalReplyEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<FinalReplyUiState>(FinalReplyUiState.Idle)
    val uiState: StateFlow<FinalReplyUiState> = _uiState.asStateFlow()

    fun generateReply(
        selectedRule: AutoReplyRule,
        customerName: String? = null,
        currentTimeMillis: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            _uiState.value = FinalReplyUiState.Generating
            try {
                val result = replyEngine.generateReply(selectedRule, customerName, currentTimeMillis)
                _uiState.value = when (result.status) {
                    FinalReplyStatus.SUCCESS -> FinalReplyUiState.Success(result)
                    FinalReplyStatus.EMPTY_REPLY -> FinalReplyUiState.Empty(result)
                    FinalReplyStatus.ERROR -> FinalReplyUiState.Error(result.details)
                }
            } catch (e: Exception) {
                _uiState.value = FinalReplyUiState.Error("Final reply generation failed: ${e.message}")
            }
        }
    }
}
