package com.example.repository

import com.example.model.CapturedNotification
import kotlinx.coroutines.flow.StateFlow

interface CapturedNotificationRepository {
    fun addNotification(notification: CapturedNotification)
    fun getCapturedNotifications(): StateFlow<List<CapturedNotification>>
    fun clearNotifications()
}
