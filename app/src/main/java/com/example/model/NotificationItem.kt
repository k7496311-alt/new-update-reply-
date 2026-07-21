package com.example.model

data class NotificationItem(
    val id: Long = 0L,
    val packageName: String,
    val appName: String,
    val sender: String,
    val conversation: String,
    val message: String,
    val timestamp: Long,
    val notificationId: Int,
    val isGroupMessage: Boolean
)
