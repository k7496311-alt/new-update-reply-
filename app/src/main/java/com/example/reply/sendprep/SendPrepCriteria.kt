package com.example.reply.sendprep

import com.example.model.QueueItem
import com.example.reply.duplicate.DuplicatePreventionResult
import com.example.reply.validation.ReplyValidationResult

/**
 * Contextual criteria required to verify system readiness before sending a reply.
 */
data class SendPrepCriteria(
    val conversationId: String,
    val expectedCustomerName: String,
    val activeCustomerName: String? = expectedCustomerName,
    val isConversationOpen: Boolean = true,
    val isInputBoxPresent: Boolean = true,
    val isAccessibilityAlive: Boolean = true,
    val replyText: String,
    val validationResult: ReplyValidationResult? = null,
    val duplicateResult: DuplicatePreventionResult? = null,
    val queueItem: QueueItem? = null
)
