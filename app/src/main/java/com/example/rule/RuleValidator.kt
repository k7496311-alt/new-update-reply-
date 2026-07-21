package com.example.rule

import com.example.model.AutoReplyRule
import com.example.model.RuleStatus
import com.example.repository.HistoryRepository
import java.util.Calendar

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}

class RuleValidator {

    /**
     * Validates a rule's active state, cooldown state, and daily/global reply limits.
     */
    suspend fun validate(rule: AutoReplyRule, historyRepository: HistoryRepository): ValidationResult {
        // 1. Check if the rule is enabled and active
        if (!rule.isEnabled || rule.status != RuleStatus.ACTIVE) {
            return ValidationResult.Invalid("Rule is disabled or inactive.")
        }

        val ruleId = rule.id
        if (ruleId == 0L) {
            // New unsaved rule is always valid for validation simulation
            return ValidationResult.Valid
        }

        val now = System.currentTimeMillis()

        // 2. Cooldown check
        if (rule.cooldownMillis > 0L) {
            val lastReplyTime = historyRepository.getLastReplyTimestampForRule(ruleId)
            if (lastReplyTime != null) {
                val elapsed = now - lastReplyTime
                if (elapsed < rule.cooldownMillis) {
                    val remainingSeconds = (rule.cooldownMillis - elapsed) / 1000
                    return ValidationResult.Invalid("Rule is on cooldown. Remaining time: ${remainingSeconds}s.")
                }
            }
        }

        // 3. Global Limit check
        // Check both globalLimit and maxReplies to ensure complete backward compatibility
        val globalLimitVal = if (rule.globalLimit > 0) rule.globalLimit else rule.maxReplies
        if (globalLimitVal > 0) {
            val totalReplies = historyRepository.getReplyCountForRule(ruleId)
            if (totalReplies >= globalLimitVal) {
                return ValidationResult.Invalid("Global reply limit reached ($totalReplies/$globalLimitVal).")
            }
        }

        // 4. Daily Limit check
        if (rule.dailyLimit > 0) {
            val startOfToday = getStartOfToday()
            val dailyReplies = historyRepository.getReplyCountForRuleSince(ruleId, startOfToday)
            if (dailyReplies >= rule.dailyLimit) {
                return ValidationResult.Invalid("Daily reply limit reached ($dailyReplies/${rule.dailyLimit}).")
            }
        }

        return ValidationResult.Valid
    }

    private fun getStartOfToday(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
