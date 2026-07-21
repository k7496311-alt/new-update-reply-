package com.example.accessibility.imo

import com.example.history.HistoryManager
import com.example.model.AutoReplyRule
import com.example.model.QueueItem
import com.example.model.QueueStatus
import com.example.repository.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository layer that coordinates access to various sub-systems for the Full Reply Orchestrator.
 * Follows Clean Architecture and the single source of truth model.
 */
class OrchestratorRepository(
    private val serviceRepository: ServiceRepository,
    private val blacklistRepository: BlacklistRepository,
    private val queueRepository: QueueRepository,
    private val ruleRepository: RuleRepository,
    private val conversationRepository: ConversationRepository,
    private val historyManager: HistoryManager,
    private val messageAnalyzer: MessageAnalyzerRepository,
    private val settingsRepository: SettingsRepository
) {

    /**
     * Emits the current running state of the automated auto-reply foreground service.
     */
    val isServiceRunning: Flow<Boolean> = serviceRepository.isServiceRunning

    /**
     * Checks if a contact is blacklisted from receiving automated responses.
     */
    suspend fun isBlacklisted(sender: String): Boolean {
        return blacklistRepository.isBlacklisted(sender)
    }

    /**
     * Retrieves all active reply rules defined in the database.
     */
    suspend fun getActiveRules(): List<AutoReplyRule> {
        return ruleRepository.getActiveRules()
    }

    /**
     * Retrieves a rule by its database ID.
     */
    suspend fun getRuleById(id: Long): AutoReplyRule? {
        return ruleRepository.getRuleById(id)
    }

    /**
     * Checks whether we should respond to the conversation, ensuring cooldown and safety limits are respected.
     */
    suspend fun shouldReply(senderName: String, packageName: String, replyText: String): Pair<Boolean, String> {
        return conversationRepository.shouldReply(senderName, packageName, replyText)
    }

    /**
     * Updates the status of a conversation inside the local tracker.
     */
    suspend fun updateQueueStatus(senderName: String, packageName: String, status: QueueStatus) {
        conversationRepository.updateQueueStatus(senderName, packageName, status)
    }

    /**
     * Commits a successful outgoing reply to the conversation database.
     */
    suspend fun recordOutgoingReply(senderName: String, packageName: String, replyText: String) {
        conversationRepository.recordOutgoingReply(senderName, packageName, replyText)
    }

    /**
     * Logs the transaction history for analytics and troubleshooting views.
     */
    suspend fun logHistory(
        ruleId: Long,
        ruleName: String,
        senderName: String,
        incomingMessage: String,
        replyText: String,
        packageName: String,
        isSuccess: Boolean
    ): Long {
        return historyManager.logReply(
            ruleId = ruleId,
            ruleName = ruleName,
            senderName = senderName,
            incomingMessage = incomingMessage,
            repliedMessage = replyText,
            packageName = packageName,
            isSuccess = isSuccess
        )
    }

    /**
     * Uses the MessageAnalyzer module to detect the type of the incoming text string.
     */
    fun analyzeMessage(message: String) = messageAnalyzer.analyze(message)

    /**
     * Checks if a specific app configuration setting is turned on in settings database.
     */
    suspend fun isAppSettingEnabled(key: String, defaultValue: Boolean = true): Boolean {
        val setting = settingsRepository.getSettingByKey(key) ?: return defaultValue
        return setting.value.toBoolean()
    }

    /**
     * Provides a stream of all currently scheduled or historical queue items.
     */
    fun getAllQueueItemsFlow(): Flow<List<QueueItem>> {
        return queueRepository.getAllQueueItems()
    }

    /**
     * Fetches a queue item directly by its database ID.
     */
    suspend fun getQueueItemById(id: Long): QueueItem? {
        return queueRepository.getQueueItemById(id)
    }

    /**
     * Deletes a queue item from database.
     */
    suspend fun deleteQueueItem(item: QueueItem) {
        queueRepository.deleteQueueItem(item)
    }
}
