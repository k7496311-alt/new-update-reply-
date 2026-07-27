package com.example.data

import android.content.Context
import android.util.Log
import com.example.accessibility.AccessibilityActionHelper
import com.example.accessibility.AccessibilityManager
import com.example.accessibility.AutoReplyAccessibilityService
import com.example.accessibility.imo.ChatOpenResult
import com.example.accessibility.imo.IMOActionPerformer
import com.example.accessibility.imo.IMONodeScanner
import com.example.model.QueueItem
import com.example.notification.NotificationPendingIntentCache
import com.example.repository.ChatOpenRepository
import kotlinx.coroutines.delay

/**
 * Concrete implementation of ChatOpenRepository using Android Accessibility APIs.
 */
class ChatOpenRepositoryImpl(
    private val context: Context,
    private val accessibilityManager: AccessibilityManager = AccessibilityManager(context),
    private val nodeScanner: IMONodeScanner = IMONodeScanner(),
    private val actionPerformer: IMOActionPerformer = IMOActionPerformer(context, accessibilityManager, nodeScanner)
) : ChatOpenRepository {

    override suspend fun openChat(queueItem: QueueItem): ChatOpenResult {
        val packageName = queueItem.packageName
        val senderName = queueItem.senderName

        if (senderName.isBlank()) {
            return ChatOpenResult.Failed("Sender name in conversation queue item is blank")
        }

        // 1. Try opening chat directly via cached notification PendingIntent if available
        val pendingIntent = NotificationPendingIntentCache.get(packageName, senderName)
        if (pendingIntent != null) {
            try {
                pendingIntent.send()
                delay(1500L) // Wait for IMO chat activity launch
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering notification pending intent for $senderName", e)
            }
        }

        // 2. If chat is not open yet, bring app package to foreground and navigate
        if (!actionPerformer.isOnChatScreen()) {
            actionPerformer.launchAppPackage(packageName)
            delay(2000L)

            // Try clicking the contact item from chat list if visible on screen
            if (actionPerformer.isOnChatListScreen()) {
                val opened = actionPerformer.openChatByContactName(senderName, packageName)
                if (opened) {
                    delay(1200L)
                }
            }
        }

        // 3. Verify opened chat header contact name
        val (isCorrect, actualSender) = verifyHeaderSenderName(senderName)
        return when {
            isCorrect -> ChatOpenResult.Success(
                senderName = senderName,
                packageName = packageName,
                details = "Chat verified successfully for sender '$senderName'"
            )
            actualSender.isNotBlank() && !actualSender.equals(senderName, ignoreCase = true) -> ChatOpenResult.WrongChat(
                expectedSender = senderName,
                actualSender = actualSender,
                details = "Opened chat header '$actualSender' does not match target sender '$senderName'"
            )
            else -> ChatOpenResult.Failed(
                reason = "Unable to open chat screen or verify header for '$senderName'",
                details = "Current header text: '$actualSender'"
            )
        }
    }

    override suspend fun verifyHeaderSenderName(expectedSender: String): Pair<Boolean, String> {
        val root = accessibilityManager.getRootNode() ?: return Pair(false, "")
        try {
            val visibleHeader = nodeScanner.findHeaderName(root) ?: ""
            if (visibleHeader.isBlank()) {
                return Pair(false, "")
            }

            val cleanExpected = expectedSender.trim()
            val cleanHeader = visibleHeader.trim()

            val matches = cleanHeader.equals(cleanExpected, ignoreCase = true) ||
                    cleanHeader.contains(cleanExpected, ignoreCase = true) ||
                    cleanExpected.contains(cleanHeader, ignoreCase = true)

            return Pair(matches, cleanHeader)
        } finally {
            root.recycle()
        }
    }

    override suspend fun closeCurrentChat(): Boolean {
        val service = AutoReplyAccessibilityService.getInstance()
        if (service != null) {
            AccessibilityActionHelper.safePerformBack(service)
            delay(800L)
            return true
        }

        val root = accessibilityManager.getRootNode()
        if (root != null) {
            try {
                val backButton = nodeScanner.findBackButton(root)
                if (backButton != null) {
                    val clicked = AccessibilityActionHelper.safeClick(backButton)
                    backButton.recycle()
                    delay(800L)
                    return clicked
                }
            } finally {
                root.recycle()
            }
        }
        return false
    }

    companion object {
        private const val TAG = "ChatOpenRepositoryImpl"
    }
}
