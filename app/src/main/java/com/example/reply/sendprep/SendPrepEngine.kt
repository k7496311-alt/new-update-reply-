package com.example.reply.sendprep

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.repository.SendPrepRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-grade Send Preparation Engine.
 *
 * Requirements:
 * - Verifies before sending:
 *   1. Conversation still open
 *   2. Same customer
 *   3. Input box exists
 *   4. Accessibility alive
 *   5. Reply VALID
 *   6. Duplicate check passed
 *   7. Queue item active
 *
 * - Returns READY_TO_SEND or NOT_READY with Reason.
 *
 * - Emits required logs:
 *   - Conversation Verified
 *   - Input Verified
 *   - Reply Ready
 *   - Ready To Send (if READY_TO_SEND)
 *   - Failure Reason (if NOT_READY)
 *
 * - CRITICAL:
 *   - STOP HERE.
 *   - DO NOT insert reply.
 *   - DO NOT click send.
 *   - DO NOT perform Accessibility actions.
 *   - Only prepare system.
 */
class SendPrepEngine(
    private val repository: SendPrepRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun prepare(
        criteria: SendPrepCriteria
    ): SendPrepResult = withContext(dispatcher) {

        val result = repository.verifySendReadiness(criteria)

        // 1. Log: Conversation Verified (or Failure)
        if (result.conversationVerified && result.sameCustomerVerified) {
            logConversationVerified(result.conversationId, result.expectedCustomerName)
        }

        // 2. Log: Input Verified (if input box present)
        if (result.inputBoxVerified) {
            logInputVerified(result.conversationId)
        }

        // 3. Log: Reply Ready (if reply valid and duplicate check passed)
        if (result.replyValid && result.duplicateCheckPassed) {
            logReplyReady(result.replyText, result.queueItem?.id)
        }

        if (result.isReady) {
            // 4. Log: Ready To Send
            logReadyToSend(result.conversationId, result.expectedCustomerName, result.replyText)
        } else {
            // 5. Log: Failure Reason
            logFailureReason(result.reason, result.failedChecks)
        }

        result
    }

    private fun logConversationVerified(conversationId: String, customerName: String) {
        val logMsg = """
            Conversation Verified
            Conversation ID: "$conversationId"
            Target Customer: "$customerName"
            Status: Active & Verified
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.REPLY,
            "Conversation Verified",
            logMsg
        )
    }

    private fun logInputVerified(conversationId: String) {
        val logMsg = """
            Input Verified
            Target Conversation: "$conversationId"
            Input Field: Present & Ready
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.REPLY,
            "Input Verified",
            logMsg
        )
    }

    private fun logReplyReady(replyText: String, queueItemId: Long?) {
        val sample = if (replyText.length > 50) "${replyText.take(50)}..." else replyText
        val logMsg = """
            Reply Ready
            Queue Item ID: ${queueItemId ?: "N/A"}
            Validated Text: "$sample"
            Length: ${replyText.length} character(s)
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.REPLY,
            "Reply Ready",
            logMsg
        )
    }

    private fun logReadyToSend(conversationId: String, customerName: String, replyText: String) {
        val logMsg = """
            Ready To Send
            Target Customer: "$customerName"
            Conversation: "$conversationId"
            Status: System READY_TO_SEND (Transmission halted as per specification)
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.REPLY,
            "Ready To Send",
            logMsg
        )
    }

    private fun logFailureReason(reason: String, failedChecks: List<String>) {
        val failedStr = failedChecks.joinToString(", ")
        val logMsg = """
            Failure Reason
            Status: NOT_READY
            Reason: $reason
            Failed Verification Checks: [$failedStr]
        """.trimIndent()

        Log.w(TAG, logMsg)
        AppLogger.warning(
            LogCategory.REPLY,
            "Failure Reason",
            logMsg
        )
    }

    companion object {
        private const val TAG = "SendPrepEngine"
    }
}
