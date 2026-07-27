package com.example.data

import com.example.accessibility.imo.ConversationContextModel
import com.example.accessibility.imo.KeywordMatchResult
import com.example.accessibility.imo.KeywordMatchRule
import com.example.accessibility.imo.KeywordMatchStatus
import com.example.accessibility.imo.RuleMatchType
import com.example.model.MatchType
import com.example.repository.KeywordMatchingRepository
import com.example.repository.RuleRepository

/**
 * Concrete implementation of KeywordMatchingRepository.
 *
 * Requirements:
 * - Uses Conversation Context generated from Step 12 (never Notification or cached text).
 * - Supports Bangla, English, Mixed Language, Unicode.
 * - Normalizes text: case-insensitive, trims whitespace, strips punctuation & unicode invisible characters.
 * - Supports Contains, Starts With, Ends With, Exact Match.
 * - Supports multiple keywords per rule (comma/line-separated).
 * - Conflict resolution order:
 *   1. Highest Priority
 *   2. Longest Keyword
 *   3. Oldest Rule
 */
class KeywordMatchingRepositoryImpl(
    private val persistentRuleRepository: RuleRepository? = null
) : KeywordMatchingRepository {

    override suspend fun getActiveRules(): List<KeywordMatchRule> {
        val dbRules = persistentRuleRepository?.getActiveRules() ?: emptyList()
        if (dbRules.isNotEmpty()) {
            return dbRules.map { rule ->
                val keywordList = rule.keyword.split(Regex("[,\\n]"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                val matchType = when (rule.matchType) {
                    MatchType.EXACT -> RuleMatchType.EXACT_MATCH
                    MatchType.STARTS_WITH -> RuleMatchType.STARTS_WITH
                    MatchType.ENDS_WITH -> RuleMatchType.ENDS_WITH
                    else -> RuleMatchType.CONTAINS
                }

                KeywordMatchRule(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    keywords = keywordList.ifEmpty { listOf(rule.keyword) },
                    matchType = matchType,
                    priority = rule.priority,
                    createdAt = rule.createdAt
                )
            }
        }

        // Return default sample domain rules if database has no rules configured
        return getDefaultSampleRules()
    }

    override suspend fun matchKeywords(
        conversationContext: ConversationContextModel,
        rules: List<KeywordMatchRule>?
    ): KeywordMatchResult {
        val activeRules = if (!rules.isNullOrEmpty()) rules else getActiveRules()
        val rawContextText = conversationContext.conversationContext
        val normalizedContext = normalizeText(rawContextText)

        if (normalizedContext.isBlank() || activeRules.isEmpty()) {
            return KeywordMatchResult(
                status = KeywordMatchStatus.NO_MATCH,
                matchedRuleId = null,
                matchedRuleName = "",
                matchedKeyword = "",
                confidence = 0.0,
                priority = 0,
                normalizedText = normalizedContext,
                originalConversation = rawContextText,
                details = "No match: Conversation context is blank or no rules configured."
            )
        }

        val candidates = mutableListOf<MatchCandidate>()

        for (rule in activeRules) {
            for (rawKeyword in rule.keywords) {
                val normalizedKw = normalizeText(rawKeyword)
                if (normalizedKw.isBlank()) continue

                val isMatch = evaluateMatch(normalizedContext, normalizedKw, rule.matchType)
                if (isMatch) {
                    val confidence = calculateConfidence(normalizedContext, normalizedKw, rule.matchType)
                    candidates.add(
                        MatchCandidate(
                            rule = rule,
                            matchedKeyword = rawKeyword.trim(),
                            normalizedKeyword = normalizedKw,
                            confidence = confidence
                        )
                    )
                }
            }
        }

        if (candidates.isEmpty()) {
            return KeywordMatchResult(
                status = KeywordMatchStatus.NO_MATCH,
                matchedRuleId = null,
                matchedRuleName = "",
                matchedKeyword = "",
                confidence = 0.0,
                priority = 0,
                normalizedText = normalizedContext,
                originalConversation = rawContextText,
                details = "No match: No rules matched the normalized context."
            )
        }

        // Priority resolution:
        // 1. Highest Priority (priority DESC)
        // 2. Longest Keyword (normalizedKeyword.length DESC)
        // 3. Oldest Rule (createdAt ASC, ruleId ASC)
        val bestCandidate = candidates.sortedWith(
            compareByDescending<MatchCandidate> { it.rule.priority }
                .thenByDescending { it.normalizedKeyword.length }
                .thenBy { it.rule.createdAt }
                .thenBy { it.rule.ruleId }
        ).first()

        return KeywordMatchResult(
            status = KeywordMatchStatus.MATCHED,
            matchedRuleId = bestCandidate.rule.ruleId,
            matchedRuleName = bestCandidate.rule.ruleName,
            matchedKeyword = bestCandidate.matchedKeyword,
            confidence = bestCandidate.confidence,
            priority = bestCandidate.rule.priority,
            normalizedText = normalizedContext,
            originalConversation = rawContextText,
            details = "Matched Rule '${bestCandidate.rule.ruleName}' (ID: ${bestCandidate.rule.ruleId}) with keyword '${bestCandidate.matchedKeyword}'."
        )
    }

    private fun evaluateMatch(text: String, keyword: String, matchType: RuleMatchType): Boolean {
        return when (matchType) {
            RuleMatchType.EXACT_MATCH -> text == keyword
            RuleMatchType.STARTS_WITH -> text.startsWith(keyword)
            RuleMatchType.ENDS_WITH -> text.endsWith(keyword)
            RuleMatchType.CONTAINS -> text.contains(keyword)
        }
    }

    private fun calculateConfidence(text: String, keyword: String, matchType: RuleMatchType): Double {
        return when (matchType) {
            RuleMatchType.EXACT_MATCH -> 1.00
            RuleMatchType.STARTS_WITH, RuleMatchType.ENDS_WITH -> 0.95
            RuleMatchType.CONTAINS -> {
                val ratio = (keyword.length.toDouble() / text.length.toDouble()).coerceAtMost(1.0)
                0.85 + (0.10 * ratio)
            }
        }
    }

    private fun normalizeText(input: String): String {
        if (input.isBlank()) return ""

        return input
            .lowercase()
            // Strip invisible Unicode characters & zero-width joiners
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
            // Normalize punctuation marks & symbols into standard space
            .replace(Regex("[\\p{Punct}\\p{S}]"), " ")
            // Collapse multiple whitespace chars into single space
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun getDefaultSampleRules(): List<KeywordMatchRule> {
        val now = System.currentTimeMillis()
        return listOf(
            KeywordMatchRule(
                ruleId = 1L,
                ruleName = "Greeting Rule",
                keywords = listOf("hi", "hello", "হ্যালো", "আসসালামু আলাইকুম", "hey"),
                matchType = RuleMatchType.CONTAINS,
                priority = 10,
                createdAt = now - 10000L
            ),
            KeywordMatchRule(
                ruleId = 2L,
                ruleName = "Price Inquiry Rule",
                keywords = listOf("price", "cost", "দাম", "মূল্য", "কত", "rate"),
                matchType = RuleMatchType.CONTAINS,
                priority = 20,
                createdAt = now - 5000L
            ),
            KeywordMatchRule(
                ruleId = 3L,
                ruleName = "Availability Check Rule",
                keywords = listOf("are you available", "available", "আছেন", "পাওয়া যাবে"),
                matchType = RuleMatchType.CONTAINS,
                priority = 15,
                createdAt = now - 2000L
            )
        )
    }

    private data class MatchCandidate(
        val rule: KeywordMatchRule,
        val matchedKeyword: String,
        val normalizedKeyword: String,
        val confidence: Double
    )
}
