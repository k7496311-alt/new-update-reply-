package com.example.reply.duplicate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Duplicate Reply Prevention Operations.
 */
sealed class DuplicatePreventionUiState {
    object Idle : DuplicatePreventionUiState()
    object Evaluating : DuplicatePreventionUiState()
    data class Allowed(val result: DuplicatePreventionResult) : DuplicatePreventionUiState()
    data class Blocked(val result: DuplicatePreventionResult) : DuplicatePreventionUiState()
    data class Error(val message: String) : DuplicatePreventionUiState()
}

/**
 * ViewModel for Duplicate Prevention Engine following MVVM architecture.
 */
class DuplicatePreventionViewModel(
    private val preventionEngine: DuplicatePreventionEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<DuplicatePreventionUiState>(DuplicatePreventionUiState.Idle)
    val uiState: StateFlow<DuplicatePreventionUiState> = _uiState.asStateFlow()

    fun evaluate(criteria: DuplicateCheckCriteria) {
        viewModelScope.launch {
            _uiState.value = DuplicatePreventionUiState.Evaluating
            try {
                val result = preventionEngine.evaluate(criteria)
                _uiState.value = when (result.status) {
                    DuplicatePreventionStatus.ALLOW -> DuplicatePreventionUiState.Allowed(result)
                    DuplicatePreventionStatus.BLOCK -> DuplicatePreventionUiState.Blocked(result)
                }
            } catch (e: Exception) {
                _uiState.value = DuplicatePreventionUiState.Error("Duplicate evaluation error: ${e.message}")
            }
        }
    }
}
