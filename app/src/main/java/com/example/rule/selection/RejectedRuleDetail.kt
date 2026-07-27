package com.example.rule.selection

/**
 * Detailed information for candidate rules rejected during evaluation.
 */
data class RejectedRuleDetail(
    val ruleId: Long,
    val ruleName: String,
    val rejectionReason: String
)
