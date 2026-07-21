package com.example

import com.example.model.Conversation
import com.example.model.ConversationStatus
import com.example.model.QueueStatus
import com.example.reply.ConversationStateManager
import com.example.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ConversationStateManagerTest {

    private class FakeConversationRepository : ConversationRepository {
        val dbMap = mutableMapOf<Pair<String, String>, Conversation>()
        private val _conversationsFlow = MutableStateFlow<List<Conversation>>(emptyList())

        private fun updateFlow() {
            _conversationsFlow.value = dbMap.values.toList()
        }

        override fun getAllConversationsFlow(): Flow<List<Conversation>> {
            return _conversationsFlow.asStateFlow()
        }

        override suspend fun getAllConversations(): List<Conversation> {
            return dbMap.values.toList()
        }

        override suspend fun getConversation(senderName: String, packageName: String): Conversation? {
            return dbMap[Pair(senderName, packageName)]
        }

        override suspend fun getConversationById(id: Long): Conversation? {
            return dbMap.values.firstOrNull { it.id == id }
        }

        override suspend fun saveConversation(conversation: Conversation): Long {
            val finalConv = if (conversation.id == 0L) {
                conversation.copy(id = (dbMap.size + 1).toLong())
            } else {
                conversation
            }
            dbMap[Pair(finalConv.senderName, finalConv.packageName)] = finalConv
            updateFlow()
            return finalConv.id
        }

        override suspend fun deleteConversation(conversation: Conversation) {
            dbMap.remove(Pair(conversation.senderName, conversation.packageName))
            updateFlow()
        }

        override suspend fun deleteConversationById(id: Long) {
            val match = dbMap.entries.firstOrNull { it.value.id == id }
            if (match != null) {
                dbMap.remove(match.key)
                updateFlow()
            }
        }

        override suspend fun clearAllConversations() {
            dbMap.clear()
            updateFlow()
        }

        override suspend fun recordIncomingMessage(
            senderName: String,
            packageName: String,
            message: String
        ): Conversation {
            val existing = getConversation(senderName, packageName)
            val updated = if (existing != null) {
                existing.copy(
                    lastMessage = message,
                    unreadCount = existing.unreadCount + 1,
                    lastActivityTime = System.currentTimeMillis(),
                    lastIncomingMessage = message,
                    repliedToLastMessage = false
                )
            } else {
                Conversation(
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
            saveConversation(updated)
            return getConversation(senderName, packageName)!!
        }

        override suspend fun recordOutgoingReply(
            senderName: String,
            packageName: String,
            replyText: String
        ): Conversation {
            val existing = getConversation(senderName, packageName)
            val updated = if (existing != null) {
                existing.copy(
                    lastReply = replyText,
                    lastReplyTime = System.currentTimeMillis(),
                    lastActivityTime = System.currentTimeMillis(),
                    unreadCount = 0,
                    repliedToLastMessage = true
                )
            } else {
                Conversation(
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
            saveConversation(updated)
            return getConversation(senderName, packageName)!!
        }

        override suspend fun clearUnreadCount(senderName: String, packageName: String): Conversation? {
            val existing = getConversation(senderName, packageName) ?: return null
            val updated = existing.copy(unreadCount = 0)
            saveConversation(updated)
            return updated
        }

        override suspend fun updateQueueStatus(
            senderName: String,
            packageName: String,
            queueStatus: QueueStatus?
        ): Conversation? {
            val existing = getConversation(senderName, packageName) ?: return null
            val updated = existing.copy(queueStatus = queueStatus)
            saveConversation(updated)
            return updated
        }

        override suspend fun lockConversation(senderName: String, packageName: String): Conversation? {
            val existing = getConversation(senderName, packageName) ?: return null
            val updated = existing.copy(
                isLocked = true,
                status = ConversationStatus.LOCKED,
                lockTimestamp = System.currentTimeMillis()
            )
            saveConversation(updated)
            return updated
        }

        override suspend fun unlockConversation(senderName: String, packageName: String): Conversation? {
            val existing = getConversation(senderName, packageName) ?: return null
            val updated = existing.copy(
                isLocked = false,
                status = ConversationStatus.ACTIVE,
                lockTimestamp = 0L
            )
            saveConversation(updated)
            return updated
        }

        override suspend fun timeoutConversation(senderName: String, packageName: String): Conversation? {
            val existing = getConversation(senderName, packageName) ?: return null
            val updated = existing.copy(
                isLocked = true,
                status = ConversationStatus.TIMED_OUT,
                lockTimestamp = System.currentTimeMillis()
            )
            saveConversation(updated)
            return updated
        }

        override suspend fun resumeConversation(senderName: String, packageName: String): Conversation? {
            val existing = getConversation(senderName, packageName) ?: return null
            val updated = existing.copy(
                isLocked = false,
                status = ConversationStatus.ACTIVE,
                lockTimestamp = 0L
            )
            saveConversation(updated)
            return updated
        }

        override suspend fun shouldReply(
            senderName: String,
            packageName: String,
            pendingReplyText: String
        ): Pair<Boolean, String> {
            val existing = getConversation(senderName, packageName)
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

    private val fakeRepository = FakeConversationRepository()
    private val manager = ConversationStateManager(fakeRepository)

    @Test
    fun testTrackConversationAndProperties() = runBlocking {
        // Record incoming message from Alice
        val conv = manager.onIncomingMessage("Alice", "com.whatsapp", "Hello!")

        assertEquals("Alice", conv.senderName)
        assertEquals("com.whatsapp", conv.packageName)
        assertEquals("Hello!", conv.lastMessage)
        assertEquals(1, conv.unreadCount)
        assertEquals(ConversationStatus.ACTIVE, conv.status)
        assertFalse(conv.isLocked)
        assertFalse(conv.repliedToLastMessage)

        // Record a second incoming message
        val conv2 = manager.onIncomingMessage("Alice", "com.whatsapp", "Are you there?")
        assertEquals("Are you there?", conv2.lastMessage)
        assertEquals(2, conv2.unreadCount)
    }

    @Test
    fun testPreventReplyingTwiceAndDuplicateReply() = runBlocking {
        val contact = "Bob"
        val pkg = "com.whatsapp"

        // 1. First incoming message
        manager.onIncomingMessage(contact, pkg, "Hi")

        // 2. Check: We should be allowed to reply
        val (allow1, reason1) = manager.shouldReply(contact, pkg, "Hello Bob!")
        assertTrue(allow1)

        // 3. Record outgoing reply
        manager.onOutgoingReply(contact, pkg, "Hello Bob!")

        // 4. Check: Try to reply again to the same incoming message -> Should block "replying twice"
        val (allow2, reason2) = manager.shouldReply(contact, pkg, "Another message")
        assertFalse(allow2)
        assertTrue(reason2.contains("Already replied"))

        // 5. Check: Try to reply with EXACT SAME text (duplicate check) -> Should block
        // Even if we reset the replied state manually for testing
        fakeRepository.saveConversation(fakeRepository.getConversation(contact, pkg)!!.copy(repliedToLastMessage = false))
        val (allow3, reason3) = manager.shouldReply(contact, pkg, "Hello Bob!")
        assertFalse(allow3)
        assertTrue(reason3.contains("Duplicate reply text"))

        // 6. Incoming new message -> should clear "repliedToLastMessage"
        manager.onIncomingMessage(contact, pkg, "What's up?")
        val (allow4, reason4) = manager.shouldReply(contact, pkg, "Not much")
        assertTrue(allow4)
    }

    @Test
    fun testConversationLockUnlock() = runBlocking {
        val contact = "Charlie"
        val pkg = "com.signal"

        manager.onIncomingMessage(contact, pkg, "Yo")

        // Lock
        val lockedConv = manager.lock(contact, pkg)
        assertNotNull(lockedConv)
        assertTrue(lockedConv!!.isLocked)
        assertEquals(ConversationStatus.LOCKED, lockedConv.status)

        // Verify shouldReply is blocked
        val (allow, reason) = manager.shouldReply(contact, pkg, "Auto reply")
        assertFalse(allow)
        assertTrue(reason.contains("locked"))

        // Unlock
        val unlockedConv = manager.unlock(contact, pkg)
        assertNotNull(unlockedConv)
        assertFalse(unlockedConv!!.isLocked)
        assertEquals(ConversationStatus.ACTIVE, unlockedConv.status)

        // Verify shouldReply is allowed again
        val (allow2, _) = manager.shouldReply(contact, pkg, "Auto reply")
        assertTrue(allow2)
    }

    @Test
    fun testInactivityTimeoutAndResume() = runBlocking {
        val contact = "Daniel"
        val pkg = "com.telegram"

        // Create active conversation
        manager.onIncomingMessage(contact, pkg, "Hey")

        // Set lastActivityTime to 2 hours ago
        val now = System.currentTimeMillis()
        val twoHoursAgo = now - (2 * 60 * 60 * 1000)
        val original = fakeRepository.getConversation(contact, pkg)!!
        val updated = original.copy(lastActivityTime = twoHoursAgo)
        fakeRepository.saveConversation(updated)

        // Run inactivity timeout sweep with 1 hour threshold
        val timedOutList = manager.checkInactivityTimeouts(1 * 60 * 60 * 1000L)
        assertEquals(1, timedOutList.size)
        assertEquals(ConversationStatus.TIMED_OUT, timedOutList.first().status)
        assertTrue(timedOutList.first().isLocked)

        // Verify shouldReply is blocked by timeout
        val (allow, reason) = manager.shouldReply(contact, pkg, "Auto")
        assertFalse(allow)
        assertTrue(reason.contains("timed out"))

        // Resume conversation
        val resumed = manager.resume(contact, pkg)
        assertNotNull(resumed)
        assertFalse(resumed!!.isLocked)
        assertEquals(ConversationStatus.ACTIVE, resumed.status)
    }
}
