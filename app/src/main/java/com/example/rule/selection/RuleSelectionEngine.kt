package com.example.rule.selection

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.AutoReplyRule
import com.example.model.LogCategory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-grade Rule Selection Engine.
 *
 * Requirements:
 * - Input: Matched Rules, Rule Database, Priority, Time.
 * - Chooses ONE final rule based on priority, schedule, enabled status, business hours, customer type, language.
 * - Rejects expired, disabled, or invalid rules.
 * - Emits exact required logs:
 *   - Candidate Rules
 *   - Rejected Rules
 *   - Selected Rule
 *   - Selection Reason
 * - No reply generation, No AI, No Accessibility.
 */
class RuleSelectionEngine(
    private val repository: RuleSelectionRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun selectRule(
        matchedRules: List<AutoReplyRule>,
        criteria: RuleSelectionCriteria = RuleSelectionCriteria()
    ): RuleSelectionResult = withContext(dispatcher) {

        val result = repository.selectBestRule(matchedRules, criteria)

        // 1. Log: Candidate Rules
        logCandidateRules(result.candidateRules)

        // 2. Log: Rejected Rules
        logRejectedRules(result.rejectedRules)

        if (result.status == RuleSelectionStatus.SELECTED && result.selectedRule != null) {
            // 3. Log: Selected Rule
            logSelectedRule(result.selectedRule, result.replyId)

            // 4. Log: Selection Reason
            logSelectionReason(result.selectionReason)
        } else {
            // Log: Selection Reason (No suitable rule)
            logSelectionReason(result.selectionReason)
        }

        result
    }

    private fun logCandidateRules(candidates: List<AutoReplyRule>) {
        val sb = StringBuilder()
        sb.append("Candidate Rules (${candidates.size} total):\n")
        candidates.forEachIndexed { idx, rule ->
            sb.append("#${idx + 1} | ID: ${rule.id} | Name: '${rule.name}' | Priority: ${rule.priority} | Enabled: ${rule.isEnabled} | Status: ${rule.status}\n")
        }

        val logMsg = sb.toString().trimEnd()
        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.REPLY,
            "Candidate Rules",
            logMsg
        )
    }

    private fun logRejectedRules(rejectedList: List<RejectedRuleDetail>) {
        if (rejectedList.isEmpty()) {
            val msg = "Rejected Rules: None (0 rules rejected)."
            Log.i(TAG, msg)
            AppLogger.info(LogCategory.REPLY, "Rejected Rules", msg)
            return
        }

        val sb = StringBuilder()
        sb.append("Rejected Rules (${rejectedList.size} rejected):\n")
        rejectedList.forEachIndexed { idx, item ->
            sb.append("#${idx + 1} | Rule ID: ${item.ruleId} ('${item.ruleName}') | Reason: ${item.rejectionReason}\n")
        }

        val logMsg = sb.toString().trimEnd()
        Log.w(TAG, logMsg)
        AppLogger.warning(
            LogCategory.REPLY,
            "Rejected Rules",
            logMsg
        )
    }

    private fun logSelectedRule(rule: AutoReplyRule, replyId: String) {
        val logMsg = """
            Selected Rule
            Rule ID: ${rule.id}
            Rule Name: "${rule.name}"
            Priority: ${rule.priority}
            Category: ${rule.category}
            Reply ID: $replyId
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.REPLY,
            "Selected Rule",
            logMsg
        )
    }

    private fun logSelectionReason(reason: String) {
        val logMsg = "Selection Reason: $reason"
        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.REPLY,
            "Selection Reason",
            logMsg
        )
    }

    companion object {
        private const val TAG = "RuleSelectionEngine"
    }
}
