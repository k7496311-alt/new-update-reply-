package com.example.model

/**
 * Domain model representing an item in the auto-reply dispatch queue.
 */
data class QueueItem(
    val id: Long = 0L,
    val ruleId: Long,
    val senderName: String,
    val incomingMessage: String,
    val replyText: String,
    val packageName: String,
    val scheduledTime: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: QueueStatus = QueueStatus.PENDING,
    val priority: Int = 0,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val errorMessage: String? = null
)

enum class QueueStatus {
    INCOMING,   // Newly received notifications prior to scheduling
    PENDING,    // Waiting queue
    PROCESSING, // Running queue
    SENT,       // Completed queue
    FAILED,     // Failed queue
    RETRY,      // Retry queue
    CANCELLED,  // Cancelled queue
    COOLDOWN,   // Cooldown queue
    SKIPPED,    // Skip queue
    EXPIRED     // Expired queue
}

