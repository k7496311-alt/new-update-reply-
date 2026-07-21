package com.example.model

import android.app.PendingIntent

/**
 * Data model representing an incoming notification, specifically used for automation and scanning.
 */
data class NotificationData(
    val senderName: String,
    val packageName: String,
    val messageText: String,
    val timestamp: Long,
    val appName: String? = null,
    val isGroupMessage: Boolean = false,
    val pendingIntent: PendingIntent? = null
)
