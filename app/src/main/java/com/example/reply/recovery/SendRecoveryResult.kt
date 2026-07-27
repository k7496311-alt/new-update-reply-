package com.example.reply.recovery

import com.example.model.QueueItem

/**
 * Result model returned by Send Recovery Engine.
 */
data class SendRecoveryResult(
    val status: SendRecoveryStatus,
    val isSuccess: Boolean,
    val retryAttempted: Boolean,
    val isDuplicateBlocked: Boolean = false,
    val movedToFailedQueue: Boolean = false,
    val workerContinued: Boolean = true,
    val conversationId: String,
    val replyText: String,
    val updatedQueueItem: QueueItem? = null,
    val reason: String = "",
    val details: String = ""
)
