package com.example.notification

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.model.NotificationItem

class NotificationParser(private val context: Context) {

    /**
     * Parses a StatusBarNotification into a NotificationItem.
     * Returns null if the notification is ignored according to criteria:
     * - Missed Calls
     * - System Notifications
     * - Battery Notifications
     * - Downloads
     */
    fun parse(sbn: StatusBarNotification?): NotificationItem? {
        if (sbn == null) return null

        val notification = sbn.notification ?: return null
        val packageName = sbn.packageName ?: ""
        val notificationId = sbn.id

        // 1. Filter out ignored categories/packages
        if (shouldIgnore(sbn, notification, packageName)) {
            return null
        }

        // 2. Extract App Name safely
        val appName = getAppName(packageName)

        // 3. Extract safe texts from Extras
        val extras = notification.extras ?: return null
        
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim() ?: ""

        val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim() ?: ""

        var sender = title
        var conversation = conversationTitle.ifEmpty { title }
        var message = text.ifEmpty { bigText }

        // Try extracting from MessagingStyle if available
        try {
            val messagingStyleMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (messagingStyleMessages != null && messagingStyleMessages.isNotEmpty()) {
                // Get the latest message
                val lastMsgBundle = messagingStyleMessages.last() as? android.os.Bundle
                if (lastMsgBundle != null) {
                    val mText = lastMsgBundle.getCharSequence("text")?.toString()?.trim() ?: ""
                    val mSender = lastMsgBundle.getCharSequence("sender")?.toString()?.trim() ?: ""
                    
                    if (mText.isNotEmpty()) {
                        message = mText
                    }
                    if (mSender.isNotEmpty()) {
                        sender = mSender
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NotificationParser", "Error parsing messaging style messages", e)
        }

        // Clean up fallback cases where message might be empty or sender is empty
        if (sender.isEmpty()) {
            sender = "Unknown Sender"
        }
        if (message.isEmpty()) {
            // If the message is still empty, try subText or infoText
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim() ?: ""
            val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.trim() ?: ""
            message = subText.ifEmpty { infoText }.ifEmpty { "No Content" }
        }

        val time = sbn.postTime

        return NotificationItem(
            packageName = packageName,
            appName = appName,
            sender = sender,
            conversation = conversation,
            message = message,
            timestamp = time,
            notificationId = notificationId,
            isGroupMessage = isGroup
        )
    }

    private fun shouldIgnore(sbn: StatusBarNotification, notification: Notification, packageName: String): Boolean {
        // A. Missed Calls
        if (notification.category == Notification.CATEGORY_MISSED_CALL) {
            return true
        }
        val lowerPkg = packageName.lowercase()
        if (lowerPkg.contains("telephony") || lowerPkg.contains("phone") || lowerPkg.contains("dialer")) {
            val extras = notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.lowercase() ?: ""
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.lowercase() ?: ""
            if (title.contains("missed") || text.contains("missed") || title.contains("call") || text.contains("call")) {
                return true
            }
        }

        // B. System Notifications
        if (packageName == "android" || packageName == "com.android.systemui") {
            return true
        }
        if (notification.category == Notification.CATEGORY_SYSTEM || notification.category == Notification.CATEGORY_SERVICE) {
            return true
        }

        // C. Battery Notifications
        if (notification.category == "sys" || lowerPkg.contains("battery") || lowerPkg.contains("power")) {
            return true
        }
        val extras = notification.extras
        if (extras != null) {
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.lowercase() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.lowercase() ?: ""
            if (title.contains("battery") || text.contains("battery") || title.contains("charging") || text.contains("low power")) {
                return true
            }
        }

        // D. Downloads
        if (notification.category == Notification.CATEGORY_PROGRESS) {
            return true
        }
        if (lowerPkg.contains("download") || lowerPkg.contains("provider.download") || lowerPkg.contains("providers.downloads")) {
            return true
        }
        if (extras != null) {
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.lowercase() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.lowercase() ?: ""
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.lowercase() ?: ""
            if (title.contains("download") || text.contains("download") || subText.contains("download") ||
                title.contains("downloading") || text.contains("downloading") || subText.contains("downloading") ||
                title.contains("progress") || text.contains("progress")
            ) {
                return true
            }
        }

        return false
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.split('.').lastOrNull()?.replaceFirstChar { it.uppercase() } ?: packageName
        }
    }
}
