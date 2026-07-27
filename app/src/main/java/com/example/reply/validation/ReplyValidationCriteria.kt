package com.example.reply.validation

/**
 * Contextual validation criteria for generated reply evaluation.
 */
data class ReplyValidationCriteria(
    val maxCharacterLimit: Int = 2000,
    val ruleId: Long? = null,
    val senderName: String? = null,
    val cooldownPeriodMs: Long = 30000L, // 30 seconds rule cooldown
    val maxRepliesPerMinute: Int = 5,    // Anti-spam threshold
    val allowDuplicateReplies: Boolean = false,
    val currentTimeMillis: Long = System.currentTimeMillis()
)
