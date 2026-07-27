package com.example.accessibility.imo

/**
 * Result model returned by Keyword Matching Engine.
 */
data class KeywordMatchResult(
    val status: KeywordMatchStatus,
    val matchedRuleId: Long?,
    val matchedRuleName: String,
    val matchedKeyword: String,
    val confidence: Double,
    val priority: Int,
    val normalizedText: String,
    val originalConversation: String,
    val details: String = ""
)
