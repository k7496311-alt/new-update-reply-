package com.example.accessibility.imo

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.repository.KeywordMatchingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-grade Keyword Matching Engine.
 *
 * IMPORTANT RULES:
 * - Never uses Notification text.
 * - Never uses cached text.
 * - Always uses Conversation Context generated from Step 12 (ConversationContextModel).
 * - Evaluates normalized Bangla, English, Mixed Language, Unicode text.
 * - Calculates rule priority (Highest Priority -> Longest Keyword -> Oldest Rule).
 * - Emits exact required logs:
 *   - Conversation
 *   - Normalized Text
 *   - Matched Rule
 *   - Matched Keyword
 *   - Rule Priority
 *   - No Match (if no rule matches)
 * - DO NOT generate reply.
 * - DO NOT open AI.
 * - DO NOT insert text.
 * - DO NOT send.
 */
class KeywordMatchingEngine(
    private val repository: KeywordMatchingRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun match(
        conversationContext: ConversationContextModel,
        rules: List<KeywordMatchRule>? = null
    ): KeywordMatchResult = withContext(dispatcher) {

        val result = repository.matchKeywords(conversationContext, rules)

        // 1. Log: Conversation
        logConversation(result.originalConversation)

        // 2. Log: Normalized Text
        logNormalizedText(result.normalizedText)

        if (result.status == KeywordMatchStatus.MATCHED) {
            // 3. Log: Matched Rule
            logMatchedRule(result.matchedRuleName, result.matchedRuleId)

            // 4. Log: Matched Keyword
            logMatchedKeyword(result.matchedKeyword, result.confidence)

            // 5. Log: Rule Priority
            logRulePriority(result.priority)
        } else {
            // 6. Log: No Match
            logNoMatch(result.details)
        }

        result
    }

    private fun logConversation(conversation: String) {
        val logMsg = "Conversation:\n\"$conversation\""
        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Conversation",
            logMsg
        )
    }

    private fun logNormalizedText(normalizedText: String) {
        val logMsg = "Normalized Text: \"$normalizedText\""
        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Normalized Text",
            logMsg
        )
    }

    private fun logMatchedRule(ruleName: String, ruleId: Long?) {
        val logMsg = "Matched Rule: $ruleName (ID: ${ruleId ?: "N/A"})"
        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.ACCESSIBILITY,
            "Matched Rule",
            logMsg
        )
    }

    private fun logMatchedKeyword(keyword: String, confidence: Double) {
        val confidencePercent = (confidence * 100).toInt()
        val logMsg = "Matched Keyword: \"$keyword\" (Confidence: $confidencePercent%)"
        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Matched Keyword",
            logMsg
        )
    }

    private fun logRulePriority(priority: Int) {
        val logMsg = "Rule Priority: $priority"
        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Rule Priority",
            logMsg
        )
    }

    private fun logNoMatch(details: String) {
        val logMsg = "No Match: $details"
        Log.w(TAG, logMsg)
        AppLogger.warning(
            LogCategory.ACCESSIBILITY,
            "No Match",
            logMsg
        )
    }

    companion object {
        private const val TAG = "KeywordMatchingEngine"
    }
}
