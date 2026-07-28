package com.example.accessibility.imo

import android.content.Context
import android.util.Log
import com.example.accessibility.AccessibilityLogger
import com.example.accessibility.AutoReplyAccessibilityService
import com.example.model.MessageType
import com.example.model.QueueItem
import com.example.model.QueueStatus
import com.example.notification.NotificationPendingIntentCache
import com.example.queue.QueueEngine
import com.example.reply.ReplyGenerationStatus
import com.example.reply.ReplyGenerator
import com.example.rule.RuleEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The master coordinator that integrates ALL modules into a complete, production-grade auto-reply flow.
 * Handles incoming notifications, direct notification clicking, media/call filtering, voice transcription,
 * rule matching, and returning the device to normal state when processing completes.
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

        fun getOrCreateInstance(context: Context): ReplyOrchestrator {
            return activeInstance ?: synchronized(this) {
                activeInstance ?: createInstance(context.applicationContext).also { activeInstance = it }
            }
        }

        private fun createInstance(appContext: Context): ReplyOrchestrator {
            val database = com.example.database.AppDatabase.getDatabase(appContext)
            val serviceRepository = com.example.data.ServiceRepositoryImpl(appContext)
            val blacklistRepository = com.example.data.BlacklistRepositoryImpl(database.blacklistDao())
            val queueRepository = com.example.data.QueueRepositoryImpl(database.queueDao())
            val ruleRepository = com.example.data.RuleRepositoryImpl(database.ruleDao())
            val conversationRepository = com.example.data.ConversationRepositoryImpl(database.conversationDao())
            val historyRepository = com.example.data.HistoryRepositoryImpl(database.historyDao())
            val historyManager = com.example.history.HistoryManager(historyRepository)
            val messageAnalyzerRepository = com.example.data.MessageAnalyzerRepositoryImpl()
            val settingsRepository = com.example.data.SettingsRepositoryImpl(database.settingsDao())

            val orchestratorRepository = OrchestratorRepository(
                serviceRepository = serviceRepository,
                blacklistRepository = blacklistRepository,
                queueRepository = queueRepository,
                ruleRepository = ruleRepository,
                conversationRepository = conversationRepository,
                historyManager = historyManager,
                messageAnalyzer = messageAnalyzerRepository,
                settingsRepository = settingsRepository
            )

            val stateMachine = OrchestratorStateMachine()

            val accessibilityManager = com.example.accessibility.AccessibilityManager(appContext)
            val nodeScanner = IMONodeScanner()
            val actionPerformer = IMOActionPerformer(appContext, accessibilityManager, nodeScanner)
            val transcriptManager = VoiceTranscriptManager()
            val voiceMessageHandler = VoiceMessageHandler(appContext, accessibilityManager, nodeScanner, transcriptManager)
            val uiManager = IMOUIManager(accessibilityManager, nodeScanner, actionPerformer)

            val ruleEngine = com.example.rule.RuleEngine()
            val replyGeneratorRepository = com.example.data.ReplyGeneratorRepositoryImpl(historyRepository, settingsRepository)
            val replyGenerator = com.example.reply.ReplyGenerator(replyGeneratorRepository)
            val replySender = ReplySender(appContext, accessibilityManager, nodeScanner, actionPerformer)
            val queueEngine = com.example.queue.QueueEngine(queueRepository)

            return ReplyOrchestrator(
                context = appContext,
                orchestratorRepository = orchestratorRepository,
                stateMachine = stateMachine,
                uiManager = uiManager,
                ruleEngine = ruleEngine,
                replyGenerator = replyGenerator,
                replySender = replySender,
                queueEngine = queueEngine
            )
        }
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

        // 1. Check if app is IMO / IMO Beta / IMO HD / IMO Lite
        if (!IMONodeScanner.isImoPackage(packageName)) {
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

        // 4. Quick check: Is notification text explicit media / missed call / link?
        if (IMOMessageClassifier.isMediaOrCallOrLink(messageText)) {
            AccessibilityLogger.w(TAG, "Notification message '$messageText' is media/call/link. Skipping enqueue.")
            return@withContext false
        }

        // 5. Match rule or fallback to find the planned response
        val activeRules = orchestratorRepository.getActiveRules()
        val bestMatchedRule = ruleEngine.findBestMatchingRule(messageText, activeRules)?.rule
        val ruleId = bestMatchedRule?.id ?: 0L
        val replyText = bestMatchedRule?.replyText ?: ""

        // 6. Enqueue into the Queue Management System
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

                    // Step 1: Pre-check if message is media / missed call / link
                    if (IMOMessageClassifier.isMediaOrCallOrLink(originalText)) {
                        stateMachine.transitionTo(OrchestratorState.SKIPPED)
                        AccessibilityLogger.w(TAG, "Original message '$originalText' is media/call/link. Skipping.")
                        orchestratorRepository.updateQueueStatus(sender, packageName, QueueStatus.SKIPPED)
                        return@withTimeout
                    }

                    // Step 2: Open target chat screen directly via Notification PendingIntent or Contact Name
                    stateMachine.transitionTo(OrchestratorState.OPENING_CHAT)
                    var chatOpened = false

                    val cachedPendingIntent = NotificationPendingIntentCache.get(packageName, sender)
                    if (cachedPendingIntent != null) {
                        try {
                            AccessibilityLogger.i(TAG, "Clicking notification PendingIntent directly for '$sender'...")
                            val sent = NotificationPendingIntentCache.sendPendingIntent(context, cachedPendingIntent)
                            if (sent) {
                                // Wait up to 4 seconds for app to bring foreground and reach chat/chat list screen
                                val startTime = System.currentTimeMillis()
                                while (System.currentTimeMillis() - startTime < 4000L) {
                                    delay(500L)
                                    if (uiManager.getActionPerformer().isOnChatScreen() || uiManager.getActionPerformer().isOnChatListScreen()) {
                                        chatOpened = true
                                        break
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            AccessibilityLogger.e(TAG, "PendingIntent click failed for '$sender', falling back to manual open", e)
                        }
                    }

                    if (!chatOpened) {
                        AccessibilityLogger.i(TAG, "Chat not opened via PendingIntent. Launching app package '$packageName' directly...")
                        uiManager.getActionPerformer().launchAppPackage(packageName)
                        delay(2000L)
                        chatOpened = uiManager.getActionPerformer().openChatByContactName(sender, packageName)
                    }

                    if (!chatOpened) {
                        throw IllegalStateException("Failed to navigate to target chat with: $sender")
                    }
                    delay(500L) // UI stabilization delay

                    // Step 3: Analyze the last message content in the conversation thread
                    stateMachine.transitionTo(OrchestratorState.ANALYZING_MESSAGE)
                    var lastMessageType = uiManager.getActionPerformer().detectLastMessageType()
                    var resolvedText = originalText

                    // Poll for up to 3.5s to read actual incoming message text directly from chat screen
                    val pollStartTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - pollStartTime < 3500L) {
                        val rootNode = uiManager.getAccessibilityManager().getRootNode()
                        val screenInfo = uiManager.getNodeScanner().scanChatConversationScreen(rootNode)
                        rootNode?.recycle()

                        val lastIncoming = screenInfo?.messages?.lastOrNull { it.isIncoming && it.text.isNotBlank() }
                        if (lastIncoming != null) {
                            lastMessageType = lastIncoming.messageType
                            if (!lastIncoming.text.equals("You have a new message", ignoreCase = true) &&
                                !lastIncoming.text.equals("New message", ignoreCase = true)) {
                                resolvedText = lastIncoming.text
                                AccessibilityLogger.i(TAG, "Extracted real incoming message from chat screen: '$resolvedText'")
                                break
                            }
                        }
                        delay(500L)
                    }

                    // Step 4: Check Voice Message vs Media vs Text
                    if (IMOMessageClassifier.isVoiceMessage(originalText, lastMessageType)) {
                        stateMachine.transitionTo(OrchestratorState.TRANSCRIBING_VOICE)
                        val transcriptText = uiManager.transcribeLastVoiceMessage()
                        if (!transcriptText.isNullOrBlank()) {
                            resolvedText = transcriptText
                            AccessibilityLogger.i(TAG, "Voice transcription successful: '$resolvedText'")
                        } else {
                            AccessibilityLogger.w(TAG, "Voice transcription failed or returned empty. Skipping voice message.")
                            stateMachine.transitionTo(OrchestratorState.SKIPPED)
                            orchestratorRepository.updateQueueStatus(sender, packageName, QueueStatus.SKIPPED)
                            uiManager.getActionPerformer().clickBackButton()
                            return@withTimeout
                        }
                    }

                    // Step 5: Verify resolved text is not media/link/call
                    if (IMOMessageClassifier.isMediaOrCallOrLink(resolvedText, lastMessageType)) {
                        stateMachine.transitionTo(OrchestratorState.SKIPPED)
                        AccessibilityLogger.w(TAG, "Unsupported media/call/link detected ($resolvedText / $lastMessageType). Skipping reply.")
                        orchestratorRepository.updateQueueStatus(sender, packageName, QueueStatus.SKIPPED)
                        uiManager.getActionPerformer().clickBackButton()
                        return@withTimeout
                    }

                    // Step 6: Match rules using Rule Engine
                    stateMachine.transitionTo(OrchestratorState.MATCHING_RULES)
                    val activeRules = orchestratorRepository.getActiveRules()
                    val rule = ruleEngine.findMatchingRule(resolvedText, activeRules)

                    if (rule == null) {
                        stateMachine.transitionTo(OrchestratorState.SKIPPED)
                        AccessibilityLogger.w(TAG, "No rule matched for text '$resolvedText'. Skipping reply.")
                        orchestratorRepository.updateQueueStatus(sender, packageName, QueueStatus.SKIPPED)
                        uiManager.getActionPerformer().clickBackButton()
                        return@withTimeout
                    }

                    // Step 7: Generate reply using Reply Generator
                    stateMachine.transitionTo(OrchestratorState.GENERATING_REPLY)
                    val finalReply = replyGenerator.generateReply(rule, sender, resolvedText)

                    // Step 8: Check cooldowns and daily limit states
                    stateMachine.transitionTo(OrchestratorState.CHECKING_COOLDOWN)
                    if (finalReply.status == ReplyGenerationStatus.COOLDOWN ||
                        finalReply.status == ReplyGenerationStatus.LIMIT_EXCEEDED ||
                        finalReply.status == ReplyGenerationStatus.NO_MATCH) {

                        stateMachine.transitionTo(OrchestratorState.SKIPPED)
                        AccessibilityLogger.w(TAG, "Reply skipped: ${finalReply.status}. Reason: ${finalReply.reason}")

                        orchestratorRepository.updateQueueStatus(sender, packageName, QueueStatus.SKIPPED)
                        uiManager.getActionPerformer().clickBackButton()
                        return@withTimeout
                    }

                    // Step 9: Send reply through low-level ReplySender
                    stateMachine.transitionTo(OrchestratorState.SENDING_REPLY)
                    val sendResult = replySender.sendReply(sender, finalReply.replyText)

                    // Step 10: Verify outgoing bubble is sent
                    stateMachine.transitionTo(OrchestratorState.VERIFYING_SENT)
                    when (sendResult) {
                        is SendResult.Success -> {
                            stateMachine.transitionTo(OrchestratorState.COMPLETING)
                            AccessibilityLogger.i(TAG, "Successfully dispatched auto reply to '$sender'")

                            // Update conversation states & logs
                            orchestratorRepository.recordOutgoingReply(sender, packageName, finalReply.replyText)
                            orchestratorRepository.logHistory(
                                ruleId = rule.id,
                                ruleName = rule.name,
                                senderName = sender,
                                incomingMessage = resolvedText,
                                replyText = finalReply.replyText,
                                packageName = packageName,
                                isSuccess = true
                            )

                            // Terminate queue item status
                            queueEngine.completeItem(itemId)
                            orchestratorRepository.updateQueueStatus(sender, packageName, QueueStatus.SENT)
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

                    // Clean up: click back button
                    uiManager.getActionPerformer().clickBackButton()
                    NotificationPendingIntentCache.remove(packageName, sender)
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
            } finally {
                // Return device to normal Home screen state after processing queue item
                try {
                    AccessibilityLogger.i(TAG, "Returning device to normal Home screen state...")
                    uiManager.getAccessibilityManager().performHome()
                } catch (e: Exception) {
                    AccessibilityLogger.e(TAG, "Failed to perform Home action on completion: ${e.message}")
                }
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
            stopQueueProcessingWorker()
        }
    }
}
