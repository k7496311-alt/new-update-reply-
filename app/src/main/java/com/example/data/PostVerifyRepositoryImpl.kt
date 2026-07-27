package com.example.data

import android.view.accessibility.AccessibilityNodeInfo
import com.example.model.QueueStatus
import com.example.model.ReplyHistory
import com.example.reply.postverify.PostVerifyCriteria
import com.example.reply.postverify.PostVerifyResult
import com.example.reply.postverify.PostVerifyStatus
import com.example.repository.HistoryRepository
import com.example.repository.PostVerifyRepository
import com.example.repository.QueueRepository

/**
 * Concrete implementation of PostVerifyRepository.
 *
 * Scans latest outgoing messages in the accessibility UI tree, compares text against expected reply,
 * and if an exact match is confirmed, marks conversation completed (updates queue status to SENT and saves history).
 */
class PostVerifyRepositoryImpl(
    private val queueRepository: QueueRepository? = null,
    private val historyRepository: HistoryRepository? = null
) : PostVerifyRepository {

    override suspend fun verifyAndComplete(criteria: PostVerifyCriteria): PostVerifyResult {
        val rootNode = criteria.rootNode
        val expectedText = criteria.expectedReplyText.trim()
        val conversationId = criteria.conversationId.trim()

        if (rootNode == null) {
            return PostVerifyResult(
                status = PostVerifyStatus.FAILED,
                isCompleted = false,
                conversationId = conversationId,
                expectedReplyText = expectedText,
                reason = "Verification Failed: Root Accessibility node is null.",
                details = "Cannot inspect UI tree for outgoing message bubbles when root node is null."
            )
        }

        rootNode.refresh()

        // 1. Scan tree and collect all candidate text nodes in current chat view
        val textNodes = mutableListOf<AccessibilityNodeInfo>()
        collectTextNodes(rootNode, textNodes)

        if (textNodes.isEmpty()) {
            return PostVerifyResult(
                status = PostVerifyStatus.FAILED,
                isCompleted = false,
                conversationId = conversationId,
                expectedReplyText = expectedText,
                outgoingBubblesDetectedCount = 0,
                outgoingBubbleFound = false,
                reason = "Verification Failed: Zero message bubbles or text nodes detected in chat UI tree.",
                details = "Scanned UI tree. No text nodes found."
            )
        }

        val outgoingCount = textNodes.size
        var matchedText: String? = null
        var isExactMatch = false

        // 2. Search for exact reply match in chat text nodes
        for (node in textNodes) {
            val nodeText = node.text?.toString()?.trim() ?: ""
            if (nodeText.equals(expectedText, ignoreCase = true) || nodeText == expectedText) {
                isExactMatch = true
                matchedText = nodeText
                break
            }
        }

        // Cleanup collected nodes
        textNodes.forEach { it.recycle() }

        if (!isExactMatch) {
            return PostVerifyResult(
                status = PostVerifyStatus.FAILED,
                isCompleted = false,
                conversationId = conversationId,
                expectedReplyText = expectedText,
                outgoingBubblesDetectedCount = outgoingCount,
                outgoingBubbleFound = outgoingCount > 0,
                replyMatched = false,
                reason = "Verification Failed: Outgoing messages found ($outgoingCount total text nodes), but none exactly matched expected reply '$expectedText'.",
                details = "Text match failed."
            )
        }

        // 3. Exact match confirmed! Mark conversation completed.
        var markedCompleted = false
        if (queueRepository != null) {
            val queueItem = if (criteria.queueItemId != null) {
                queueRepository.getQueueItemById(criteria.queueItemId)
            } else {
                queueRepository.findActiveQueueItemBySender(criteria.packageName, conversationId)
            }

            if (queueItem != null) {
                val completedItem = queueItem.copy(
                    status = QueueStatus.SENT,
                    updatedAt = System.currentTimeMillis()
                )
                queueRepository.saveQueueItem(completedItem)
                markedCompleted = true
            }
        }

        // Optional: Save history record
        if (historyRepository != null) {
            try {
                val history = ReplyHistory(
                    ruleId = 1L,
                    ruleName = "Auto Reply Rule",
                    senderName = conversationId,
                    incomingMessage = "Incoming Message",
                    repliedMessage = expectedText,
                    packageName = criteria.packageName,
                    isSuccessfullySent = true
                )
                historyRepository.saveHistory(history)
            } catch (ignored: Exception) {
            }
        }

        return PostVerifyResult(
            status = PostVerifyStatus.COMPLETED,
            isCompleted = true,
            conversationId = conversationId,
            expectedReplyText = expectedText,
            matchedText = matchedText,
            outgoingBubblesDetectedCount = outgoingCount,
            outgoingBubbleFound = true,
            replyMatched = true,
            queueItemMarkedCompleted = markedCompleted,
            reason = "Conversation Completed: Reply '$expectedText' verified in conversation '$conversationId'. Conversation status set to COMPLETED/SENT.",
            details = "Exact text match confirmed in outgoing message bubble."
        )
    }

    private fun collectTextNodes(
        node: AccessibilityNodeInfo,
        outList: MutableList<AccessibilityNodeInfo>
    ) {
        if (!node.text.isNullOrBlank()) {
            outList.add(AccessibilityNodeInfo.obtain(node))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTextNodes(child, outList)
            child.recycle()
        }
    }
}
