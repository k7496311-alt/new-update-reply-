package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.NotificationItem

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String,
    val appName: String,
    val sender: String,
    val conversation: String,
    val message: String,
    val timestamp: Long,
    val notificationId: Int,
    val isGroupMessage: Boolean
) {
    fun toDomainModel(): NotificationItem {
        return NotificationItem(
            id = id,
            packageName = packageName,
            appName = appName,
            sender = sender,
            conversation = conversation,
            message = message,
            timestamp = timestamp,
            notificationId = notificationId,
            isGroupMessage = isGroupMessage
        )
    }

    companion object {
        fun fromDomainModel(item: NotificationItem): NotificationEntity {
            return NotificationEntity(
                id = item.id,
                packageName = item.packageName,
                appName = item.appName,
                sender = item.sender,
                conversation = item.conversation,
                message = item.message,
                timestamp = item.timestamp,
                notificationId = item.notificationId,
                isGroupMessage = item.isGroupMessage
            )
        }
    }
}
