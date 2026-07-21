package com.example

import com.example.history.HistoryManager
import com.example.model.*
import com.example.queue.QueueEngine
import com.example.queue.SmartQueueProcessor
import com.example.reply.FinalReply
import com.example.reply.ReplyGenerationStatus
import com.example.reply.ReplyGenerator
import com.example.reply.ReplyGeneratorRepository
import com.example.repository.ConversationRepository
import com.example.repository.HistoryRepository
import com.example.repository.QueueRepository
import com.example.repository.RuleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartQueueProcessorTest {

    private lateinit var fakeQueueRepository: FakeQueueRepository
    private lateinit var queueEngine: QueueEngine
    private lateinit var fakeReplyRepository: FakeReplyRepository
    private lateinit var replyGenerator: ReplyGenerator
    private lateinit var fakeRuleRepository: FakeRuleRepository
    private lateinit var fakeConversationRepository: FakeConversationRepository
    private lateinit var fakeHistoryRepository: FakeHistoryRepository
    private lateinit var historyManager: HistoryManager
    private lateinit var processor: SmartQueueProcessor

    @Before
    fun setUp() {
        fakeQueueRepository = FakeQueueRepository()
        queueEngine = QueueEngine(fakeQueueRepository, Dispatchers.Unconfined)
        fakeReplyRepository = FakeReplyRepository()
        replyGenerator = ReplyGenerator(fakeReplyRepository)
        fakeRuleRepository = FakeRuleRepository()
        fakeConversationRepository = FakeConversationRepository()
        fakeHistoryRepository = FakeHistoryRepository()
        historyManager = HistoryManager(fakeHistoryRepository)

        processor = SmartQueueProcessor(
            queueRepository = fakeQueueRepository,
            queueEngine = queueEngine,
            replyGenerator = replyGenerator,
            ruleRepository = fakeRuleRepository,
            conversationRepository = fakeConversationRepository,
            historyManager = historyManager,
            dispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun testProcessNextItem_Success() = runBlocking {
        // 1. Arrange
        val now = System.currentTimeMillis()
        val rule = AutoReplyRule(
            id = 100L,
            name = "Welcome Rule",
            keyword = "hello",
            matchType = MatchType.EXACT,
            replyText = "Hi there [Name]!"
        )
        fakeRuleRepository.saveRule(rule)

        val queueItem = QueueItem(
            id = 1L,
            ruleId = 100L,
            senderName = "Alice",
            incomingMessage = "hello",
            replyText = "Hi there [Name]!",
            packageName = "com.whatsapp",
            scheduledTime = now - 1000, // Past, eligible to process immediately
            createdAt = now
        )
        fakeQueueRepository.saveQueueItem(queueItem)

        // 2. Act
        processor.processNextItem()

        // 3. Assert
        val updatedItem = fakeQueueRepository.getQueueItemById(1L)
        assertNotNull(updatedItem)
        assertEquals(QueueStatus.SENT, updatedItem?.status)

        val updatedConv = fakeConversationRepository.getConversation("Alice", "com.whatsapp")
        assertNotNull(updatedConv)
        assertEquals(QueueStatus.SENT, updatedConv?.queueStatus)
        assertTrue(updatedConv?.repliedToLastMessage == true)
        assertEquals("Hi there Alice!", updatedConv?.lastReply)

        val histories = fakeHistoryRepository.getAllHistoryDirect()
        assertEquals(1, histories.size)
        assertEquals("Hi there Alice!", histories[0].repliedMessage)
        assertTrue(histories[0].isSuccessfullySent)
    }

    @Test
    fun testProcessNextItem_Expired() = runBlocking {
        // 1. Arrange
        val now = System.currentTimeMillis()
        val queueItem = QueueItem(
            id = 2L,
            ruleId = 100L,
            senderName = "Bob",
            incomingMessage = "hello",
            replyText = "Expired?",
            packageName = "com.whatsapp",
            scheduledTime = now - 1000,
            createdAt = now - (2 * 3600000L) // 2 hours old (Threshold is 1 hour)
        )
        fakeQueueRepository.saveQueueItem(queueItem)

        // 2. Act
        processor.processNextItem()

        // 3. Assert
        val updatedItem = fakeQueueRepository.getQueueItemById(2L)
        assertEquals(QueueStatus.EXPIRED, updatedItem?.status)
    }

    @Test
    fun testProcessNextItem_SkippedByConversationRepository() = runBlocking {
        // Arrange
        val now = System.currentTimeMillis()
        val queueItem = QueueItem(
            id = 3L,
            ruleId = 100L,
            senderName = "Charlie",
            incomingMessage = "hello",
            replyText = "No reply",
            packageName = "com.whatsapp",
            scheduledTime = now - 1000,
            createdAt = now
        )
        fakeQueueRepository.saveQueueItem(queueItem)
        // Mark conversation as ineligible
        fakeConversationRepository.shouldReplyResult = Pair(false, "Already replied to the last message once. Prevent replying twice.")

        // Act
        processor.processNextItem()

        // Assert
        val updatedItem = fakeQueueRepository.getQueueItemById(3L)
        assertEquals(QueueStatus.SKIPPED, updatedItem?.status)
        assertEquals("Already replied to the last message once. Prevent replying twice.", updatedItem?.errorMessage)
    }

    @Test
    fun testProcessNextItem_CooldownTransition() = runBlocking {
        // Arrange
        val now = System.currentTimeMillis()
        val rule = AutoReplyRule(
            id = 101L,
            name = "Cooldown Rule",
            keyword = "cooldown",
            matchType = MatchType.EXACT,
            replyText = "Under cooldown",
            cooldownMillis = 5000L
        )
        fakeRuleRepository.saveRule(rule)
        // Mock a previous reply timestamp within the cooldown window
        fakeReplyRepository.saveLastReplyTimestamp(101L, now - 1000L)

        val queueItem = QueueItem(
            id = 4L,
            ruleId = 101L,
            senderName = "Daniel",
            incomingMessage = "cooldown",
            replyText = "Under cooldown",
            packageName = "com.telegram",
            scheduledTime = now - 1000,
            createdAt = now
        )
        fakeQueueRepository.saveQueueItem(queueItem)

        // Act
        processor.processNextItem()

        // Assert
        val updatedItem = fakeQueueRepository.getQueueItemById(4L)
        assertEquals(QueueStatus.COOLDOWN, updatedItem?.status)
        assertTrue(updatedItem?.errorMessage?.contains("cooldown") == true)
    }

    // FAKES IMPLEMENTATION
    class FakeQueueRepository : QueueRepository {
        private val items = mutableListOf<QueueItem>()
        private var nextId = 1L

        override fun getAllQueueItems(): Flow<List<QueueItem>> = flow {
            emit(items)
        }

        override suspend fun getQueueItemsByStatus(status: QueueStatus): List<QueueItem> {
            return items.filter { it.status == status }
        }

        override suspend fun getQueueItemsByStatuses(statuses: List<QueueStatus>): List<QueueItem> {
            return items.filter { it.status in statuses }
                .sortedWith(compareByDescending<QueueItem> { it.priority }.thenBy { it.scheduledTime })
        }

        override suspend fun getActiveQueueCount(): Int {
            return items.count { it.status in listOf(QueueStatus.INCOMING, QueueStatus.PENDING, QueueStatus.PROCESSING, QueueStatus.RETRY) }
        }

        override suspend fun findDuplicate(packageName: String, senderName: String, incomingMessage: String): QueueItem? {
            return items.firstOrNull {
                it.packageName == packageName &&
                it.senderName == senderName &&
                it.incomingMessage == incomingMessage &&
                it.status in listOf(QueueStatus.INCOMING, QueueStatus.PENDING, QueueStatus.PROCESSING, QueueStatus.RETRY)
            }
        }

        override suspend fun getQueueItemById(id: Long): QueueItem? {
            return items.firstOrNull { it.id == id }
        }

        override suspend fun saveQueueItem(item: QueueItem): Long {
            if (item.id == 0L) {
                val saved = item.copy(id = nextId++)
                items.add(saved)
                return saved.id
            } else {
                val index = items.indexOfFirst { it.id == item.id }
                if (index != -1) {
                    items[index] = item
                } else {
                    items.add(item)
                }
                return item.id
            }
        }

        override suspend fun deleteQueueItem(item: QueueItem) {
            items.removeIf { it.id == item.id }
        }

        override suspend fun deleteQueueItemById(id: Long) {
            items.removeIf { it.id == id }
        }

        override suspend fun clearQueue() {
            items.clear()
        }
    }

    class FakeReplyRepository : ReplyGeneratorRepository {
        private var defaultReplySetting: String? = null
        private val lastReplyTimestamps = mutableMapOf<Long, Long>()
        private val globalReplyCounts = mutableMapOf<Long, Int>()
        private val replyCountsSince = mutableMapOf<Pair<Long, Long>, Int>()
        private val sequentialIndices = mutableMapOf<Long, Int>()

        override suspend fun getDefaultReplySetting(): String? = defaultReplySetting
        override suspend fun saveDefaultReplySetting(reply: String) {
            defaultReplySetting = reply
        }
        override suspend fun getLastReplyTimestamp(ruleId: Long): Long? = lastReplyTimestamps[ruleId]
        override suspend fun getGlobalReplyCount(ruleId: Long): Int = globalReplyCounts[ruleId] ?: 0
        override suspend fun getReplyCountSince(ruleId: Long, timestamp: Long): Int = replyCountsSince[Pair(ruleId, timestamp)] ?: 0
        override suspend fun getSequentialIndex(ruleId: Long): Int = sequentialIndices[ruleId] ?: 0
        override suspend fun saveSequentialIndex(ruleId: Long, index: Int) {
            sequentialIndices[ruleId] = index
        }

        fun saveLastReplyTimestamp(ruleId: Long, ts: Long) {
            lastReplyTimestamps[ruleId] = ts
        }
    }

    class FakeRuleRepository : RuleRepository {
        private val rules = mutableMapOf<Long, AutoReplyRule>()

        override fun getAllRules(): Flow<List<AutoReplyRule>> = flow {
            emit(rules.values.toList())
        }

        override suspend fun getActiveRules(): List<AutoReplyRule> {
            return rules.values.toList()
        }

        override suspend fun getRuleById(id: Long): AutoReplyRule? {
            return rules[id]
        }

        override suspend fun saveRule(rule: AutoReplyRule): Long {
            rules[rule.id] = rule
            return rule.id
        }

        override suspend fun deleteRule(rule: AutoReplyRule) {
            rules.remove(rule.id)
        }
    }

    class FakeConversationRepository : ConversationRepository {
        private val conversations = mutableMapOf<Pair<String, String>, Conversation>()
        var shouldReplyResult: Pair<Boolean, String> = Pair(true, "Eligible to reply")

        override fun getAllConversationsFlow(): Flow<List<Conversation>> = flow {
            emit(conversations.values.toList())
        }

        override suspend fun getAllConversations(): List<Conversation> = conversations.values.toList()

        override suspend fun getConversation(senderName: String, packageName: String): Conversation? {
            return conversations[Pair(senderName, packageName)]
        }

        override suspend fun getConversationById(id: Long): Conversation? {
            return conversations.values.firstOrNull { it.id == id }
        }

        override suspend fun saveConversation(conversation: Conversation): Long {
            conversations[Pair(conversation.senderName, conversation.packageName)] = conversation
            return conversation.id
        }

        override suspend fun deleteConversation(conversation: Conversation) {
            conversations.remove(Pair(conversation.senderName, conversation.packageName))
        }

        override suspend fun deleteConversationById(id: Long) {
            val conv = conversations.values.firstOrNull { it.id == id }
            if (conv != null) {
                conversations.remove(Pair(conv.senderName, conv.packageName))
            }
        }

        override suspend fun clearAllConversations() {
            conversations.clear()
        }

        override suspend fun recordIncomingMessage(senderName: String, packageName: String, message: String): Conversation {
            val existing = conversations[Pair(senderName, packageName)] ?: Conversation(
                id = 1L,
                senderName = senderName,
                packageName = packageName,
                lastMessage = message
            )
            val updated = existing.copy(lastMessage = message, repliedToLastMessage = false)
            conversations[Pair(senderName, packageName)] = updated
            return updated
        }

        override suspend fun recordOutgoingReply(senderName: String, packageName: String, replyText: String): Conversation {
            val existing = conversations[Pair(senderName, packageName)] ?: Conversation(
                id = 1L,
                senderName = senderName,
                packageName = packageName,
                lastMessage = ""
            )
            val updated = existing.copy(lastReply = replyText, lastReplyTime = System.currentTimeMillis(), repliedToLastMessage = true)
            conversations[Pair(senderName, packageName)] = updated
            return updated
        }

        override suspend fun clearUnreadCount(senderName: String, packageName: String): Conversation? {
            val existing = conversations[Pair(senderName, packageName)] ?: return null
            val updated = existing.copy(unreadCount = 0)
            conversations[Pair(senderName, packageName)] = updated
            return updated
        }

        override suspend fun updateQueueStatus(senderName: String, packageName: String, queueStatus: QueueStatus?): Conversation? {
            val existing = conversations[Pair(senderName, packageName)] ?: Conversation(
                id = 1L,
                senderName = senderName,
                packageName = packageName,
                lastMessage = ""
            )
            val updated = existing.copy(queueStatus = queueStatus)
            conversations[Pair(senderName, packageName)] = updated
            return updated
        }

        override suspend fun lockConversation(senderName: String, packageName: String): Conversation? = null
        override suspend fun unlockConversation(senderName: String, packageName: String): Conversation? = null
        override suspend fun timeoutConversation(senderName: String, packageName: String): Conversation? = null
        override suspend fun resumeConversation(senderName: String, packageName: String): Conversation? = null

        override suspend fun shouldReply(senderName: String, packageName: String, pendingReplyText: String): Pair<Boolean, String> {
            return shouldReplyResult
        }
    }

    class FakeHistoryRepository : HistoryRepository {
        private val historyList = mutableListOf<ReplyHistory>()

        override fun getAllHistory(): Flow<List<ReplyHistory>> = flow {
            emit(historyList)
        }

        override suspend fun saveHistory(history: ReplyHistory): Long {
            historyList.add(history)
            return historyList.size.toLong()
        }

        override suspend fun deleteHistory(history: ReplyHistory) {
            historyList.removeIf { it.id == history.id }
        }

        override suspend fun clearHistory() {
            historyList.clear()
        }

        override suspend fun getReplyCountForRule(ruleId: Long): Int {
            return historyList.count { it.ruleId == ruleId }
        }

        override suspend fun getReplyCountForRuleSince(ruleId: Long, sinceTimestamp: Long): Int {
            return historyList.count { it.ruleId == ruleId && it.timestamp >= sinceTimestamp }
        }

        override suspend fun getLastReplyTimestampForRule(ruleId: Long): Long? {
            return historyList.filter { it.ruleId == ruleId }.maxOfOrNull { it.timestamp }
        }

        fun getAllHistoryDirect(): List<ReplyHistory> = historyList
    }
}
