package com.example.rule.selection

import com.example.model.AutoReplyRule
import com.example.model.RuleStatus

/**
 * Concrete implementation of RuleSelectionRepository.
 * Filters out disabled, expired, or invalid rules, verifies business hours, customer type,
 * and language criteria, and selects ONE final rule with the highest priority.
 */
class RuleSelectionRepositoryImpl : RuleSelectionRepository {

    override suspend fun selectBestRule(
        matchedRules: List<AutoReplyRule>,
        criteria: RuleSelectionCriteria
    ): RuleSelectionResult {

        if (matchedRules.isEmpty()) {
            return RuleSelectionResult(
                status = RuleSelectionStatus.NO_SUITABLE_RULE,
                selectedRule = null,
                selectedRuleId = null,
                replyId = "",
                selectionReason = "No candidate matched rules provided as input.",
                candidateRules = emptyList(),
                rejectedRules = emptyList()
            )
        }

        val rejectedList = mutableListOf<RejectedRuleDetail>()
        val validCandidates = mutableListOf<AutoReplyRule>()

        for (rule in matchedRules) {
            val rejectionReason = validateRule(rule, criteria)
            if (rejectionReason != null) {
                rejectedList.add(
                    RejectedRuleDetail(
                        ruleId = rule.id,
                        ruleName = rule.name,
                        rejectionReason = rejectionReason
                    )
                )
            } else {
                validCandidates.add(rule)
            }
        }

        if (validCandidates.isEmpty()) {
            return RuleSelectionResult(
                status = RuleSelectionStatus.NO_SUITABLE_RULE,
                selectedRule = null,
                selectedRuleId = null,
                replyId = "",
                selectionReason = "All candidate rules were rejected during schedule, status, and criteria evaluation.",
                candidateRules = matchedRules,
                rejectedRules = rejectedList
            )
        }

        // Sort valid candidate rules:
        // 1. Highest Priority (priority DESC)
        // 2. Creation time (createdAt ASC - oldest rule wins ties)
        // 3. Rule ID (id ASC)
        val selectedRule = validCandidates.sortedWith(
            compareByDescending<AutoReplyRule> { it.priority }
                .thenBy { it.createdAt }
                .thenBy { it.id }
        ).first()

        val replyId = "REPLY_${selectedRule.id}_${selectedRule.category.uppercase()}"
        val selectionReason = "Selected Rule '${selectedRule.name}' (ID: ${selectedRule.id}) with highest priority (${selectedRule.priority}) among ${validCandidates.size} valid candidate(s)."

        return RuleSelectionResult(
            status = RuleSelectionStatus.SELECTED,
            selectedRule = selectedRule,
            selectedRuleId = selectedRule.id,
            replyId = replyId,
            selectionReason = selectionReason,
            candidateRules = matchedRules,
            rejectedRules = rejectedList
        )
    }

    private fun validateRule(rule: AutoReplyRule, criteria: RuleSelectionCriteria): String? {
        // 1. Disabled rule check
        if (!rule.isEnabled) {
            return "Disabled rule: Rule is explicitly disabled (isEnabled = false)."
        }

        if (rule.status != RuleStatus.ACTIVE) {
            return "Disabled rule: Rule status is inactive (${rule.status})."
        }

        // 2. Invalid rule check (empty reply text)
        if (rule.replyText.isBlank()) {
            return "Invalid rule: Reply text is blank or missing."
        }

        // 3. Business hours constraint check
        if (!criteria.isBusinessHoursActive && rule.category.equals("BusinessHours", ignoreCase = true)) {
            return "Business Hours constraint: Rule requires active business hours."
        }

        // 4. Customer Type constraint check
        if (criteria.customerType != CustomerType.ALL) {
            val ruleCategory = rule.category.uppercase()
            if (ruleCategory.contains("VIP") && criteria.customerType != CustomerType.VIP) {
                return "Customer Type mismatch: Rule targets VIP customers."
            }
            if (ruleCategory.contains("NEW") && criteria.customerType != CustomerType.NEW_CUSTOMER) {
                return "Customer Type mismatch: Rule targets new customers."
            }
        }

        // 5. Language constraint check
        if (criteria.language != RuleLanguage.ALL) {
            val ruleCategory = rule.category.uppercase()
            if (ruleCategory.contains("BANGLA") && criteria.language != RuleLanguage.BANGLA) {
                return "Language mismatch: Rule requires Bangla language context."
            }
            if (ruleCategory.contains("ENGLISH") && criteria.language != RuleLanguage.ENGLISH) {
                return "Language mismatch: Rule requires English language context."
            }
        }

        return null
    }
}
