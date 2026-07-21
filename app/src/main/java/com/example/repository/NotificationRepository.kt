package com.example.repository

import com.example.model.NotificationItem
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun getAllNotifications(): Flow<List<NotificationItem>>
    suspend fun saveNotification(notification: NotificationItem): Long
    suspend fun deleteNotificationById(id: Long)
    suspend fun clearNotifications()
}
