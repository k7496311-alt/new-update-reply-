package com.example.accessibility.imo

/**
 * Domain rule model representing a rule with keyword list and priority for Keyword Matching Engine.
 */
data class KeywordMatchRule(
    val ruleId: Long,
    val ruleName: String,
    val keywords: List<String>,
    val matchType: RuleMatchType = RuleMatchType.CONTAINS,
    val priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class RuleMatchType {
    EXACT_MATCH,
    CONTAINS,
    STARTS_WITH,
    ENDS_WITH
}
