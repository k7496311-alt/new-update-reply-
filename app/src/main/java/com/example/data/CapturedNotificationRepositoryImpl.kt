package com.example.data

import com.example.model.CapturedNotification
import com.example.repository.CapturedNotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

class CapturedNotificationRepositoryImpl private constructor() : CapturedNotificationRepository {

    private val notificationsList = CopyOnWriteArrayList<CapturedNotification>()
    private val _notificationsFlow = MutableStateFlow<List<CapturedNotification>>(emptyList())

    override fun addNotification(notification: CapturedNotification) {
        notificationsList.add(0, notification)
        _notificationsFlow.value = notificationsList.toList()
    }

    override fun getCapturedNotifications(): StateFlow<List<CapturedNotification>> {
        return _notificationsFlow.asStateFlow()
    }

    override fun clearNotifications() {
        notificationsList.clear()
        _notificationsFlow.value = emptyList()
    }

    companion object {
        @Volatile
        private var INSTANCE: CapturedNotificationRepositoryImpl? = null

        fun getInstance(): CapturedNotificationRepositoryImpl {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CapturedNotificationRepositoryImpl().also { INSTANCE = it }
            }
        }
    }
}
