package com.example.data

import com.example.accessibility.imo.ConversationContextModel
import com.example.accessibility.imo.ExtractedTextModel
import com.example.repository.ConversationContextRepository

/**
 * Concrete implementation of ConversationContextRepository.
 * Combines chronological readable text messages into a unified conversation context string.
 */
class ConversationContextRepositoryImpl : ConversationContextRepository {

    override suspend fun buildConversationContext(
        extractedMessages: List<ExtractedTextModel>,
        maxMessagesToInclude: Int
    ): ConversationContextModel {
        if (extractedMessages.isEmpty()) {
            return ConversationContextModel(
                conversationContext = "",
                lastMessage = "",
                oldestIncludedText = "",
                totalMessages = 0,
                characterCount = 0,
                messageOrder = emptyList(),
                details = "No extracted text messages available to build conversation context"
            )
        }

        // Take the latest maxMessagesToInclude while preserving chronological order
        val includedMessages = if (extractedMessages.size > maxMessagesToInclude) {
            extractedMessages.takeLast(maxMessagesToInclude)
        } else {
            extractedMessages
        }

        // Combine messages into ONE unified conversation string
        val contextBuilder = StringBuilder()
        includedMessages.forEachIndexed { index, msg ->
            if (index > 0) {
                contextBuilder.append("\n")
            }
            contextBuilder.append(msg.text)
        }

        val combinedContext = contextBuilder.toString()
        val totalMsgs = includedMessages.size
        val charCount = combinedContext.length
        val lastMsg = includedMessages.last().text
        val oldestMsg = includedMessages.first().text

        return ConversationContextModel(
            conversationContext = combinedContext,
            lastMessage = lastMsg,
            oldestIncludedText = oldestMsg,
            totalMessages = totalMsgs,
            characterCount = charCount,
            messageOrder = includedMessages,
            details = "Successfully combined $totalMsgs message(s) into conversation context ($charCount characters)"
        )
    }
}
