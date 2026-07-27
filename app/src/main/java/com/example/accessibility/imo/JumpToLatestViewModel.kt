package com.example.accessibility.imo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Jump To Latest Engine operations.
 */
sealed class JumpToLatestUiState {
    object Idle : JumpToLatestUiState()
    object Executing : JumpToLatestUiState()
    data class Completed(val result: JumpToLatestResult) : JumpToLatestUiState()
}

/**
 * ViewModel for Jump To Latest Engine interactions following MVVM architecture.
 */
class JumpToLatestViewModel(
    private val engine: JumpToLatestEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<JumpToLatestUiState>(JumpToLatestUiState.Idle)
    val uiState: StateFlow<JumpToLatestUiState> = _uiState.asStateFlow()

    fun executeJumpToLatest() {
        viewModelScope.launch {
            _uiState.value = JumpToLatestUiState.Executing
            val result = engine.executeJumpToLatest()
            _uiState.value = JumpToLatestUiState.Completed(result)
        }
    }
}
