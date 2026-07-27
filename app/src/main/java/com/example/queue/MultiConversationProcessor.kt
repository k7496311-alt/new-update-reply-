package com.example.queue

import android.util.Log
import com.example.model.QueueItem
import com.example.repository.MultiConversationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Production-grade Multi-Conversation Processing Engine.
 *
 * Capabilities:
 * - Process unlimited queued conversations sequentially.
 * - Enforces FIFO (First-In, First-Out) execution order.
 * - Automatically merges multiple notifications from the same customer into a single active queue item.
 * - Never skips conversations.
 * - Never processes two conversations simultaneously.
 * - Generates required logs:
 *   - Queue Started
 *   - Conversation Started
 *   - Conversation Finished
 *   - Queue Remaining
 *   - Queue Empty
 */
class MultiConversationProcessor(
    private val repository: MultiConversationRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val processorScope = CoroutineScope(dispatcher + SupervisorJob())
    private var workerJob: Job? = null

    /**
     * Enqueues or merges a notification from a customer and triggers multi-conversation processing.
     */
    suspend fun processNotification(
        packageName: String,
        senderName: String,
        messageText: String,
        timestamp: Long = System.currentTimeMillis()
    ): QueueItem = withContext(dispatcher) {
        // Enqueue or merge into existing active conversation
        val item = repository.enqueueOrMergeNotification(packageName, senderName, messageText, timestamp)

        // Trigger processing pipeline
        triggerWorker()

        item
    }

    /**
     * Triggers the single worker loop to process all queued conversations until empty.
     */
    fun triggerWorker(
        criteria: MultiConversationCriteria = MultiConversationCriteria()
    ) {
        if (workerJob?.isActive == true) {
            // Worker is already running and will automatically pick up any newly enqueued or merged items
            return
        }

        workerJob = processorScope.launch {
            repository.processAllQueuedConversations(criteria)
        }
    }

    /**
     * Directly executes all queued conversations synchronously on the current coroutine context.
     */
    suspend fun executeAllNow(
        criteria: MultiConversationCriteria = MultiConversationCriteria()
    ): MultiConversationResult = withContext(dispatcher) {
        repository.processAllQueuedConversations(criteria)
    }

    /**
     * Returns remaining active conversation count.
     */
    suspend fun getRemainingCount(): Int {
        return repository.getRemainingQueueCount()
    }

    companion object {
        private const val TAG = "MultiConversationProcessor"
    }
}
