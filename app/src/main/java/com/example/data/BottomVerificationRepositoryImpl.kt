package com.example.data

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.example.accessibility.AccessibilityActionHelper
import com.example.accessibility.AccessibilityManager
import com.example.accessibility.imo.IMONodeScanner
import com.example.repository.BottomCheckState
import com.example.repository.BottomVerificationRepository

/**
 * Concrete implementation of BottomVerificationRepository using Accessibility APIs.
 */
class BottomVerificationRepositoryImpl(
    private val context: Context,
    private val accessibilityManager: AccessibilityManager = AccessibilityManager(context),
    private val nodeScanner: IMONodeScanner = IMONodeScanner()
) : BottomVerificationRepository {

    override suspend fun checkBottomState(): BottomCheckState {
        val root = accessibilityManager.getRootNode() ?: return BottomCheckState(
            isAtBottom = false,
            visibleMessageCount = 0,
            currentPosition = "Position Unknown (Root Node Null)",
            canScrollForward = false,
            hasJumpButton = false
        )

        try {
            // 1. Check if floating "Jump To Latest" button exists
            val jumpButton = nodeScanner.findJumpToLatestButton(root)
            val hasJumpButton = jumpButton != null
            jumpButton?.recycle()

            // 2. Find chat list container
            val listContainer = nodeScanner.findMessageListContainer(root)
            val visibleMessageCount = listContainer?.childCount ?: 0

            var canScrollForward = false
            if (listContainer != null) {
                val actions = listContainer.actionList
                canScrollForward = actions.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD } || listContainer.isScrollable
            }

            // 3. Bottom position condition: No Jump button present AND list cannot scroll forward further
            // (or if list is small/fits entirely on screen)
            val isAtBottom = !hasJumpButton && (!canScrollForward || visibleMessageCount == 0)

            val currentPosString = if (visibleMessageCount > 0) {
                "Visible Item Range: 0 to ${visibleMessageCount - 1} | At Bottom: $isAtBottom"
            } else {
                "Empty/Initial Chat List | At Bottom: $isAtBottom"
            }

            listContainer?.recycle()

            return BottomCheckState(
                isAtBottom = isAtBottom,
                visibleMessageCount = visibleMessageCount,
                currentPosition = currentPosString,
                canScrollForward = canScrollForward,
                hasJumpButton = hasJumpButton
            )
        } finally {
            root.recycle()
        }
    }

    override suspend fun performControlledScrollDown(): Boolean {
        val root = accessibilityManager.getRootNode() ?: return false
        try {
            val scrollableNode = nodeScanner.findScrollableContainer(root) ?: return false
            val scrolled = AccessibilityActionHelper.safeScrollForward(scrollableNode)
            scrollableNode.recycle()
            return scrolled
        } finally {
            root.recycle()
        }
    }
}
