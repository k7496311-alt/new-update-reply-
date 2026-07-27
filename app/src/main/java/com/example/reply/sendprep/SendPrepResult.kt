package com.example.reply.sendprep

import com.example.model.QueueItem

/**
 * Detailed result returned by Send Preparation Engine.
 */
data class SendPrepResult(
    val status: SendPrepStatus,
    val isReady: Boolean,
    val reason: String,
    val conversationVerified: Boolean,
    val sameCustomerVerified: Boolean,
    val inputBoxVerified: Boolean,
    val accessibilityAlive: Boolean,
    val replyValid: Boolean,
    val duplicateCheckPassed: Boolean,
    val queueItemActive: Boolean,
    val conversationId: String,
    val expectedCustomerName: String,
    val replyText: String,
    val queueItem: QueueItem? = null,
    val failedChecks: List<String> = emptyList()
)
