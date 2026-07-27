package com.example.accessibility.imo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Bottom Position Verification Engine.
 */
sealed class BottomVerificationUiState {
    object Idle : BottomVerificationUiState()
    object Verifying : BottomVerificationUiState()
    data class Completed(val result: BottomVerificationResult) : BottomVerificationUiState()
}

/**
 * ViewModel for Bottom Position Verification Engine following MVVM pattern.
 */
class BottomVerificationViewModel(
    private val engine: BottomVerificationEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<BottomVerificationUiState>(BottomVerificationUiState.Idle)
    val uiState: StateFlow<BottomVerificationUiState> = _uiState.asStateFlow()

    fun verifyBottomPosition() {
        viewModelScope.launch {
            _uiState.value = BottomVerificationUiState.Verifying
            val result = engine.verifyBottomPosition()
            _uiState.value = BottomVerificationUiState.Completed(result)
        }
    }
}
