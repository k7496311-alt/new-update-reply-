package com.example.data

import android.content.Context
import com.example.accessibility.AccessibilityManager
import com.example.accessibility.NodeScanner
import com.example.accessibility.imo.ChatReadyResult
import com.example.accessibility.imo.ChatReadyStatus
import com.example.accessibility.imo.IMONodeScanner
import com.example.repository.ChatReadyRepository
import com.example.repository.ScreenReadyState
import kotlinx.coroutines.delay

/**
 * Concrete implementation of ChatReadyRepository using Accessibility node scanning.
 */
class ChatReadyRepositoryImpl(
    private val context: Context,
    private val accessibilityManager: AccessibilityManager = AccessibilityManager(context),
    private val nodeScanner: IMONodeScanner = IMONodeScanner()
) : ChatReadyRepository {

    override suspend fun checkScreenReadyState(): ScreenReadyState {
        val root = accessibilityManager.getRootNode() ?: return ScreenReadyState(
            messageListExists = false,
            inputBoxExists = false,
            sendButtonExists = false,
            totalNodeCount = 0,
            visibleNodeCount = 0,
            isAllReady = false
        )

        try {
            val scannedNodes = NodeScanner.scanTree(root)
            val totalNodes = scannedNodes.size
            val visibleNodes = scannedNodes.count { it.isVisibleToUser }

            val messageListNode = nodeScanner.findMessageListContainer(root)
            val messageListExists = messageListNode != null
            messageListNode?.recycle()

            val inputNode = nodeScanner.findInputField(root)
            val inputBoxExists = inputNode != null
            inputNode?.recycle()

            val sendNode = nodeScanner.findSendButton(root) ?: nodeScanner.findMicButton(root)
            val sendButtonExists = sendNode != null
            sendNode?.recycle()

            val isAllReady = messageListExists && inputBoxExists && sendButtonExists

            return ScreenReadyState(
                messageListExists = messageListExists,
                inputBoxExists = inputBoxExists,
                sendButtonExists = sendButtonExists,
                totalNodeCount = totalNodes,
                visibleNodeCount = visibleNodes,
                isAllReady = isAllReady
            )
        } finally {
            root.recycle()
        }
    }

    override suspend fun waitForChatReady(timeoutMs: Long, pollIntervalMs: Long): ChatReadyResult {
        val startTime = System.currentTimeMillis()
        var lastState = checkScreenReadyState()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (lastState.isAllReady) {
                return ChatReadyResult(
                    status = ChatReadyStatus.CHAT_READY,
                    messageListExists = lastState.messageListExists,
                    inputBoxExists = lastState.inputBoxExists,
                    sendButtonExists = lastState.sendButtonExists,
                    nodeCount = lastState.totalNodeCount,
                    visibleNodes = lastState.visibleNodeCount,
                    elapsedTimeMs = System.currentTimeMillis() - startTime,
                    details = "IMO Chat screen verified fully loaded"
                )
            }
            delay(pollIntervalMs)
            lastState = checkScreenReadyState()
        }

        return ChatReadyResult(
            status = ChatReadyStatus.CHAT_TIMEOUT,
            messageListExists = lastState.messageListExists,
            inputBoxExists = lastState.inputBoxExists,
            sendButtonExists = lastState.sendButtonExists,
            nodeCount = lastState.totalNodeCount,
            visibleNodes = lastState.visibleNodeCount,
            elapsedTimeMs = System.currentTimeMillis() - startTime,
            details = "Timeout reached waiting for chat screen elements"
        )
    }
}
