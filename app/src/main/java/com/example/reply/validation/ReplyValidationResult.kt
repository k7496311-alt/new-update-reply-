package com.example.reply.validation

/**
 * Result model holding reply validation status, detailed reason, and failed rule checks.
 */
data class ReplyValidationResult(
    val status: ReplyValidationStatus,
    val isValid: Boolean,
    val reason: String,
    val ruleId: Long?,
    val replyText: String?,
    val characterCount: Int,
    val failedChecks: List<String> = emptyList(),
    val details: String = ""
)
