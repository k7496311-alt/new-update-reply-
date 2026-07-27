package com.example.reply

/**
 * Domain model containing the generated final reply and associated metadata.
 */
data class FinalReplyResult(
    val status: FinalReplyStatus,
    val selectedRuleId: Long?,
    val selectedRuleName: String,
    val rawReplyText: String,
    val expandedReplyText: String,
    val characterCount: Int,
    val customerName: String?,
    val date: String,
    val time: String,
    val details: String = ""
)
