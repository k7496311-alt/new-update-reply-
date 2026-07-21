package com.example.accessibility.imo

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.example.accessibility.AccessibilityLogger
import com.example.accessibility.AccessibilityManager
import com.example.model.NotificationData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Controller class that bridges system events, background threads, and active
 * UI Automation flows. It manages automation loops, handles system interruptions gracefully,
 * and maintains safety lockouts.
 */
class IMOAccessibilityController(
    private val context: Context,
    private val accessibilityManager: AccessibilityManager,
    val uiManager: IMOUIManager
) {

    companion object {
        private const val TAG = "IMOAccessibilityController"
    }

    private val controllerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var activeAutomationJob: kotlinx.coroutines.Job? = null

    /**
     * Intercepts and parses raw system accessibility events, routing them to the scanner.
     */
    fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: ""
        if (packageName != IMONodeScanner.PACKAGE_IMO && packageName != IMONodeScanner.PACKAGE_IMO_LITE) {
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                AccessibilityLogger.d(TAG, "IMO event observed: type=${event.eventType} package=$packageName")
                // Handle dynamic layout state scans when requested or in background
            }
        }
    }

    /**
     * Public API to start an asynchronous automation process to open a notification and reply.
     */
    fun autoReplyToNotificationAsync(notification: NotificationData, replyText: String, onComplete: (Boolean) -> Unit) {
        activeAutomationJob?.cancel()
        activeAutomationJob = controllerScope.launch {
            AccessibilityLogger.i(TAG, "Initiating notification auto-reply workflow asynchronously")
            
            // 1. Open chat via notification intent
            val launched = uiManager.getActionPerformer().openChatByNotification(notification)
            if (!launched) {
                onComplete(false)
                return@launch
            }

            // 2. Type and send reply text
            val typed = uiManager.getActionPerformer().typeMessage(replyText)
            if (typed) {
                val sent = uiManager.getActionPerformer().clickSendButton()
                if (sent) {
                    uiManager.getActionPerformer().clickBackButton()
                    onComplete(true)
                    return@launch
                }
            }
            onComplete(false)
        }
    }

    /**
     * High-level workflow to automate Voice-to-Text transcript reading for incoming messages.
     */
    fun startVoiceTranscriptionFlowAsync(contactName: String, onTranscriptReceived: (String?) -> Unit) {
        activeAutomationJob?.cancel()
        activeAutomationJob = controllerScope.launch {
            AccessibilityLogger.i(TAG, "Starting Voice Transcription flow for '$contactName'")
            
            // Step 1: Open target chat
            val opened = uiManager.getActionPerformer().openChatByContactName(contactName)
            if (!opened) {
                onTranscriptReceived(null)
                return@launch
            }

            // Step 2: Transcribe last voice message
            val transcript = uiManager.transcribeLastVoiceMessage()
            
            // Step 3: Go back
            uiManager.getActionPerformer().clickBackButton()
            
            onTranscriptReceived(transcript)
        }
    }

    /**
     * Safely terminates any active background automation tasks.
     */
    fun cancelActiveAutomations() {
        if (activeAutomationJob?.isActive == true) {
            AccessibilityLogger.w(TAG, "Cancelling active IMO automations...")
            activeAutomationJob?.cancel()
            activeAutomationJob = null
        }
    }
}
