package com.example.data

import com.example.database.NotificationDao
import com.example.database.NotificationEntity
import com.example.model.NotificationItem
import com.example.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NotificationRepositoryImpl(
    private val notificationDao: NotificationDao
) : NotificationRepository {

    override fun getAllNotifications(): Flow<List<NotificationItem>> {
        return notificationDao.getAllNotificationsFlow().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun saveNotification(notification: NotificationItem): Long {
        val entity = NotificationEntity.fromDomainModel(notification)
        return notificationDao.insertNotification(entity)
    }

    override suspend fun deleteNotificationById(id: Long) {
        notificationDao.deleteNotificationById(id)
    }

    override suspend fun clearNotifications() {
        notificationDao.clearNotifications()
    }
}
