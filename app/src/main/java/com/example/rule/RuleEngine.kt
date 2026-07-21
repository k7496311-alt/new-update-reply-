package com.example.rule

import com.example.model.AutoReplyRule

class RuleEngine {

    private val matcher = RuleMatcher()
    private val matchingEngine = RuleMatchingEngine()

    /**
     * Backward-compatible findMatchingRule method.
     */
    fun findMatchingRule(incomingMessage: String, activeRules: List<AutoReplyRule>): AutoReplyRule? {
        return findBestMatchingRule(incomingMessage, activeRules)?.rule
    }

    /**
     * Highly optimized, scored matching returning the BestMatchedRule details.
     */
    fun findBestMatchingRule(incomingMessage: String, activeRules: List<AutoReplyRule>): BestMatchedRule? {
        matchingEngine.updateRulesIndex(activeRules)
        return matchingEngine.findBestMatch(incomingMessage)
    }
}
