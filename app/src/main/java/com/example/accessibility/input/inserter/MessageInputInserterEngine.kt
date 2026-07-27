package com.example.accessibility.input.inserter

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.repository.MessageInputInserterRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-grade Message Input Inserter Engine.
 *
 * Requirements:
 * - Insert validated reply into message input using ACTION_SET_TEXT.
 * - If unsupported, use supported fallback strategy.
 * - Read input field again after insertion and verify text exact match.
 * - If mismatch: clear input and retry once.
 * - Return:
 *   - INSERT_SUCCESS
 *   - INSERT_FAILED
 * - Logs:
 *   - Insert Started
 *   - Insert Success
 *   - Inserted Characters
 *   - Verification Passed
 *   - Verification Failed
 *   - Retry
 * - CONSTRAINTS:
 *   - No Send Button click.
 *   - Production quality.
 *   - No TODO.
 */
class MessageInputInserterEngine(
    private val repository: MessageInputInserterRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun insertReply(
        criteria: MessageInputInsertCriteria
    ): MessageInputInsertResult = withContext(dispatcher) {

        // 1. Log: Insert Started
        logInsertStarted(criteria.replyText)

        val result = repository.insertReplyText(criteria)

        // Log if retries occurred or verification failed
        if (result.attemptsCount > 1) {
            logVerificationFailed(criteria.replyText, result.actualInsertedText, isFinal = false)
            logRetry(result.attemptsCount)
        }

        if (result.isSuccess) {
            // 2. Log: Inserted Characters
            logInsertedCharacters(result.insertedCharacterCount)

            // 3. Log: Verification Passed
            logVerificationPassed(result.actualInsertedText)

            // 4. Log: Insert Success
            logInsertSuccess(result.actualInsertedText, result.usedFallbackStrategy)
        } else {
            // Log: Verification Failed (Final)
            logVerificationFailed(criteria.replyText, result.actualInsertedText, isFinal = true)
            logInsertFailed(result.reason)
        }

        result
    }

    private fun logInsertStarted(replyText: String) {
        val sample = if (replyText.length > 50) "${replyText.take(50)}..." else replyText
        val logMsg = """
            Insert Started
            Text Length: ${replyText.length} character(s)
            Preview: "$sample"
            Primary Strategy: Accessibility ACTION_SET_TEXT
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Insert Started",
            logMsg
        )
    }

    private fun logInsertedCharacters(count: Int) {
        val logMsg = "Inserted Characters: $count character(s) successfully written to input field."
        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Inserted Characters",
            logMsg
        )
    }

    private fun logVerificationPassed(actualText: String) {
        val logMsg = """
            Verification Passed
            Input field text re-read from accessibility node.
            Exact Match Verified: "$actualText"
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.ACCESSIBILITY,
            "Verification Passed",
            logMsg
        )
    }

    private fun logVerificationFailed(expected: String, actual: String, isFinal: Boolean) {
        val logMsg = """
            Verification Failed
            Expected Length: ${expected.length}, Actual Length: ${actual.length}
            Expected: "$expected"
            Actual: "$actual"
            Is Final Attempt: $isFinal
        """.trimIndent()

        Log.w(TAG, logMsg)
        AppLogger.warning(
            LogCategory.ACCESSIBILITY,
            "Verification Failed",
            logMsg
        )
    }

    private fun logRetry(attemptNumber: Int) {
        val logMsg = "Retry: Text mismatch detected. Input field cleared. Executing retry attempt #$attemptNumber."
        Log.w(TAG, logMsg)
        AppLogger.warning(
            LogCategory.ACCESSIBILITY,
            "Retry",
            logMsg
        )
    }

    private fun logInsertSuccess(insertedText: String, usedFallback: Boolean) {
        val strategyStr = if (usedFallback) "Fallback Strategy" else "Primary ACTION_SET_TEXT"
        val logMsg = """
            Insert Success
            Strategy Used: $strategyStr
            Verified Length: ${insertedText.length} character(s)
            Status: Text ready in input field (No Send Button Clicked as specified)
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.ACCESSIBILITY,
            "Insert Success",
            logMsg
        )
    }

    private fun logInsertFailed(reason: String) {
        val logMsg = """
            Insert Failed
            Status: INSERT_FAILED
            Reason: $reason
        """.trimIndent()

        Log.e(TAG, logMsg)
        AppLogger.warning(
            LogCategory.ACCESSIBILITY,
            "Insert Failed",
            logMsg
        )
    }

    companion object {
        private const val TAG = "MessageInputInserterEngine"
    }
}
