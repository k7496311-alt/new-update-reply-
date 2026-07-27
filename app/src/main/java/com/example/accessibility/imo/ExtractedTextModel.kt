package com.example.accessibility.imo

/**
 * Model representing a single readable extracted chat text message with preserved sequence order.
 */
data class ExtractedTextModel(
    val sequenceIndex: Int,
    val nodeIndex: Int,
    val text: String,
    val isIncoming: Boolean?,
    val scriptType: String,
    val rawBubble: MessageBubbleModel
)
