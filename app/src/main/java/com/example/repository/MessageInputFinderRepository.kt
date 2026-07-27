package com.example.repository

import android.view.accessibility.AccessibilityNodeInfo
import com.example.accessibility.input.MessageInputFinderCriteria
import com.example.accessibility.input.MessageInputVerificationResult

/**
 * Clean Architecture repository interface for locating and verifying the message input field.
 */
interface MessageInputFinderRepository {
    /**
     * Traverses the Accessibility tree from rootNode, locates candidate editable fields,
     * filters out search boxes / non-composer inputs, verifies attributes (Editable, Enabled, Visible, Focusable, IMO Chat Screen),
     * and selects ONLY the message composer field.
     */
    suspend fun findAndVerifyMessageInput(
        rootNode: AccessibilityNodeInfo?,
        criteria: MessageInputFinderCriteria = MessageInputFinderCriteria()
    ): MessageInputVerificationResult
}
