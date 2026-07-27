package com.example.queue

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.model.QueueItem
import com.example.model.QueueStatus
import com.example.repository.QueueRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Conversation Queue System:
 * - Stores conversations (keyed by sender), NOT raw individual notifications.
 * - If the same sender sends multiple notifications, keeps only ONE active queue item and updates it with the newest message.
 * - Maintained in FIFO order (First-In, First-Out).
 * - Enforces single-thread execution: Only ONE conversation may run/process at a time, others wait.
 * - Persistent storage via QueueRepository guarantees conversations are never lost.
 * - Logs explicit lifecycle events: Queue Added, Queue Updated, Queue Started, Queue Finished, Queue Size.
 */
class ConversationQueue(
    private val queueRepository: QueueRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scheduler: QueueScheduler = QueueScheduler(queueRepository, dispatcher)
) {

    private val queueScope = CoroutineScope(dispatcher + SupervisorJob())

    /**
     * Enqueues or updates a conversation.
     * If the sender already exists in the active queue, updates the existing item with the newest message.
     * Otherwise, creates a new conversation queue item.
     */
    suspend fun enqueueOrUpdate(
        packageName: String,
        senderName: String,
        messageText: String,
        timestamp: Long = System.currentTimeMillis()
    ): QueueItem = withContext(dispatcher) {
        // Check if an active conversation already exists for this sender
        val existingItem = queueRepository.findActiveQueueItemBySender(packageName, senderName)

        val queueItem: QueueItem
        if (existingItem != null) {
            // Update existing conversation queue item with the newest notification content
            val updated = existingItem.copy(
                incomingMessage = messageText,
                updatedAt = timestamp,
                scheduledTime = timestamp
            )
            queueRepository.saveQueueItem(updated)
            queueItem = updated

            val size = getQueueSize()

            val logMsg = """
                Queue Updated
                Sender: $senderName
                Message: $messageText
                Queue Size: $size
            """.trimIndent()

            Log.i(TAG, logMsg)
            AppLogger.info(
                LogCategory.QUEUE,
                "Queue Updated ($senderName)",
                "Sender: $senderName | Message: '$messageText' | Queue Size: $size"
            )
        } else {
            // Add new conversation queue item
            val newItem = QueueItem(
                ruleId = 0L,
                senderName = senderName,
                incomingMessage = messageText,
                replyText = "",
                packageName = packageName,
                scheduledTime = timestamp,
                createdAt = timestamp,
                updatedAt = timestamp,
                status = QueueStatus.PENDING
            )

            val newId = queueRepository.saveQueueItem(newItem)
            queueItem = newItem.copy(id = newId)

            val size = getQueueSize()

            val logMsg = """
                Queue Added
                Sender: $senderName
                Message: $messageText
                Queue Size: $size
            """.trimIndent()

            Log.i(TAG, logMsg)
            AppLogger.info(
                LogCategory.QUEUE,
                "Queue Added ($senderName)",
                "Sender: $senderName | Message: '$messageText' | Queue Size: $size"
            )
        }

        // Trigger queue scheduler to handle pending conversations
        triggerProcessor()

        return@withContext queueItem
    }

    /**
     * Returns the number of currently active queued conversations (PENDING, PROCESSING, RETRY).
     */
    suspend fun getQueueSize(): Int = withContext(dispatcher) {
        val activeCount = queueRepository.getActiveQueueCount()
        Log.d(TAG, "Queue Size: $activeCount")
        return@withContext activeCount
    }

    /**
     * Starts or triggers the queue scheduler worker loop.
     */
    fun startProcessor() {
        scheduler.triggerScheduler()
    }

    private fun triggerProcessor() {
        scheduler.triggerScheduler()
    }

    /**
     * Delegates processing loop execution to QueueScheduler.
     */
    suspend fun processNextConversation() {
        scheduler.processQueueLoop()
    }

    companion object {
        private const val TAG = "ConversationQueue"
    }
}
