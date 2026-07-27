package com.example.accessibility.input

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Result model returned by Message Input Finder detailing node attributes and verification state.
 */
data class MessageInputVerificationResult(
    val status: MessageInputStatus,
    val isVerified: Boolean,
    val inputNode: AccessibilityNodeInfo? = null,
    val bounds: Rect = Rect(),
    val nodeId: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val isEditable: Boolean = false,
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false,
    val isFocusable: Boolean = false,
    val belongsToImoChat: Boolean = false,
    val candidateNodesCount: Int = 0,
    val wrongInputReasons: List<String> = emptyList(),
    val reason: String = "",
    val details: String = ""
)
