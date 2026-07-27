package com.example.repository

import com.example.reply.validation.ReplyValidationCriteria
import com.example.reply.validation.ReplyValidationResult

/**
 * Clean Architecture repository interface for validating generated replies before transmission.
 */
interface ReplyValidationRepository {
    /**
     * Validates a generated reply against required safety checks:
     * - Null / Empty / Whitespace
     * - Oversized character count (> 2000)
     * - Corrupted Unicode (\uFFFD, unassigned control chars, malformed surrogates)
     * - Invalid unexpanded variables ({variable}, [variable])
     * - Duplicate replies sent recently
     * - Spam rate limits & cooldown enforcement
     */
    suspend fun validateReply(
        replyText: String?,
        criteria: ReplyValidationCriteria = ReplyValidationCriteria()
    ): ReplyValidationResult
}
