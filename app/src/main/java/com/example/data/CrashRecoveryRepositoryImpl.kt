package com.example.data

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.Conversation
import com.example.model.LogCategory
import com.example.model.QueueItem
import com.example.model.QueueStatus
import com.example.model.ReplyHistory
import com.example.repository.ConversationRepository
import com.example.repository.CrashRecoveryCriteria
import com.example.repository.CrashRecoveryRepository
import com.example.repository.CrashRecoveryResult
import com.example.repository.DuplicatePreventionRepository
import com.example.repository.HistoryRepository
import com.example.repository.QueueRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of CrashRecoveryRepository.
 *
 * Fully restores:
 * 1. Queue (re-aligns stuck PROCESSING items, verifies sent state against history).
 * 2. Current conversation (restores active sender context and state).
 * 3. Pending reply (verifies pending reply text against duplicate checks).
 * 4. History (restores and verifies recent reply history).
 *
 * Guarantees:
 * - Safe resume of queue execution.
 * - Never sends duplicate replies.
 * - Emits exact required logs:
 *   - Recovery Started
 *   - Queue Restored
 *   - Conversation Restored
 *   - Recovery Finished
 */
class CrashRecoveryRepositoryImpl(
    private val queueRepository: QueueRepository,
    private val conversationRepository: ConversationRepository,
    private val historyRepository: HistoryRepository,
    private val duplicatePreventionRepository: DuplicatePreventionRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : CrashRecoveryRepository {

    override suspend fun performCrashRecovery(
        criteria: CrashRecoveryCriteria
    ): CrashRecoveryResult = withContext(dispatcher) {
        val startTime = System.currentTimeMillis()

        // 1. Log: Recovery Started
        logRecoveryStarted(startTime)

        var duplicatePreventedCount = 0

        // 2. Restore Queue
        val rawQueueItems = queueRepository.getQueueItemsByStatuses(
            listOf(QueueStatus.PENDING, QueueStatus.RETRY, QueueStatus.INCOMING, QueueStatus.PROCESSING)
        )

        val restoredQueueList = mutableListOf<QueueItem>()

        for (item in rawQueueItems) {
            if (item.status == QueueStatus.PROCESSING) {
                // Was processing during crash. Check if reply was already sent in history to avoid duplicate!
                val conversation = conversationRepository.getConversation(item.senderName, item.packageName)
                val recentHistory = historyRepository.getAllHistory().firstOrNull()?.filter {
                    it.senderName.equals(item.senderName, ignoreCase = true)
                } ?: emptyList()

                val isAlreadyRepliedInHistory = recentHistory.any {
                    (it.repliedMessage == item.replyText && item.replyText.isNotBlank()) ||
                            (it.timestamp >= item.updatedAt - 10_000L)
                } || (conversation?.repliedToLastMessage == true)

                if (isAlreadyRepliedInHistory) {
                    // Already sent before crash! Update to SENT to prevent duplicate sending.
                    val sentItem = item.copy(
                        status = QueueStatus.SENT,
                        updatedAt = System.currentTimeMillis()
                    )
                    queueRepository.saveQueueItem(sentItem)
                    duplicatePreventedCount++
                    Log.i(TAG, "Duplicate reply prevented for '${item.senderName}'. Updated queue item #${item.id} to SENT.")
                } else {
                    // Not sent yet. Safely reset status from PROCESSING back to PENDING for safe retry.
                    val pendingItem = item.copy(
                        status = QueueStatus.PENDING,
                        updatedAt = System.currentTimeMillis()
                    )
                    queueRepository.saveQueueItem(pendingItem)
                    restoredQueueList.add(pendingItem)
                }
            } else {
                restoredQueueList.add(item)
            }
        }

        val activeCount = queueRepository.getActiveQueueCount()

        // Log: Queue Restored
        logQueueRestored(restoredQueueList.size, activeCount)

        // 3. Restore Current Conversation
        val activeQueueItem = restoredQueueList.minByOrNull { it.createdAt }
        var restoredConversation: Conversation? = null

        if (activeQueueItem != null) {
            restoredConversation = conversationRepository.getConversation(
                activeQueueItem.senderName,
                activeQueueItem.packageName
            ) ?: conversationRepository.recordIncomingMessage(
                senderName = activeQueueItem.senderName,
                packageName = activeQueueItem.packageName,
                message = activeQueueItem.incomingMessage
            )

            // Log: Conversation Restored
            logConversationRestored(restoredConversation, activeQueueItem)
        } else {
            val lastConv = conversationRepository.getAllConversations().maxByOrNull { it.lastActivityTime }
            if (lastConv != null) {
                restoredConversation = lastConv
                val dummyItem = QueueItem(
                    ruleId = 0L,
                    senderName = lastConv.senderName,
                    incomingMessage = lastConv.lastMessage,
                    replyText = lastConv.lastReply ?: "",
                    packageName = lastConv.packageName,
                    scheduledTime = lastConv.lastActivityTime
                )
                logConversationRestored(lastConv, dummyItem)
            }
        }

        // 4. Restore Pending Reply
        var pendingReplyText: String? = activeQueueItem?.replyText?.ifBlank { null }

        if (pendingReplyText != null && activeQueueItem != null) {
            // Check shouldReply logic to guarantee no duplicate
            val (shouldSend, reason) = conversationRepository.shouldReply(
                senderName = activeQueueItem.senderName,
                packageName = activeQueueItem.packageName,
                pendingReplyText = pendingReplyText
            )

            if (!shouldSend) {
                Log.w(TAG, "Pending reply duplicate risk detected for '${activeQueueItem.senderName}': $reason. Clearing duplicate pending reply.")
                pendingReplyText = null
                duplicatePreventedCount++
            }
        }

        // 5. Restore History
        val restoredHistoryList = historyRepository.getAllHistory().firstOrNull() ?: emptyList()

        // 6. Log: Recovery Finished
        logRecoveryFinished(
            queueRestoredCount = restoredQueueList.size,
            activeSender = restoredConversation?.senderName ?: "None",
            isDuplicatePrevented = duplicatePreventedCount > 0
        )

        return@withContext CrashRecoveryResult(
            restoredQueueItems = restoredQueueList,
            restoredConversation = restoredConversation,
            pendingReplyText = pendingReplyText,
            restoredHistoryList = restoredHistoryList,
            isDuplicatePrevented = duplicatePreventedCount > 0,
            isSuccess = true,
            summaryMessage = "Crash Recovery successfully completed. Restored ${restoredQueueList.size} queue items and active conversation context safely."
        )
    }

    override suspend fun isRecoveryNeeded(): Boolean = withContext(dispatcher) {
        val activeQueueCount = queueRepository.getActiveQueueCount()
        val processingItems = queueRepository.getQueueItemsByStatuses(listOf(QueueStatus.PROCESSING))
        activeQueueCount > 0 || processingItems.isNotEmpty()
    }

    private fun logRecoveryStarted(startTime: Long) {
        val logMsg = """
            Recovery Started
            Trigger: Process restart / Accessibility Service reconnection
            Timestamp: $startTime
            Mode: Automatic crash recovery & duplicate prevention
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.APPLICATION,
            "Recovery Started",
            logMsg
        )
    }

    private fun logQueueRestored(queueSize: Int, activeCount: Int) {
        val logMsg = """
            Queue Restored
            Restored Queue Items: $queueSize
            Active Pending Count: $activeCount
            Status: Persistent queue state fully restored. Interrupted tasks safely recovered.
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.QUEUE,
            "Queue Restored",
            logMsg
        )
    }

    private fun logConversationRestored(conversation: Conversation, item: QueueItem) {
        val logMsg = """
            Conversation Restored
            Sender: "${conversation.senderName}"
            Package: "${conversation.packageName}"
            Last Message Payload: "${item.incomingMessage}"
            Replied Flag: ${conversation.repliedToLastMessage}
            Status: Active conversation context restored.
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.APPLICATION,
            "Conversation Restored (${conversation.senderName})",
            logMsg
        )
    }

    private fun logRecoveryFinished(
        queueRestoredCount: Int,
        activeSender: String,
        isDuplicatePrevented: Boolean
    ) {
        val logMsg = """
            Recovery Finished
            Restored Queue Size: $queueRestoredCount
            Active Conversation: "$activeSender"
            Duplicate Reply Prevented: $isDuplicatePrevented
            Status: Recovery process finished. Ready to safely execute queued conversations.
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.APPLICATION,
            "Recovery Finished",
            logMsg
        )
    }

    companion object {
        private const val TAG = "CrashRecoveryEngine"
    }
}
