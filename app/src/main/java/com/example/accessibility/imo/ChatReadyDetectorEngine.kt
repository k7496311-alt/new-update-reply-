package com.example.accessibility.imo

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.repository.ChatReadyRepository
import com.example.repository.ScreenReadyState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Production-grade IMO Chat Ready Detector.
 * Detects when IMO chat screen is fully loaded by verifying via Accessibility:
 * 1. Message list exists
 * 2. Input box exists
 * 3. Send button exists
 *
 * Configurable timeout duration (default: 5000ms).
 *
 * Emits exact required logs:
 * - Waiting Chat
 * - Chat Ready
 * - Timeout
 * - Node Count
 * - Visible Nodes
 */
class ChatReadyDetectorEngine(
    private val repository: ChatReadyRepository,
    private val defaultTimeoutMs: Long = 5000L,
    private val pollIntervalMs: Long = 200L,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun detectChatReady(
        timeoutMs: Long = defaultTimeoutMs
    ): ChatReadyResult = withContext(dispatcher) {
        val startTime = System.currentTimeMillis()

        // 1. Log initial Waiting Chat
        logWaitingChat(timeoutMs, 0L)

        var elapsedTime = 0L
        var state: ScreenReadyState = repository.checkScreenReadyState()

        // Log initial Node metrics
        logNodeMetrics(state.totalNodeCount, state.visibleNodeCount)

        while (elapsedTime < timeoutMs) {
            if (state.isAllReady) {
                val totalTime = System.currentTimeMillis() - startTime
                val result = ChatReadyResult(
                    status = ChatReadyStatus.CHAT_READY,
                    messageListExists = state.messageListExists,
                    inputBoxExists = state.inputBoxExists,
                    sendButtonExists = state.sendButtonExists,
                    nodeCount = state.totalNodeCount,
                    visibleNodes = state.visibleNodeCount,
                    elapsedTimeMs = totalTime,
                    details = "All required UI components verified present (Message List, Input Box, Send Button)"
                )

                logChatReady(result)
                logNodeMetrics(state.totalNodeCount, state.visibleNodeCount)
                return@withContext result
            }

            delay(pollIntervalMs)
            elapsedTime = System.currentTimeMillis() - startTime
            state = repository.checkScreenReadyState()

            logWaitingChat(timeoutMs, elapsedTime)
            logNodeMetrics(state.totalNodeCount, state.visibleNodeCount)
        }

        // Timeout reached
        val totalTime = System.currentTimeMillis() - startTime
        val result = ChatReadyResult(
            status = ChatReadyStatus.CHAT_TIMEOUT,
            messageListExists = state.messageListExists,
            inputBoxExists = state.inputBoxExists,
            sendButtonExists = state.sendButtonExists,
            nodeCount = state.totalNodeCount,
            visibleNodes = state.visibleNodeCount,
            elapsedTimeMs = totalTime,
            details = "Timeout after ${totalTime}ms. Component state: MessageList=${state.messageListExists}, InputBox=${state.inputBoxExists}, SendButton=${state.sendButtonExists}"
        )

        logTimeout(result)
        logNodeMetrics(state.totalNodeCount, state.visibleNodeCount)
        return@withContext result
    }

    private fun logWaitingChat(timeoutMs: Long, elapsedTimeMs: Long) {
        val logMsg = """
            Waiting Chat
            Elapsed: ${elapsedTimeMs}ms
            Max Timeout: ${timeoutMs}ms
            Status: Polling for chat UI components (Message List, Input Box, Send Button)
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Waiting Chat",
            logMsg
        )
    }

    private fun logChatReady(result: ChatReadyResult) {
        val logMsg = """
            Chat Ready
            Elapsed Time: ${result.elapsedTimeMs}ms
            Message List: ${result.messageListExists}
            Input Box: ${result.inputBoxExists}
            Send Button: ${result.sendButtonExists}
            Node Count: ${result.nodeCount}
            Visible Nodes: ${result.visibleNodes}
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.ACCESSIBILITY,
            "Chat Ready",
            logMsg
        )
    }

    private fun logTimeout(result: ChatReadyResult) {
        val logMsg = """
            Timeout
            Elapsed Time: ${result.elapsedTimeMs}ms
            Message List: ${result.messageListExists}
            Input Box: ${result.inputBoxExists}
            Send Button: ${result.sendButtonExists}
            Node Count: ${result.nodeCount}
            Visible Nodes: ${result.visibleNodes}
            Reason: Chat screen components failed to load within timeout window
        """.trimIndent()

        Log.w(TAG, logMsg)
        AppLogger.warning(
            LogCategory.ACCESSIBILITY,
            "Timeout",
            logMsg
        )
    }

    private fun logNodeMetrics(totalNodes: Int, visibleNodes: Int) {
        val countLog = "Node Count: $totalNodes"
        val visibleLog = "Visible Nodes: $visibleNodes"

        Log.d(TAG, countLog)
        Log.d(TAG, visibleLog)

        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Node Count",
            "$countLog | $visibleLog"
        )
    }

    companion object {
        private const val TAG = "ChatReadyDetectorEngine"
    }
}
