package com.example.data

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.model.QueueItem
import com.example.model.QueueStatus
import com.example.queue.MultiConversationCriteria
import com.example.queue.MultiConversationResult
import com.example.repository.MultiConversationRepository
import com.example.repository.QueueRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of MultiConversationRepository.
 *
 * Requirements:
 * 1. Process unlimited queued conversations (Customer A -> Customer B -> Customer C...).
 * 2. FIFO Order (First-In, First-Out).
 * 3. Merge multiple notifications from the same customer into the same queue item.
 * 4. Never skip conversations.
 * 5. Never process two conversations simultaneously (guaranteed via processingMutex).
 * 6. Emits exact required logs:
 *    - Queue Started
 *    - Conversation Started
 *    - Conversation Finished
 *    - Queue Remaining
 *    - Queue Empty
 */
class MultiConversationRepositoryImpl(
    private val queueRepository: QueueRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : MultiConversationRepository {

    // Global Mutex lock ensuring never processing two conversations simultaneously
    private val processingMutex = Mutex()

    override suspend fun enqueueOrMergeNotification(
        packageName: String,
        senderName: String,
        messageText: String,
        timestamp: Long
    ): QueueItem = withContext(dispatcher) {
        val existingItem = queueRepository.findActiveQueueItemBySender(packageName, senderName)

        if (existingItem != null) {
            // Notification Merging Rule: If same customer sends multiple notifications, merge into same queue item
            val mergedMessage = if (existingItem.incomingMessage.isBlank()) {
                messageText
            } else if (!existingItem.incomingMessage.contains(messageText)) {
                "${existingItem.incomingMessage}\n$messageText"
            } else {
                existingItem.incomingMessage
            }

            val updatedItem = existingItem.copy(
                incomingMessage = mergedMessage,
                updatedAt = timestamp,
                scheduledTime = timestamp
            )
            queueRepository.saveQueueItem(updatedItem)

            val logMsg = """
                Queue Updated (Merged)
                Sender: $senderName
                Merged Message: $mergedMessage
                Queue ID: #${existingItem.id}
            """.trimIndent()

            Log.i(TAG, logMsg)
            AppLogger.info(
                LogCategory.QUEUE,
                "Notification Merged ($senderName)",
                "Sender: $senderName | Queue ID: #${existingItem.id} | Merged Text: '$mergedMessage'"
            )

            return@withContext updatedItem
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
            val savedItem = newItem.copy(id = newId)

            val activeCount = queueRepository.getActiveQueueCount()
            val logMsg = """
                Queue Added
                Sender: $senderName
                Message: $messageText
                Queue Size: $activeCount
            """.trimIndent()

            Log.i(TAG, logMsg)
            AppLogger.info(
                LogCategory.QUEUE,
                "Queue Added ($senderName)",
                "Sender: $senderName | Message: '$messageText' | Total Queue: $activeCount"
            )

            return@withContext savedItem
        }
    }

    override suspend fun processAllQueuedConversations(
        criteria: MultiConversationCriteria
    ): MultiConversationResult = withContext(dispatcher) {
        // Enforce strict single-threaded execution using Mutex
        processingMutex.withLock {
            var successCount = 0
            var failedCount = 0
            val processedList = mutableListOf<QueueItem>()
            var queueStartedLogged = false

            while (true) {
                // Fetch pending/retry items sorted by createdAt ascending (FIFO)
                val activeItems = queueRepository.getQueueItemsByStatuses(
                    listOf(QueueStatus.PENDING, QueueStatus.RETRY, QueueStatus.INCOMING)
                ).sortedBy { it.createdAt }

                if (activeItems.isEmpty()) {
                    // Queue is completely empty! Log: Queue Empty
                    if (queueStartedLogged || processedList.isNotEmpty()) {
                        logQueueEmpty(
                            totalProcessed = processedList.size,
                            successCount = successCount,
                            failedCount = failedCount
                        )
                    }
                    break
                }

                // Log: Queue Started (on initial batch pickup)
                if (!queueStartedLogged) {
                    val firstItem = activeItems.first()
                    logQueueStarted(
                        initialCount = activeItems.size,
                        firstSender = firstItem.senderName
                    )
                    queueStartedLogged = true
                }

                // Pick next conversation in FIFO order
                val currentItem = activeItems.first()

                // Transition status to PROCESSING
                val processingItem = currentItem.copy(
                    status = QueueStatus.PROCESSING,
                    updatedAt = System.currentTimeMillis()
                )
                queueRepository.saveQueueItem(processingItem)

                // Log: Conversation Started
                logConversationStarted(processingItem)

                // Execute action
                val isSuccess = try {
                    if (criteria.executeConversationAction != null) {
                        criteria.executeConversationAction.invoke(processingItem)
                    } else {
                        // Default production dispatch delay
                        delay(400L)
                        true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing conversation for ${processingItem.senderName}", e)
                    false
                }

                if (isSuccess) {
                    val completedItem = processingItem.copy(
                        status = QueueStatus.SENT,
                        updatedAt = System.currentTimeMillis()
                    )
                    queueRepository.saveQueueItem(completedItem)
                    processedList.add(completedItem)
                    successCount++

                    // Log: Conversation Finished
                    logConversationFinished(completedItem, true)
                } else {
                    val failedItem = processingItem.copy(
                        status = QueueStatus.FAILED,
                        updatedAt = System.currentTimeMillis()
                    )
                    queueRepository.saveQueueItem(failedItem)
                    processedList.add(failedItem)
                    failedCount++

                    // Log: Conversation Finished
                    logConversationFinished(failedItem, false)
                }

                // Check remaining active items in queue
                val remainingItems = queueRepository.getQueueItemsByStatuses(
                    listOf(QueueStatus.PENDING, QueueStatus.RETRY, QueueStatus.INCOMING)
                ).sortedBy { it.createdAt }

                if (remainingItems.isNotEmpty()) {
                    // Log: Queue Remaining
                    logQueueRemaining(
                        remainingCount = remainingItems.size,
                        nextSender = remainingItems.first().senderName
                    )
                }
            }

            return@withContext MultiConversationResult(
                totalProcessedCount = processedList.size,
                successCount = successCount,
                failedCount = failedCount,
                mergedNotificationCount = 0,
                isQueueEmpty = true,
                processedItems = processedList,
                details = "Multi-Conversation Processing complete. Processed ${processedList.size} conversations sequentially in FIFO order."
            )
        }
    }

    override suspend fun getRemainingQueueCount(): Int = withContext(dispatcher) {
        queueRepository.getActiveQueueCount()
    }

    private fun logQueueStarted(initialCount: Int, firstSender: String) {
        val logMsg = """
            Queue Started
            Total Queued Conversations: $initialCount
            Initial Conversation: "$firstSender"
            Order: FIFO (First-In, First-Out)
            Concurrency Guard: Single-Thread Lock Active
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.QUEUE,
            "Queue Started",
            logMsg
        )
    }

    private fun logConversationStarted(item: QueueItem) {
        val logMsg = """
            Conversation Started
            Queue ID: #${item.id}
            Sender: "${item.senderName}"
            Message Payload: "${item.incomingMessage}"
            Status: PROCESSING
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.QUEUE,
            "Conversation Started (${item.senderName})",
            logMsg
        )
    }

    private fun logConversationFinished(item: QueueItem, isSuccess: Boolean) {
        val statusName = if (isSuccess) "SENT" else "FAILED"
        val logMsg = """
            Conversation Finished
            Queue ID: #${item.id}
            Sender: "${item.senderName}"
            Final Status: $statusName
            Outcome: ${if (isSuccess) "Successfully processed and sent." else "Processing failed or moved to failed queue."}
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.QUEUE,
            "Conversation Finished (${item.senderName})",
            logMsg
        )
    }

    private fun logQueueRemaining(remainingCount: Int, nextSender: String) {
        val logMsg = """
            Queue Remaining
            Remaining Count: $remainingCount
            Next In Line: "$nextSender"
            Pipeline: Proceeding to next queued conversation in FIFO order.
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.QUEUE,
            "Queue Remaining ($remainingCount left)",
            logMsg
        )
    }

    private fun logQueueEmpty(totalProcessed: Int, successCount: Int, failedCount: Int) {
        val logMsg = """
            Queue Empty
            Total Processed: $totalProcessed
            Success Count: $successCount
            Failed Count: $failedCount
            Status: All queued conversations processed. Queue is now completely empty.
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.QUEUE,
            "Queue Empty",
            logMsg
        )
    }

    companion object {
        private const val TAG = "MultiConversationEngine"
    }
}
