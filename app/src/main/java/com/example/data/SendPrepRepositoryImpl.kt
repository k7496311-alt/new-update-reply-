package com.example.data

import com.example.accessibility.AccessibilityManager
import com.example.model.QueueStatus
import com.example.reply.duplicate.DuplicatePreventionStatus
import com.example.reply.sendprep.SendPrepCriteria
import com.example.reply.sendprep.SendPrepResult
import com.example.reply.sendprep.SendPrepStatus
import com.example.reply.validation.ReplyValidationStatus
import com.example.repository.SendPrepRepository

/**
 * Concrete implementation of SendPrepRepository.
 * Validates system state against all 7 pre-sending criteria before any send action occurs.
 */
class SendPrepRepositoryImpl(
    private val accessibilityManager: AccessibilityManager? = null
) : SendPrepRepository {

    override suspend fun verifySendReadiness(criteria: SendPrepCriteria): SendPrepResult {
        val failedChecks = mutableListOf<String>()

        // 1. Accessibility alive
        val isAccessibilityAlive = criteria.isAccessibilityAlive &&
                (accessibilityManager?.isServiceRunning() ?: true)
        if (!isAccessibilityAlive) {
            failedChecks.add("Accessibility service is not active")
        }

        // 2. Queue item active
        val isQueueItemActive = criteria.queueItem != null && (
                criteria.queueItem.status == QueueStatus.INCOMING ||
                        criteria.queueItem.status == QueueStatus.PENDING ||
                        criteria.queueItem.status == QueueStatus.PROCESSING ||
                        criteria.queueItem.status == QueueStatus.RETRY
                )
        if (!isQueueItemActive) {
            failedChecks.add("Queue item is not active or missing")
        }

        // 3. Conversation still open
        val isConversationOpen = criteria.isConversationOpen
        if (!isConversationOpen) {
            failedChecks.add("Conversation is no longer open")
        }

        // 4. Same customer
        val isSameCustomer = criteria.activeCustomerName != null &&
                (criteria.activeCustomerName.equals(criteria.expectedCustomerName, ignoreCase = true) ||
                        criteria.expectedCustomerName.contains(criteria.activeCustomerName, ignoreCase = true) ||
                        criteria.activeCustomerName.contains(criteria.expectedCustomerName, ignoreCase = true))
        if (!isSameCustomer) {
            failedChecks.add("Active customer name ('${criteria.activeCustomerName}') does not match expected ('${criteria.expectedCustomerName}')")
        }

        // 5. Input box exists
        val isInputBoxPresent = criteria.isInputBoxPresent
        if (!isInputBoxPresent) {
            failedChecks.add("Input box does not exist in target view")
        }

        // 6. Reply VALID
        val isReplyValid = criteria.replyText.isNotBlank() &&
                (criteria.validationResult == null || criteria.validationResult.status == ReplyValidationStatus.VALID)
        if (!isReplyValid) {
            failedChecks.add("Reply is invalid or failed validation checks")
        }

        // 7. Duplicate check passed
        val isDuplicatePassed = criteria.duplicateResult == null ||
                criteria.duplicateResult.status == DuplicatePreventionStatus.ALLOW
        if (!isDuplicatePassed) {
            failedChecks.add("Duplicate reply check failed (blocked by duplicate engine)")
        }

        val isOverallReady = failedChecks.isEmpty()
        val status = if (isOverallReady) SendPrepStatus.READY_TO_SEND else SendPrepStatus.NOT_READY
        val reason = if (isOverallReady) {
            "All 7 send preparation checks passed successfully. System is READY_TO_SEND."
        } else {
            "Send Preparation Failed: ${failedChecks.joinToString("; ")}."
        }

        return SendPrepResult(
            status = status,
            isReady = isOverallReady,
            reason = reason,
            conversationVerified = isConversationOpen && isSameCustomer,
            sameCustomerVerified = isSameCustomer,
            inputBoxVerified = isInputBoxPresent,
            accessibilityAlive = isAccessibilityAlive,
            replyValid = isReplyValid,
            duplicateCheckPassed = isDuplicatePassed,
            queueItemActive = isQueueItemActive,
            conversationId = criteria.conversationId,
            expectedCustomerName = criteria.expectedCustomerName,
            replyText = criteria.replyText,
            queueItem = criteria.queueItem,
            failedChecks = failedChecks
        )
    }
}
