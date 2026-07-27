package com.example.data

import com.example.model.DebugDashboardState
import com.example.repository.DebugDashboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Singleton / Concrete implementation for Debug Dashboard state repository.
 * Operates purely in memory using StateFlow with zero impact on production execution.
 */
class DebugDashboardRepositoryImpl : DebugDashboardRepository {

    private val _debugState = MutableStateFlow(DebugDashboardState())
    override val debugState: StateFlow<DebugDashboardState> = _debugState.asStateFlow()

    override fun updateCurrentQueue(queueCount: Int) {
        _debugState.update { it.copy(currentQueueCount = queueCount, lastUpdatedTimestamp = System.currentTimeMillis()) }
    }

    override fun updateCurrentCustomer(customer: String) {
        _debugState.update { it.copy(currentCustomer = customer, lastUpdatedTimestamp = System.currentTimeMillis()) }
    }

    override fun updateCurrentStep(step: String) {
        _debugState.update { it.copy(currentStep = step, lastUpdatedTimestamp = System.currentTimeMillis()) }
    }

    override fun updateCurrentChat(chat: String) {
        _debugState.update { it.copy(currentChat = chat, lastUpdatedTimestamp = System.currentTimeMillis()) }
    }

    override fun updateLastReadMessages(messages: List<String>) {
        _debugState.update { it.copy(lastReadMessages = messages, lastUpdatedTimestamp = System.currentTimeMillis()) }
    }

    override fun updateMatchedRule(rule: String) {
        _debugState.update { it.copy(matchedRule = rule, lastUpdatedTimestamp = System.currentTimeMillis()) }
    }

    override fun updateGeneratedReply(reply: String) {
        _debugState.update { it.copy(generatedReply = reply, lastUpdatedTimestamp = System.currentTimeMillis()) }
    }

    override fun updateInsertStatus(status: String) {
        _debugState.update { it.copy(insertStatus = status, lastUpdatedTimestamp = System.currentTimeMillis()) }
    }

    override fun updateSendStatus(status: String) {
        _debugState.update { it.copy(sendStatus = status, lastUpdatedTimestamp = System.currentTimeMillis()) }
    }

    override fun updateAccessibilityStatus(status: String, nodeCount: Int) {
        _debugState.update {
            it.copy(
                accessibilityStatus = status,
                nodeCount = nodeCount,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
        }
    }

    override fun updateLatestError(error: String) {
        _debugState.update {
            it.copy(
                latestError = error,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
        }
    }

    override fun updateLatestLog(log: String) {
        _debugState.update {
            it.copy(
                latestLog = log,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
        }
    }

    override fun setDebugMode(enabled: Boolean) {
        _debugState.update { it.copy(isDebugModeEnabled = enabled, lastUpdatedTimestamp = System.currentTimeMillis()) }
    }

    override fun resetState() {
        _debugState.value = DebugDashboardState()
    }

    companion object {
        @Volatile
        private var instance: DebugDashboardRepositoryImpl? = null

        fun getInstance(): DebugDashboardRepositoryImpl {
            return instance ?: synchronized(this) {
                instance ?: DebugDashboardRepositoryImpl().also { instance = it }
            }
        }
    }
}
