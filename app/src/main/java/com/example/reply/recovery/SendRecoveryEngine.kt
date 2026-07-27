package com.example.reply.recovery

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.repository.SendRecoveryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-grade Failed Sending Recovery Engine.
 *
 * Requirements:
 * - Retry ONLY ONCE.
 * - Never create duplicate reply.
 * - If retry fails:
 *   - Move conversation to Failed Queue.
 *   - Continue next conversation.
 *
 * Emits required logs:
 * - Retry Started
 * - Retry Success
 * - Retry Failed
 * - Moved To Failed Queue
 * - Worker Continued
 *
 * Return:
 * - SendRecoveryResult with status RETRY_SUCCESS or MOVED_TO_FAILED.
 */
class SendRecoveryEngine(
    private val repository: SendRecoveryRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun recover(
        criteria: SendRecoveryCriteria
    ): SendRecoveryResult = withContext(dispatcher) {

        // 1. Log: Retry Started
        logRetryStarted(criteria.conversationId, criteria.replyText)

        val result = repository.recoverFailedSend(criteria)

        if (result.isSuccess) {
            // 2. Log: Retry Success
            logRetrySuccess(result.conversationId)
        } else {
            // 3. Log: Retry Failed
            logRetryFailed(result.conversationId, result.reason)

            if (result.movedToFailedQueue) {
                // 4. Log: Moved To Failed Queue
                logMovedToFailedQueue(result.conversationId)
            }
        }

        if (result.workerContinued) {
            // 5. Log: Worker Continued
            logWorkerContinued(result.conversationId)
        }

        result
    }

    private fun logRetryStarted(conversationId: String, replyText: String) {
        val sample = if (replyText.length > 50) "${replyText.take(50)}..." else replyText
        val logMsg = """
            Retry Started
            Target Conversation: "$conversationId"
            Attempt: Single Retry Execution
            Payload: "$sample"
            Guard: Duplicate prevention active
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.REPLY,
            "Retry Started",
            logMsg
        )
    }

    private fun logRetrySuccess(conversationId: String) {
        val logMsg = """
            Retry Success
            Conversation ID: "$conversationId"
            Status: Single retry succeeded. Transmission completed.
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.REPLY,
            "Retry Success",
            logMsg
        )
    }

    private fun logRetryFailed(conversationId: String, reason: String) {
        val logMsg = """
            Retry Failed
            Conversation ID: "$conversationId"
            Reason: $reason
            Rule: Single retry limit exhausted.
        """.trimIndent()

        Log.w(TAG, logMsg)
        AppLogger.warning(
            LogCategory.REPLY,
            "Retry Failed",
            logMsg
        )
    }

    private fun logMovedToFailedQueue(conversationId: String) {
        val logMsg = """
            Moved To Failed Queue
            Conversation ID: "$conversationId"
            New Queue Status: FAILED
            Queue Action: Conversation isolated in Failed Queue for manual review/retry.
        """.trimIndent()

        Log.e(TAG, logMsg)
        AppLogger.warning(
            LogCategory.REPLY,
            "Moved To Failed Queue",
            logMsg
        )
    }

    private fun logWorkerContinued(conversationId: String) {
        val logMsg = """
            Worker Continued
            Previous Conversation: "$conversationId"
            Status: Pipeline unlocked. Proceeding to process next queued conversation.
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.REPLY,
            "Worker Continued",
            logMsg
        )
    }

    companion object {
        private const val TAG = "SendRecoveryEngine"
    }
}
