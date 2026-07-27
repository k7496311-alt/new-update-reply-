package com.example.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.repository.CrashRecoveryCriteria
import com.example.repository.CrashRecoveryRepository
import com.example.repository.CrashRecoveryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Crash Recovery.
 */
data class CrashRecoveryUiState(
    val isRecovering: Boolean = false,
    val isRecoveryCompleted: Boolean = false,
    val restoredQueueCount: Int = 0,
    val activeSenderName: String = "",
    val duplicatePrevented: Boolean = false,
    val statusText: String = "Idle"
)

/**
 * Production MVVM ViewModel for Crash Recovery.
 */
class CrashRecoveryViewModel(
    private val crashRecoveryRepository: CrashRecoveryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CrashRecoveryUiState())
    val uiState: StateFlow<CrashRecoveryUiState> = _uiState.asStateFlow()

    /**
     * Executes crash recovery.
     */
    fun triggerCrashRecovery(criteria: CrashRecoveryCriteria = CrashRecoveryCriteria()) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRecovering = true,
                statusText = "Recovery Started..."
            )

            val result: CrashRecoveryResult = crashRecoveryRepository.performCrashRecovery(criteria)

            _uiState.value = _uiState.value.copy(
                isRecovering = false,
                isRecoveryCompleted = true,
                restoredQueueCount = result.restoredQueueItems.size,
                activeSenderName = result.restoredConversation?.senderName ?: "None",
                duplicatePrevented = result.isDuplicatePrevented,
                statusText = result.summaryMessage
            )
        }
    }
}
