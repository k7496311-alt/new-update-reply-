package com.example.repository

import com.example.model.AutoReplyRule
import com.example.reply.FinalReplyResult

/**
 * Clean Architecture repository interface for Final Reply Generation.
 */
interface FinalReplyRepository {
    /**
     * Generates the final reply strictly from the selected rule's stored reply text,
     * expanding variables ({customer_name}, {date}, {time}) without altering any other text.
     */
    suspend fun generateFinalReply(
        selectedRule: AutoReplyRule,
        customerName: String? = null,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): FinalReplyResult
}
