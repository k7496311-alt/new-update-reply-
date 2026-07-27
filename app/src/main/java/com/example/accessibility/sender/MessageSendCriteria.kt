package com.example.accessibility.sender

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Contextual criteria required to perform and verify a message send action via Accessibility.
 */
data class MessageSendCriteria(
    val sendButtonNode: AccessibilityNodeInfo? = null,
    val composerNode: AccessibilityNodeInfo? = null,
    val rootNode: AccessibilityNodeInfo? = null,
    val sentText: String,
    val postClickWaitMs: Long = 600L
)
