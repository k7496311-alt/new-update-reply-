package com.example.rule.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AutoReplyRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Rule Selection operations.
 */
sealed class RuleSelectionUiState {
    object Idle : RuleSelectionUiState()
    object Evaluating : RuleSelectionUiState()
    data class Success(val result: RuleSelectionResult) : RuleSelectionUiState()
    data class NoSuitableRule(val result: RuleSelectionResult) : RuleSelectionUiState()
    data class Error(val message: String) : RuleSelectionUiState()
}

/**
 * ViewModel for Rule Selection Engine following MVVM pattern.
 */
class RuleSelectionViewModel(
    private val selectionEngine: RuleSelectionEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<RuleSelectionUiState>(RuleSelectionUiState.Idle)
    val uiState: StateFlow<RuleSelectionUiState> = _uiState.asStateFlow()

    fun selectRule(
        matchedRules: List<AutoReplyRule>,
        criteria: RuleSelectionCriteria = RuleSelectionCriteria()
    ) {
        viewModelScope.launch {
            _uiState.value = RuleSelectionUiState.Evaluating
            try {
                val result = selectionEngine.selectRule(matchedRules, criteria)
                _uiState.value = when (result.status) {
                    RuleSelectionStatus.SELECTED -> RuleSelectionUiState.Success(result)
                    RuleSelectionStatus.NO_SUITABLE_RULE -> RuleSelectionUiState.NoSuitableRule(result)
                }
            } catch (e: Exception) {
                _uiState.value = RuleSelectionUiState.Error("Rule selection failed: ${e.message}")
            }
        }
    }
}
