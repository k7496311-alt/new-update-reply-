package com.example.reply

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.AutoReplyRule
import com.example.model.LogCategory
import com.example.repository.FinalReplyRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-grade Final Reply Engine.
 *
 * Requirements:
 * - Reply comes ONLY from selected rule.
 * - Never creates random text.
 * - Never modifies saved reply except expanding variables ({customer_name}, {date}, {time}).
 * - Leaves missing variables empty.
 * - Preserves Bangla, English, Unicode, Emojis, Long reply, Multi-line reply.
 * - Emits exact required logs:
 *   - Reply Loaded
 *   - Variables Expanded
 *   - Final Character Count
 *   - Final Reply
 * - DO NOT insert text.
 * - DO NOT send reply.
 */
class FinalReplyEngine(
    private val repository: FinalReplyRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun generateReply(
        selectedRule: AutoReplyRule,
        customerName: String? = null,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): FinalReplyResult = withContext(dispatcher) {

        val result = repository.generateFinalReply(selectedRule, customerName, currentTimeMillis)

        // 1. Log: Reply Loaded
        logReplyLoaded(result.rawReplyText, result.selectedRuleName, result.selectedRuleId)

        // 2. Log: Variables Expanded
        logVariablesExpanded(
            result.expandedReplyText,
            result.customerName,
            result.date,
            result.time
        )

        // 3. Log: Final Character Count
        logFinalCharacterCount(result.characterCount)

        // 4. Log: Final Reply
        logFinalReply(result.expandedReplyText)

        result
    }

    private fun logReplyLoaded(rawReply: String, ruleName: String, ruleId: Long?) {
        val logMsg = """
            Reply Loaded
            Source Rule: "$ruleName" (ID: ${ruleId ?: "N/A"})
            Raw Stored Reply:
            "$rawReply"
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.REPLY,
            "Reply Loaded",
            logMsg
        )
    }

    private fun logVariablesExpanded(
        expandedText: String,
        customerName: String?,
        date: String,
        time: String
    ) {
        val nameValue = customerName?.takeIf { it.isNotBlank() } ?: "(empty)"
        val logMsg = """
            Variables Expanded
            Replacements applied: {customer_name} -> "$nameValue", {date} -> "$date", {time} -> "$time"
            Expanded Text:
            "$expandedText"
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.REPLY,
            "Variables Expanded",
            logMsg
        )
    }

    private fun logFinalCharacterCount(charCount: Int) {
        val logMsg = "Final Character Count: $charCount character(s)"
        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.REPLY,
            "Final Character Count",
            logMsg
        )
    }

    private fun logFinalReply(finalReply: String) {
        val logMsg = """
            Final Reply
            "$finalReply"
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.REPLY,
            "Final Reply",
            logMsg
        )
    }

    companion object {
        private const val TAG = "FinalReplyEngine"
    }
}
