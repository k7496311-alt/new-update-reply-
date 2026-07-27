package com.example.repository

import com.example.accessibility.imo.ConversationContextModel
import com.example.accessibility.imo.ExtractedTextModel

/**
 * Clean Architecture repository interface for constructing unified Conversation Context.
 */
interface ConversationContextRepository {
    /**
     * Combines sequence-ordered readable text messages into a unified conversation context.
     */
    suspend fun buildConversationContext(
        extractedMessages: List<ExtractedTextModel>,
        maxMessagesToInclude: Int = 20
    ): ConversationContextModel
}
