package com.example.reply

import com.example.model.AutoReplyRule

/**
 * Represents the final result of reply generation.
 */
data class FinalReply(
    val replyText: String,
    val isTriggered: Boolean,
    val status: ReplyGenerationStatus,
    val delayMillis: Long = 0L,
    val ruleId: Long? = null,
    val reason: String = ""
)

enum class ReplyGenerationStatus {
    SUCCESS,          // Reply successfully generated
    COOLDOWN,         // Throttled by cooldown configuration
    LIMIT_EXCEEDED,   // Daily or global limit reached
    NO_MATCH,         // No rule matched and no default reply configured
    SKIPPED,          // Explicitly skipped (e.g., rule disabled or error)
    DEFAULT           // Generated from fallback/default reply settings
}
