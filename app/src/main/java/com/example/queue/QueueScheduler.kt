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
 * Queue Scheduler:
 * - Only one worker execution loop.
 * - Never processes two conversations simultaneously.
 * - When current conversation finishes, automatically dequeues next conversation in FIFO order.
 * - Scheduler survives notification bursts, screen off, process restarts, and temporary failures without losing queue state.
 * - If processing fails, requeues once (retryCount < 1 -> retryCount = 1, status = RETRY).
 * - Produces exact logs:
 *   - Worker Busy
 *   - Worker Idle
 *   - Next Conversation
 *   - Retry
 *   - Queue Empty
 */
class QueueScheduler(
    private val queueRepository: QueueRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val executionHandler: (suspend (QueueItem) -> Unit)? = null
) {

    private val schedulerScope = CoroutineScope(dispatcher + SupervisorJob())
    private val workerMutex = Mutex()
    private val isWorkerBusy = AtomicBoolean(false)
    private var workerJob: Job? = null

    init {
        // Recover any stale processing state (e.g. from app restart or screen off)
        schedulerScope.launch {
            recoverStaleQueueItems()
        }
    }

    /**
     * Recovers items that were left in PROCESSING status due to process interruption or screen off.
     * Requeues them once if retryCount < 1, otherwise marks them as FAILED.
     */
    suspend fun recoverStaleQueueItems() = withContext(dispatcher) {
        try {
            val processingItems = queueRepository.getQueueItemsByStatus(QueueStatus.PROCESSING)
            for (item in processingItems) {
                if (item.retryCount < MAX_RETRY_COUNT) {
                    val retryItem = item.copy(
                        status = QueueStatus.RETRY,
                        retryCount = item.retryCount + 1,
                        updatedAt = System.currentTimeMillis()
                    )
                    queueRepository.saveQueueItem(retryItem)

                    val logMsg = """
                        Retry
                        Sender: ${item.senderName}
                        Message: ${item.incomingMessage}
                        Attempt: ${retryItem.retryCount}/$MAX_RETRY_COUNT
                        Reason: Recovered after process crash or interruption
                    """.trimIndent()

                    Log.w(TAG, logMsg)
                    AppLogger.warning(LogCategory.QUEUE, "Retry (${item.senderName})", logMsg)
                } else {
                    val failedItem = item.copy(
                        status = QueueStatus.FAILED,
                        errorMessage = "Failed during process interruption",
                        updatedAt = System.currentTimeMillis()
                    )
                    queueRepository.saveQueueItem(failedItem)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error recovering stale queue items in QueueScheduler", e)
        }
    }

    /**
     * Triggers the single worker loop to evaluate and dequeue pending items.
     */
    fun triggerScheduler() {
        workerJob?.cancel()
        workerJob = schedulerScope.launch {
            processQueueLoop()
        }
    }

    /**
     * Main worker loop. Guarantees single-worker execution via workerMutex lock.
     */
    suspend fun processQueueLoop() = withContext(dispatcher) {
        if (!workerMutex.tryLock()) {
            // Worker is already running in another coroutine thread
            return@withContext
        }

        try {
            while (isActive) {
                // Fetch next pending or retry item in FIFO order
                val pendingItems = queueRepository.getQueueItemsByStatuses(
                    listOf(QueueStatus.PENDING, QueueStatus.RETRY, QueueStatus.INCOMING)
                ).sortedBy { it.createdAt }

                if (pendingItems.isEmpty()) {
                    if (isWorkerBusy.getAndSet(false)) {
                        val idleLog = "Worker Idle\nAll queued conversations processed."
                        Log.i(TAG, idleLog)
                        AppLogger.info(LogCategory.QUEUE, "Worker Idle", idleLog)
                    }

                    val activeCount = queueRepository.getActiveQueueCount()
                    if (activeCount == 0) {
                        val emptyLog = "Queue Empty\nNo pending conversations remaining."
                        Log.i(TAG, emptyLog)
                        AppLogger.info(LogCategory.QUEUE, "Queue Empty", emptyLog)
                    }
                    break
                }

                if (!isWorkerBusy.getAndSet(true)) {
                    val busyLog = "Worker Busy\nStarted processing conversation queue."
                    Log.i(TAG, busyLog)
                    AppLogger.info(LogCategory.QUEUE, "Worker Busy", busyLog)
                }

                val nextItem = pendingItems.first()

                // Log: Next Conversation
                val nextLog = """
                    Next Conversation
                    Queue ID: ${nextItem.id}
                    Sender: ${nextItem.senderName}
                    Message: ${nextItem.incomingMessage}
                    Status: ${nextItem.status}
                """.trimIndent()

                Log.i(TAG, nextLog)
                AppLogger.info(
                    LogCategory.QUEUE,
                    "Next Conversation (${nextItem.senderName})",
                    "Queue ID: ${nextItem.id} | Sender: ${nextItem.senderName} | Message: '${nextItem.incomingMessage}'"
                )

                // Transition item to PROCESSING
                val processingItem = nextItem.copy(
                    status = QueueStatus.PROCESSING,
                    updatedAt = System.currentTimeMillis()
                )
                queueRepository.saveQueueItem(processingItem)

                // Process conversation safely
                try {
                    if (executionHandler != null) {
                        executionHandler.invoke(processingItem)
                    } else {
                        // Default simulation processing time
                        delay(600L)
                    }

                    // Successfully completed
                    val finishedItem = processingItem.copy(
                        status = QueueStatus.SENT,
                        updatedAt = System.currentTimeMillis()
                    )
                    queueRepository.saveQueueItem(finishedItem)

                } catch (e: Exception) {
                    Log.e(TAG, "Error executing conversation for sender ${processingItem.senderName}", e)

                    if (processingItem.retryCount < MAX_RETRY_COUNT) {
                        // Requeue once
                        val retryItem = processingItem.copy(
                            status = QueueStatus.RETRY,
                            retryCount = processingItem.retryCount + 1,
                            errorMessage = e.message,
                            updatedAt = System.currentTimeMillis()
                        )
                        queueRepository.saveQueueItem(retryItem)

                        val retryLog = """
                            Retry
                            Sender: ${processingItem.senderName}
                            Message: ${processingItem.incomingMessage}
                            Attempt: ${retryItem.retryCount}/$MAX_RETRY_COUNT
                            Error: ${e.localizedMessage ?: e.message}
                        """.trimIndent()

                        Log.w(TAG, retryLog)
                        AppLogger.warning(
                            LogCategory.QUEUE,
                            "Retry (${processingItem.senderName})",
                            retryLog
                        )
                    } else {
                        // Max retries exceeded -> FAILED
                        val failedItem = processingItem.copy(
                            status = QueueStatus.FAILED,
                            errorMessage = e.message,
                            updatedAt = System.currentTimeMillis()
                        )
                        queueRepository.saveQueueItem(failedItem)

                        Log.e(TAG, "Conversation failed for ${processingItem.senderName} after $MAX_RETRY_COUNT retries")
                    }
                }
            }
        } finally {
            workerMutex.unlock()
        }
    }

    /**
     * Returns whether the worker is currently busy processing a conversation.
     */
    fun isWorkerBusy(): Boolean = isWorkerBusy.get()

    companion object {
        private const val TAG = "QueueScheduler"
        private const val MAX_RETRY_COUNT = 1 // Requeue once on failure
    }
}
