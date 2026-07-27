package com.example.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.QueueItem
import com.example.repository.PendingNotificationRecoveryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Pending Notification Recovery Screen / Component.
 */
data class RecoveryUiState(
    val isProcessing: Boolean = false,
    val pendingQueueCount: Int = 0,
    val lastRecoveredCount: Int = 0,
    val lastProcessedSender: String = "",
    val statusMessage: String = "Idle"
)

/**
 * Production MVVM ViewModel for Pending Notification Recovery.
 */
class PendingNotificationRecoveryViewModel(
    private val recoveryRepository: PendingNotificationRecoveryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecoveryUiState())
    val uiState: StateFlow<RecoveryUiState> = _uiState.asStateFlow()

    /**
     * Enqueues an incoming notification arriving during conversation execution.
     */
    fun onNewNotificationArrived(
        packageName: String,
        senderName: String,
        messageText: String
    ) {
        viewModelScope.launch {
            val item = recoveryRepository.handleIncomingNotification(packageName, senderName, messageText)
            _uiState.value = _uiState.value.copy(
                statusMessage = "Notification captured and queued for ${item.senderName}",
                pendingQueueCount = _uiState.value.pendingQueueCount + 1
            )
        }
    }

    /**
     * Triggers recovery of all pending notifications from database storage.
     */
    fun recoverPendingNotifications() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, statusMessage = "Recovering pending notifications...")
            val recoveredItems = recoveryRepository.recoverAllPendingNotifications()
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                lastRecoveredCount = recoveredItems.size,
                pendingQueueCount = recoveredItems.size,
                statusMessage = "Recovery Success: ${recoveredItems.size} pending notifications restored."
            )
        }
    }

    /**
     * Fetches and continues processing the next pending conversation.
     */
    fun continueProcessingNext() {
        viewModelScope.launch {
            val nextItem = recoveryRepository.fetchNextPendingConversation()
            if (nextItem != null) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = true,
                    lastProcessedSender = nextItem.senderName,
                    statusMessage = "Continue Processing: ${nextItem.senderName}"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    pendingQueueCount = 0,
                    statusMessage = "Queue is empty."
                )
            }
        }
    }
}
