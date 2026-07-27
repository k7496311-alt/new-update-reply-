package com.example.accessibility.input.inserter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Message Input Insertion.
 */
sealed class MessageInputInserterUiState {
    object Idle : MessageInputInserterUiState()
    object Inserting : MessageInputInserterUiState()
    data class Success(val result: MessageInputInsertResult) : MessageInputInserterUiState()
    data class Failed(val result: MessageInputInsertResult) : MessageInputInserterUiState()
    data class Error(val message: String) : MessageInputInserterUiState()
}

/**
 * ViewModel for Message Input Inserter Engine following MVVM architecture.
 */
class MessageInputInserterViewModel(
    private val inserterEngine: MessageInputInserterEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<MessageInputInserterUiState>(MessageInputInserterUiState.Idle)
    val uiState: StateFlow<MessageInputInserterUiState> = _uiState.asStateFlow()

    fun insertReply(criteria: MessageInputInsertCriteria) {
        viewModelScope.launch {
            _uiState.value = MessageInputInserterUiState.Inserting
            try {
                val result = inserterEngine.insertReply(criteria)
                _uiState.value = if (result.isSuccess) {
                    MessageInputInserterUiState.Success(result)
                } else {
                    MessageInputInserterUiState.Failed(result)
                }
            } catch (e: Exception) {
                _uiState.value = MessageInputInserterUiState.Error("Text insertion failed: ${e.message}")
            }
        }
    }
}
