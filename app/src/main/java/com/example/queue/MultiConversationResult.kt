package com.example.queue

import com.example.model.QueueItem

/**
 * Result model returned by Multi-Conversation Processing engine.
 */
data class MultiConversationResult(
    val totalProcessedCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val mergedNotificationCount: Int,
    val isQueueEmpty: Boolean,
    val processedItems: List<QueueItem>,
    val details: String = ""
)
