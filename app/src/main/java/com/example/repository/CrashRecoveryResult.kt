package com.example.repository

import com.example.model.Conversation
import com.example.model.QueueItem
import com.example.model.ReplyHistory

/**
 * Detailed result model returned after crash recovery execution.
 */
data class CrashRecoveryResult(
    val restoredQueueItems: List<QueueItem>,
    val restoredConversation: Conversation?,
    val pendingReplyText: String?,
    val restoredHistoryList: List<ReplyHistory>,
    val isDuplicatePrevented: Boolean,
    val isSuccess: Boolean,
    val summaryMessage: String
)
