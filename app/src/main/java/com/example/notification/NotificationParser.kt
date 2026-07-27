package com.example.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.model.CapturedNotification
import com.example.model.NotificationItem

class NotificationParser(private val context: Context) {

    private val allowedPackages = setOf(
        "com.imo.android.imoimbeta",
        "com.imo.android.imoim",
        "com.imo.android.imoimlite"
    )

    /**
     * Parses incoming notification and returns CapturedNotification if it passes filtering criteria:
     * - Target package matching (e.g. com.imo.android.imoimbeta)
     * - Not a group summary notification
     * - Not a silent notification
     */
    fun parseCapturedNotification(sbn: StatusBarNotification?): CapturedNotification? {
        if (sbn == null) return null

        val packageName = sbn.packageName ?: return null

        // 1. Monitor only imo packages
        if (!isImoPackage(packageName)) {
            return null
        }

        val notification = sbn.notification ?: return null

        // 2. Ignore group summary notifications
        if (isGroupSummary(sbn, notification)) {
            return null
        }

        // 3. Ignore silent notifications
        if (isSilent(sbn, notification)) {
            return null
        }

        val extras = notification.extras ?: return null

        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val rawText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim() ?: ""

        var senderName = rawTitle
        var notificationText = rawText.ifEmpty { bigText }

        // Dynamic extraction from MessagingStyle extras
        try {
            val messagingStyleMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (messagingStyleMessages != null && messagingStyleMessages.isNotEmpty()) {
                val lastMsgBundle = messagingStyleMessages.last() as? android.os.Bundle
                if (lastMsgBundle != null) {
                    val mText = lastMsgBundle.getCharSequence("text")?.toString()?.trim() ?: ""
                    val mSender = lastMsgBundle.getCharSequence("sender")?.toString()?.trim() ?: ""

                    if (mText.isNotEmpty()) {
                        notificationText = mText
                    }
                    if (mSender.isNotEmpty()) {
                        senderName = mSender
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing MessagingStyle extras", e)
        }

        if (senderName.isEmpty()) {
            senderName = "Unknown Sender"
        }

        return CapturedNotification(
            notificationId = sbn.id,
            packageName = packageName,
            senderName = senderName,
            title = rawTitle.ifEmpty { senderName },
            text = notificationText,
            postTime = sbn.postTime
        )
    }

    /**
     * Parses a StatusBarNotification into a NotificationItem for DB storage.
     */
    fun parse(sbn: StatusBarNotification?): NotificationItem? {
        val captured = parseCapturedNotification(sbn) ?: return null

        return NotificationItem(
            packageName = captured.packageName,
            appName = getAppName(captured.packageName),
            sender = captured.senderName,
            conversation = captured.title,
            message = captured.text,
            timestamp = captured.postTime,
            notificationId = captured.notificationId,
            isGroupMessage = false
        )
    }

    private fun isImoPackage(packageName: String): Boolean {
        if (allowedPackages.contains(packageName)) return true
        return packageName.startsWith("com.imo.android.imoim")
    }

    private fun isGroupSummary(sbn: StatusBarNotification, notification: Notification): Boolean {
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return true
        }
        val extras = notification.extras ?: return false
        return extras.getBoolean("android.isGroupSummary", false)
    }

    private fun isSilent(sbn: StatusBarNotification, notification: Notification): Boolean {
        if (notification.priority <= Notification.PRIORITY_LOW) {
            return true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = notification.channelId ?: return false
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null) {
                val channel = notificationManager.getNotificationChannel(channelId)
                if (channel != null && channel.importance <= NotificationManager.IMPORTANCE_LOW) {
                    return true
                }
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

    companion object {
        private const val TAG = "NotificationParser"
    }
}
