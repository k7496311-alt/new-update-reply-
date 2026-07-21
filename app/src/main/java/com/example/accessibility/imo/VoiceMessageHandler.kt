package com.example.accessibility.imo

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.accessibility.AccessibilityActionHelper
import com.example.accessibility.AccessibilityLogger
import com.example.accessibility.AccessibilityManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.regex.Pattern

/**
 * VoiceMessageHandler is responsible for detecting voice message bubbles, triggering
 * transcription via IMO's "A" button, waiting for transcribed text, reading it,
 * and performing error recovery and safety checks.
 */
class VoiceMessageHandler(
    private val context: Context,
    private val accessibilityManager: AccessibilityManager,
    private val nodeScanner: IMONodeScanner,
    private val transcriptManager: VoiceTranscriptManager
) {

    companion object {
        private const val TAG = "VoiceMessageHandler"
        private val DURATION_PATTERN = Pattern.compile("^\\d+:\\d{2}$")
        private const val MAX_RETRIES = 3
        private const val CLICK_DELAY_MS = 500L
        private const val RETRY_DELAY_MS = 1000L
        private const val TRANSCRIPT_WAIT_MS = 5000L
        private const val TOTAL_TIMEOUT_MS = 10000L
    }

    // Flag indicating whether the user is actively touching the screen or interacting
    @Volatile
    private var isUserInterfering = false
    private var lastUserInteractionTime = 0L

    /**
     * Set user interference status. Call this when human activity is detected.
     */
    fun setUserInterfering(interfering: Boolean) {
        isUserInterfering = interfering
        if (interfering) {
            lastUserInteractionTime = System.currentTimeMillis()
            AccessibilityLogger.w(TAG, "User interaction detected. Pausing automations.")
        }
    }

    /**
     * Checks if we should pause or abort because of user interference.
     */
    private fun checkUserInterference(): Boolean {
        if (isUserInterfering) return true
        if (System.currentTimeMillis() - lastUserInteractionTime < 3000L) {
            return true
        }
        return false
    }

    /**
     * Main entry point to handle voice-to-text transcription of the last visible voice message in the chat.
     */
    suspend fun transcribeLastVoiceMessage(): VoiceTranscriptResult {
        val startTime = System.currentTimeMillis()
        AccessibilityLogger.i(TAG, "Starting voice-to-text transcript workflow")

        if (checkUserInterference()) {
            return VoiceTranscriptResult(
                originalText = null,
                success = false,
                errorMessage = "User interaction in progress, transcription aborted for safety.",
                processingTime = System.currentTimeMillis() - startTime
            )
        }

        // Wait 500ms after screen stable before performing any operations
        delay(CLICK_DELAY_MS)

        var lastError: String? = null
        for (attempt in 1..MAX_RETRIES) {
            AccessibilityLogger.d(TAG, "Transcription attempt $attempt/$MAX_RETRIES")

            if (checkUserInterference()) {
                return VoiceTranscriptResult(
                    originalText = null,
                    success = false,
                    errorMessage = "User interrupted during attempt $attempt",
                    processingTime = System.currentTimeMillis() - startTime
                )
            }

            val root = accessibilityManager.getRootNode()
            if (root == null) {
                lastError = "Could not retrieve screen root node"
                delay(RETRY_DELAY_MS)
                continue
            }

            // Step 1: Detect voice message bubble
            val voiceNodeInfo = detectLastVoiceMessageBubble(root)
            if (voiceNodeInfo == null) {
                root.recycle()
                lastError = "No eligible voice message bubble detected"
                AccessibilityLogger.w(TAG, "No eligible voice bubble found: $lastError")
                delay(RETRY_DELAY_MS)
                continue
            }

            val bubbleNode = voiceNodeInfo.bubbleNode
            val aButton = voiceNodeInfo.aButtonNode
            val messageKey = voiceNodeInfo.uniqueKey

            // Step 2: Confirm if already transcribed
            if (transcriptManager.isTranscribed(messageKey)) {
                val cached = transcriptManager.getTranscript(messageKey)
                AccessibilityLogger.i(TAG, "Voice message is already transcribed. Returning cached transcript.")
                bubbleNode.recycle()
                aButton?.recycle()
                root.recycle()
                return VoiceTranscriptResult(
                    originalText = cached,
                    success = true,
                    errorMessage = null,
                    processingTime = System.currentTimeMillis() - startTime
                )
            }

            if (aButton == null) {
                bubbleNode.recycle()
                root.recycle()
                lastError = "Voice message found but the 'A' (transcribe) button was not detected"
                AccessibilityLogger.e(TAG, lastError)
                delay(RETRY_DELAY_MS)
                continue
            }

            // Save state of text elements prior to click to observe dynamic changes
            val existingTexts = extractTextsFromNode(bubbleNode)

            // Step 3: Click "A" button
            AccessibilityLogger.i(TAG, "Clicking 'A' button to initiate transcription...")
            val clicked = AccessibilityActionHelper.safeClick(aButton)
            aButton.recycle()

            if (!clicked) {
                bubbleNode.recycle()
                root.recycle()
                lastError = "Failed to click 'A' button"
                AccessibilityLogger.w(TAG, lastError)
                delay(RETRY_DELAY_MS)
                continue
            }

            // Step 4: Wait for transcript to appear (3-5 seconds)
            AccessibilityLogger.d(TAG, "Waiting for transcription results...")
            val transcript = waitForNewTranscriptText(messageKey, bubbleNode, existingTexts)
            bubbleNode.recycle()
            root.recycle()

            if (transcript != null) {
                // Success! Store in-memory cache and return result
                transcriptManager.saveTranscript(messageKey, transcript)
                return VoiceTranscriptResult(
                    originalText = transcript,
                    success = true,
                    errorMessage = null,
                    processingTime = System.currentTimeMillis() - startTime
                )
            } else {
                lastError = "Transcription timed out or returned empty result"
                AccessibilityLogger.w(TAG, lastError)
                delay(RETRY_DELAY_MS)
            }
        }

        return VoiceTranscriptResult(
            originalText = null,
            success = false,
            errorMessage = lastError ?: "Unknown transcription error",
            processingTime = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Scans the active screen hierarchical tree to detect the last visible voice message bubble
     * that meets all of the core criteria.
     */
    fun detectLastVoiceMessageBubble(root: AccessibilityNodeInfo): VoiceBubbleInfo? {
        val voiceBubbles = mutableListOf<VoiceBubbleInfo>()
        findVoiceBubblesRecursive(root, voiceBubbles)

        // Return the last voice message bubble (typically the most recent one at the bottom of the screen)
        return voiceBubbles.lastOrNull()
    }

    private fun findVoiceBubblesRecursive(node: AccessibilityNodeInfo, list: MutableList<VoiceBubbleInfo>) {
        if (isVoiceMessageBubble(node)) {
            val aButton = findAButtonInOrNear(node)
            val uniqueKey = generateUniqueMessageKey(node)
            list.add(
                VoiceBubbleInfo(
                    bubbleNode = AccessibilityNodeInfo.obtain(node),
                    aButtonNode = aButton,
                    uniqueKey = uniqueKey
                )
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findVoiceBubblesRecursive(child, list)
            child.recycle()
        }
    }

    /**
     * Applies criteria to check if a node qualifies as a Voice Message Bubble.
     * Criteria:
     * 1. Contains voice UI elements (like resource-id or descriptions matching voice, sound, audio)
     * 2. Contains duration text matching "0:05", "1:30" (pattern match: \d+:\d{2})
     * 3. Contains a waveform visualization (custom wave components, or seeks/bars/icons)
     * 4. Near or inside contains an "A" button.
     */
    private fun isVoiceMessageBubble(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString() ?: ""
        val resId = node.viewIdResourceName ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""

        // Filter out obviously non-bubble layouts early
        if (node.childCount == 0) return false

        // Heuristics check: does it or its descendants have the markers?
        val hasVoiceIndicator = resId.contains("voice") || 
                resId.contains("audio") || 
                resId.contains("sound") || 
                contentDesc.contains("voice", ignoreCase = true) || 
                contentDesc.contains("audio", ignoreCase = true)

        val hasDurationText = hasDescendantMatching(node) { child ->
            val text = child.text?.toString() ?: ""
            DURATION_PATTERN.matcher(text).matches()
        }

        val hasWaveform = hasDescendantMatching(node) { child ->
            val id = child.viewIdResourceName ?: ""
            val desc = child.contentDescription?.toString() ?: ""
            val clsName = child.className?.toString() ?: ""
            id.contains("wave") || 
                    id.contains("progress") || 
                    id.contains("seek") || 
                    id.contains("visualizer") ||
                    desc.contains("waveform", ignoreCase = true) ||
                    desc.contains("visualizer", ignoreCase = true) ||
                    clsName.contains("ProgressBar") || 
                    clsName.contains("SeekBar")
        }

        val hasAButton = hasDescendantMatching(node) { child ->
            val text = child.text?.toString() ?: ""
            val id = child.viewIdResourceName ?: ""
            val desc = child.contentDescription?.toString() ?: ""
            text == "A" || 
                    id.contains("transcribe") || 
                    id.contains("voice_to_text") ||
                    desc.contains("transcribe", ignoreCase = true)
        }

        return (hasVoiceIndicator || hasDurationText || hasWaveform) && hasAButton
    }

    private fun findAButtonInOrNear(bubble: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Search inside the bubble first
        val button = findFirstDescendant(bubble) { child ->
            val text = child.text?.toString() ?: ""
            val id = child.viewIdResourceName ?: ""
            val desc = child.contentDescription?.toString() ?: ""
            text == "A" || 
                    id.contains("transcribe") || 
                    id.contains("voice_to_text") ||
                    desc.contains("transcribe", ignoreCase = true)
        }
        if (button != null) return button

        // Search siblings if any
        val parent = bubble.parent ?: return null
        for (i in 0 until parent.childCount) {
            val sibling = parent.getChild(i) ?: continue
            val text = sibling.text?.toString() ?: ""
            val id = sibling.viewIdResourceName ?: ""
            val desc = sibling.contentDescription?.toString() ?: ""
            if (text == "A" || id.contains("transcribe") || desc.contains("transcribe", ignoreCase = true)) {
                return sibling // Sibling is already obtained
            }
            sibling.recycle()
        }
        parent.recycle()
        return null
    }

    /**
     * Polls the active screen to detect the appearance of transcribed text inside or under the voice bubble.
     */
    private suspend fun waitForNewTranscriptText(
        messageKey: String,
        bubbleNode: AccessibilityNodeInfo,
        existingTexts: Set<String>
    ): String? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < TRANSCRIPT_WAIT_MS) {
            if (checkUserInterference()) return null

            // Re-acquire fresh root to inspect screen updates
            val freshRoot = accessibilityManager.getRootNode()
            if (freshRoot != null) {
                // Find our voice bubble inside the fresh hierarchy by comparing bounds
                val freshBubble = findNodeByBounds(freshRoot, bubbleNode)
                if (freshBubble != null) {
                    val freshTexts = extractTextsFromNode(freshBubble)
                    
                    // Filter out existing texts, durations, empty entries, and the "A" text
                    val newTexts = freshTexts.filter { text ->
                        text.isNotEmpty() &&
                        text != "A" &&
                        !DURATION_PATTERN.matcher(text).matches() &&
                        !existingTexts.contains(text) &&
                        !text.contains("transcribe", ignoreCase = true) &&
                        !text.contains("failed", ignoreCase = true)
                    }

                    if (newTexts.isNotEmpty()) {
                        val transcript = newTexts.joinToString(" ").trim()
                        freshBubble.recycle()
                        freshRoot.recycle()
                        return transcript
                    }

                    // Check if IMO shows a "transcription failed" error text explicitly
                    val hasFailedText = freshTexts.any { it.contains("transcription failed", ignoreCase = true) || it.contains("fail", ignoreCase = true) }
                    if (hasFailedText) {
                        AccessibilityLogger.e(TAG, "IMO indicated transcription failed.")
                        freshBubble.recycle()
                        freshRoot.recycle()
                        return null
                    }

                    freshBubble.recycle()
                }
                freshRoot.recycle()
            }
            delay(500L) // Poll every 500ms
        }
        return null
    }

    private fun extractTextsFromNode(node: AccessibilityNodeInfo): Set<String> {
        val texts = mutableSetOf<String>()
        extractTextsRecursive(node, texts)
        return texts
    }

    private fun extractTextsRecursive(node: AccessibilityNodeInfo, set: MutableSet<String>) {
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            set.add(text)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            extractTextsRecursive(child, set)
            child.recycle()
        }
    }

    private fun findNodeByBounds(root: AccessibilityNodeInfo, target: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val targetBounds = Rect()
        target.getBoundsInScreen(targetBounds)

        return findFirstDescendant(root) { child ->
            val bounds = Rect()
            child.getBoundsInScreen(bounds)
            bounds == targetBounds && child.className == target.className
        }
    }

    private fun generateUniqueMessageKey(node: AccessibilityNodeInfo): String {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        // Combine coordinates + any child text to create a robust temporary in-memory key
        val texts = extractTextsFromNode(node).joinToString("_")
        return "voice_${bounds.left}_${bounds.top}_${bounds.right}_${bounds.bottom}_$texts"
    }

    // --- Helpers for tree traversal ---

    private fun hasDescendantMatching(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): Boolean {
        if (predicate(node)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = hasDescendantMatching(child, predicate)
            child.recycle()
            if (found) return true
        }
        return false
    }

    private fun findFirstDescendant(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstDescendant(child, predicate)
            child.recycle()
            if (found != null) {
                return found
            }
        }
        return null
    }

    /**
     * Model containing candidate voice bubble and its corresponding button node.
     */
    data class VoiceBubbleInfo(
        val bubbleNode: AccessibilityNodeInfo,
        val aButtonNode: AccessibilityNodeInfo?,
        val uniqueKey: String
    )
}
