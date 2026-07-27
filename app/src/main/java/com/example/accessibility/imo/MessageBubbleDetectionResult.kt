package com.example.accessibility.imo

/**
 * Detection result containing classified message bubbles in chronological order and summary counts.
 */
data class MessageBubbleDetectionResult(
    val bubbles: List<MessageBubbleModel>,
    val bubbleCount: Int,
    val incomingCount: Int,
    val outgoingCount: Int,
    val stickerCount: Int,
    val missedCallCount: Int,
    val unknownCount: Int
)
