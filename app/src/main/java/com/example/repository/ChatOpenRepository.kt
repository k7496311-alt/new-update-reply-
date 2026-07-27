package com.example.repository

import com.example.accessibility.imo.ChatOpenResult
import com.example.model.QueueItem

/**
 * Repository interface for managing IMO chat opening operations.
 * Follows Clean Architecture guidelines and SOLID principles.
 */
interface ChatOpenRepository {
    /**
     * Attempts to open the IMO chat for the provided QueueItem.
     */
    suspend fun openChat(queueItem: QueueItem): ChatOpenResult

    /**
     * Verifies whether the currently visible chat header matches [expectedSender].
     * Returns Pair(isMatches, actualVisibleSenderName).
     */
    suspend fun verifyHeaderSenderName(expectedSender: String): Pair<Boolean, String>

    /**
     * Closes the currently open chat window (performing BACK or clicking back button).
     */
    suspend fun closeCurrentChat(): Boolean
}
