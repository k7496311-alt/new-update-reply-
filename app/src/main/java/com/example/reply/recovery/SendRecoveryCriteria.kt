package com.example.reply.recovery

import android.view.accessibility.AccessibilityNodeInfo
import com.example.model.QueueItem

/**
 * Contextual criteria for executing single retry recovery on failed sending operations.
 */
data class SendRecoveryCriteria(
    val conversationId: String,
    val packageName: String = "com.imo.android.imoim",
    val queueItem: QueueItem? = null,
    val replyText: String,
    val rootNode: AccessibilityNodeInfo? = null,
    val executeRetryAction: (suspend () -> Boolean)? = null
)
