package com.example.rule

import com.example.model.AutoReplyRule
import com.example.repository.HistoryRepository
import com.example.repository.RuleRepository

sealed class PipelineResult {
    data class Matched(
        val rule: AutoReplyRule,
        val replyText: String,
        val delayMillis: Long
    ) : PipelineResult()
    
    object NoMatch : PipelineResult()
    data class Rejected(val rule: AutoReplyRule, val reason: String) : PipelineResult()
}

class RuleEngineManager(
    private val ruleRepository: RuleRepository,
    private val historyRepository: HistoryRepository,
    private val matcher: RuleMatcher = RuleMatcher(),
    private val validator: RuleValidator = RuleValidator(),
    private val executor: RuleExecutor = RuleExecutor()
) {

    /**
     * Evaluates the entire auto-reply execution pipeline for an incoming message.
     * Processes rules in descending order of priority, checking match and validating constraints.
     */
    suspend fun processPipeline(
        senderName: String,
        incomingMessage: String
    ): PipelineResult {
        // 1. Fetch active rules
        val activeRules = ruleRepository.getActiveRules()
        if (activeRules.isEmpty()) {
            return PipelineResult.NoMatch
        }

        // 2. Sort rules by Priority descending (higher number = higher priority), then by creation timestamp
        val sortedRules = activeRules.sortedWith(
            compareByDescending<AutoReplyRule> { it.priority }
                .thenByDescending { it.createdAt }
        )

        // 3. Find first matching rule
        val matchedRule = sortedRules.firstOrNull { rule ->
            matcher.isMatch(incomingMessage, rule)
        } ?: return PipelineResult.NoMatch

        // 4. Validate limits (cooldown, daily limits, global limits)
        return when (val validationResult = validator.validate(matchedRule, historyRepository)) {
            is ValidationResult.Valid -> {
                // 5. Execute rule (formatting & place-holding)
                when (val executionResult = executor.execute(matchedRule, senderName, incomingMessage)) {
                    is ExecutionResult.Success -> {
                        PipelineResult.Matched(
                            rule = matchedRule,
                            replyText = executionResult.replyText,
                            delayMillis = executionResult.delayMillis
                        )
                    }
                    is ExecutionResult.Error -> {
                        PipelineResult.Rejected(matchedRule, executionResult.reason)
                    }
                }
            }
            is ValidationResult.Invalid -> {
                PipelineResult.Rejected(matchedRule, validationResult.reason)
            }
        }
    }
}
