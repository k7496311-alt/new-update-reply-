package com.example.rule

import com.example.model.AutoReplyRule

/**
 * Represents the best matching result from the Rule Matching Engine.
 */
data class BestMatchedRule(
    val rule: AutoReplyRule,
    val matchedKeyword: String,
    val score: Int, // Match strength/priority score
    val selectedReply: String
)
