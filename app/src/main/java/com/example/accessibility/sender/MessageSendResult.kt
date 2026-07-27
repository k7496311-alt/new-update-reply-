package com.example.accessibility.sender

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Result model returned after performing message send execution.
 */
data class MessageSendResult(
    val status: MessageSendStatus,
    val isSuccess: Boolean,
    val clickPerformed: Boolean,
    val composerCleared: Boolean,
    val outgoingBubbleFound: Boolean,
    val sentText: String,
    val reason: String = "",
    val details: String = ""
)
