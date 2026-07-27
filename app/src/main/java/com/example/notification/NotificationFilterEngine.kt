package com.example.notification

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.logger.AppLogger
import com.example.model.CapturedNotification
import com.example.model.LogCategory
import com.example.model.QueueItem
import com.example.model.QueueStatus
import com.example.queue.ConversationQueue
import com.example.repository.QueueRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Filter Engine that evaluates incoming notifications according to strict personal chat rules:
 * - Ignores non-imo notifications
 * - Ignores summary notifications
 * - Ignores empty notifications
 * - Ignores edited notifications
 * - Ignores duplicate notifications
 * 
 * For valid personal chat notifications:
 * - If sender already exists in active queue: DO NOT create duplicate. Update existing queue item.
 * - Otherwise: Enqueue new queue item.
 * 
 * Generates detailed logs: Filtered, Accepted, Rejected, Duplicate merged.
 */
class NotificationFilterEngine(
    private val context: Context,
    private val queueRepository: QueueRepository,
    private val conversationQueue: ConversationQueue = ConversationQueue(queueRepository)
) {

    // Thread-safe cache of recently processed notifications for duplicate detection (key -> timestamp)
    private val recentNotificationsCache = ConcurrentHashMap<String, Long>()

    // Thread-safe cache of notification IDs -> last message text to detect identical edited updates
    private val processedNotificationIdCache = ConcurrentHashMap<Int, String>()

    private val allowedImoPackages = setOf(
        "com.imo.android.imoimbeta",
        "com.imo.android.imoim",
        "com.imo.android.imoimlite"
    )

    suspend fun filterAndProcessNotification(sbn: StatusBarNotification?): FilterResult {
        if (sbn == null) {
            val result = FilterResult.Rejected(REASON_EMPTY, "StatusBarNotification is null")
            logFilterOutcome(null, result)
            return result
        }

        val packageName = sbn.packageName ?: ""
        val notification = sbn.notification
        val postTime = sbn.postTime

        // 1. Check non-imo notification
        if (!isImoPackage(packageName)) {
            val result = FilterResult.Rejected(
                reason = REASON_NON_IMO,
                details = "Package '$packageName' is not a supported imo application"
            )
            logFilterOutcome(
                CapturedNotification(sbn.id, packageName, "Unknown", "", "", postTime),
                result
            )
            return result
        }

        if (notification == null) {
            val result = FilterResult.Rejected(REASON_EMPTY, "Notification object is null")
            logFilterOutcome(
                CapturedNotification(sbn.id, packageName, "Unknown", "", "", postTime),
                result
            )
            return result
        }

        // 2. Check summary notification
        if (isSummaryNotification(sbn, notification)) {
            val result = FilterResult.Rejected(
                reason = REASON_SUMMARY,
                details = "Notification is a group summary or call notification flag"
            )
            logFilterOutcome(
                CapturedNotification(sbn.id, packageName, "Group/Summary", "Summary", "", postTime),
                result
            )
            return result
        }

        // Extract title, text, and sender
        val extras = notification.extras
        val rawTitle = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val rawText = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim() ?: ""

        var senderName = rawTitle
        var messageText = rawText.ifEmpty { bigText }

        // Dynamic extraction from MessagingStyle extras
        try {
            val messagingStyleMessages = extras?.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (messagingStyleMessages != null && messagingStyleMessages.isNotEmpty()) {
                val lastMsgBundle = messagingStyleMessages.last() as? android.os.Bundle
                if (lastMsgBundle != null) {
                    val mText = lastMsgBundle.getCharSequence("text")?.toString()?.trim() ?: ""
                    val mSender = lastMsgBundle.getCharSequence("sender")?.toString()?.trim() ?: ""

                    if (mText.isNotEmpty()) {
                        messageText = mText
                    }
                    if (mSender.isNotEmpty()) {
                        senderName = mSender
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting messaging style extras", e)
        }

        val captured = CapturedNotification(
            notificationId = sbn.id,
            packageName = packageName,
            senderName = senderName,
            title = rawTitle,
            text = messageText,
            postTime = postTime
        )

        // 3. Check empty notification
        if (senderName.isBlank() || messageText.isBlank()) {
            val result = FilterResult.Rejected(
                reason = REASON_EMPTY,
                details = "Sender name or message text is empty or blank"
            )
            logFilterOutcome(captured, result)
            return result
        }

        // 4. Check edited notification
        if (isEditedNotification(sbn, notification, messageText)) {
            val result = FilterResult.Rejected(
                reason = REASON_EDITED,
                details = "Notification contains edited metadata or matches unchanged edited post"
            )
            logFilterOutcome(captured, result)
            return result
        }

        // 5. Check duplicate notification
        val cacheKey = "$packageName:$senderName:$messageText"
        val currentTime = System.currentTimeMillis()
        val lastSeenTime = recentNotificationsCache[cacheKey]

        if (lastSeenTime != null && (currentTime - lastSeenTime) < DUPLICATE_TIME_WINDOW_MS) {
            val result = FilterResult.Rejected(
                reason = REASON_DUPLICATE,
                details = "Identical message received from '$senderName' within ${DUPLICATE_TIME_WINDOW_MS / 1000}s threshold"
            )
            logFilterOutcome(captured, result)
            return result
        }

        // Record in cache
        recentNotificationsCache[cacheKey] = currentTime
        processedNotificationIdCache[sbn.id] = messageText
        cleanOldCacheEntries(currentTime)

        // All rejection filters passed -> Process valid personal chat notification
        val existingActiveQueueItem = queueRepository.findActiveQueueItemBySender(packageName, senderName)

        val queueItem = conversationQueue.enqueueOrUpdate(
            packageName = packageName,
            senderName = senderName,
            messageText = messageText,
            timestamp = currentTime
        )

        val finalResult = if (existingActiveQueueItem != null) {
            FilterResult.DuplicateMerged(
                existingQueueItem = existingActiveQueueItem,
                updatedQueueItem = queueItem,
                details = "Updated existing active conversation queue item #${existingActiveQueueItem.id} for sender '$senderName' with new text: '$messageText'"
            )
        } else {
            FilterResult.Accepted(
                queueItem = queueItem,
                details = "Enqueued new valid conversation queue item for sender '$senderName' (Queue Item #${queueItem.id})"
            )
        }

        logFilterOutcome(captured, finalResult)
        return finalResult
    }

    private fun isImoPackage(packageName: String): Boolean {
        if (allowedImoPackages.contains(packageName)) return true
        return packageName.startsWith("com.imo.android.imoim")
    }

    private fun isSummaryNotification(sbn: StatusBarNotification, notification: Notification): Boolean {
        // Group summary flag
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return true
        }

        val extras = notification.extras
        if (extras != null) {
            if (extras.getBoolean("android.isGroupSummary", false)) {
                return true
            }
        }

        // Low priority or silent group notification category
        if (notification.category == Notification.CATEGORY_CALL && notification.priority <= Notification.PRIORITY_LOW) {
            return true
        }

        return false
    }

    private fun isEditedNotification(sbn: StatusBarNotification, notification: Notification, text: String): Boolean {
        val extras = notification.extras
        if (extras != null) {
            if (extras.getBoolean("android.isEdited", false)) {
                return true
            }
        }

        // Check if message ends with explicit edited markers without new content
        val lowerText = text.lowercase()
        if (lowerText.endsWith("(edited)") || lowerText.endsWith("[edited]")) {
            return true
        }

        // Check if the exact same notification ID was previously posted with the exact same text
        val previousText = processedNotificationIdCache[sbn.id]
        if (previousText != null && previousText == text) {
            return true
        }

        return false
    }

    private fun cleanOldCacheEntries(currentTime: Long) {
        if (recentNotificationsCache.size > 100) {
            recentNotificationsCache.entries.removeIf { (currentTime - it.value) > DUPLICATE_TIME_WINDOW_MS * 2 }
        }
    }

    private fun logFilterOutcome(captured: CapturedNotification?, result: FilterResult) {
        val packageName = captured?.packageName ?: "N/A"
        val sender = captured?.senderName ?: "N/A"
        val title = captured?.title ?: "N/A"
        val text = captured?.text ?: "N/A"
        val timestamp = captured?.postTime ?: System.currentTimeMillis()

        when (result) {
            is FilterResult.Accepted -> {
                val formattedLog = """
                    Filtered: Accepted
                    Package: $packageName
                    Sender: $sender
                    Title: $title
                    Text: $text
                    Timestamp: $timestamp
                    Details: ${result.details}
                """.trimIndent()

                Log.i(TAG, formattedLog)

                AppLogger.success(
                    LogCategory.NOTIFICATION,
                    "Accepted: $sender",
                    formattedLog
                )
                AppLogger.info(
                    LogCategory.QUEUE,
                    "Accepted & Enqueued for $sender",
                    "Queue Item ID: ${result.queueItem.id} | Message: '$text'"
                )
            }

            is FilterResult.DuplicateMerged -> {
                val formattedLog = """
                    Filtered: Duplicate merged
                    Package: $packageName
                    Sender: $sender
                    Title: $title
                    Text: $text
                    Timestamp: $timestamp
                    Details: ${result.details}
                """.trimIndent()

                Log.i(TAG, formattedLog)

                AppLogger.info(
                    LogCategory.NOTIFICATION,
                    "Duplicate merged: $sender",
                    formattedLog
                )
                AppLogger.info(
                    LogCategory.QUEUE,
                    "Duplicate merged for $sender",
                    "Updated Queue Item #${result.updatedQueueItem.id} with new text: '$text'"
                )
            }

            is FilterResult.Rejected -> {
                val formattedLog = """
                    Filtered: Rejected (${result.reason})
                    Package: $packageName
                    Sender: $sender
                    Title: $title
                    Text: $text
                    Timestamp: $timestamp
                    Details: ${result.details}
                """.trimIndent()

                Log.w(TAG, formattedLog)

                AppLogger.warning(
                    LogCategory.NOTIFICATION,
                    "Rejected (${result.reason}): $sender",
                    formattedLog
                )
            }
        }
    }

    companion object {
        private const val TAG = "NotificationFilterEngine"
        private const val DUPLICATE_TIME_WINDOW_MS = 10000L // 10 seconds duplicate window

        const val REASON_NON_IMO = "non-imo notification"
        const val REASON_SUMMARY = "summary notification"
        const val REASON_EMPTY = "empty notification"
        const val REASON_EDITED = "edited notification"
        const val REASON_DUPLICATE = "duplicate notification"
    }
}

sealed class FilterResult {
    data class Accepted(
        val queueItem: QueueItem,
        val details: String
    ) : FilterResult()

    data class DuplicateMerged(
        val existingQueueItem: QueueItem,
        val updatedQueueItem: QueueItem,
        val details: String
    ) : FilterResult()

    data class Rejected(
        val reason: String,
        val details: String
    ) : FilterResult()
}
