package com.example.data

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.example.accessibility.AccessibilityActionHelper
import com.example.accessibility.AccessibilityManager
import com.example.accessibility.imo.IMONodeScanner
import com.example.repository.JumpToLatestRepository
import kotlinx.coroutines.delay

/**
 * Concrete implementation of JumpToLatestRepository using Accessibility APIs.
 */
class JumpToLatestRepositoryImpl(
    private val context: Context,
    private val accessibilityManager: AccessibilityManager = AccessibilityManager(context),
    private val nodeScanner: IMONodeScanner = IMONodeScanner()
) : JumpToLatestRepository {

    override suspend fun findJumpToLatestButton(): AccessibilityNodeInfo? {
        val root = accessibilityManager.getRootNode() ?: return null
        return try {
            nodeScanner.findJumpToLatestButton(root)
        } finally {
            root.recycle()
        }
    }

    override suspend fun clickJumpButton(node: AccessibilityNodeInfo): Boolean {
        return AccessibilityActionHelper.safeClick(node)
    }

    override suspend fun verifyJumpCompleted(timeoutMs: Long, pollIntervalMs: Long): Boolean {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val currentButton = findJumpToLatestButton()
            if (currentButton == null) {
                // Button disappeared, latest chat position reached
                return true
            } else {
                currentButton.recycle()
            }
            delay(pollIntervalMs)
        }

        // Final check after timeout
        val finalButton = findJumpToLatestButton()
        val isDisappeared = finalButton == null
        finalButton?.recycle()
        return isDisappeared
    }
}
