package com.example.data

import com.example.model.QueueItem
import com.example.model.QueueStatus
import com.example.reply.duplicate.DuplicatePreventionEngine
import com.example.reply.duplicate.DuplicatePreventionStatus
import com.example.reply.recovery.SendRecoveryCriteria
import com.example.reply.recovery.SendRecoveryResult
import com.example.reply.recovery.SendRecoveryStatus
import com.example.repository.QueueRepository
import com.example.repository.SendRecoveryRepository

/**
 * Concrete implementation of SendRecoveryRepository.
 *
 * Requirements:
 * - Retry ONLY ONCE.
 * - Never create duplicate reply (uses DuplicatePreventionEngine check).
 * - If retry fails:
 *   - Move conversation queue item to FAILED status in repository.
 *   - Signal worker to continue next conversation.
 */
class SendRecoveryRepositoryImpl(
    private val queueRepository: QueueRepository? = null,
    private val duplicatePreventionEngine: DuplicatePreventionEngine? = null
) : SendRecoveryRepository {

    override suspend fun recoverFailedSend(criteria: SendRecoveryCriteria): SendRecoveryResult {
        val conversationId = criteria.conversationId
        val replyText = criteria.replyText
        var targetQueueItem = criteria.queueItem

        if (targetQueueItem == null && queueRepository != null) {
            targetQueueItem = queueRepository.findActiveQueueItemBySender(criteria.packageName, conversationId)
        }

        // Guard Requirement 1: Single retry check
        val currentRetryCount = targetQueueItem?.retryCount ?: 0
        if (currentRetryCount >= 1) {
            // Already retried once! Move directly to failed queue.
            val failedItem = moveToFailedQueue(targetQueueItem, criteria.packageName, conversationId)
            return SendRecoveryResult(
                status = SendRecoveryStatus.MOVED_TO_FAILED,
                isSuccess = false,
                retryAttempted = false,
                isDuplicateBlocked = false,
                movedToFailedQueue = true,
                workerContinued = true,
                conversationId = conversationId,
                replyText = replyText,
                updatedQueueItem = failedItem,
                reason = "Retry Limit Reached: Conversation already attempted retry ($currentRetryCount times). Moved to Failed Queue.",
                details = "Enforced strict single-retry rule."
            )
        }

        // Guard Requirement 2: Duplicate check - NEVER create duplicate reply
        if (duplicatePreventionEngine != null) {
            val dupCheck = duplicatePreventionEngine.checkDuplicate(
                replyText = replyText,
                conversationId = conversationId,
                packageName = criteria.packageName
            )

            if (dupCheck.status == DuplicatePreventionStatus.BLOCK) {
                val failedItem = moveToFailedQueue(targetQueueItem, criteria.packageName, conversationId)
                return SendRecoveryResult(
                    status = SendRecoveryStatus.MOVED_TO_FAILED,
                    isSuccess = false,
                    retryAttempted = false,
                    isDuplicateBlocked = true,
                    movedToFailedQueue = true,
                    workerContinued = true,
                    conversationId = conversationId,
                    replyText = replyText,
                    updatedQueueItem = failedItem,
                    reason = "Duplicate Reply Blocked: Duplicate prevention engine detected identical reply sent previously. Retry cancelled and moved to Failed Queue.",
                    details = "Duplicate rule triggered: ${dupCheck.reason}"
                )
            }
        }

        // Increment retry count in queue repository
        val itemInRetry = targetQueueItem?.copy(
            retryCount = currentRetryCount + 1,
            status = QueueStatus.RETRY,
            updatedAt = System.currentTimeMillis()
        )
        if (itemInRetry != null && queueRepository != null) {
            queueRepository.saveQueueItem(itemInRetry)
        }

        // Execute single retry action callback
        val retryOutcome = try {
            criteria.executeRetryAction?.invoke() ?: false
        } catch (e: Exception) {
            false
        }

        if (retryOutcome) {
            // Retry Succeeded! Update item status to SENT
            val sentItem = itemInRetry?.copy(
                status = QueueStatus.SENT,
                updatedAt = System.currentTimeMillis()
            )
            if (sentItem != null && queueRepository != null) {
                queueRepository.saveQueueItem(sentItem)
            }

            return SendRecoveryResult(
                status = SendRecoveryStatus.RETRY_SUCCESS,
                isSuccess = true,
                retryAttempted = true,
                isDuplicateBlocked = false,
                movedToFailedQueue = false,
                workerContinued = true,
                conversationId = conversationId,
                replyText = replyText,
                updatedQueueItem = sentItem,
                reason = "Retry Success: Single retry attempt succeeded. Conversation marked SENT.",
                details = "Retry action returned true."
            )
        } else {
            // Retry Failed! Move conversation to Failed Queue & continue worker
            val failedItem = moveToFailedQueue(itemInRetry, criteria.packageName, conversationId)

            return SendRecoveryResult(
                status = SendRecoveryStatus.MOVED_TO_FAILED,
                isSuccess = false,
                retryAttempted = true,
                isDuplicateBlocked = false,
                movedToFailedQueue = true,
                workerContinued = true,
                conversationId = conversationId,
                replyText = replyText,
                updatedQueueItem = failedItem,
                reason = "Retry Failed: Single retry attempt failed. Moved conversation to Failed Queue. Worker continuing next item.",
                details = "Single retry executed and failed."
            )
        }
    }

    private suspend fun moveToFailedQueue(
        item: QueueItem?,
        packageName: String,
        senderName: String
    ): QueueItem? {
        val targetItem = item ?: queueRepository?.findActiveQueueItemBySender(packageName, senderName)
        val failedItem = targetItem?.copy(
            status = QueueStatus.FAILED,
            updatedAt = System.currentTimeMillis()
        )
        if (failedItem != null && queueRepository != null) {
            queueRepository.saveQueueItem(failedItem)
        }
        return failedItem
    }
}
