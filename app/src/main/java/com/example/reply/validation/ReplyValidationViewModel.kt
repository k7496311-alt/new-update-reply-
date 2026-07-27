package com.example.reply.validation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Reply Validation Engine.
 */
sealed class ReplyValidationUiState {
    object Idle : ReplyValidationUiState()
    object Validating : ReplyValidationUiState()
    data class Valid(val result: ReplyValidationResult) : ReplyValidationUiState()
    data class Invalid(val result: ReplyValidationResult) : ReplyValidationUiState()
    data class Error(val message: String) : ReplyValidationUiState()
}

/**
 * ViewModel for Reply Validation operations following MVVM pattern.
 */
class ReplyValidationViewModel(
    private val validationEngine: ReplyValidationEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReplyValidationUiState>(ReplyValidationUiState.Idle)
    val uiState: StateFlow<ReplyValidationUiState> = _uiState.asStateFlow()

    fun validateReply(
        replyText: String?,
        criteria: ReplyValidationCriteria = ReplyValidationCriteria()
    ) {
        viewModelScope.launch {
            _uiState.value = ReplyValidationUiState.Validating
            try {
                val result = validationEngine.validateReply(replyText, criteria)
                _uiState.value = if (result.isValid) {
                    ReplyValidationUiState.Valid(result)
                } else {
                    ReplyValidationUiState.Invalid(result)
                }
            } catch (e: Exception) {
                _uiState.value = ReplyValidationUiState.Error("Validation execution error: ${e.message}")
            }
        }
    }
}
