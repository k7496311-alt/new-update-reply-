package com.example.repository

import com.example.model.QueueItem

/**
 * Clean Architecture repository interface for recovering and enqueuing pending notifications
 * arriving during conversation execution.
 *
 * Requirements:
 * - While processing one conversation, new notifications may arrive.
 * - Immediately enqueue them without interrupting current conversation execution.
 * - After current conversation finishes, continue with next queued conversation automatically.
 * - Never lose any notification.
 * - Required logs: New Notification, Queue Updated, Continue Processing, Recovery Success.
 */
interface PendingNotificationRecoveryRepository {

    /**
     * Called immediately when a new notification arrives while processing a conversation.
     * Enqueues or merges the notification without interrupting the active conversation execution.
     */
    suspend fun handleIncomingNotification(
        packageName: String,
        senderName: String,
        messageText: String,
        timestamp: Long = System.currentTimeMillis()
    ): QueueItem

    /**
     * Fetches the next pending conversation item in FIFO order and logs 'Continue Processing'.
     */
    suspend fun fetchNextPendingConversation(): QueueItem?

    /**
     * Recovers all unhandled/pending notifications from storage to guarantee no notification is ever lost.
     * Emits 'Recovery Success' log when completed.
     */
    suspend fun recoverAllPendingNotifications(): List<QueueItem>

    /**
     * Returns true if there are active processing or pending items in queue.
     */
    suspend fun hasPendingWork(): Boolean
}
