package com.example.repository

import com.example.model.ReplyHistory
import com.example.reply.duplicate.DuplicateCheckCriteria
import com.example.reply.duplicate.DuplicatePreventionResult

/**
 * Clean Architecture repository interface for Duplicate Reply Prevention.
 */
interface DuplicatePreventionRepository {
    /**
     * Evaluates history to check if the same conversation received the same reply recently,
     * or if per-rule/per-conversation cooldowns are active.
     */
    suspend fun evaluateDuplicateRisk(
        criteria: DuplicateCheckCriteria
    ): DuplicatePreventionResult

    /**
     * Retrieves recent reply history records for a specific conversation.
     */
    suspend fun getHistoryForConversation(
        conversationId: String
    ): List<ReplyHistory>
}
