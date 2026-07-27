package com.example.rule.selection

import com.example.model.AutoReplyRule

/**
 * Result model holding the selected rule, reply identifier, selection reason, and audit lists.
 */
data class RuleSelectionResult(
    val status: RuleSelectionStatus,
    val selectedRule: AutoReplyRule?,
    val selectedRuleId: Long?,
    val replyId: String,
    val selectionReason: String,
    val candidateRules: List<AutoReplyRule>,
    val rejectedRules: List<RejectedRuleDetail>
)
