package com.example.reply.postverify

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Contextual criteria required to verify that a sent reply actually appeared in the conversation.
 */
data class PostVerifyCriteria(
    val conversationId: String,
    val expectedReplyText: String,
    val rootNode: AccessibilityNodeInfo? = null,
    val queueItemId: Long? = null,
    val packageName: String = "com.imo.android.imoim",
    val checkTimeoutMs: Long = 1000L
)
