package com.example.accessibility.imo

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.repository.BottomVerificationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Production-grade Bottom Position Verification Engine.
 * Requirements:
 * - Confirms newest message is visible after Jump To Latest.
 * - If not at bottom, performs controlled downward scroll.
 * - Repeats until bottom is reached or maximum retry reached (prevents infinite scrolling / overscrolling).
 * - Generates exact required logs:
 *   - Bottom Verified
 *   - Scroll Down
 *   - Retry
 *   - Bottom Failed
 *   - Current Position
 *   - Visible Message Count
 * - Stops cleanly after verification without reading, replying, or inserting text.
 */
class BottomVerificationEngine(
    private val repository: BottomVerificationRepository,
    private val maxScrollRetries: Int = 3,
    private val scrollDelayMs: Long = 400L,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun verifyBottomPosition(): BottomVerificationResult = withContext(dispatcher) {
        // 1. Initial State Check
        var state = repository.checkBottomState()

        logCurrentPosition(state.currentPosition)
        logVisibleMessageCount(state.visibleMessageCount)

        if (state.isAtBottom) {
            logBottomVerified(scrollCount = 0, messageCount = state.visibleMessageCount)
            return@withContext BottomVerificationResult(
                status = BottomVerificationStatus.BOTTOM_VERIFIED,
                isAtBottom = true,
                scrollCount = 0,
                visibleMessageCount = state.visibleMessageCount,
                currentPosition = state.currentPosition,
                details = "Chat verified to be positioned at newest message (0 scrolls required)."
            )
        }

        // 2. Controlled downward scrolling loop (maxRetries limit prevents infinite scrolling)
        var scrollCount = 0
        while (scrollCount < maxScrollRetries) {
            scrollCount++

            // Log: Scroll Down
            logScrollDown(attempt = scrollCount, maxAttempts = maxScrollRetries)

            val scrollSuccess = repository.performControlledScrollDown()
            if (!scrollSuccess) {
                Log.w(TAG, "Controlled scroll down attempt $scrollCount returned false (end of scroll or non-scrollable)")
            }

            delay(scrollDelayMs)

            state = repository.checkBottomState()

            // Log: Current Position & Visible Message Count
            logCurrentPosition(state.currentPosition)
            logVisibleMessageCount(state.visibleMessageCount)

            if (state.isAtBottom) {
                logBottomVerified(scrollCount = scrollCount, messageCount = state.visibleMessageCount)
                return@withContext BottomVerificationResult(
                    status = BottomVerificationStatus.BOTTOM_VERIFIED,
                    isAtBottom = true,
                    scrollCount = scrollCount,
                    visibleMessageCount = state.visibleMessageCount,
                    currentPosition = state.currentPosition,
                    details = "Bottom position reached successfully after $scrollCount controlled downward scroll(s)."
                )
            } else {
                // Log: Retry
                logRetry(attempt = scrollCount, maxAttempts = maxScrollRetries)
            }
        }

        // 3. Max retries exceeded without reaching bottom
        logBottomFailed(maxRetries = maxScrollRetries, lastPosition = state.currentPosition)
        return@withContext BottomVerificationResult(
            status = BottomVerificationStatus.BOTTOM_FAILED,
            isAtBottom = false,
            scrollCount = maxScrollRetries,
            visibleMessageCount = state.visibleMessageCount,
            currentPosition = state.currentPosition,
            details = "Failed to confirm bottom position after $maxScrollRetries controlled scroll attempts."
        )
    }

    private fun logBottomVerified(scrollCount: Int, messageCount: Int) {
        val logMsg = """
            Bottom Verified
            Scrolls Performed: $scrollCount
            Visible Messages: $messageCount
            Status: Chat is confirmed positioned at the newest message.
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.ACCESSIBILITY,
            "Bottom Verified",
            logMsg
        )
    }

    private fun logScrollDown(attempt: Int, maxAttempts: Int) {
        val logMsg = """
            Scroll Down
            Attempt: $attempt/$maxAttempts
            Action: Executing controlled downward scroll towards newest message
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Scroll Down",
            logMsg
        )
    }

    private fun logRetry(attempt: Int, maxAttempts: Int) {
        val logMsg = """
            Retry
            Attempt: $attempt/$maxAttempts
            Reason: Bottom not yet reached. Retrying controlled downward scroll.
        """.trimIndent()

        Log.w(TAG, logMsg)
        AppLogger.warning(
            LogCategory.ACCESSIBILITY,
            "Retry",
            logMsg
        )
    }

    private fun logBottomFailed(maxRetries: Int, lastPosition: String) {
        val logMsg = """
            Bottom Failed
            Max Scrolls Reached: $maxRetries
            Last Known Position: $lastPosition
            Reason: Unable to reach newest message bottom within maximum allowed scroll attempts.
        """.trimIndent()

        Log.e(TAG, logMsg)
        AppLogger.critical(
            LogCategory.ACCESSIBILITY,
            "Bottom Failed",
            logMsg
        )
    }

    private fun logCurrentPosition(position: String) {
        val logMsg = "Current Position: $position"
        Log.d(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Current Position",
            logMsg
        )
    }

    private fun logVisibleMessageCount(count: Int) {
        val logMsg = "Visible Message Count: $count"
        Log.d(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Visible Message Count",
            logMsg
        )
    }

    companion object {
        private const val TAG = "BottomVerificationEngine"
    }
}
