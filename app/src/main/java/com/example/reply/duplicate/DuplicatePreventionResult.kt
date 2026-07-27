package com.example.reply.duplicate

import com.example.model.ReplyHistory

/**
 * Detailed result model returned by Duplicate Prevention Engine.
 */
data class DuplicatePreventionResult(
    val status: DuplicatePreventionStatus,
    val isAllowed: Boolean,
    val reason: String,
    val conversationId: String,
    val replyText: String,
    val ruleId: Long?,
    val matchedHistoryItem: ReplyHistory? = null,
    val lastReplyTimestamp: Long? = null,
    val remainingCooldownMs: Long = 0L,
    val details: String = ""
)
