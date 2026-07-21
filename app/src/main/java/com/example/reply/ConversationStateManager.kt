package com.example.reply

import com.example.model.Conversation
import com.example.model.ConversationStatus
import com.example.model.QueueStatus
import com.example.repository.ConversationRepository

/**
 * Manages conversation states and coordinates auto-reply gating,
 * lock management, and timeout automation.
 */
class ConversationStateManager(
    private val repository: ConversationRepository
) {

    /**
     * Records a new incoming message from a sender, resetting the replied state
     * so that a new reply can be generated.
     */
    suspend fun onIncomingMessage(
        senderName: String,
        packageName: String,
        message: String
    ): Conversation {
        return repository.recordIncomingMessage(senderName, packageName, message)
    }

    /**
     * Records that an auto-reply has been sent, locking the conversation from
     * subsequent automated replies until a new message arrives from the contact.
     */
    suspend fun onOutgoingReply(
        senderName: String,
        packageName: String,
        replyText: String
    ): Conversation {
        return repository.recordOutgoingReply(senderName, packageName, replyText)
    }

    /**
     * Evaluates if we are allowed to send a reply.
     * Prevents duplicate replies (exact same text consecutively) and replying twice (multiple replies
     * to the same incoming message).
     */
    suspend fun shouldReply(
        senderName: String,
        packageName: String,
        replyText: String
    ): Pair<Boolean, String> {
        return repository.shouldReply(senderName, packageName, replyText)
    }

    /**
     * Explicitly lock a conversation.
     */
    suspend fun lock(senderName: String, packageName: String): Conversation? {
        return repository.lockConversation(senderName, packageName)
    }

    /**
     * Explicitly unlock a conversation.
     */
    suspend fun unlock(senderName: String, packageName: String): Conversation? {
        return repository.unlockConversation(senderName, packageName)
    }

    /**
     * Transition conversation to TIMED_OUT status due to inactivity.
     */
    suspend fun timeout(senderName: String, packageName: String): Conversation? {
        return repository.timeoutConversation(senderName, packageName)
    }

    /**
     * Resume conversation to ACTIVE status.
     */
    suspend fun resume(senderName: String, packageName: String): Conversation? {
        return repository.resumeConversation(senderName, packageName)
    }

    /**
     * Resets the unread counter for a contact.
     */
    suspend fun clearUnread(senderName: String, packageName: String): Conversation? {
        return repository.clearUnreadCount(senderName, packageName)
    }

    /**
     * Updates active dispatch queue status.
     */
    suspend fun updateQueueStatus(
        senderName: String,
        packageName: String,
        status: QueueStatus?
    ): Conversation? {
        return repository.updateQueueStatus(senderName, packageName, status)
    }

    /**
     * Scans all conversations and automatically locks/timeouts conversations
     * that have been inactive for more than the specified duration.
     */
    suspend fun checkInactivityTimeouts(timeoutMillis: Long): List<Conversation> {
        val now = System.currentTimeMillis()
        val conversations = repository.getAllConversations()
        val updatedList = mutableListOf<Conversation>()

        for (conv in conversations) {
            // Only timeout active conversations
            if (conv.status == ConversationStatus.ACTIVE) {
                val inactiveDuration = now - conv.lastActivityTime
                if (inactiveDuration >= timeoutMillis) {
                    val updated = repository.timeoutConversation(conv.senderName, conv.packageName)
                    if (updated != null) {
                        updatedList.add(updated)
                    }
                }
            }
        }
        return updatedList
    }
}
