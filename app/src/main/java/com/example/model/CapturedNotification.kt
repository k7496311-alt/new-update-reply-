package com.example.model

data class CapturedNotification(
    val notificationId: Int,
    val packageName: String,
    val senderName: String,
    val title: String,
    val text: String,
    val postTime: Long
)
