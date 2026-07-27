package com.example.data

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.example.accessibility.input.inserter.MessageInputInsertCriteria
import com.example.accessibility.input.inserter.MessageInputInsertResult
import com.example.accessibility.input.inserter.MessageInputInsertStatus
import com.example.repository.MessageInputInserterRepository
import kotlinx.coroutines.delay

/**
 * Concrete implementation of MessageInputInserterRepository.
 *
 * Handles:
 * - Primary insertion via Accessibility ACTION_SET_TEXT.
 * - Fallback insertion strategies if primary action is unsupported.
 * - Post-insertion text verification.
 * - Input clearing and single retry loop on text mismatch.
 */
class MessageInputInserterRepositoryImpl : MessageInputInserterRepository {

    override suspend fun insertReplyText(criteria: MessageInputInsertCriteria): MessageInputInsertResult {
        val targetNode = criteria.targetNode
        val replyText = criteria.replyText

        if (targetNode == null) {
            return MessageInputInsertResult(
                status = MessageInputInsertStatus.INSERT_FAILED,
                isSuccess = false,
                expectedText = replyText,
                actualInsertedText = "",
                insertedCharacterCount = 0,
                attemptsCount = 0,
                verificationPassed = false,
                usedFallbackStrategy = false,
                reason = "Insert Failed: Target Accessibility node is null.",
                details = "Cannot perform text insertion when target input node is null."
            )
        }

        var attempts = 0
        var usedFallback = false
        var currentActualText = ""
        var verificationPassed = false

        while (attempts <= criteria.maxRetries) {
            attempts++

            // Attempt primary insertion strategy: ACTION_SET_TEXT
            val primarySuccess = tryPrimarySetText(targetNode, replyText)

            if (!primarySuccess) {
                // Primary failed, execute fallback strategy
                usedFallback = true
                tryFallbackSetText(targetNode, replyText)
            }

            // Short delay to allow UI text engine to update accessibility node state
            if (criteria.delayBeforeVerifyMs > 0) {
                delay(criteria.delayBeforeVerifyMs)
            }

            // Refresh node and verify inserted text
            targetNode.refresh()
            currentActualText = targetNode.text?.toString() ?: ""

            // Exact match check
            if (currentActualText == replyText) {
                verificationPassed = true
                return MessageInputInsertResult(
                    status = MessageInputInsertStatus.INSERT_SUCCESS,
                    isSuccess = true,
                    expectedText = replyText,
                    actualInsertedText = currentActualText,
                    insertedCharacterCount = currentActualText.length,
                    attemptsCount = attempts,
                    verificationPassed = true,
                    usedFallbackStrategy = usedFallback,
                    reason = "Insert Success: Reply text inserted and verified successfully.",
                    details = "Exact text match verified after $attempts attempt(s). Length: ${currentActualText.length} chars."
                )
            }

            // Text mismatch occurred!
            if (attempts <= criteria.maxRetries) {
                // Clear input before retrying
                clearInputNode(targetNode)
                delay(100L)
            }
        }

        // All attempts failed verification
        return MessageInputInsertResult(
            status = MessageInputInsertStatus.INSERT_FAILED,
            isSuccess = false,
            expectedText = replyText,
            actualInsertedText = currentActualText,
            insertedCharacterCount = currentActualText.length,
            attemptsCount = attempts,
            verificationPassed = false,
            usedFallbackStrategy = usedFallback,
            reason = "Insert Failed: Verification failed after $attempts attempt(s). Expected length ${replyText.length}, actual length ${currentActualText.length}.",
            details = "Actual text in field: '$currentActualText'"
        )
    }

    private fun tryPrimarySetText(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } catch (e: Exception) {
            false
        }
    }

    private fun tryFallbackSetText(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            // Fallback 1: Click then FOCUS then ACTION_SET_TEXT
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }

            var success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

            // Fallback 2: Try parent if container node was selected
            if (!success) {
                var parent = node.parent
                while (parent != null) {
                    if (parent.isEditable) {
                        success = parent.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                        if (success) break
                    }
                    parent = parent.parent
                }
            }

            success
        } catch (e: Exception) {
            false
        }
    }

    private fun clearInputNode(node: AccessibilityNodeInfo) {
        try {
            val emptyBundle = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    ""
                )
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, emptyBundle)
        } catch (ignored: Exception) {
        }
    }
}
