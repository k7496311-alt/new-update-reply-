package com.example.reply.duplicate

/**
 * Contextual criteria for checking and preventing duplicate replies.
 */
data class DuplicateCheckCriteria(
    val conversationId: String, // Sender name, phone, or chat ID
    val replyText: String,
    val ruleId: Long? = null,
    val ruleName: String? = null,
    val cooldownPeriodMs: Long = 60000L, // Default 60 seconds general cooldown
    val perRuleCooldownMs: Long? = null, // Custom per-rule cooldown
    val perConversationCooldownMs: Long? = null, // Custom per-conversation cooldown
    val currentTimeMillis: Long = System.currentTimeMillis()
)
