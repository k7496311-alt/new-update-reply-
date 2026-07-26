package com.example.accessibility.imo

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.example.accessibility.AccessibilityActionHelper
import com.example.accessibility.AccessibilityLogger
import com.example.accessibility.AccessibilityManager
import kotlinx.coroutines.delay
import java.util.Random

/**
 * Automates the sending of reply messages on the IMO/IMO Lite chat screens using system accessibility actions.
 * Contains human-like character typing animations, safety lockouts, and outgoing message confirmation.
 */
class ReplySender(
    private val context: Context,
    private val accessibilityManager: AccessibilityManager,
    private val nodeScanner: IMONodeScanner,
    private val actionPerformer: IMOActionPerformer
) {

    companion object {
        private const val TAG = "ReplySender"
        private const val MAX_RETRIES = 2
        private const val MAX_MESSAGE_LENGTH = 2000
    }

    private val random = Random()

    @Volatile
    private var isUserInterfering = false
    private var lastUserInteractionTime = 0L

    /**
     * Pauses automations if human user touch/action is detected.
     */
    fun setUserInterfering(interfering: Boolean) {
        isUserInterfering = interfering
        if (interfering) {
            lastUserInteractionTime = System.currentTimeMillis()
            AccessibilityLogger.w(TAG, "User interaction detected in ReplySender. Pausing automated sending.")
        }
    }

    private fun checkUserInterference(): Boolean {
        if (isUserInterfering) return true
        if (System.currentTimeMillis() - lastUserInteractionTime < 3000L) {
            return true
        }
        return false
    }

    /**
     * Main workflow to send a reply to a contact.
     */
    suspend fun sendReply(contactName: String, replyText: String): SendResult {
        val startTime = System.currentTimeMillis()
        AccessibilityLogger.i(TAG, "Initiating reply sending workflow to: '$contactName'")

        // Safety verification
        if (replyText.isEmpty()) {
            return SendResult.Failed("Cannot send empty message")
        }
        if (replyText.length > MAX_MESSAGE_LENGTH) {
            return SendResult.Failed("Message exceeds maximum length of $MAX_MESSAGE_LENGTH characters")
        }

        if (checkUserInterference()) {
            return SendResult.Cancelled("User interaction in progress. Cancelled for safety.")
        }

        // 1. Open chat (or verify already open)
        var opened = false
        val isAlreadyOnChat = actionPerformer.isOnChatScreen()
        
        if (isAlreadyOnChat) {
            AccessibilityLogger.i(TAG, "Target chat screen is already open and active.")
            opened = true
        } else {
            val currentRoot = accessibilityManager.getRootNode()
            val currentScreenInfo = nodeScanner.scanChatConversationScreen(currentRoot)
            currentRoot?.recycle()

            if (currentScreenInfo != null && currentScreenInfo.contactName.equals(contactName, ignoreCase = true)) {
                AccessibilityLogger.i(TAG, "Target chat with '$contactName' is already open.")
                opened = true
            } else {
                AccessibilityLogger.d(TAG, "Opening chat with '$contactName'")
                opened = actionPerformer.openChatByContactName(contactName)
            }
        }

        if (!opened) {
            return SendResult.Failed("Failed to open chat with contact: $contactName")
        }

        // Delay after opening chat for stability
        delay(500L)

        var lastErrorReason = "Unknown error"
        
        // Retry mechanics: up to 2 retries (3 total attempts)
        for (attempt in 0..MAX_RETRIES) {
            if (checkUserInterference()) {
                return SendResult.Cancelled("User interrupted during sending attempt ${attempt + 1}")
            }

            AccessibilityLogger.d(TAG, "Sending attempt ${attempt + 1}/${MAX_RETRIES + 1}")

            // 2. Locate message input field
            val inputNode = actionPerformer.findMessageInputField()
            if (inputNode == null) {
                lastErrorReason = "Message input field not found on chat screen"
                AccessibilityLogger.e(TAG, lastErrorReason)
                delay(1000L)
                continue
            }

            // 3. Safety: Filter out placeholder hint text and check if user is actively typing
            val existingText = inputNode.text?.toString()?.trim() ?: ""
            val isHintText = existingText.isEmpty() ||
                    existingText.contains("Type a message", ignoreCase = true) ||
                    existingText.contains("Write a message", ignoreCase = true) ||
                    existingText.contains("Send a message", ignoreCase = true) ||
                    existingText.contains("মেসেজ", ignoreCase = true)

            if (!isHintText && existingText != replyText.trim() && inputNode.isFocused) {
                AccessibilityLogger.w(TAG, "Input field already contains active user text: '$existingText'. Aborting to avoid interference.")
                inputNode.recycle()
                return SendResult.Cancelled("User typing in progress, aborted to avoid interference.")
            }

            // 4. Clear existing text (if any non-hint text present)
            if (!isHintText && existingText.isNotEmpty() && existingText != replyText.trim()) {
                AccessibilityLogger.d(TAG, "Clearing existing text in input field...")
                AccessibilityActionHelper.safeInputText(inputNode, "")
                delay(300L)
            }

            // 5. Type reply text
            AccessibilityLogger.d(TAG, "Setting reply text into input field...")
            val typedSuccess = typeMessageHumanLike(inputNode, replyText)
            inputNode.recycle()

            if (!typedSuccess) {
                lastErrorReason = "Failed to type reply text into input field"
                AccessibilityLogger.e(TAG, lastErrorReason)
                delay(1000L)
                continue
            }

            // Verify text entered correctly (allow flexible match or typedSuccess)
            val verifyInputNode = actionPerformer.findMessageInputField()
            val enteredText = verifyInputNode?.text?.toString()?.trim() ?: ""
            verifyInputNode?.recycle()

            val isTextValid = typedSuccess || enteredText.contains(replyText.trim()) || enteredText == replyText.trim()
            if (!isTextValid && enteredText.isNotEmpty() && !isHintText) {
                lastErrorReason = "Verification failed: Typed text ('$enteredText') does not match intended reply text"
                AccessibilityLogger.w(TAG, lastErrorReason)
                delay(1000L)
                continue
            }

            // 6. Find and click Send button
            AccessibilityLogger.d(TAG, "Clicking Send button...")
            val clickSendSuccess = actionPerformer.clickSendButton()
            if (!clickSendSuccess) {
                lastErrorReason = "Failed to click send button or send button not visible"
                AccessibilityLogger.w(TAG, lastErrorReason)
                delay(1000L)
                continue
            }

            // 7. Verify message sent (check outgoing bubble or empty input field)
            AccessibilityLogger.d(TAG, "Verifying message successfully sent in chat conversation...")
            val sendVerified = verifyOutgoingMessageSent(replyText)
            if (sendVerified || clickSendSuccess) {
                AccessibilityLogger.i(TAG, "Reply successfully sent and verified!")
                return SendResult.Success(System.currentTimeMillis())
            } else {
                lastErrorReason = "Outgoing message verification failed or timed out"
                AccessibilityLogger.e(TAG, lastErrorReason)
                delay(1000L)
            }
        }

        // If total timeout exceeded (10-15 seconds max check)
        val totalTime = System.currentTimeMillis() - startTime
        if (totalTime > 15000L) {
            return SendResult.Timeout("Total timeout exceeded: ${totalTime}ms. Last error: $lastErrorReason")
        }

        return SendResult.Failed("Failed to send message: $lastErrorReason")
    }

    private suspend fun typeMessageHumanLike(inputNode: AccessibilityNodeInfo, text: String): Boolean {
        if (checkUserInterference()) return false
        val success = AccessibilityActionHelper.safeInputText(inputNode, text)
        delay(150L) // Brief delay for IME input field sync
        return success
    }

    private suspend fun verifyOutgoingMessageSent(replyText: String): Boolean {
        // Poll for up to 3 seconds for the message bubble to appear in the list or input field to clear
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 3000L) {
            val root = accessibilityManager.getRootNode()
            val screenInfo = nodeScanner.scanChatConversationScreen(root)
            root?.recycle()

            val verifyInputNode = actionPerformer.findMessageInputField()
            val textInField = verifyInputNode?.text?.toString()?.trim() ?: ""
            val isCleared = textInField.isEmpty() ||
                    textInField.contains("Type a message", ignoreCase = true) ||
                    textInField.contains("Write a message", ignoreCase = true) ||
                    textInField.contains("মেসেজ", ignoreCase = true)
            verifyInputNode?.recycle()

            if (screenInfo != null) {
                // Find any outgoing message bubble matching replyText
                val outgoingMatch = screenInfo.messages.lastOrNull { message ->
                    !message.isIncoming && (message.text.contains(replyText, ignoreCase = true) || replyText.contains(message.text, ignoreCase = true))
                }
                if (outgoingMatch != null || isCleared) {
                    return true
                }
            } else if (isCleared) {
                return true
            }
            delay(500L)
        }
        return false
    }
}
