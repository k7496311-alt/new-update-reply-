package com.example.repository

import com.example.accessibility.input.inserter.MessageInputInsertCriteria
import com.example.accessibility.input.inserter.MessageInputInsertResult

/**
 * Clean Architecture repository interface for inserting text into message input fields.
 */
interface MessageInputInserterRepository {
    /**
     * Inserts reply text into the target accessibility input node.
     * Uses ACTION_SET_TEXT primary strategy and fallback strategies if unsupported.
     * Verifies inserted text after insertion, and retries once upon mismatch after clearing.
     */
    suspend fun insertReplyText(
        criteria: MessageInputInsertCriteria
    ): MessageInputInsertResult
}
