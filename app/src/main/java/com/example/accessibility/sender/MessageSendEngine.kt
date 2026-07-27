package com.example.accessibility.sender

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.repository.MessageSendRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-grade Message Send Executor Engine.
 *
 * Requirements:
 * - Click Send Button ONCE.
 * - Never double click.
 * - Wait for UI update.
 * - Confirm message composer becomes empty OR outgoing message appears.
 * - Return: SEND_SUCCESS or SEND_FAILED
 * - Emits required logs:
 *   - Click Started
 *   - Click Success
 *   - Outgoing Bubble Found
 *   - Composer Cleared
 *   - Send Failed
 * - Constraints:
 *   - No retry here.
 *   - Production quality.
 *   - No placeholder.
 */
class MessageSendEngine(
    private val repository: MessageSendRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun executeSend(
        criteria: MessageSendCriteria
    ): MessageSendResult = withContext(dispatcher) {

        // 1. Log: Click Started
        logClickStarted(criteria.sentText)

        val result = repository.performSend(criteria)

        if (result.clickPerformed) {
            // 2. Log: Click Success
            logClickSuccess()
        }

        if (result.composerCleared) {
            // 3. Log: Composer Cleared
            logComposerCleared()
        }

        if (result.outgoingBubbleFound) {
            // 4. Log: Outgoing Bubble Found
            logOutgoingBubbleFound(result.sentText)
        }

        if (!result.isSuccess) {
            // 5. Log: Send Failed
            logSendFailed(result.reason)
        } else {
            logSendSuccess(result)
        }

        result
    }

    private fun logClickStarted(sentText: String) {
        val sample = if (sentText.length > 50) "${sentText.take(50)}..." else sentText
        val logMsg = """
            Click Started
            Action: Performing single click on Send Button
            Payload: "$sample"
            Guard: Double click prevented
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Click Started",
            logMsg
        )
    }

    private fun logClickSuccess() {
        val logMsg = "Click Success: Send button clicked once. Awaiting UI update."
        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.ACCESSIBILITY,
            "Click Success",
            logMsg
        )
    }

    private fun logComposerCleared() {
        val logMsg = "Composer Cleared: Message input composer field is now empty."
        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.ACCESSIBILITY,
            "Composer Cleared",
            logMsg
        )
    }

    private fun logOutgoingBubbleFound(text: String) {
        val sample = if (text.length > 50) "${text.take(50)}..." else text
        val logMsg = """
            Outgoing Bubble Found
            Confirmed outgoing message bubble in chat UI tree matching text: "$sample"
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.ACCESSIBILITY,
            "Outgoing Bubble Found",
            logMsg
        )
    }

    private fun logSendSuccess(result: MessageSendResult) {
        val logMsg = """
            Send Success
            Status: SEND_SUCCESS
            Verified: Composer Cleared = ${result.composerCleared}, Outgoing Bubble Found = ${result.outgoingBubbleFound}
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.ACCESSIBILITY,
            "Send Success",
            logMsg
        )
    }

    private fun logSendFailed(reason: String) {
        val logMsg = """
            Send Failed
            Status: SEND_FAILED
            Reason: $reason
            Note: No retry executed as per specification
        """.trimIndent()

        Log.e(TAG, logMsg)
        AppLogger.warning(
            LogCategory.ACCESSIBILITY,
            "Send Failed",
            logMsg
        )
    }

    companion object {
        private const val TAG = "MessageSendEngine"
    }
}
