package com.example.data

import android.view.accessibility.AccessibilityNodeInfo
import com.example.accessibility.sender.MessageSendCriteria
import com.example.accessibility.sender.MessageSendResult
import com.example.accessibility.sender.MessageSendStatus
import com.example.repository.MessageSendRepository
import kotlinx.coroutines.delay

/**
 * Concrete implementation of MessageSendRepository.
 * Performs a SINGLE click on Send Button, waits for UI update, and verifies transmission
 * by checking if the composer is cleared or an outgoing message bubble with sent text appears.
 */
class MessageSendRepositoryImpl : MessageSendRepository {

    override suspend fun performSend(criteria: MessageSendCriteria): MessageSendResult {
        val sendButton = criteria.sendButtonNode
        val composer = criteria.composerNode
        val sentText = criteria.sentText

        if (sendButton == null) {
            return MessageSendResult(
                status = MessageSendStatus.SEND_FAILED,
                isSuccess = false,
                clickPerformed = false,
                composerCleared = false,
                outgoingBubbleFound = false,
                sentText = sentText,
                reason = "Send Failed: Send button accessibility node is null.",
                details = "Cannot click send button when node reference is null."
            )
        }

        // 1. Perform SINGLE click on Send Button (Guard against double click)
        val clickSuccess = executeSingleClick(sendButton)

        if (!clickSuccess) {
            return MessageSendResult(
                status = MessageSendStatus.SEND_FAILED,
                isSuccess = false,
                clickPerformed = false,
                composerCleared = false,
                outgoingBubbleFound = false,
                sentText = sentText,
                reason = "Send Failed: Failed to execute click action on send button node.",
                details = "Accessibility performAction(ACTION_CLICK) returned false."
            )
        }

        // 2. Wait for UI update
        delay(criteria.postClickWaitMs.coerceAtLeast(300L))

        // 3. Re-examine composer field state
        var isComposerCleared = false
        if (composer != null) {
            composer.refresh()
            val currentComposerText = composer.text?.toString()?.trim() ?: ""
            isComposerCleared = currentComposerText.isEmpty() || currentComposerText != sentText
        }

        // 4. Scan accessibility tree for outgoing bubble matching sent text
        var isOutgoingBubbleFound = false
        if (criteria.rootNode != null) {
            criteria.rootNode.refresh()
            isOutgoingBubbleFound = checkForOutgoingBubble(criteria.rootNode, sentText)
        }

        val sendVerified = isComposerCleared || isOutgoingBubbleFound

        return if (sendVerified) {
            MessageSendResult(
                status = MessageSendStatus.SEND_SUCCESS,
                isSuccess = true,
                clickPerformed = true,
                composerCleared = isComposerCleared,
                outgoingBubbleFound = isOutgoingBubbleFound,
                sentText = sentText,
                reason = "SEND_SUCCESS: Transmission confirmed. Composer cleared ($isComposerCleared), Outgoing bubble found ($isOutgoingBubbleFound).",
                details = "Send button clicked once. Post-send UI state verified."
            )
        } else {
            MessageSendResult(
                status = MessageSendStatus.SEND_FAILED,
                isSuccess = false,
                clickPerformed = true,
                composerCleared = false,
                outgoingBubbleFound = false,
                sentText = sentText,
                reason = "Send Failed: Send button clicked once, but UI update did not reflect cleared composer or outgoing message bubble.",
                details = "No retry executed as per specification."
            )
        }
    }

    private fun executeSingleClick(node: AccessibilityNodeInfo): Boolean {
        var clicked = try {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (e: Exception) {
            false
        }

        if (!clicked && node.parent != null) {
            clicked = try {
                node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } catch (e: Exception) {
                false
            }
        }

        return clicked
    }

    private fun checkForOutgoingBubble(rootNode: AccessibilityNodeInfo, textToMatch: String): Boolean {
        if (textToMatch.isBlank()) return false
        val nodesWithText = mutableListOf<AccessibilityNodeInfo>()
        collectTextNodes(rootNode, nodesWithText)

        val found = nodesWithText.any { node ->
            val nodeText = node.text?.toString()?.trim() ?: ""
            nodeText.equals(textToMatch.trim(), ignoreCase = true) || nodeText.contains(textToMatch.trim(), ignoreCase = true)
        }

        nodesWithText.forEach { it.recycle() }
        return found
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
