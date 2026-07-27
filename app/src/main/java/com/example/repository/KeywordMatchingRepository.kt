package com.example.repository

import com.example.accessibility.imo.ConversationContextModel
import com.example.accessibility.imo.KeywordMatchResult
import com.example.accessibility.imo.KeywordMatchRule

/**
 * Clean Architecture repository interface for Keyword Matching Engine.
 */
interface KeywordMatchingRepository {
    /**
     * Matches the provided conversation context against a set of keyword rules.
     * Uses normalized text (Bangla, English, Unicode, case-insensitive, punctuation-stripped)
     * and calculates rule priority (Highest Priority -> Longest Keyword -> Oldest Rule).
     */
    suspend fun matchKeywords(
        conversationContext: ConversationContextModel,
        rules: List<KeywordMatchRule>? = null
    ): KeywordMatchResult

    /**
     * Retrieves active default or persistent rules configured in the application.
     */
    suspend fun getActiveRules(): List<KeywordMatchRule>
}
