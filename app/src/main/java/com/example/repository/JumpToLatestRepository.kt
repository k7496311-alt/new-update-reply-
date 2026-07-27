package com.example.repository

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Clean Architecture repository interface for searching, clicking, and verifying
 * floating "Jump To Latest" button in IMO chat window.
 */
interface JumpToLatestRepository {
    /**
     * Searches active accessibility tree for floating "Jump to Latest" button.
     */
    suspend fun findJumpToLatestButton(): AccessibilityNodeInfo?

    /**
     * Performs click action on the provided jump button node or its clickable parent.
     */
    suspend fun clickJumpButton(node: AccessibilityNodeInfo): Boolean

    /**
     * Verifies that the jump button has disappeared and latest chat position is reached.
     */
    suspend fun verifyJumpCompleted(timeoutMs: Long = 2000L, pollIntervalMs: Long = 200L): Boolean
}
