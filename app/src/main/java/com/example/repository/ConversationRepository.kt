package com.example.repository

import com.example.model.Conversation
import com.example.model.QueueStatus
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    /**
     * Observes all conversations ordered by last activity time.
     */
    fun getAllConversationsFlow(): Flow<List<Conversation>>

    /**
     * Retrieves all conversations.
     */
    suspend fun getAllConversations(): List<Conversation>

    /**
     * Retrieves a conversation by sender/contact name and package name.
     */
    suspend fun getConversation(senderName: String, packageName: String): Conversation?

    /**
     * Retrieves a conversation by id.
     */
    suspend fun getConversationById(id: Long): Conversation?

    /**
     * Inserts or updates a conversation.
     */
    suspend fun saveConversation(conversation: Conversation): Long

    /**
     * Deletes a conversation.
     */
    suspend fun deleteConversation(conversation: Conversation)

    /**
     * Deletes a conversation by id.
     */
    suspend fun deleteConversationById(id: Long)

    /**
     * Clears all conversations from the database.
     */
    suspend fun clearAllConversations()

    /**
     * Records a newly received message, updates unread count, activity time,
     * and clears the 'repliedToLastMessage' flag.
     */
    suspend fun recordIncomingMessage(
        senderName: String,
        packageName: String,
        message: String
    ): Conversation

    /**
     * Records a sent reply, updates the last reply text, last reply time,
     * sets 'repliedToLastMessage' to true to prevent sending twice, and clears unread count.
     */
    suspend fun recordOutgoingReply(
        senderName: String,
        packageName: String,
        replyText: String
    ): Conversation

    /**
     * Resets the unread count for a conversation.
     */
    suspend fun clearUnreadCount(senderName: String, packageName: String): Conversation?

    /**
     * Updates the current active queue status for the conversation.
     */
    suspend fun updateQueueStatus(
        senderName: String,
        packageName: String,
        queueStatus: QueueStatus?
    ): Conversation?

    /**
     * Locks auto-replies for the conversation.
     */
    suspend fun lockConversation(senderName: String, packageName: String): Conversation?

    /**
     * Unlocks auto-replies for the conversation.
     */
    suspend fun unlockConversation(senderName: String, packageName: String): Conversation?

    /**
     * Mark conversation as timed out (suspending auto-replies).
     */
    suspend fun timeoutConversation(senderName: String, packageName: String): Conversation?

    /**
     * Resume auto-replies for the conversation (setting state back to ACTIVE and unlocking).
     */
    suspend fun resumeConversation(senderName: String, packageName: String): Conversation?

    /**
     * Helper to determine if we should send a reply to a conversation.
     * Evaluates locks, timeouts, and prevents duplicate replies/replying twice.
     * Returns a pair of Boolean (shouldReply) and String (reason why not).
     */
    suspend fun shouldReply(
        senderName: String,
        packageName: String,
        pendingReplyText: String
    ): Pair<Boolean, String>
}
