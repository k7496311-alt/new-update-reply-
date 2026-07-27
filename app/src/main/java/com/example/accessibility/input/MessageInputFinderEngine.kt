package com.example.accessibility.input

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.repository.MessageInputFinderRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-grade Message Input Finder Engine.
 *
 * Requirements:
 * - Search entire Accessibility tree.
 * - Locate ONLY editable message input field.
 * - Verify:
 *   - Editable
 *   - Enabled
 *   - Visible
 *   - Focusable
 *   - Belongs to IMO chat screen
 * - If multiple EditText exist:
 *   - Select message composer only.
 * - Emits exact required logs:
 *   - Input Found
 *   - Input Verified
 *   - Multiple Inputs
 *   - Wrong Input
 *   - Input Missing
 * - Return:
 *   - Input Node
 *   - Bounds
 *   - Node ID
 *   - Verification Result
 * - Constraints:
 *   - DO NOT insert text.
 *   - DO NOT click.
 *   - No placeholder.
 */
class MessageInputFinderEngine(
    private val repository: MessageInputFinderRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun findAndVerifyInput(
        rootNode: AccessibilityNodeInfo?,
        criteria: MessageInputFinderCriteria = MessageInputFinderCriteria()
    ): MessageInputVerificationResult = withContext(dispatcher) {

        val result = repository.findAndVerifyMessageInput(rootNode, criteria)

        // 1. Log: Input Found (if any candidate input nodes were detected)
        if (result.candidateNodesCount > 0) {
            logInputFound(result.candidateNodesCount, result.nodeId, result.bounds)
        }

        // 2. Log: Multiple Inputs (if more than 1 editable candidate exists)
        if (result.candidateNodesCount > 1) {
            logMultipleInputs(result.candidateNodesCount, result.nodeId, result.bounds)
        }

        // 3. Log: Wrong Input (if candidate nodes were evaluated but rejected)
        if (result.wrongInputReasons.isNotEmpty()) {
            logWrongInput(result.wrongInputReasons)
        }

        if (result.isVerified) {
            // 4. Log: Input Verified
            logInputVerified(result)
        } else {
            // 5. Log: Input Missing
            logInputMissing(result.reason)
        }

        result
    }

    private fun logInputFound(candidateCount: Int, selectedNodeId: String?, bounds: Rect) {
        val logMsg = """
            Input Found
            Candidates Detected: $candidateCount editable field(s)
            Selected ID: "${selectedNodeId ?: "Unknown"}"
            Screen Bounds: $bounds
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Input Found",
            logMsg
        )
    }

    private fun logInputVerified(result: MessageInputVerificationResult) {
        val logMsg = """
            Input Verified
            Node ID: "${result.nodeId ?: "Unknown"}"
            Class: "${result.className ?: "EditText"}"
            Bounds: ${result.bounds}
            Checks: Editable=${result.isEditable}, Enabled=${result.isEnabled}, Visible=${result.isVisible}, Focusable=${result.isFocusable}, BelongsToImo=${result.belongsToImoChat}
            Status: Message Composer Successfully Verified
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.ACCESSIBILITY,
            "Input Verified",
            logMsg
        )
    }

    private fun logMultipleInputs(candidateCount: Int, chosenId: String?, chosenBounds: Rect) {
        val logMsg = """
            Multiple Inputs
            Total EditText Candidates Found: $candidateCount
            Resolution: Selected message composer field ID "${chosenId ?: "Unknown"}" at lower screen bounds $chosenBounds and filtered out search/filter inputs.
        """.trimIndent()

        Log.w(TAG, logMsg)
        AppLogger.warning(
            LogCategory.ACCESSIBILITY,
            "Multiple Inputs",
            logMsg
        )
    }

    private fun logWrongInput(rejectionReasons: List<String>) {
        val logMsg = """
            Wrong Input
            Rejected Candidate Field(s):
            ${rejectionReasons.joinToString("\n")}
        """.trimIndent()

        Log.w(TAG, logMsg)
        AppLogger.warning(
            LogCategory.ACCESSIBILITY,
            "Wrong Input",
            logMsg
        )
    }

    private fun logInputMissing(reason: String) {
        val logMsg = """
            Input Missing
            Status: INPUT_MISSING / UNVERIFIED
            Reason: $reason
        """.trimIndent()

        Log.w(TAG, logMsg)
        AppLogger.warning(
            LogCategory.ACCESSIBILITY,
            "Input Missing",
            logMsg
        )
    }

    companion object {
        private const val TAG = "MessageInputFinderEngine"
    }
}
