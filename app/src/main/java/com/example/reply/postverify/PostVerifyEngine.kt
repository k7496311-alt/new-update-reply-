package com.example.reply.postverify

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.repository.PostVerifyRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-grade Post-Send Reply Verification Engine.
 *
 * Requirements:
 * - Scan latest outgoing messages in conversation.
 * - Find exact reply text.
 * - Compare text.
 * - If exact match:
 *   - Mark conversation completed.
 * - Otherwise:
 *   - Return failure.
 *
 * Emits required logs:
 * - Verification Started
 * - Outgoing Found
 * - Reply Matched
 * - Verification Failed
 * - Conversation Completed
 *
 * Return:
 * - PostVerifyResult with status COMPLETED or FAILED.
 */
class PostVerifyEngine(
    private val repository: PostVerifyRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun verifyReplyAndComplete(
        criteria: PostVerifyCriteria
    ): PostVerifyResult = withContext(dispatcher) {

        // 1. Log: Verification Started
        logVerificationStarted(criteria.conversationId, criteria.expectedReplyText)

        val result = repository.verifyAndComplete(criteria)

        if (result.outgoingBubbleFound) {
            // 2. Log: Outgoing Found
            logOutgoingFound(result.outgoingBubblesDetectedCount)
        }

        if (result.replyMatched && result.matchedText != null) {
            // 3. Log: Reply Matched
            logReplyMatched(result.matchedText)

            // 4. Log: Conversation Completed
            logConversationCompleted(result.conversationId)
        } else {
            // 5. Log: Verification Failed
            logVerificationFailed(result.reason)
        }

        result
    }

    private fun logVerificationStarted(conversationId: String, expectedReplyText: String) {
        val sample = if (expectedReplyText.length > 50) "${expectedReplyText.take(50)}..." else expectedReplyText
        val logMsg = """
            Verification Started
            Conversation ID: "$conversationId"
            Expected Reply Text: "$sample"
            Scanning Accessibility tree for active chat message bubbles...
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Verification Started",
            logMsg
        )
    }

    private fun logOutgoingFound(detectedCount: Int) {
        val logMsg = """
            Outgoing Found
            Detected $detectedCount message bubble(s)/text node(s) in active chat view.
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Outgoing Found",
            logMsg
        )
    }

    private fun logReplyMatched(matchedText: String) {
        val sample = if (matchedText.length > 50) "${matchedText.take(50)}..." else matchedText
        val logMsg = """
            Reply Matched
            Exact text match verified in outgoing message bubble: "$sample"
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.ACCESSIBILITY,
            "Reply Matched",
            logMsg
        )
    }

    private fun logConversationCompleted(conversationId: String) {
        val logMsg = """
            Conversation Completed
            Conversation ID: "$conversationId"
            Status: SENT / COMPLETED
            Reply successfully verified in chat history.
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.ACCESSIBILITY,
            "Conversation Completed",
            logMsg
        )
    }

    private fun logVerificationFailed(reason: String) {
        val logMsg = """
            Verification Failed
            Status: FAILED
            Reason: $reason
        """.trimIndent()

        Log.e(TAG, logMsg)
        AppLogger.warning(
            LogCategory.ACCESSIBILITY,
            "Verification Failed",
            logMsg
        )
    }

    companion object {
        private const val TAG = "PostVerifyEngine"
    }
}
