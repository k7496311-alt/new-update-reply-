package com.example.accessibility.input

import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Message Input Finder operations.
 */
sealed class MessageInputFinderUiState {
    object Idle : MessageInputFinderUiState()
    object Searching : MessageInputFinderUiState()
    data class Verified(val result: MessageInputVerificationResult) : MessageInputFinderUiState()
    data class WrongInput(val result: MessageInputVerificationResult) : MessageInputFinderUiState()
    data class Missing(val result: MessageInputVerificationResult) : MessageInputFinderUiState()
    data class Error(val message: String) : MessageInputFinderUiState()
}

/**
 * ViewModel for Message Input Finder following MVVM architecture.
 */
class MessageInputFinderViewModel(
    private val finderEngine: MessageInputFinderEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<MessageInputFinderUiState>(MessageInputFinderUiState.Idle)
    val uiState: StateFlow<MessageInputFinderUiState> = _uiState.asStateFlow()

    fun findAndVerifyInput(
        rootNode: AccessibilityNodeInfo?,
        criteria: MessageInputFinderCriteria = MessageInputFinderCriteria()
    ) {
        viewModelScope.launch {
            _uiState.value = MessageInputFinderUiState.Searching
            try {
                val result = finderEngine.findAndVerifyInput(rootNode, criteria)
                _uiState.value = when {
                    result.isVerified -> MessageInputFinderUiState.Verified(result)
                    result.status == MessageInputStatus.WRONG_INPUT -> MessageInputFinderUiState.WrongInput(result)
                    else -> MessageInputFinderUiState.Missing(result)
                }
            } catch (e: Exception) {
                _uiState.value = MessageInputFinderUiState.Error("Message input finder failed: ${e.message}")
            }
        }
    }
}
