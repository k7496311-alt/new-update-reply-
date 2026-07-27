package com.example.accessibility.imo

/**
 * Result metrics for Chat Ready Detection.
 */
data class ChatReadyResult(
    val status: ChatReadyStatus,
    val messageListExists: Boolean,
    val inputBoxExists: Boolean,
    val sendButtonExists: Boolean,
    val nodeCount: Int,
    val visibleNodes: Int,
    val elapsedTimeMs: Long,
    val details: String = ""
)
