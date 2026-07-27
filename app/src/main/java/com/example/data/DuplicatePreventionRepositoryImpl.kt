package com.example.data

import com.example.model.ReplyHistory
import com.example.reply.duplicate.DuplicateCheckCriteria
import com.example.reply.duplicate.DuplicatePreventionResult
import com.example.reply.duplicate.DuplicatePreventionStatus
import com.example.repository.DuplicatePreventionRepository
import com.example.repository.HistoryRepository
import kotlinx.coroutines.flow.firstOrNull

/**
 * Concrete implementation of DuplicatePreventionRepository.
 *
 * Reads history and enforces:
 * - Per-conversation duplicate message protection.
 * - Per-rule cooldown limits.
 * - Configurable cooldown periods.
 */
class DuplicatePreventionRepositoryImpl(
    private val historyRepository: HistoryRepository? = null
) : DuplicatePreventionRepository {

    override suspend fun getHistoryForConversation(conversationId: String): List<ReplyHistory> {
        if (historyRepository == null) return emptyList()
        val allHistory = historyRepository.getAllHistory().firstOrNull() ?: emptyList()
        return allHistory.filter {
            it.senderName.equals(conversationId.trim(), ignoreCase = true) ||
                    it.packageName.equals(conversationId.trim(), ignoreCase = true)
        }
    }

    override suspend fun evaluateDuplicateRisk(criteria: DuplicateCheckCriteria): DuplicatePreventionResult {
        val conversationId = criteria.conversationId.trim()
        val replyText = criteria.replyText.trim()
        val now = criteria.currentTimeMillis

        if (historyRepository == null) {
            return DuplicatePreventionResult(
                status = DuplicatePreventionStatus.ALLOW,
                isAllowed = true,
                reason = "Reply Allowed: No history database configured, duplicate check bypassed.",
                conversationId = conversationId,
                replyText = replyText,
                ruleId = criteria.ruleId,
                matchedHistoryItem = null,
                lastReplyTimestamp = null,
                remainingCooldownMs = 0L,
                details = "ALLOW: No persistent history repository."
            )
        }

        val allHistory = historyRepository.getAllHistory().firstOrNull() ?: emptyList()
        val conversationHistory = allHistory.filter {
            it.senderName.equals(conversationId, ignoreCase = true)
        }

        val effectiveCooldownMs = criteria.perConversationCooldownMs
            ?: criteria.perRuleCooldownMs
            ?: criteria.cooldownPeriodMs

        // 1. Check for exact duplicate reply in same conversation within cooldown window
        val duplicateMatch = conversationHistory.firstOrNull { hist ->
            hist.repliedMessage.trim().equals(replyText, ignoreCase = true) &&
                    (now - hist.timestamp) <= effectiveCooldownMs
        }

        if (duplicateMatch != null) {
            val elapsedMs = now - duplicateMatch.timestamp
            val remainingMs = (effectiveCooldownMs - elapsedMs).coerceAtLeast(0L)
            return DuplicatePreventionResult(
                status = DuplicatePreventionStatus.BLOCK,
                isAllowed = false,
                reason = "Duplicate Reply: Same reply text was already sent to conversation '$conversationId' ${elapsedMs / 1000}s ago.",
                conversationId = conversationId,
                replyText = replyText,
                ruleId = criteria.ruleId,
                matchedHistoryItem = duplicateMatch,
                lastReplyTimestamp = duplicateMatch.timestamp,
                remainingCooldownMs = remainingMs,
                details = "BLOCK: Duplicate reply detected within cooldown window (${remainingMs / 1000}s remaining)."
            )
        }

        // 2. Check per-rule cooldown for this conversation or globally
        if (criteria.ruleId != null) {
            val ruleCooldown = criteria.perRuleCooldownMs ?: criteria.cooldownPeriodMs
            val lastRuleHistory = allHistory.firstOrNull { hist ->
                hist.ruleId == criteria.ruleId &&
                        hist.senderName.equals(conversationId, ignoreCase = true) &&
                        (now - hist.timestamp) <= ruleCooldown
            }

            if (lastRuleHistory != null) {
                val elapsedMs = now - lastRuleHistory.timestamp
                val remainingMs = (ruleCooldown - elapsedMs).coerceAtLeast(0L)
                return DuplicatePreventionResult(
                    status = DuplicatePreventionStatus.BLOCK,
                    isAllowed = false,
                    reason = "Cooldown Active: Rule ID ${criteria.ruleId} is on per-conversation cooldown for '$conversationId'.",
                    conversationId = conversationId,
                    replyText = replyText,
                    ruleId = criteria.ruleId,
                    matchedHistoryItem = lastRuleHistory,
                    lastReplyTimestamp = lastRuleHistory.timestamp,
                    remainingCooldownMs = remainingMs,
                    details = "BLOCK: Rule cooldown active (${remainingMs / 1000}s remaining)."
                )
            }
        }

        // 3. No duplicate found and no active cooldown -> ALLOW
        val mostRecentHistory = conversationHistory.maxByOrNull { it.timestamp }
        return DuplicatePreventionResult(
            status = DuplicatePreventionStatus.ALLOW,
            isAllowed = true,
            reason = "Reply Allowed: No recent duplicate reply found and no active cooldown for conversation '$conversationId'.",
            conversationId = conversationId,
            replyText = replyText,
            ruleId = criteria.ruleId,
            matchedHistoryItem = mostRecentHistory,
            lastReplyTimestamp = mostRecentHistory?.timestamp,
            remainingCooldownMs = 0L,
            details = "ALLOW: Verified safe to transmit."
        )
    }
}
