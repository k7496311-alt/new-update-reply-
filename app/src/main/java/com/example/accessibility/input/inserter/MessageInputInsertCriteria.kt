package com.example.accessibility.input.inserter

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Criteria for inserting a validated reply text into an Accessibility input node.
 */
data class MessageInputInsertCriteria(
    val replyText: String,
    val targetNode: AccessibilityNodeInfo? = null,
    val maxRetries: Int = 1,
    val delayBeforeVerifyMs: Long = 100L
)
