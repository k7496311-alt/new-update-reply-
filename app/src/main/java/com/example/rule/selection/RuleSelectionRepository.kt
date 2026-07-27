package com.example.rule.selection

import com.example.model.AutoReplyRule

/**
 * Clean Architecture repository interface for evaluating and choosing ONE final rule.
 */
interface RuleSelectionRepository {
    /**
     * Evaluates matched candidate rules against database, priorities, business hours, customer type,
     * language, schedule and validity filters to choose ONE final rule.
     */
    suspend fun selectBestRule(
        matchedRules: List<AutoReplyRule>,
        criteria: RuleSelectionCriteria = RuleSelectionCriteria()
    ): RuleSelectionResult
}
