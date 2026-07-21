package com.example.accessibility.imo

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.example.accessibility.AccessibilityActionHelper
import com.example.accessibility.AccessibilityLogger
import com.example.accessibility.AccessibilityManager
import com.example.model.MessageType
import com.example.model.NotificationData
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Executes automated actions on IMO/IMO Lite screens.
 * Contains safety measures including 10s action timeouts, retry mechanics, fallback options,
 * state validation, and detailed execution logging.
 */
class IMOActionPerformer(
    private val context: Context,
    private val accessibilityManager: AccessibilityManager,
    private val nodeScanner: IMONodeScanner
) {

    companion object {
        private const val TAG = "IMOActionPerformer"
        private const val DEFAULT_TIMEOUT_MS = 10000L
        private const val MAX_RETRIES = 3
    }

    /**
     * Verifies if current screen matches Chat Conversation Screen.
     */
    fun isOnChatScreen(): Boolean {
        val root = accessibilityManager.getRootNode()
        val result = nodeScanner.isOnChatScreen(root)
        root?.recycle()
        return result
    }

    /**
     * Verifies if current screen matches Chat List Screen.
     */
    fun isOnChatListScreen(): Boolean {
        val root = accessibilityManager.getRootNode()
        val result = nodeScanner.isOnChatListScreen(root)
        root?.recycle()
        return result
    }

    /**
     * Attempts to locate a chat matching [contactName] and click to open it.
     * Implements scrolling fallback traversal to find off-screen contacts.
     */
    suspend fun openChatByContactName(contactName: String): Boolean {
        AccessibilityLogger.i(TAG, "Opening chat for contact: '$contactName'")
        
        return executeActionWithRetry("openChatByContactName") {
            if (!isOnChatListScreen()) {
                AccessibilityLogger.w(TAG, "Not on chat list screen. Attempting error recovery...")
                recoverToChatListScreen()
            }

            var attempts = 0
            var found = false
            while (attempts < 5 && !found) {
                val root = accessibilityManager.getRootNode()
                val listItems = nodeScanner.scanChatListScreen(root)
                root?.recycle()

                val match = listItems.find { it.contactName.equals(contactName, ignoreCase = true) }
                if (match != null && match.contactNode != null) {
                    AccessibilityLogger.i(TAG, "Contact '$contactName' located. Clicking...")
                    val clicked = AccessibilityActionHelper.safeClick(match.contactNode)
                    match.contactNode.recycle()
                    listItems.forEach { it.contactNode?.recycle() }
                    
                    if (clicked) {
                        // Wait a brief period to allow chat screen to load
                        delay(1000L)
                        if (isOnChatScreen()) {
                            found = true
                            break
                        }
                    }
                } else {
                    listItems.forEach { it.contactNode?.recycle() }
                }

                // Scroll down to find the contact
                AccessibilityLogger.d(TAG, "Contact '$contactName' not visible. Scrolling list down...")
                val scrolled = scrollChatDown()
                if (!scrolled) {
                    break // End of list or scroll failed
                }
                delay(500L)
                attempts++
            }
            found
        }
    }

    /**
     * Launches the IMO app by triggering the incoming notification's pending intent.
     * Falls back to launching package manager direct intent if the intent is absent.
     */
    suspend fun openChatByNotification(notification: NotificationData): Boolean {
        AccessibilityLogger.i(TAG, "Opening chat via notification from '${notification.senderName}'")
        
        return executeActionWithRetry("openChatByNotification") {
            try {
                if (notification.pendingIntent != null) {
                    notification.pendingIntent.send()
                    delay(1500L) // Wait for app launch
                    true
                } else {
                    AccessibilityLogger.w(TAG, "Notification has no PendingIntent. Fallback to package launcher.")
                    val intent = context.packageManager.getLaunchIntentForPackage(notification.packageName)
                    if (intent != null) {
                        context.startActivity(intent)
                        delay(2000L)
                        true
                    } else {
                        AccessibilityLogger.e(TAG, "Failed to resolve launch intent for package '${notification.packageName}'")
                        false
                    }
                }
            } catch (e: Exception) {
                AccessibilityLogger.e(TAG, "Error launching notification action", e)
                false
            }
        }
    }

    /**
     * Determines the message type of the last visible message in the active conversation.
     */
    fun detectLastMessageType(): MessageType {
        val root = accessibilityManager.getRootNode()
        val screenInfo = nodeScanner.scanChatConversationScreen(root)
        root?.recycle()

        val lastMsg = screenInfo?.messages?.lastOrNull()
        val type = lastMsg?.messageType ?: MessageType.EMPTY
        AccessibilityLogger.d(TAG, "Detected last message type: $type (Text: '${lastMsg?.text ?: ""}')")
        return type
    }

    /**
     * Triggers the "A" Voice-to-Text translation button on the active chat conversation screen.
     */
    suspend fun clickVoiceToTextButton(): Boolean {
        AccessibilityLogger.i(TAG, "Attempting to click Voice-to-Text button")
        return executeActionWithRetry("clickVoiceToTextButton") {
            val root = accessibilityManager.getRootNode()
            val voiceToTextNode = nodeScanner.findVoiceToTextButton(root)
            root?.recycle()

            if (voiceToTextNode != null) {
                val success = AccessibilityActionHelper.safeClick(voiceToTextNode)
                voiceToTextNode.recycle()
                success
            } else {
                AccessibilityLogger.w(TAG, "Voice-to-Text 'A' button not found on screen.")
                false
            }
        }
    }

    /**
     * Waits up to the specified timeout for the transcription to appear in the active chat.
     */
    suspend fun waitForTranscript(timeoutMs: Long = 5000L): String? {
        AccessibilityLogger.i(TAG, "Waiting for transcription. Timeout: ${timeoutMs}ms")
        return withTimeoutOrNull(timeoutMs) {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val text = readTranscribedText()
                if (!text.isNullOrEmpty()) {
                    AccessibilityLogger.i(TAG, "Transcription text detected: '$text'")
                    return@withTimeoutOrNull text
                }
                delay(500L)
            }
            null
        }
    }

    /**
     * Inspects the UI elements to read any transcribed text from the voice-to-text converter.
     */
    fun readTranscribedText(): String? {
        val root = accessibilityManager.getRootNode()
        val text = nodeScanner.findTranscribedText(root)
        root?.recycle()
        return text
    }

    /**
     * Locates the active text input node on the chat screen.
     * Note: Caller is responsible for recycling the returned AccessibilityNodeInfo.
     */
    fun findMessageInputField(): AccessibilityNodeInfo? {
        val root = accessibilityManager.getRootNode()
        val input = nodeScanner.findInputField(root)
        root?.recycle()
        return input
    }

    /**
     * Types a message into the active message input field.
     */
    suspend fun typeMessage(text: String): Boolean {
        AccessibilityLogger.i(TAG, "Typing message: '$text'")
        return executeActionWithRetry("typeMessage") {
            val inputNode = findMessageInputField()
            if (inputNode != null) {
                val success = AccessibilityActionHelper.safeInputText(inputNode, text)
                inputNode.recycle()
                success
            } else {
                AccessibilityLogger.e(TAG, "Cannot find message input field to type text.")
                false
            }
        }
    }

    /**
     * Clicks the Send button on the active conversation screen.
     */
    suspend fun clickSendButton(): Boolean {
        AccessibilityLogger.i(TAG, "Clicking Send button")
        return executeActionWithRetry("clickSendButton") {
            val root = accessibilityManager.getRootNode()
            val sendButton = nodeScanner.findSendButton(root)
            root?.recycle()

            if (sendButton != null) {
                val success = AccessibilityActionHelper.safeClick(sendButton)
                sendButton.recycle()
                success
            } else {
                AccessibilityLogger.e(TAG, "Send button not found.")
                false
            }
        }
    }

    /**
     * Clicks the Back button to navigate out of the active conversation.
     */
    suspend fun clickBackButton(): Boolean {
        AccessibilityLogger.i(TAG, "Clicking Back button")
        return executeActionWithRetry("clickBackButton") {
            val root = accessibilityManager.getRootNode()
            val backButton = nodeScanner.findBackButton(root)
            root?.recycle()

            if (backButton != null) {
                val success = AccessibilityActionHelper.safeClick(backButton)
                backButton.recycle()
                success
            } else {
                AccessibilityLogger.w(TAG, "Back button not found. Using system-level back gesture.")
                accessibilityManager.performBack()
            }
        }
    }

    /**
     * Scrolls the chat view upwards.
     */
    suspend fun scrollChatUp(): Boolean {
        AccessibilityLogger.d(TAG, "Scrolling chat up")
        val root = accessibilityManager.getRootNode()
        // Find scrollable node
        val scrollableNode = findScrollableNode(root)
        root?.recycle()

        return if (scrollableNode != null) {
            val success = AccessibilityActionHelper.safeScrollBackward(scrollableNode)
            scrollableNode.recycle()
            success
        } else {
            false
        }
    }

    /**
     * Scrolls the chat view downwards.
     */
    suspend fun scrollChatDown(): Boolean {
        AccessibilityLogger.d(TAG, "Scrolling chat down")
        val root = accessibilityManager.getRootNode()
        val scrollableNode = findScrollableNode(root)
        root?.recycle()

        return if (scrollableNode != null) {
            val success = AccessibilityActionHelper.safeScrollForward(scrollableNode)
            scrollableNode.recycle()
            success
        } else {
            false
        }
    }

    // --- Safety and Utility Helpers ---

    private fun findScrollableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isScrollable) {
            return AccessibilityNodeInfo.obtain(root)
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findScrollableNode(child)
            child.recycle()
            if (found != null) {
                return found
            }
        }
        return null
    }

    private suspend fun recoverToChatListScreen() {
        AccessibilityLogger.w(TAG, "Initiating recovery procedure to Chat List Screen")
        var attempts = 0
        while (attempts < 3 && !isOnChatListScreen()) {
            accessibilityManager.performBack()
            delay(1000L)
            attempts++
        }
    }

    /**
     * Safe execution wrapper handling timeouts, state validation, and error recovery.
     */
    private suspend fun executeActionWithRetry(
        actionName: String,
        block: suspend () -> Boolean
    ): Boolean {
        var lastSuccess = false
        val startTime = System.currentTimeMillis()

        for (attempt in 1..MAX_RETRIES) {
            AccessibilityLogger.d(TAG, "Action '$actionName': attempt $attempt/$MAX_RETRIES starting.")
            
            val result = withTimeoutOrNull(DEFAULT_TIMEOUT_MS) {
                try {
                    block()
                } catch (e: Exception) {
                    AccessibilityLogger.e(TAG, "Exception during action '$actionName'", e)
                    false
                }
            }

            if (result == true) {
                lastSuccess = true
                val duration = System.currentTimeMillis() - startTime
                AccessibilityLogger.i(TAG, "Action '$actionName' succeeded in ${duration}ms (Attempt $attempt)")
                break
            } else {
                AccessibilityLogger.w(TAG, "Action '$actionName' failed on attempt $attempt.")
                if (attempt < MAX_RETRIES) {
                    delay(1000L) // Wait before retrying
                }
            }
        }

        if (!lastSuccess) {
            AccessibilityLogger.e(TAG, "Action '$actionName' failed permanently after $MAX_RETRIES attempts")
        }
        return lastSuccess
    }
}
