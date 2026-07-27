package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DebugDashboardRepositoryImpl
import com.example.model.DebugDashboardState
import com.example.repository.DebugDashboardRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the Debug Dashboard screen.
 */
class DebugDashboardViewModel(
    private val repository: DebugDashboardRepository = DebugDashboardRepositoryImpl.getInstance()
) : ViewModel() {

    val uiState: StateFlow<DebugDashboardState> = repository.debugState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DebugDashboardState()
    )

    fun toggleDebugMode(enabled: Boolean) {
        repository.setDebugMode(enabled)
    }

    fun resetDashboard() {
        repository.resetState()
    }

    fun simulateTestExecution() {
        repository.updateCurrentCustomer("John Doe")
        repository.updateCurrentStep("Sending Message")
        repository.updateCurrentQueue(3)
        repository.updateLastReadMessages(listOf("Hello, are you available?", "Can you reply soon?"))
        repository.updateMatchedRule("Keyword: 'available'")
        repository.updateGeneratedReply("Yes, I am currently available! How can I help?")
        repository.updateInsertStatus("SUCCESS")
        repository.updateSendStatus("SENT")
        repository.updateAccessibilityStatus("Active & Connected", 148)
        repository.updateLatestLog("Conversation processing completed for John Doe.")
    }
}
