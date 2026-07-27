package com.example.accessibility.imo

/**
 * Structured model containing the constructed Conversation Context and related analytics.
 */
data class ConversationContextModel(
    val conversationContext: String,
    val lastMessage: String,
    val oldestIncludedText: String,
    val totalMessages: Int,
    val characterCount: Int,
    val messageOrder: List<ExtractedTextModel>,
    val details: String = ""
)
