package com.example.accessibility.sendbutton

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Result model returned by Send Button Detector detailing node attributes and verification state.
 */
data class SendButtonVerificationResult(
    val status: SendButtonStatus,
    val isVerified: Boolean,
    val sendButtonNode: AccessibilityNodeInfo? = null,
    val bounds: Rect = Rect(),
    val nodeId: String? = null,
    val className: String? = null,
    val contentDescription: String? = null,
    val isClickable: Boolean = false,
    val isVisible: Boolean = false,
    val isEnabled: Boolean = false,
    val isBesideComposer: Boolean = false,
    val candidateCount: Int = 0,
    val reason: String = "",
    val details: String = ""
)
