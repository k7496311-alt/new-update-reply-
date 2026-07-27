package com.example.queue

import com.example.model.QueueItem

/**
 * Contextual criteria for executing multi-conversation queue processing.
 */
data class MultiConversationCriteria(
    val packageName: String = "com.imo.android.imoim",
    val autoExecuteReplies: Boolean = true,
    val executeConversationAction: (suspend (QueueItem) -> Boolean)? = null
)
