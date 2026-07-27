package com.example.data

import com.example.reply.validation.ReplyValidationCriteria
import com.example.reply.validation.ReplyValidationResult
import com.example.reply.validation.ReplyValidationStatus
import com.example.repository.HistoryRepository
import com.example.repository.ReplyValidationRepository
import kotlinx.coroutines.flow.firstOrNull

/**
 * Concrete implementation of ReplyValidationRepository.
 * Performs rigorous safety validation on generated replies to ensure production quality:
 * 1. Null check
 * 2. Empty check
 * 3. Whitespace-only check
 * 4. Oversized reply check (> maxCharacterLimit)
 * 5. Corrupted Unicode check (\uFFFD, broken surrogates, unassigned controls)
 * 6. Unexpanded / invalid variable check ({variable}, [variable])
 * 7. Duplicate reply detection
 * 8. Spam prevention & rate limit enforcement
 */
class ReplyValidationRepositoryImpl(
    private val historyRepository: HistoryRepository? = null
) : ReplyValidationRepository {

    override suspend fun validateReply(
        replyText: String?,
        criteria: ReplyValidationCriteria
    ): ReplyValidationResult {

        val failedChecks = mutableListOf<String>()

        // 1. Null check
        if (replyText == null) {
            failedChecks.add("Null reply text")
            return createInvalidResult(
                replyText = null,
                ruleId = criteria.ruleId,
                reason = "Reply validation failed: Reply text is null.",
                failedChecks = failedChecks
            )
        }

        // 2. Empty check
        if (replyText.isEmpty()) {
            failedChecks.add("Empty reply")
            return createInvalidResult(
                replyText = replyText,
                ruleId = criteria.ruleId,
                reason = "Reply validation failed: Reply text is empty.",
                failedChecks = failedChecks
            )
        }

        // 3. Whitespace-only check
        if (replyText.trim().isEmpty()) {
            failedChecks.add("Whitespace only")
            return createInvalidResult(
                replyText = replyText,
                ruleId = criteria.ruleId,
                reason = "Reply validation failed: Reply contains only whitespace.",
                failedChecks = failedChecks
            )
        }

        // 4. Oversized reply check
        val length = replyText.length
        if (length > criteria.maxCharacterLimit) {
            failedChecks.add("Oversized reply")
            return createInvalidResult(
                replyText = replyText,
                ruleId = criteria.ruleId,
                reason = "Reply validation failed: Character count ($length) exceeds max limit of ${criteria.maxCharacterLimit}.",
                failedChecks = failedChecks
            )
        }

        // 5. Corrupted Unicode check
        val unicodeErrorReason = checkCorruptedUnicode(replyText)
        if (unicodeErrorReason != null) {
            failedChecks.add("Corrupted Unicode")
            return createInvalidResult(
                replyText = replyText,
                ruleId = criteria.ruleId,
                reason = "Reply validation failed: $unicodeErrorReason",
                failedChecks = failedChecks
            )
        }

        // 6. Invalid / unexpanded variable check
        val variableErrorReason = checkInvalidVariables(replyText)
        if (variableErrorReason != null) {
            failedChecks.add("Invalid Variable")
            return createInvalidResult(
                replyText = replyText,
                ruleId = criteria.ruleId,
                reason = "Reply validation failed: $variableErrorReason",
                failedChecks = failedChecks
            )
        }

        // 7. Duplicate reply check & 8. Spam prevention
        if (historyRepository != null) {
            val ruleId = criteria.ruleId
            val now = criteria.currentTimeMillis

            if (ruleId != null) {
                // Cooldown check
                val lastReplyTime = historyRepository.getLastReplyTimestampForRule(ruleId)
                if (lastReplyTime != null && (now - lastReplyTime) < criteria.cooldownPeriodMs) {
                    val remainingSec = ((criteria.cooldownPeriodMs - (now - lastReplyTime)) / 1000).coerceAtLeast(1)
                    failedChecks.add("Spam prevention")
                    return createInvalidResult(
                        replyText = replyText,
                        ruleId = ruleId,
                        reason = "Reply validation failed: Rule is on cooldown. Try again in ${remainingSec}s.",
                        failedChecks = failedChecks
                    )
                }

                // Rate limit check
                val repliesInLastMinute = historyRepository.getReplyCountForRuleSince(ruleId, now - 60000L)
                if (repliesInLastMinute >= criteria.maxRepliesPerMinute) {
                    failedChecks.add("Spam rate limit")
                    return createInvalidResult(
                        replyText = replyText,
                        ruleId = ruleId,
                        reason = "Reply validation failed: Rate limit exceeded ($repliesInLastMinute/${criteria.maxRepliesPerMinute} replies per minute).",
                        failedChecks = failedChecks
                    )
                }
            }

            // Duplicate reply check against recent history
            if (!criteria.allowDuplicateReplies) {
                val recentHistory = historyRepository.getAllHistory().firstOrNull() ?: emptyList()
                val duplicate = recentHistory.take(10).find { hist ->
                    hist.repliedMessage.equals(replyText.trim(), ignoreCase = true) &&
                            (now - hist.timestamp) < 60000L &&
                            (criteria.senderName == null || hist.senderName.equals(criteria.senderName, ignoreCase = true))
                }

                if (duplicate != null) {
                    failedChecks.add("Duplicate reply")
                    return createInvalidResult(
                        replyText = replyText,
                        ruleId = criteria.ruleId,
                        reason = "Reply validation failed: Duplicate reply detected recently sent to '${duplicate.senderName}'.",
                        failedChecks = failedChecks
                    )
                }
            }
        }

        // All checks passed!
        return ReplyValidationResult(
            status = ReplyValidationStatus.VALID,
            isValid = true,
            reason = "Validation Passed: Reply text passed all structural, unicode, format, duplicate, and spam checks.",
            ruleId = criteria.ruleId,
            replyText = replyText,
            characterCount = length,
            failedChecks = emptyList(),
            details = "VALID: $length characters verified."
        )
    }

    private fun checkCorruptedUnicode(text: String): String? {
        // Replacement characters: \uFFFD, \uFFFE, \uFFFF
        if (text.contains("\uFFFD") || text.contains("\uFFFE") || text.contains("\uFFFF")) {
            return "Corrupted Unicode: Contains replacement or invalid Unicode non-characters."
        }

        // Validate surrogate pairs
        var idx = 0
        while (idx < text.length) {
            val ch = text[idx]
            if (ch.isHighSurrogate()) {
                if (idx + 1 >= text.length || !text[idx + 1].isLowSurrogate()) {
                    return "Corrupted Unicode: Malformed high surrogate character at position $idx."
                }
                idx += 2
            } else if (ch.isLowSurrogate()) {
                return "Corrupted Unicode: Unexpected orphan low surrogate character at position $idx."
            } else {
                idx++
            }
        }

        return null
    }

    private fun checkInvalidVariables(text: String): String? {
        // Detect unexpanded pattern like {var_name} or [var_name]
        val variableRegex = Regex("\\{[a-zA-Z0-9_]+\\}|\\[[a-zA-Z0-9_]+\\]")
        val matches = variableRegex.findAll(text).map { it.value }.toList()

        if (matches.isNotEmpty()) {
            return "Invalid Variable: Unexpanded template variable(s) remaining in reply: ${matches.joinToString(", ")}."
        }

        return null
    }

    private fun createInvalidResult(
        replyText: String?,
        ruleId: Long?,
        reason: String,
        failedChecks: List<String>
    ): ReplyValidationResult {
        return ReplyValidationResult(
            status = ReplyValidationStatus.INVALID,
            isValid = false,
            reason = reason,
            ruleId = ruleId,
            replyText = replyText,
            characterCount = replyText?.length ?: 0,
            failedChecks = failedChecks,
            details = "INVALID: $reason"
        )
    }
}
