package com.example

import com.example.model.QueueItem
import com.example.model.QueueStatus
import com.example.queue.QueueEngine
import com.example.repository.QueueRepository
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
class QueueEngineTest {

    private lateinit var fakeRepository: FakeQueueRepository
    private lateinit var queueEngine: QueueEngine

    @Before
    fun setUp() {
        fakeRepository = FakeQueueRepository()
        queueEngine = QueueEngine(fakeRepository)
    }

    @Test
    fun testEnqueueSuccess() = runBlocking {
        val result = queueEngine.enqueue(
            ruleId = 42L,
            senderName = "John Doe",
            incomingMessage = "Hello",
            replyText = "Hi there",
            packageName = "com.whatsapp",
            priority = 1,
            delayMillis = 0L
        )

        assertTrue(result.isSuccess)
        val id = result.getOrThrow()
        assertEquals(1L, id)

        val savedItem = fakeRepository.getQueueItemById(id)
        assertNotNull(savedItem)
        // Auto-transition from INCOMING to PENDING (Waiting Queue)
        assertEquals(QueueStatus.PENDING, savedItem?.status)
        assertEquals(1, savedItem?.priority)
    }

    @Test
    fun testPreventDuplicateQueue() = runBlocking {
        // Enqueue first item
        val firstResult = queueEngine.enqueue(
            ruleId = 42L,
            senderName = "John Doe",
            incomingMessage = "Hello",
            replyText = "Hi there",
            packageName = "com.whatsapp"
        )
        assertTrue(firstResult.isSuccess)

        // Attempt second identical item
        val secondResult = queueEngine.enqueue(
            ruleId = 42L,
            senderName = "John Doe",
            incomingMessage = "Hello",
            replyText = "Hi there",
            packageName = "com.whatsapp"
        )

        assertTrue(secondResult.isFailure)
        assertTrue(secondResult.exceptionOrNull()?.message?.contains("Duplicate") == true)
        assertEquals(1, fakeRepository.itemsCount())
    }

