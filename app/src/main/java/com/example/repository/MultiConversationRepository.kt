package com.example.repository

import com.example.model.QueueItem
import com.example.queue.MultiConversationCriteria
import com.example.queue.MultiConversationResult

/**
 * Clean Architecture repository interface for processing unlimited queued conversations.
 *
 * Requirements:
 * - Process unlimited queued conversations sequentially (Customer A -> Customer B -> Customer C...).
 * - FIFO Order (First-In, First-Out).
 * - Merge multiple notifications from the same customer into the same queue item.
 * - Never skip conversations.
 * - Never process two conversations simultaneously.
 * - Emit exact required lifecycle logs: Queue Started, Conversation Started, Conversation Finished, Queue Remaining, Queue Empty.
 */
interface MultiConversationRepository {

    /**
     * Enqueues a new conversation notification or merges it into an existing active conversation queue item.
     */
    suspend fun enqueueOrMergeNotification(
        packageName: String,
        senderName: String,
        messageText: String,
        timestamp: Long = System.currentTimeMillis()
    ): QueueItem

    /**
     * Processes all queued conversations sequentially in FIFO order until the queue becomes empty.
     */
    suspend fun processAllQueuedConversations(
        criteria: MultiConversationCriteria = MultiConversationCriteria()
    ): MultiConversationResult

    /**
     * Returns the total count of currently active (pending/retry/processing) queue items.
     */
    suspend fun getRemainingQueueCount(): Int
}
