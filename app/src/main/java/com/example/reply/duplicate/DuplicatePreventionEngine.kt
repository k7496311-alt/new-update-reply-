package com.example.reply.duplicate

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.model.ReplyHistory
import com.example.repository.DuplicatePreventionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-grade Duplicate Prevention Engine.
 *
 * Requirements:
 * - Reads history to block duplicate replies to the same conversation.
 * - Supports configurable cooldowns per conversation and per rule.
 * - Emits exact required logs:
 *   - History Found
 *   - Duplicate Reply
 *   - Cooldown Active
 *   - Reply Allowed
 * - Returns ALLOW or BLOCK.
 * - NO Accessibility, NO Send.
 */
class DuplicatePreventionEngine(
    private val repository: DuplicatePreventionRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun evaluate(
        criteria: DuplicateCheckCriteria
    ): DuplicatePreventionResult = withContext(dispatcher) {

        val conversationHistory = repository.getHistoryForConversation(criteria.conversationId)
        if (conversationHistory.isNotEmpty()) {
            // 1. Log: History Found
            logHistoryFound(criteria.conversationId, conversationHistory)
        }

        val result = repository.evaluateDuplicateRisk(criteria)

        when (result.status) {
            DuplicatePreventionStatus.BLOCK -> {
                if (result.reason.contains("Duplicate Reply", ignoreCase = true)) {
                    // 2. Log: Duplicate Reply
                    logDuplicateReply(
                        conversationId = result.conversationId,
                        replyText = result.replyText,
                        matchedHistory = result.matchedHistoryItem
                    )
                }

                // 3. Log: Cooldown Active
                logCooldownActive(
                    remainingMs = result.remainingCooldownMs,
                    reason = result.reason
                )
            }
            DuplicatePreventionStatus.ALLOW -> {
                // 4. Log: Reply Allowed
                logReplyAllowed(
                    conversationId = result.conversationId,
                    ruleId = result.ruleId
                )
            }
        }

        result
    }

    private fun logHistoryFound(conversationId: String, historyList: List<ReplyHistory>) {
        val mostRecent = historyList.maxByOrNull { it.timestamp }
        val logMsg = """
            History Found
            Conversation ID: "$conversationId"
            Total Previous Messages: ${historyList.size}
            Latest Message Timestamp: ${mostRecent?.timestamp ?: "N/A"}
            Latest Reply: "${mostRecent?.repliedMessage ?: "N/A"}"
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.REPLY,
            "History Found",
            logMsg
        )
    }

    private fun logDuplicateReply(
        conversationId: String,
        replyText: String,
        matchedHistory: ReplyHistory?
    ) {
        val logMsg = """
            Duplicate Reply
            Blocked duplicate reply to conversation "$conversationId":
            Text: "$replyText"
            Matched Record ID: ${matchedHistory?.id ?: "N/A"}
        """.trimIndent()

        Log.w(TAG, logMsg)
        AppLogger.warning(
            LogCategory.REPLY,
            "Duplicate Reply",
            logMsg
        )
    }

    private fun logCooldownActive(remainingMs: Long, reason: String) {
        val remainingSec = (remainingMs / 1000).coerceAtLeast(1)
        val logMsg = """
            Cooldown Active
            Remaining Cooldown: ${remainingSec}s (${remainingMs}ms)
            Reason: $reason
        """.trimIndent()

        Log.w(TAG, logMsg)
        AppLogger.warning(
            LogCategory.REPLY,
            "Cooldown Active",
            logMsg
        )
    }

    private fun logReplyAllowed(conversationId: String, ruleId: Long?) {
        val logMsg = """
            Reply Allowed
            Target Conversation: "$conversationId"
            Rule ID: ${ruleId ?: "N/A"}
            Status: Safe to transmit (ALLOW)
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.REPLY,
            "Reply Allowed",
            logMsg
        )
    }

    companion object {
        private const val TAG = "DuplicatePreventionEngine"
    }
}