    @Test
    fun testMaxQueueSizeConstraint() = runBlocking {
        // Set up active items to max size
        for (i in 1..QueueEngine.MAX_QUEUE_SIZE) {
            fakeRepository.saveQueueItem(
                QueueItem(
                    ruleId = i.toLong(),
                    senderName = "Sender $i",
                    incomingMessage = "Msg $i",
                    replyText = "Reply $i",
                    packageName = "pkg",
                    scheduledTime = System.currentTimeMillis(),
                    status = QueueStatus.PENDING
                )
            )
        }

        // Try enqueuing one more
        val result = queueEngine.enqueue(
            ruleId = 999L,
            senderName = "Overflow Sender",
            incomingMessage = "Overflow Msg",
            replyText = "Overflow Reply",
            packageName = "pkg"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Maximum queue size") == true)
    }

    @Test
    fun testPriorityQueueOrdering() = runBlocking {
        val now = System.currentTimeMillis()

        // 1. Enqueue low priority item with scheduledTime in past
        fakeRepository.saveQueueItem(
            QueueItem(
                id = 10L,
                ruleId = 1L,
                senderName = "A",
                incomingMessage = "Low",
                replyText = "Reply A",
                packageName = "pkg",
                scheduledTime = now - 1000,
                status = QueueStatus.PENDING,
                priority = 0
            )
        )

        // 2. Enqueue high priority item with scheduledTime in past (should be selected first)
        fakeRepository.saveQueueItem(
            QueueItem(
                id = 20L,
                ruleId = 2L,
                senderName = "B",
                incomingMessage = "High",
                replyText = "Reply B",
                packageName = "pkg",
                scheduledTime = now - 500,
                status = QueueStatus.PENDING,
                priority = 10
            )
        )

        // 3. Enqueue item scheduled in future (should NOT be dispatched yet)
        fakeRepository.saveQueueItem(
            QueueItem(
                id = 30L,
                ruleId = 3L,
                senderName = "C",
                incomingMessage = "Future",
                replyText = "Reply C",
                packageName = "pkg",
                scheduledTime = now + 5000,
                status = QueueStatus.PENDING,
                priority = 100
            )
        )

        // First dispatch should pick up the high priority item (ID 20)
        val firstDispatched = queueEngine.dispatchNext()
        assertNotNull(firstDispatched)
        assertEquals(20L, firstDispatched?.id)
        assertEquals(QueueStatus.PROCESSING, firstDispatched?.status)

        // Second dispatch should pick up the low priority item (ID 10)
        val secondDispatched = queueEngine.dispatchNext()
        assertNotNull(secondDispatched)
        assertEquals(10L, secondDispatched?.id)

        // Third dispatch should return null because ID 30 is in the future
        val thirdDispatched = queueEngine.dispatchNext()
        assertNull(thirdDispatched)
    }

    @Test
    fun testQueueTimeoutReaping() = runBlocking {
        val now = System.currentTimeMillis()

        // Enqueue an item stuck in PROCESSING for over the timeout limit
        val stuckItemId = fakeRepository.saveQueueItem(
            QueueItem(
                ruleId = 1L,
                senderName = "A",
                incomingMessage = "Stuck",
                replyText = "Reply",
                packageName = "pkg",
                scheduledTime = now,
                updatedAt = now - (QueueEngine.QUEUE_TIMEOUT_MILLIS + 5000), // Timed out
                status = QueueStatus.PROCESSING,
                maxRetries = 2,
                retryCount = 0
            )
        )

        // Run maintenance
        queueEngine.runMaintenance()

        val reapedItem = fakeRepository.getQueueItemById(stuckItemId)
        assertNotNull(reapedItem)
        // Should trigger retry flow -> transitions to RETRY with incremented retry count
        assertEquals(QueueStatus.RETRY, reapedItem?.status)
        assertEquals(1, reapedItem?.retryCount)
    }

    @Test
    fun testQueueRetryLimit() = runBlocking {
        val itemId = fakeRepository.saveQueueItem(
            QueueItem(
                ruleId = 1L,
                senderName = "A",
                incomingMessage = "FailMsg",
                replyText = "Reply",
                packageName = "pkg",
                scheduledTime = System.currentTimeMillis(),
                status = QueueStatus.PROCESSING,
                maxRetries = 2,
                retryCount = 1
            )
        )

        // 1. Fail first time -> retryCount goes 1 -> 2, status remains RETRY since retryCount <= maxRetries (2)
        val firstFail = queueEngine.handleItemFailure(itemId, "Network timeout")
        assertTrue(firstFail)
        val itemAfterFirstFail = fakeRepository.getQueueItemById(itemId)
        assertEquals(QueueStatus.RETRY, itemAfterFirstFail?.status)
        assertEquals(2, itemAfterFirstFail?.retryCount)

        // 2. Fail second time -> retryCount goes 2 -> 3, exceeds maxRetries (2), should transition to FAILED
        val secondFail = queueEngine.handleItemFailure(itemId, "Service unavailable")
        assertTrue(secondFail)
        val itemAfterSecondFail = fakeRepository.getQueueItemById(itemId)
        assertEquals(QueueStatus.FAILED, itemAfterSecondFail?.status)
        assertEquals("Service unavailable", itemAfterSecondFail?.errorMessage)
    }

    @Test
    fun testCancelAndResume() = runBlocking {
        val id = fakeRepository.saveQueueItem(
            QueueItem(
                ruleId = 1L,
                senderName = "John",
                incomingMessage = "Query",
                replyText = "Reply",
                packageName = "pkg",
                scheduledTime = System.currentTimeMillis(),
                status = QueueStatus.PENDING
            )
        )

        // Cancel
        val cancelled = queueEngine.cancelItem(id)
        assertTrue(cancelled)
        assertEquals(QueueStatus.CANCELLED, fakeRepository.getQueueItemById(id)?.status)

        // Resume
        val resumed = queueEngine.resumeItem(id)
        assertTrue(resumed)
        val finalItem = fakeRepository.getQueueItemById(id)
        assertEquals(QueueStatus.PENDING, finalItem?.status)
        assertEquals(0, finalItem?.retryCount)
        assertNull(finalItem?.errorMessage)
    }

    /**
     * In-memory, clean implementation of QueueRepository for fast and robust unit testing.
     */
    class FakeQueueRepository : QueueRepository {
        private val items = mutableListOf<QueueItem>()
        private var nextId = 1L

        fun itemsCount() = items.size

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
}
