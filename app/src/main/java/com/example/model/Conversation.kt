package com.example.model

/**
 * Domain model representing a tracked conversation with a contact on an app.
 */
data class Conversation(
    val id: Long = 0L,
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
)
