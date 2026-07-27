package com.example.accessibility.imo

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.repository.MessageBubbleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-grade Message Bubble Detection Engine.
 * Requirements:
 * - Uses Accessibility tree from Step 9
 * - Detects every chat bubble
 * - Classifies each node deterministically into exactly one type
 * - Saves chronological top-to-bottom layout order
 * - Emits exact required logs:
 *   - Bubble Count
 *   - Incoming Count
 *   - Outgoing Count
 *   - Sticker Count
 *   - Missed Call Count
 *   - Unknown Count
 * - Performs NO reply, NO keyword matching, ONLY bubble classification.
 */
class MessageBubbleDetectionEngine(
    private val repository: MessageBubbleRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun detectAndClassifyBubbles(
        scanReport: UiScanReport? = null
    ): MessageBubbleDetectionResult = withContext(dispatcher) {

        val result = repository.detectAndClassifyMessageBubbles(scanReport)

        // 1. Log: Bubble Count
        logBubbleCount(result.bubbleCount)

        // 2. Log: Incoming Count
        logIncomingCount(result.incomingCount)

        // 3. Log: Outgoing Count
        logOutgoingCount(result.outgoingCount)

        // 4. Log: Sticker Count
        logStickerCount(result.stickerCount)

        // 5. Log: Missed Call Count
        logMissedCallCount(result.missedCallCount)

        // 6. Log: Unknown Count
        logUnknownCount(result.unknownCount)

        // 7. Log summary breakdown of classified bubbles in chronological order
        logBubbleDetails(result.bubbles)

        result
    }

    private fun logBubbleCount(count: Int) {
        val msg = "Bubble Count: $count"
        Log.i(TAG, msg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Bubble Count",
            msg
        )
    }

    private fun logIncomingCount(count: Int) {
        val msg = "Incoming Count: $count"
        Log.i(TAG, msg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Incoming Count",
            msg
        )
    }

    private fun logOutgoingCount(count: Int) {
        val msg = "Outgoing Count: $count"
        Log.i(TAG, msg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Outgoing Count",
            msg
        )
    }

    private fun logStickerCount(count: Int) {
        val msg = "Sticker Count: $count"
        Log.i(TAG, msg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Sticker Count",
            msg
        )
    }

    private fun logMissedCallCount(count: Int) {
        val msg = "Missed Call Count: $count"
        Log.i(TAG, msg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Missed Call Count",
            msg
        )
    }

    private fun logUnknownCount(count: Int) {
        val msg = "Unknown Count: $count"
        Log.i(TAG, msg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Unknown Count",
            msg
        )
    }

    private fun logBubbleDetails(bubbles: List<MessageBubbleModel>) {
        if (bubbles.isEmpty()) {
            val msg = "No chat bubbles detected in current Accessibility UI tree."
            Log.d(TAG, msg)
            return
        }

        val sb = StringBuilder()
        sb.append("Chronological Chat Bubbles (${bubbles.size} total):\n")
        bubbles.forEachIndexed { idx, bubble ->
            val detail = "#${idx + 1} | Type: ${bubble.type} | ID: '${bubble.resourceId}' | Text: '${bubble.text}' | Desc: '${bubble.contentDescription}' | Bounds: ${bubble.bounds.toShortString()}"
            Log.d(TAG, detail)
            sb.append(detail).append("\n")
        }

        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Chat Bubbles Summary",
            "Classified ${bubbles.size} bubbles in chronological order."
        )
    }

    companion object {
        private const val TAG = "MessageBubbleDetectionEngine"
    }
}
