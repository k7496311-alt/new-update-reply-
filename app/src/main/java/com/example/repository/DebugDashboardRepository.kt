package com.example.repository

import com.example.model.DebugDashboardState
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for real-time Debug Dashboard status tracking.
 */
interface DebugDashboardRepository {
    val debugState: StateFlow<DebugDashboardState>

    fun updateCurrentQueue(queueCount: Int)
    fun updateCurrentCustomer(customer: String)
    fun updateCurrentStep(step: String)
    fun updateCurrentChat(chat: String)
    fun updateLastReadMessages(messages: List<String>)
    fun updateMatchedRule(rule: String)
    fun updateGeneratedReply(reply: String)
    fun updateInsertStatus(status: String)
    fun updateSendStatus(status: String)
    fun updateAccessibilityStatus(status: String, nodeCount: Int)
    fun updateLatestError(error: String)
    fun updateLatestLog(log: String)
    fun setDebugMode(enabled: Boolean)
    fun resetState()
}
