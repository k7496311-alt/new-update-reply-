package com.example.model

/**
 * Domain model representing a rule for auto-reply logic.
 */
data class AutoReplyRule(
    val id: Long = 0L,
    val name: String,
    val keyword: String,
    val replyText: String,
    val isEnabled: Boolean = true,
    val matchType: MatchType = MatchType.CONTAINS,
    val replyDelayMillis: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: RuleStatus = RuleStatus.ACTIVE,
    val priority: Int = 0,
    val cooldownMillis: Long = 0L,
    val maxReplies: Int = 0,
    val category: String = "General",

    // Rule Options & Matching Configuration
    val isCaseSensitive: Boolean = false,
    val shouldTrimSpaces: Boolean = true,
    val shouldIgnoreEmoji: Boolean = false,
    val shouldIgnoreSymbols: Boolean = false,
    val shouldIgnoreMultipleSpaces: Boolean = false,

    // Performance and limit rules
    val dailyLimit: Int = 0, // 0 for unlimited
    val globalLimit: Int = 0 // 0 for unlimited (synced with maxReplies for backward compatibility)
)

enum class MatchType {
    EXACT,
    CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    EXCLUDE,
    REGEX
}

enum class RuleStatus {
    ACTIVE,
    INACTIVE
}
