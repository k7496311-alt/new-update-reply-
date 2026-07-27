package com.example.accessibility.imo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.QueueItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Chat Open operations.
 */
sealed class ChatOpenUiState {
    object Idle : ChatOpenUiState()
    data class Opening(val queueItem: QueueItem) : ChatOpenUiState()
    data class Success(val senderName: String, val result: ChatOpenResult.Success) : ChatOpenUiState()
    data class Failed(val senderName: String, val reason: String) : ChatOpenUiState()
}

/**
 * ViewModel for Chat Open Engine interactions following MVVM pattern.
 */
class ChatOpenViewModel(
    private val chatOpenEngine: ChatOpenEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatOpenUiState>(ChatOpenUiState.Idle)
    val uiState: StateFlow<ChatOpenUiState> = _uiState.asStateFlow()

    fun openChatForConversation(queueItem: QueueItem) {
        viewModelScope.launch {
            _uiState.value = ChatOpenUiState.Opening(queueItem)
            val result = chatOpenEngine.openChatForConversation(queueItem)
            _uiState.value = when (result) {
                is ChatOpenResult.Success -> ChatOpenUiState.Success(
                    senderName = result.senderName,
                    result = result
                )
                is ChatOpenResult.WrongChat -> ChatOpenUiState.Failed(
                    senderName = result.expectedSender,
                    reason = "Opened wrong chat '${result.actualSender}'"
                )
                is ChatOpenResult.Failed -> ChatOpenUiState.Failed(
                    senderName = queueItem.senderName,
                    reason = result.reason
                )
            }
        }
    }
}
