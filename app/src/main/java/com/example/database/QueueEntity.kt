package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.QueueItem
import com.example.model.QueueStatus

@Entity(tableName = "reply_queue")
data class QueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ruleId: Long,
    val senderName: String,
    val incomingMessage: String,
    val replyText: String,
    val packageName: String,
    val scheduledTime: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val status: QueueStatus,
    val priority: Int = 0,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val errorMessage: String? = null
) {
    fun toDomainModel(): QueueItem {
        return QueueItem(
            id = id,
            ruleId = ruleId,
            senderName = senderName,
            incomingMessage = incomingMessage,
            replyText = replyText,
            packageName = packageName,
            scheduledTime = scheduledTime,
            createdAt = createdAt,
            updatedAt = updatedAt,
            status = status,
            priority = priority,
            retryCount = retryCount,
            maxRetries = maxRetries,
            errorMessage = errorMessage
        )
    }

    companion object {
        fun fromDomainModel(item: QueueItem): QueueEntity {
            return QueueEntity(
                id = item.id,
                ruleId = item.ruleId,
                senderName = item.senderName,
                incomingMessage = item.incomingMessage,
                replyText = item.replyText,
                packageName = item.packageName,
                scheduledTime = item.scheduledTime,
                createdAt = item.createdAt,
                updatedAt = item.updatedAt,
                status = item.status,
                priority = item.priority,
                retryCount = item.retryCount,
                maxRetries = item.maxRetries,
                errorMessage = item.errorMessage
            )
        }
    }
}
