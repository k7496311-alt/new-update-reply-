package com.example.repository

import android.view.accessibility.AccessibilityNodeInfo
import com.example.accessibility.sendbutton.SendButtonDetectorCriteria
import com.example.accessibility.sendbutton.SendButtonVerificationResult

/**
 * Clean Architecture repository interface for locating and verifying the send button.
 */
interface SendButtonDetectorRepository {
    /**
     * Traverses the Accessibility tree from rootNode, locates candidate send button nodes,
     * verifies attributes (Clickable, Visible, Enabled, Beside Message Composer),
     * and selects the valid Send Button node.
     */
    suspend fun findAndVerifySendButton(
        rootNode: AccessibilityNodeInfo?,
        criteria: SendButtonDetectorCriteria = SendButtonDetectorCriteria()
    ): SendButtonVerificationResult
}
