package com.example.accessibility.imo

import android.content.Context
import android.util.Log
import com.example.accessibility.AccessibilityLogger
import com.example.accessibility.AutoReplyAccessibilityService
import com.example.model.MessageType
import com.example.model.QueueItem
import com.example.model.QueueStatus
import com.example.queue.QueueEngine
import com.example.reply.ReplyGenerationStatus
import com.example.reply.ReplyGenerator
import com.example.rule.RuleEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The master coordinator that integrates ALL modules into a complete, production-grade auto-reply flow.
 * Handles incoming notifications, enqueuing, priority execution, voice transcription, and reliable dispatching.
 */
class ReplyOrchestrator(
    private val context: Context,
    private val orchestratorRepository: OrchestratorRepository,
    val stateMachine: OrchestratorStateMachine,
    private val uiManager: IMOUIManager,
    private val ruleEngine: RuleEngine,
    private val replyGenerator: ReplyGenerator,
    private val replySender: ReplySender,
    private val queueEngine: QueueEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    companion object {
        private const val TAG = "ReplyOrchestrator"
        private const val MAX_PROCESSING_TIME_MS = 30000L // 30s timeout per message
        private const val FAILURE_THRESHOLD = 3

        @Volatile
        private var activeInstance: ReplyOrchestrator? = null

        fun getInstance(): ReplyOrchestrator? = activeInstance
    }

    private val orchestratorScope = CoroutineScope(dispatcher + SupervisorJob())
    private val processMutex = Mutex()
    private var workerJob: Job? = null
    private var processingJob: Job? = null

    @Volatile
    private var consecutiveFailures = 0

    private val _isProcessingActive = MutableStateFlow(false)
    val isProcessingActive = _isProcessingActive.asStateFlow()

    init {
        activeInstance = this
        // Start monitoring queue
        startQueueProcessingWorker()
    }

    /**
     * Entry point called by NotificationListener whenever a notification is posted.
     */
    suspend fun onNotificationReceived(packageName: String, sender: String, messageText: String): Boolean = withContext(dispatcher) {
        AccessibilityLogger.i(TAG, "Notification received from '$sender' on app '$packageName'. Message: '$messageText'")

        // 1. Check if app is IMO/IMO Lite
        if (packageName != IMONodeScanner.PACKAGE_IMO && packageName != IMONodeScanner.PACKAGE_IMO_LITE) {
            AccessibilityLogger.d(TAG, "App '$packageName' is not supported. Ignoring notification.")
            return@withContext false
        }

        // 2. Check if service is enabled (master switch and system service)
        val isMasterSettingEnabled = orchestratorRepository.isAppSettingEnabled("service_enabled", true)
        val isServiceActive = AutoReplyAccessibilityService.isActive()
        if (!isMasterSettingEnabled || !isServiceActive) {
            AccessibilityLogger.w(TAG, "Orchestrator skip: Service enabled setting=$isMasterSettingEnabled, active=$isServiceActive")
            return@withContext false
        }

        // 3. Check if sender is blacklisted
        if (orchestratorRepository.isBlacklisted(sender)) {
            AccessibilityLogger.w(TAG, "Sender '$sender' is blacklisted. Aborting automated queue.")
            return@withContext false
        }

        // 4. Match rule or fallback to find the planned response
        val activeRules = orchestratorRepository.getActiveRules()
        val bestMatchedRule = ruleEngine.findBestMatchingRule(messageText, activeRules)?.rule
        val ruleId = bestMatchedRule?.id ?: 0L
        val replyText = bestMatchedRule?.replyText ?: ""

        // 5. Enqueue into the Queue Management System
        stateMachine.transitionTo(OrchestratorState.QUEUED)
        val priority = bestMatchedRule?.priority ?: 0
        val delayTime = bestMatchedRule?.replyDelayMillis ?: 0L

        AccessibilityLogger.i(TAG, "Enqueuing reply for '$sender'. Rule ID: $ruleId, Priority: $priority")
        val result = queueEngine.enqueue(
            ruleId = ruleId,
            senderName = sender,
            incomingMessage = messageText,
            replyText = replyText,
            packageName = packageName,
            priority = priority,
            delayMillis = delayTime
        )

        stateMachine.transitionTo(OrchestratorState.IDLE)
        return@withContext result.isSuccess
    }

    /**
     * Launches the persistent background worker that polls the Queue Engine and executes automations.
     */
    fun startQueueProcessingWorker() {
        if (workerJob?.isActive == true) return

        AccessibilityLogger.i(TAG, "Starting ReplyOrchestrator background worker loop...")
        workerJob = orchestratorScope.launch {
            _isProcessingActive.value = true
            while (isActive) {
                try {
                    // Perform queue maintenance
                    queueEngine.runMaintenance()

                    // Attempt to process next enqueued task
                    val nextItem = queueEngine.dispatchNext()
                    if (nextItem != null) {
                        // Cancel any other running or lower-priority processing if urgent item arrives
                        if (nextItem.priority > 0) {
                            cancelActiveProcessing()
                        }

                        processingJob = launch {
                            executeReplyWorkflow(nextItem)
                        }
                        processingJob?.join()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AccessibilityLogger.e(TAG, "Exception in orchestrator loop: ${e.message}", e)
                }
                delay(1000L) // Sleep brief interval to prevent cpu spinning
            }
            _isProcessingActive.value = false
        }
    }

    /**
     * Shuts down the orchestrator's background loops cleanly.
     */
    fun stopQueueProcessingWorker() {
        AccessibilityLogger.i(TAG, "Stopping ReplyOrchestrator background worker...")
        workerJob?.cancel()
        workerJob = null
        cancelActiveProcessing()
    }

    /**
     * Cancels the currently executing UI automation sequence.
     */
    fun cancelActiveProcessing() {
        if (processingJob?.isActive == true) {
            AccessibilityLogger.w(TAG, "Cancelling active processing sequence due to prioritization.")
            processingJob?.cancel()
            processingJob = null
            stateMachine.transitionTo(OrchestratorState.IDLE)
        }
    }

    /**
     * Execution worker for a single Queue item. Wrapped inside a Mutex lock to
     * enforce the rule: "Only ONE conversation processed at a time."
     */
    private suspend fun executeReplyWorkflow(queueItem: QueueItem) {
        processMutex.withLock {
            val itemId = queueItem.id
            val sender = queueItem.senderName
            val packageName = queueItem.packageName
            val originalText = queueItem.incomingMessage

            AccessibilityLogger.i(TAG, "Starting automated processing of Queue Item $itemId for '$sender'")

            try {
                orchestratorRepository.updateQueueStatus(sender, packageName, QueueStatus.PROCESSING)

                // Enforce safety constraint: Max processing time per message = 30 seconds
                withTimeout(MAX_PROCESSING_TIME_MS) {

                    // Step 8a: Open target chat screen in IMO
                    stateMachine.transitionTo(OrchestratorState.OPENING_CHAT)
                    val chatOpened = uiManager.getActionPerformer().openChatByContactName(sender)
                    if (!chatOpened) {
                        throw IllegalStateException("Failed to navigate to target chat with: $sender")
                    }
                    delay(500L) // UI stabilization delay

                    // Step 8b: Analyze the last message content in the conversation thread
                    stateMachine.transitionTo(OrchestratorState.ANALYZING_MESSAGE)
                    val lastMessageType = uiManager.getActionPerformer().detectLastMessageType()

                    var resolvedText = originalText

                    // Step 8c: If the message type is VOICE_MESSAGE -> click "A" and transcribe
                    if (lastMessageType == MessageType.VOICE_MESSAGE) {
                        stateMachine.transitionTo(OrchestratorState.TRANSCRIBING_VOICE)
                        val transcriptText = uiManager.transcribeLastVoiceMessage()
                        if (transcriptText != null) {
                            resolvedText = transcriptText
                            AccessibilityLogger.i(TAG, "Voice transcription successful: '$resolvedText'")
                        } else {
                            AccessibilityLogger.w(TAG, "Voice transcription returned empty. Relying on original message text.")
                        }
                    } else if (lastMessageType == MessageType.IMAGE ||
                               lastMessageType == MessageType.STICKER ||
                               lastMessageType == MessageType.VIDEO) {
                        // Step 8d: If image/sticker/video -> skip
                        stateMachine.transitionTo(OrchestratorState.SKIPPED)
                        AccessibilityLogger.w(TAG, "Unsupported media message detected ($lastMessageType). Skipping reply.")

                        val skippedItem = queueItem.copy(
                            status = QueueStatus.SKIPPED,
                            errorMessage = "Skipped unsupported media: $lastMessageType",
                            updatedAt = System.currentTimeMillis()
                        )
                        orchestratorRepository.updateQueueStatus(sender, packageName, QueueStatus.SKIPPED)
                        uiManager.getActionPerformer().clickBackButton()
                        return@withTimeout
                    }

                    // Step 8f: Match rules using Rule Engine
                    stateMachine.transitionTo(OrchestratorState.MATCHING_RULES)
                    val activeRules = orchestratorRepository.getActiveRules()
                    val rule = ruleEngine.findMatchingRule(resolvedText, activeRules)

                    // Step 8g: Generate reply using Reply Generator
                    stateMachine.transitionTo(OrchestratorState.GENERATING_REPLY)
                    val finalReply = replyGenerator.generateReply(rule, sender, resolvedText)

                    // Step 8h: Check cooldowns and daily limit states
                    stateMachine.transitionTo(OrchestratorState.CHECKING_COOLDOWN)
                    if (finalReply.status == ReplyGenerationStatus.COOLDOWN ||
                        finalReply.status == ReplyGenerationStatus.LIMIT_EXCEEDED ||
                        finalReply.status == ReplyGenerationStatus.NO_MATCH) {

                        stateMachine.transitionTo(OrchestratorState.SKIPPED)
                        AccessibilityLogger.w(TAG, "Reply skipped: ${finalReply.status}. Reason: ${finalReply.reason}")

                        val skippedItem = queueItem.copy(
                            status = QueueStatus.SKIPPED,
                            errorMessage = finalReply.reason,
                            updatedAt = System.currentTimeMillis()
                        )
                        orchestratorRepository.updateQueueStatus(sender, packageName, QueueStatus.SKIPPED)
                        uiManager.getActionPerformer().clickBackButton()
                        return@withTimeout
                    }

                    // Step 8j: Send reply through low-level ReplySender (contains human typing animation)
                    stateMachine.transitionTo(OrchestratorState.SENDING_REPLY)
                    val sendResult = replySender.sendReply(sender, finalReply.replyText)

                    // Step 8k: Verify outgoing bubble is sent
                    stateMachine.transitionTo(OrchestratorState.VERIFYING_SENT)
                    when (sendResult) {
                        is SendResult.Success -> {
                            // Step 8l, m, n: Finalize history, conversation tracking, and queue completion
                            stateMachine.transitionTo(OrchestratorState.COMPLETING)
                            AccessibilityLogger.i(TAG, "Successfully dispatched and verified auto reply to '$sender'")

                            // Update conversation states
                            orchestratorRepository.recordOutgoingReply(sender, packageName, finalReply.replyText)

                            // Save to historical logs
                            orchestratorRepository.logHistory(
                                ruleId = rule?.id ?: 0L,
                                ruleName = rule?.name ?: "Default Reply",
                                senderName = sender,
                                incomingMessage = resolvedText,
                                replyText = finalReply.replyText,
                                packageName = packageName,
                                isSuccess = true
                            )

                            // Terminate queue item status
                            queueEngine.completeItem(itemId)
                            orchestratorRepository.updateQueueStatus(sender, packageName, QueueStatus.SENT)

                            // Reset consecutive failures
                            consecutiveFailures = 0
                        }
                        is SendResult.Cancelled -> {
                            AccessibilityLogger.w(TAG, "Workflow cancelled mid-process: ${sendResult.message}")
                            stateMachine.transitionTo(OrchestratorState.SKIPPED)
                            orchestratorRepository.updateQueueStatus(sender, packageName, QueueStatus.CANCELLED)
                        }
                        is SendResult.Timeout -> {
                            throw IllegalStateException("Send operation timed out: ${sendResult.message}")
                        }
                        is SendResult.Failed -> {
                            throw IllegalStateException("Send operation failed: ${sendResult.reason}")
                        }
                    }

                    // Clean up and return to Chat List Screen
                    uiManager.getActionPerformer().clickBackButton()
                    stateMachine.transitionTo(OrchestratorState.IDLE)
                }
            } catch (e: TimeoutCancellationException) {
                consecutiveFailures++
                AccessibilityLogger.e(TAG, "Timeout processing item $itemId. Processing exceeded 30s limit.")
                stateMachine.transitionTo(OrchestratorState.FAILED)
                queueEngine.handleItemFailure(itemId, "Total processing time limit of 30 seconds exceeded")
                orchestratorRepository.updateQueueStatus(sender, packageName, QueueStatus.FAILED)
                uiManager.getActionPerformer().clickBackButton()
                checkConsecutiveFailures()
            } catch (e: Exception) {
                consecutiveFailures++
                AccessibilityLogger.e(TAG, "Error processing item $itemId: ${e.message}", e)
                stateMachine.transitionTo(OrchestratorState.FAILED)
                queueEngine.handleItemFailure(itemId, e.message ?: "Unknown execution failure")
                orchestratorRepository.updateQueueStatus(sender, packageName, QueueStatus.FAILED)
                uiManager.getActionPerformer().clickBackButton()
                checkConsecutiveFailures()
            }
        }
    }

    /**
     * Automatically pauses or skips queue tasks if consecutive failures exceed safe threshold limits.
     */
    private fun checkConsecutiveFailures() {
        if (consecutiveFailures >= FAILURE_THRESHOLD) {
            com.example.logger.AppLogger.critical(
                com.example.model.LogCategory.APPLICATION,
                "Auto-Reply Safety Lockdown triggered! Exceeded $FAILURE_THRESHOLD consecutive failures. Check accessibility settings."
            )
            // Stop the loop or throttle further attempts to protect system resources
            stopQueueProcessingWorker()
        }
    }
}
