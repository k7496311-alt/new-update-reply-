package com.example.data

import com.example.database.ConversationDao
import com.example.database.ConversationEntity
import com.example.model.Conversation
import com.example.model.ConversationStatus
import com.example.model.QueueStatus
import com.example.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConversationRepositoryImpl(
    private val conversationDao: ConversationDao
) : ConversationRepository {

    override fun getAllConversationsFlow(): Flow<List<Conversation>> {
        return conversationDao.getAllConversationsFlow().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getAllConversations(): List<Conversation> {
        return conversationDao.getAllConversations().map { it.toDomainModel() }
    }

    override suspend fun getConversation(senderName: String, packageName: String): Conversation? {
        return conversationDao.getConversation(senderName, packageName)?.toDomainModel()
    }

    override suspend fun getConversationById(id: Long): Conversation? {
        return conversationDao.getConversationById(id)?.toDomainModel()
    }

    override suspend fun saveConversation(conversation: Conversation): Long {
        val entity = ConversationEntity.fromDomainModel(conversation)
        return if (entity.id == 0L) {
            conversationDao.insertConversation(entity)
        } else {
            conversationDao.updateConversation(entity)
            entity.id
        }
    }

    override suspend fun deleteConversation(conversation: Conversation) {
        conversationDao.deleteConversation(ConversationEntity.fromDomainModel(conversation))
    }

    override suspend fun deleteConversationById(id: Long) {
        conversationDao.deleteConversationById(id)
    }

    override suspend fun clearAllConversations() {
        conversationDao.clearAllConversations()
    }

    override suspend fun recordIncomingMessage(
        senderName: String,
        packageName: String,
        message: String
    ): Conversation {
        val existing = conversationDao.getConversation(senderName, packageName)
        val updated = if (existing != null) {
            existing.copy(
                lastMessage = message,
                unreadCount = existing.unreadCount + 1,
                lastActivityTime = System.currentTimeMillis(),
                lastIncomingMessage = message,
                repliedToLastMessage = false
            )
        } else {
            ConversationEntity(
                senderName = senderName,
                packageName = packageName,
                lastMessage = message,
                unreadCount = 1,
                lastActivityTime = System.currentTimeMillis(),
                status = ConversationStatus.ACTIVE,
                isLocked = false,
                lastIncomingMessage = message,
                repliedToLastMessage = false
            )
        }
        val id = if (updated.id == 0L) {
            conversationDao.insertConversation(updated)
        } else {
            conversationDao.updateConversation(updated)
            updated.id
        }
        return updated.copy(id = id).toDomainModel()
    }

    override suspend fun recordOutgoingReply(
        senderName: String,
        packageName: String,
        replyText: String
    ): Conversation {
        val existing = conversationDao.getConversation(senderName, packageName)
        val updated = if (existing != null) {
            existing.copy(
                lastReply = replyText,
                lastReplyTime = System.currentTimeMillis(),
                lastActivityTime = System.currentTimeMillis(),
                unreadCount = 0,
                repliedToLastMessage = true
            )
        } else {
            ConversationEntity(
                senderName = senderName,
                packageName = packageName,
                lastMessage = "",
                lastReply = replyText,
                lastReplyTime = System.currentTimeMillis(),
                lastActivityTime = System.currentTimeMillis(),
                unreadCount = 0,
                status = ConversationStatus.ACTIVE,
                isLocked = false,
                lastIncomingMessage = "",
                repliedToLastMessage = true
            )
        }
        val id = if (updated.id == 0L) {
            conversationDao.insertConversation(updated)
        } else {
            conversationDao.updateConversation(updated)
            updated.id
        }
        return updated.copy(id = id).toDomainModel()
    }

    override suspend fun clearUnreadCount(senderName: String, packageName: String): Conversation? {
        val existing = conversationDao.getConversation(senderName, packageName) ?: return null
        val updated = existing.copy(unreadCount = 0)
        conversationDao.updateConversation(updated)
        return updated.toDomainModel()
    }

    override suspend fun updateQueueStatus(
        senderName: String,
        packageName: String,
        queueStatus: QueueStatus?
    ): Conversation? {
        val existing = conversationDao.getConversation(senderName, packageName) ?: return null
        val updated = existing.copy(queueStatus = queueStatus)
        conversationDao.updateConversation(updated)
        return updated.toDomainModel()
    }

    override suspend fun lockConversation(senderName: String, packageName: String): Conversation? {
        val existing = conversationDao.getConversation(senderName, packageName) ?: return null
        val updated = existing.copy(
            isLocked = true,
            status = ConversationStatus.LOCKED,
            lockTimestamp = System.currentTimeMillis()
        )
        conversationDao.updateConversation(updated)
        return updated.toDomainModel()
    }

    override suspend fun unlockConversation(senderName: String, packageName: String): Conversation? {
        val existing = conversationDao.getConversation(senderName, packageName) ?: return null
        val updated = existing.copy(
            isLocked = false,
            status = ConversationStatus.ACTIVE,
            lockTimestamp = 0L
        )
        conversationDao.updateConversation(updated)
        return updated.toDomainModel()
    }

    override suspend fun timeoutConversation(senderName: String, packageName: String): Conversation? {
        val existing = conversationDao.getConversation(senderName, packageName) ?: return null
        val updated = existing.copy(
            isLocked = true,
            status = ConversationStatus.TIMED_OUT,
            lockTimestamp = System.currentTimeMillis()
        )
        conversationDao.updateConversation(updated)
        return updated.toDomainModel()
    }

    override suspend fun resumeConversation(senderName: String, packageName: String): Conversation? {
        val existing = conversationDao.getConversation(senderName, packageName) ?: return null
        val updated = existing.copy(
            isLocked = false,
            status = ConversationStatus.ACTIVE,
            lockTimestamp = 0L
        )
        conversationDao.updateConversation(updated)
        return updated.toDomainModel()
    }

    override suspend fun shouldReply(
        senderName: String,
        packageName: String,
        pendingReplyText: String
    ): Pair<Boolean, String> {
        val existing = conversationDao.getConversation(senderName, packageName)
            ?: return Pair(true, "First interaction. Eligible to reply.")

        when (existing.status) {
            ConversationStatus.LOCKED -> return Pair(false, "Conversation is explicitly locked.")
            ConversationStatus.TIMED_OUT -> return Pair(false, "Conversation is timed out.")
            ConversationStatus.PAUSED -> return Pair(false, "Conversation auto-reply is paused.")
            ConversationStatus.ARCHIVED -> return Pair(false, "Conversation is archived.")
            ConversationStatus.ACTIVE -> { /* proceed */ }
        }

        if (existing.isLocked) {
            return Pair(false, "Conversation is locked.")
        }

        if (existing.repliedToLastMessage) {
            return Pair(false, "Already replied to the last message once. Prevent replying twice.")
        }

        val lastReply = existing.lastReply
        if (lastReply != null && lastReply.equals(pendingReplyText, ignoreCase = true)) {
            return Pair(false, "Duplicate reply text detected. Prevent duplicate reply.")
        }

        return Pair(true, "Eligible for auto-reply.")
    }
}
