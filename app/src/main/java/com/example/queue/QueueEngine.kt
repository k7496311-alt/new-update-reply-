package com.example.queue

import android.util.Log
import com.example.model.QueueItem
import com.example.model.QueueStatus
import com.example.repository.QueueRepository
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import com.example.logger.AppLogger
import com.example.model.LogCategory

class QueueEngine(
    private val queueRepository: QueueRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    companion object {
        private const val TAG = "QueueEngine"
        const val MAX_QUEUE_SIZE = 100
        const val QUEUE_TIMEOUT_MILLIS = 30000L // 30 seconds running timeout
        const val RETRY_COOLDOWN_MILLIS = 5000L // 5 seconds initial backoff delay
    }

    private val engineScope = CoroutineScope(dispatcher + SupervisorJob())
    private var monitorJob: Job? = null
    private val isMonitoring = AtomicBoolean(false)

    /**
     * Enqueues a new item into the queue.
     * Enforce max size and duplicate prevention constraints.
     */
    suspend fun enqueue(
        ruleId: Long,
        senderName: String,
        incomingMessage: String,
        replyText: String,
        packageName: String,
        priority: Int = 0,
        delayMillis: Long = 0L
    ): Result<Long> = withContext(dispatcher) {
        try {
            // 1. Check Maximum Queue Size
            val activeCount = queueRepository.getActiveQueueCount()
            if (activeCount >= MAX_QUEUE_SIZE) {
                logW("Enqueue failed: Max queue size ($MAX_QUEUE_SIZE) reached. Active count is $activeCount")
                return@withContext Result.failure(IllegalStateException("Maximum queue size of $MAX_QUEUE_SIZE reached"))
            }

            // 2. Prevent duplicate queue items in active/non-terminal states
            val duplicate = queueRepository.findDuplicate(packageName, senderName, incomingMessage)
            if (duplicate != null) {
                logW("Enqueue skipped: Duplicate item already exists in active queue (ID: ${duplicate.id}, Status: ${duplicate.status})")
                return@withContext Result.failure(IllegalStateException("Duplicate queue item already exists in active state"))
            }

            val now = System.currentTimeMillis()
            val scheduledTime = now + delayMillis

            // Create initial item in INCOMING state (Incoming Queue)
            val incomingItem = QueueItem(
                ruleId = ruleId,
                senderName = senderName,
                incomingMessage = incomingMessage,
                replyText = replyText,
                packageName = packageName,
                scheduledTime = scheduledTime,
                createdAt = now,
                updatedAt = now,
                status = QueueStatus.INCOMING,
                priority = priority
            )

            val id = queueRepository.saveQueueItem(incomingItem)
            logI("Enqueued new item into INCOMING queue. ID: $id, Priority: $priority")

            // Transition item automatically from INCOMING to PENDING (Waiting Queue)
            val pendingItem = incomingItem.copy(
                id = id,
                status = QueueStatus.PENDING,
                updatedAt = System.currentTimeMillis()
            )
            queueRepository.saveQueueItem(pendingItem)
            logD("Transitioned item $id from INCOMING to PENDING (Waiting Queue)")

            Result.success(id)
        } catch (e: Exception) {
            logE("Error enqueuing item: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Dispatches the next available item that is ready for execution, sorted by priority (Priority Queue)
     * and scheduled execution time.
     * Changes status to PROCESSING (Running Queue).
     */
    suspend fun dispatchNext(): QueueItem? = withContext(dispatcher) {
        try {
            val now = System.currentTimeMillis()
            // Fetch PENDING and RETRY items
            val activeItems = queueRepository.getQueueItemsByStatuses(
                listOf(QueueStatus.PENDING, QueueStatus.RETRY)
            )

            // Find the highest priority item whose scheduledTime is <= current time
            val dispatchableItem = activeItems.firstOrNull { it.scheduledTime <= now }

            if (dispatchableItem != null) {
                val runningItem = dispatchableItem.copy(
                    status = QueueStatus.PROCESSING,
                    updatedAt = now
                )
                queueRepository.saveQueueItem(runningItem)
                logI("Dispatched item ${runningItem.id} to PROCESSING (Running Queue). Priority: ${runningItem.priority}")
                return@withContext runningItem
            }
        } catch (e: Exception) {
            logE("Error dispatching next queue item: ${e.message}", e)
        }
        null
    }

    /**
     * Marks an item as successfully completed.
     * Transitions status to SENT (Completed Queue).
     */
    suspend fun completeItem(id: Long): Boolean = withContext(dispatcher) {
        try {
            val item = queueRepository.getQueueItemById(id)
            if (item != null && item.status == QueueStatus.PROCESSING) {
                val completedItem = item.copy(
                    status = QueueStatus.SENT,
                    updatedAt = System.currentTimeMillis()
                )
                queueRepository.saveQueueItem(completedItem)
                logI("Item $id successfully completed and transitioned to SENT (Completed Queue)")
                return@withContext true
            } else {
                logW("Cannot complete item $id: Item not found or not in PROCESSING state")
            }
        } catch (e: Exception) {
            logE("Error completing item $id: ${e.message}", e)
        }
        false
    }

    /**
     * Handles item failure. Triggers retry logic (Queue Retry) or marks as failed.
     * Transition to RETRY (Retry Queue) or FAILED (Failed Queue).
     */
    suspend fun handleItemFailure(id: Long, errorMessage: String): Boolean = withContext(dispatcher) {
        try {
            val item = queueRepository.getQueueItemById(id)
            if (item != null) {
                val currentRetry = item.retryCount
                val maxRetryLimit = item.maxRetries

                if (currentRetry < maxRetryLimit) {
                    val nextRetryCount = currentRetry + 1
                    // Progressive exponential backoff delay calculation
                    val backoffDelay = RETRY_COOLDOWN_MILLIS * nextRetryCount
                    val nextScheduledTime = System.currentTimeMillis() + backoffDelay

                    val retryItem = item.copy(
                        status = QueueStatus.RETRY,
                        retryCount = nextRetryCount,
                        scheduledTime = nextScheduledTime,
                        errorMessage = errorMessage,
                        updatedAt = System.currentTimeMillis()
                    )
                    queueRepository.saveQueueItem(retryItem)
                    logI("Item $id failed. Rescheduled for retry ($nextRetryCount/$maxRetryLimit) in ${backoffDelay}ms. Status: RETRY")
                } else {
                    val failedItem = item.copy(
                        status = QueueStatus.FAILED,
                        errorMessage = errorMessage,
                        updatedAt = System.currentTimeMillis()
                    )
                    queueRepository.saveQueueItem(failedItem)
                    logW("Item $id exceeded max retries ($maxRetryLimit). Transitioned to FAILED (Failed Queue)")
                }
                return@withContext true
            }
        } catch (e: Exception) {
            logE("Error handling failure for item $id: ${e.message}", e)
        }
        false
    }

    /**
     * Cancels a pending or processing item.
     * Transitions status to CANCELLED (Queue Cancel).
     */
    suspend fun cancelItem(id: Long): Boolean = withContext(dispatcher) {
        try {
            val item = queueRepository.getQueueItemById(id)
            if (item != null && item.status != QueueStatus.SENT && item.status != QueueStatus.FAILED) {
                val cancelledItem = item.copy(
                    status = QueueStatus.CANCELLED,
                    updatedAt = System.currentTimeMillis()
                )
                queueRepository.saveQueueItem(cancelledItem)
                logI("Item $id cancelled and transitioned to CANCELLED (Cancelled Queue)")
                return@withContext true
            } else {
                logW("Cannot cancel item $id: Item not found or in terminal state")
            }
        } catch (e: Exception) {
            logE("Error cancelling item $id: ${e.message}", e)
        }
        false
    }

    /**
     * Resumes a cancelled or failed item.
     * Resets retries and schedules for immediate execution.
     * Transitions status to PENDING (Queue Resume).
     */
    suspend fun resumeItem(id: Long): Boolean = withContext(dispatcher) {
        try {
            val item = queueRepository.getQueueItemById(id)
            if (item != null && (item.status == QueueStatus.CANCELLED || item.status == QueueStatus.FAILED)) {
                val resumedItem = item.copy(
                    status = QueueStatus.PENDING,
                    retryCount = 0,
                    scheduledTime = System.currentTimeMillis(),
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
                queueRepository.saveQueueItem(resumedItem)
                logI("Item $id resumed. Scheduled immediately as PENDING (Waiting Queue)")
                return@withContext true
            } else {
                logW("Cannot resume item $id: Item not found or not in CANCELLED/FAILED state")
            }
        } catch (e: Exception) {
            logE("Error resuming item $id: ${e.message}", e)
        }
        false
    }

    /**
     * Periodic maintenance audit that reaps and fails items stuck in PROCESSING (Queue Timeout).
     */
    suspend fun runMaintenance() = withContext(dispatcher) {
        try {
            val runningItems = queueRepository.getQueueItemsByStatus(QueueStatus.PROCESSING)
            val now = System.currentTimeMillis()

            runningItems.forEach { item ->
                if (now - item.updatedAt > QUEUE_TIMEOUT_MILLIS) {
                    logW("Queue Timeout detected: Item ${item.id} has been in PROCESSING for over ${QUEUE_TIMEOUT_MILLIS / 1000}s. Reaping...")
                    handleItemFailure(item.id, "Queue execution timeout exceeded")
                }
            }
        } catch (e: Exception) {
            logE("Error during queue maintenance run: ${e.message}", e)
        }
    }

    /**
     * Starts a lifecycle-safe background monitor loop that performs timeout reaping and log checks.
     */
    fun startQueueMonitor(intervalMillis: Long = 10000L) {
        if (!isMonitoring.compareAndSet(false, true)) {
            logD("Queue monitor is already running")
            return
        }

        logI("Starting active Queue Monitor background loop")
        monitorJob = engineScope.launch {
            while (isActive) {
                try {
                    runMaintenance()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logE("Exception inside Queue Monitor loop: ${e.message}", e)
                }
                delay(intervalMillis)
            }
        }
    }

    /**
     * Stops the background queue monitor cleanly.
     */
    fun stopQueueMonitor() {
        if (isMonitoring.compareAndSet(true, false)) {
            logI("Stopping Queue Monitor background loop")
            monitorJob?.cancel()
            monitorJob = null
        }
    }

    /**
     * Clears all items from the queue.
     */
    suspend fun clearAll() = withContext(dispatcher) {
        queueRepository.clearQueue()
        logI("Queue completely cleared")
    }

    // Material design informational logging
    private fun logD(message: String) {
        Log.d(TAG, "⚙️ [QueueEngine] $message")
        AppLogger.info(LogCategory.QUEUE, message)
    }
    private fun logI(message: String) {
        Log.i(TAG, "⚙️ [QueueEngine] ℹ️ $message")
        AppLogger.success(LogCategory.QUEUE, message)
    }
    private fun logW(message: String) {
        Log.w(TAG, "⚙️ [QueueEngine] ⚠️ $message")
        AppLogger.warning(LogCategory.QUEUE, message)
    }
    private fun logE(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, "⚙️ [QueueEngine] ❌ $message", throwable)
            AppLogger.critical(LogCategory.QUEUE, message, Log.getStackTraceString(throwable))
        } else {
            Log.e(TAG, "⚙️ [QueueEngine] ❌ $message")
            AppLogger.critical(LogCategory.QUEUE, message)
        }
    }
}
