package com.example.repository

import com.example.accessibility.sender.MessageSendCriteria
import com.example.accessibility.sender.MessageSendResult

/**
 * Clean Architecture repository interface for performing a single message send action.
 */
interface MessageSendRepository {
    /**
     * Clicks Send Button ONCE, waits for UI update, and confirms composer clearing OR outgoing bubble detection.
     */
    suspend fun performSend(
        criteria: MessageSendCriteria
    ): MessageSendResult
}
