package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

object AccessibilityActionHelper {
    private const val CATEGORY = "ActionHelper"

    /**
     * Performs a safe click on a node.
     * If the node is not clickable, it recursively checks parents for a clickable ancestor.
     * Returns true if the action was successfully performed on the node or one of its ancestors.
     */
    fun safeClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) {
            AccessibilityLogger.w(CATEGORY, "safeClick: Node is null")
            return false
        }

        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                val success = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    AccessibilityLogger.d(CATEGORY, "Clicked node: ${current.className} (${current.viewIdResourceName})")
                    return true
                }
            }
            // Keep a reference to the parent, and recycle the current node if we obtained it
            val parent = current.parent
            current = parent
        }

        AccessibilityLogger.e(CATEGORY, "Failed to click node or find a clickable ancestor")
        return false
    }

    /**
     * Safely inputs text into an editable node using ACTION_SET_TEXT, selection setting, and paste triggers.
     * Returns true if successful.
     */
    fun safeInputText(node: AccessibilityNodeInfo?, text: String, context: android.content.Context? = null): Boolean {
        if (node == null) {
            AccessibilityLogger.w(CATEGORY, "safeInputText: Node is null")
            return false
        }

        // Try focusing and clicking the node first to ensure IME/accessibility readiness
        try {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (e: Exception) {
            AccessibilityLogger.w(CATEGORY, "safeInputText: Focus/Click attempt notice: ${e.message}")
        }

        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        var success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        if (!success && !node.isEditable) {
            // Try parent or child if the container node was passed
            var current: AccessibilityNodeInfo? = node
            for (i in 0 until 3) {
                current = current?.parent ?: break
                if (current.isEditable) {
                    success = current.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    if (success) break
                }
            }
        }

        if (success) {
            // Set selection to end of text to trigger TextWatcher updates
            try {
                val selectionArgs = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, text.length)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
            } catch (e: Exception) {
                // Ignore selection error
            }

            // Copy to clipboard and perform PASTE if context is available, to ensure UI TextWatcher fires
            if (context != null && text.isNotEmpty()) {
                try {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    if (clipboard != null) {
                        val clip = android.content.ClipData.newPlainText("AutoReplyText", text)
                        clipboard.setPrimaryClip(clip)
                        node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    }
                } catch (e: Exception) {
                    AccessibilityLogger.w(CATEGORY, "Clipboard paste fallback note: ${e.message}")
                }
            }

            AccessibilityLogger.d(CATEGORY, "Safely input text: \"$text\" into ${node.viewIdResourceName}")
        } else {
            // Try clipboard paste as direct primary input
            if (context != null && text.isNotEmpty()) {
                try {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    if (clipboard != null) {
                        val clip = android.content.ClipData.newPlainText("AutoReplyText", text)
                        clipboard.setPrimaryClip(clip)
                        success = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    }
                } catch (e: Exception) {
                    AccessibilityLogger.e(CATEGORY, "Clipboard paste failed: ${e.message}")
                }
            }
            if (!success) {
                AccessibilityLogger.e(CATEGORY, "Failed to set text via ACTION_SET_TEXT on ${node.viewIdResourceName}")
            }
        }
        return success
    }

    /**
     * Safely scrolls a node forward (down or right).
     */
    fun safeScrollForward(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) {
            AccessibilityLogger.w(CATEGORY, "safeScrollForward: Node is null")
            return false
        }
        if (!node.isScrollable) {
            AccessibilityLogger.w(CATEGORY, "safeScrollForward: Node is not scrollable")
            return false
        }
        val success = node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        AccessibilityLogger.d(CATEGORY, "safeScrollForward performed: $success")
        return success
    }

    /**
     * Safely scrolls a node backward (up or left).
     */
    fun safeScrollBackward(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) {
            AccessibilityLogger.w(CATEGORY, "safeScrollBackward: Node is null")
            return false
        }
        if (!node.isScrollable) {
            AccessibilityLogger.w(CATEGORY, "safeScrollBackward: Node is not scrollable")
            return false
        }
        val success = node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        AccessibilityLogger.d(CATEGORY, "safeScrollBackward performed: $success")
        return success
    }

    /**
     * Performs a global back action via the accessibility service.
     */
    fun safePerformBack(service: AccessibilityService?): Boolean {
        if (service == null) {
            AccessibilityLogger.w(CATEGORY, "safePerformBack: Service is null")
            return false
        }
        val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        AccessibilityLogger.d(CATEGORY, "safePerformBack global action: $success")
        return success
    }

    /**
     * Performs a global home action via the accessibility service.
     */
    fun safePerformHome(service: AccessibilityService?): Boolean {
        if (service == null) {
            AccessibilityLogger.w(CATEGORY, "safePerformHome: Service is null")
            return false
        }
        val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        AccessibilityLogger.d(CATEGORY, "safePerformHome global action: $success")
        return success
    }

    /**
     * Safe delay that logs entry and exit of the waiting period.
     */
    suspend fun safeDelay(durationMillis: Long, reason: String = "cooldown") {
        AccessibilityLogger.d(CATEGORY, "Starting safe delay of ${durationMillis}ms for: $reason")
        delay(durationMillis)
        AccessibilityLogger.d(CATEGORY, "Completed safe delay for: $reason")
    }

    /**
     * Executes a block with retries, timeout, and a recovery mechanism on failure.
     */
    suspend fun <T> executeWithRetry(
        maxAttempts: Int = 3,
        timeoutMillis: Long = 5000L,
        retryDelayMillis: Long = 1000L,
        onRecovery: (suspend (attempt: Int, error: Throwable) -> Unit)? = null,
        block: suspend () -> T
    ): Result<T> {
        var lastError: Throwable? = null
        for (attempt in 1..maxAttempts) {
            try {
                AccessibilityLogger.d(CATEGORY, "Executing action: Attempt $attempt/$maxAttempts")
                val result = withTimeout(timeoutMillis) {
                    block()
                }
                AccessibilityLogger.i(CATEGORY, "Action completed successfully on attempt $attempt")
                return Result.success(result)
            } catch (e: Throwable) {
                lastError = e
                AccessibilityLogger.w(CATEGORY, "Attempt $attempt failed: ${e.localizedMessage}")
                if (onRecovery != null && attempt < maxAttempts) {
                    try {
                        AccessibilityLogger.i(CATEGORY, "Executing error recovery for attempt $attempt")
                        onRecovery(attempt, e)
                    } catch (recoveryEx: Throwable) {
                        AccessibilityLogger.e(CATEGORY, "Error during recovery execution", recoveryEx)
                    }
                }
                if (attempt < maxAttempts) {
                    safeDelay(retryDelayMillis, "Retry spacing delay")
                }
            }
        }
        val finalError = lastError ?: Exception("Unknown error during action execution")
        AccessibilityLogger.e(CATEGORY, "Action failed completely after $maxAttempts attempts", finalError)
        return Result.failure(finalError)
    }
}
