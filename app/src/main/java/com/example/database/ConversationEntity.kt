package com.example.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.Conversation
import com.example.model.ConversationStatus
import com.example.model.QueueStatus

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["senderName", "packageName"], unique = true)]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val senderName: String,
    val packageName: String,
    val lastMessage: String,
    val lastReply: String? = null,
    val lastReplyTime: Long = 0L,
    val unreadCount: Int = 0,
    val queueStatus: QueueStatus? = null,
    val status: ConversationStatus = ConversationStatus.ACTIVE,
    val lastActivityTime: Long = System.currentTimeMillis(),
    val isLocked: Boolean = false,
    val lockTimestamp: Long = 0L,
    val lastIncomingMessage: String = "",
    val repliedToLastMessage: Boolean = false
) {
    fun toDomainModel(): Conversation {
        return Conversation(
            id = id,
            senderName = senderName,
            packageName = packageName,
            lastMessage = lastMessage,
            lastReply = lastReply,
            lastReplyTime = lastReplyTime,
            unreadCount = unreadCount,
            queueStatus = queueStatus,
            status = status,
            lastActivityTime = lastActivityTime,
            isLocked = isLocked,
            lockTimestamp = lockTimestamp,
            lastIncomingMessage = lastIncomingMessage,
            repliedToLastMessage = repliedToLastMessage
        )
    }

    companion object {
        fun fromDomainModel(item: Conversation): ConversationEntity {
            return ConversationEntity(
                id = item.id,
                senderName = item.senderName,
                packageName = item.packageName,
                lastMessage = item.lastMessage,
                lastReply = item.lastReply,
                lastReplyTime = item.lastReplyTime,
                unreadCount = item.unreadCount,
                queueStatus = item.queueStatus,
                status = item.status,
                lastActivityTime = item.lastActivityTime,
                isLocked = item.isLocked,
                lockTimestamp = item.lockTimestamp,
                lastIncomingMessage = item.lastIncomingMessage,
                repliedToLastMessage = item.repliedToLastMessage
            )
        }
    }
}
