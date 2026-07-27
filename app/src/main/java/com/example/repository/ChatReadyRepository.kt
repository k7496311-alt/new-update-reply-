package com.example.repository

import com.example.accessibility.imo.ChatReadyResult

data class ScreenReadyState(
    val messageListExists: Boolean,
    val inputBoxExists: Boolean,
    val sendButtonExists: Boolean,
    val totalNodeCount: Int,
    val visibleNodeCount: Int,
    val isAllReady: Boolean
)

/**
 * Clean Architecture Repository interface for Chat Ready Detection.
 */
interface ChatReadyRepository {
    /**
     * Inspects active window nodes to determine present components and count metrics.
     */
    suspend fun checkScreenReadyState(): ScreenReadyState

    /**
     * Polling loop that waits until chat components are verified or timeout is reached.
     */
    suspend fun waitForChatReady(timeoutMs: Long = 5000L, pollIntervalMs: Long = 200L): ChatReadyResult
}
