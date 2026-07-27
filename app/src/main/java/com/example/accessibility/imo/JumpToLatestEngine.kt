package com.example.accessibility.imo

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.repository.JumpToLatestRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-grade Jump To Latest Engine.
 * Requirements:
 * - Searches entire Accessibility tree for floating "Jump To Latest" button
 * - Evaluates AccessibilityNodeInfo, ContentDescription, Clickable, Visible, Bounds
 * - If found: clicks button once, waits until button disappears, verifies latest position reached
 * - If not found: continues normally without interruption
 * - Generates exact required logs:
 *   - Jump Button Found
 *   - Jump Button Clicked
 *   - Jump Success
 *   - Jump Not Found
 *   - Verification Success
 */
class JumpToLatestEngine(
    private val repository: JumpToLatestRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun executeJumpToLatest(): JumpToLatestResult = withContext(dispatcher) {
        val buttonNode = repository.findJumpToLatestButton()

        if (buttonNode == null) {
            logJumpNotFound()
            return@withContext JumpToLatestResult(
                status = JumpToLatestStatus.JUMP_NOT_FOUND,
                buttonFound = false,
                buttonClicked = false,
                verificationSuccess = true,
                details = "No floating Jump to Latest button detected on chat screen. Continuing normally."
            )
        }

        try {
            // 1. Log: Jump Button Found
            logJumpButtonFound(buttonNode)

            // 2. Click button once
            val clicked = repository.clickJumpButton(buttonNode)
            if (clicked) {
                // 3. Log: Jump Button Clicked
                logJumpButtonClicked()
            } else {
                Log.w(TAG, "Attempted click on Jump To Latest button failed")
            }

            // 4. Wait until button disappears and verify latest position reached
            val verified = repository.verifyJumpCompleted(timeoutMs = 2500L, pollIntervalMs = 200L)

            return@withContext if (verified) {
                // 5. Log: Verification Success
                logVerificationSuccess()
                // 6. Log: Jump Success
                logJumpSuccess()

                JumpToLatestResult(
                    status = JumpToLatestStatus.JUMP_SUCCESS,
                    buttonFound = true,
                    buttonClicked = clicked,
                    verificationSuccess = true,
                    details = "Jump to latest message completed and verified successfully"
                )
            } else {
                Log.w(TAG, "Jump button did not disappear after click within timeout window")
                JumpToLatestResult(
                    status = JumpToLatestStatus.JUMP_FAILED,
                    buttonFound = true,
                    buttonClicked = clicked,
                    verificationSuccess = false,
                    details = "Jump button remained visible after click attempt"
                )
            }
        } finally {
            buttonNode.recycle()
        }
    }

    private fun logJumpButtonFound(node: android.view.accessibility.AccessibilityNodeInfo) {
        val resId = node.viewIdResourceName ?: "N/A"
        val desc = node.contentDescription?.toString() ?: "N/A"
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)

        val logMsg = """
            Jump Button Found
            Resource ID: $resId
            Content Description: $desc
            Clickable: ${node.isClickable}
            Visible: ${node.isVisibleToUser}
            Bounds: ${bounds.toShortString()}
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Jump Button Found",
            logMsg
        )
    }

    private fun logJumpButtonClicked() {
        val logMsg = "Jump Button Clicked"
        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Jump Button Clicked",
            logMsg
        )
    }

    private fun logVerificationSuccess() {
        val logMsg = """
            Verification Success
            Details: Floating Jump button disappeared. Chat confirmed at latest position.
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.ACCESSIBILITY,
            "Verification Success",
            logMsg
        )
    }

    private fun logJumpSuccess() {
        val logMsg = """
            Jump Success
            Status: Successfully navigated chat to latest messages.
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.ACCESSIBILITY,
            "Jump Success",
            logMsg
        )
    }

    private fun logJumpNotFound() {
        val logMsg = """
            Jump Not Found
            Details: Floating Jump To Latest button is not present. Chat is already at latest position or normal view. Continuing normally.
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Jump Not Found",
            logMsg
        )
    }

    companion object {
        private const val TAG = "JumpToLatestEngine"
    }
}
