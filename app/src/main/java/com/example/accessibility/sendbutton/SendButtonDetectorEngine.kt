package com.example.accessibility.sendbutton

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.repository.SendButtonDetectorRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-grade Send Button Detector Engine.
 *
 * Requirements:
 * - Search Accessibility tree.
 * - Verify:
 *   - Clickable
 *   - Visible
 *   - Enabled
 *   - Located beside message composer
 * - Return:
 *   - Send Button Node
 *   - Bounds
 *   - Verification Result
 * - Emits required logs:
 *   - Send Button Found
 *   - Verified
 *   - Missing
 * - Constraints:
 *   - NO clicking.
 *   - Only detection.
 *   - Production quality.
 */
class SendButtonDetectorEngine(
    private val repository: SendButtonDetectorRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun detectSendButton(
        rootNode: AccessibilityNodeInfo?,
        criteria: SendButtonDetectorCriteria = SendButtonDetectorCriteria()
    ): SendButtonVerificationResult = withContext(dispatcher) {

        val result = repository.findAndVerifySendButton(rootNode, criteria)

        // 1. Log: Send Button Found (if any candidates detected)
        if (result.candidateCount > 0) {
            logSendButtonFound(result.candidateCount, result.nodeId, result.bounds)
        }

        if (result.isVerified) {
            // 2. Log: Verified
            logVerified(result)
        } else {
            // 3. Log: Missing
            logMissing(result.reason)
        }

        result
    }

    private fun logSendButtonFound(candidateCount: Int, nodeId: String?, bounds: Rect) {
        val logMsg = """
            Send Button Found
            Candidate Count: $candidateCount node(s) detected
            Node ID: "${nodeId ?: "Unknown"}"
            Bounds: $bounds
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Send Button Found",
            logMsg
        )
    }

    private fun logVerified(result: SendButtonVerificationResult) {
        val logMsg = """
            Verified
            Node ID: "${result.nodeId ?: "Unknown"}"
            Class: "${result.className ?: "ImageButton"}"
            Bounds: ${result.bounds}
            Content Description: "${result.contentDescription ?: "N/A"}"
            Status: Clickable=${result.isClickable}, Visible=${result.isVisible}, Enabled=${result.isEnabled}, BesideComposer=${result.isBesideComposer}
            Result: Send Button Verified & Ready (No click performed)
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.ACCESSIBILITY,
            "Verified",
            logMsg
        )
    }

    private fun logMissing(reason: String) {
        val logMsg = """
            Missing
            Status: MISSING / UNVERIFIED
            Reason: $reason
        """.trimIndent()

        Log.w(TAG, logMsg)
        AppLogger.warning(
            LogCategory.ACCESSIBILITY,
            "Missing",
            logMsg
        )
    }

    companion object {
        private const val TAG = "SendButtonDetectorEngine"
    }
}
