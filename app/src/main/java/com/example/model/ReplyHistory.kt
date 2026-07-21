package com.example.model

/**
 * Domain model representing a log of an automatically sent reply.
 */
data class ReplyHistory(
    val id: Long = 0L,
    val ruleId: Long,
    val ruleName: String,
    val senderName: String,
    val incomingMessage: String,
    val repliedMessage: String,
    val packageName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccessfullySent: Boolean = true,
    val createdAt: Long = timestamp,
    val updatedAt: Long = System.currentTimeMillis(),
    val status: HistoryStatus = if (isSuccessfullySent) HistoryStatus.SENT else HistoryStatus.FAILED,
    val reason: String = ""
)

enum class HistoryStatus {
    SENT,
    FAILED,
    SKIPPED
}
