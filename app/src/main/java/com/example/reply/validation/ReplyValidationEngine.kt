package com.example.reply.validation

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.repository.ReplyValidationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-grade Reply Validation Engine.
 *
 * Requirements:
 * - Validates generated reply against null, empty, whitespace, oversized (>2000), corrupted Unicode,
 *   unexpanded variables, duplicate replies, and spam prevention limits.
 * - Returns VALID or INVALID with reason.
 * - Emits required logs:
 *   - Validation Started
 *   - Validation Passed
 *   - Validation Failed
 *   - Reason
 * - DO NOT insert text.
 * - DO NOT send reply.
 * - Only validation.
 */
class ReplyValidationEngine(
    private val repository: ReplyValidationRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun validateReply(
        replyText: String?,
        criteria: ReplyValidationCriteria = ReplyValidationCriteria()
    ): ReplyValidationResult = withContext(dispatcher) {

        // 1. Log: Validation Started
        logValidationStarted(replyText, criteria.ruleId)

        val result = repository.validateReply(replyText, criteria)

        if (result.isValid) {
            // 2. Log: Validation Passed
            logValidationPassed(result.characterCount)

            // 3. Log: Reason
            logReason(result.reason, isPassed = true)
        } else {
            // 2. Log: Validation Failed
            logValidationFailed(result.failedChecks)

            // 3. Log: Reason
            logReason(result.reason, isPassed = false)
        }

        result
    }

    private fun logValidationStarted(replyText: String?, ruleId: Long?) {
        val sample = if (replyText == null) "null" else "\"${replyText.take(50)}\""
        val logMsg = "Validation Started: Evaluating reply $sample (Rule ID: ${ruleId ?: "N/A"})"
        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.REPLY,
            "Validation Started",
            logMsg
        )
    }

    private fun logValidationPassed(characterCount: Int) {
        val logMsg = "Validation Passed: Reply meets all safety and formatting standards ($characterCount characters)."
        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.REPLY,
            "Validation Passed",
            logMsg
        )
    }

    private fun logValidationFailed(failedChecks: List<String>) {
        val checksStr = failedChecks.joinToString(", ")
        val logMsg = "Validation Failed: Safety check(s) failed [$checksStr]."
        Log.w(TAG, logMsg)
        AppLogger.warning(
            LogCategory.REPLY,
            "Validation Failed",
            logMsg
        )
    }

    private fun logReason(reason: String, isPassed: Boolean) {
        val logMsg = "Reason: $reason"
        if (isPassed) {
            Log.i(TAG, logMsg)
            AppLogger.info(
                LogCategory.REPLY,
                "Reason",
                logMsg
            )
        } else {
            Log.e(TAG, logMsg)
            AppLogger.warning(
                LogCategory.REPLY,
                "Reason",
                logMsg
            )
        }
    }

    companion object {
        private const val TAG = "ReplyValidationEngine"
    }
}
