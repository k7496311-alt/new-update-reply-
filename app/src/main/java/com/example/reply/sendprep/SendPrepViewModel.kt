package com.example.reply.sendprep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Send Preparation Operations.
 */
sealed class SendPrepUiState {
    object Idle : SendPrepUiState()
    object Verifying : SendPrepUiState()
    data class ReadyToSend(val result: SendPrepResult) : SendPrepUiState()
    data class NotReady(val result: SendPrepResult) : SendPrepUiState()
    data class Error(val message: String) : SendPrepUiState()
}

/**
 * ViewModel for Send Preparation Engine following MVVM pattern.
 */
class SendPrepViewModel(
    private val sendPrepEngine: SendPrepEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<SendPrepUiState>(SendPrepUiState.Idle)
    val uiState: StateFlow<SendPrepUiState> = _uiState.asStateFlow()

    fun prepare(criteria: SendPrepCriteria) {
        viewModelScope.launch {
            _uiState.value = SendPrepUiState.Verifying
            try {
                val result = sendPrepEngine.prepare(criteria)
                _uiState.value = if (result.isReady) {
                    SendPrepUiState.ReadyToSend(result)
                } else {
                    SendPrepUiState.NotReady(result)
                }
            } catch (e: Exception) {
                _uiState.value = SendPrepUiState.Error("Send preparation error: ${e.message}")
            }
        }
    }
}
