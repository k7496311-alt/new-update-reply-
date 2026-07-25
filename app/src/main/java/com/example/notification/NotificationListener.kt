package com.example.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.NotificationRepositoryImpl
import com.example.data.QueueRepositoryImpl
import com.example.data.RuleRepositoryImpl
import com.example.database.AppDatabase
import com.example.model.AutoReplyRule
import com.example.rule.RuleMatcher
import com.example.model.MatchType
import com.example.model.QueueItem
import com.example.model.QueueStatus
import com.example.repository.NotificationRepository
import com.example.repository.QueueRepository
import com.example.repository.RuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.logger.AppLogger
import com.example.model.LogCategory

/**
 * Service to listen to system notification events.
 * Enables reading of incoming messages, storing notifications, and support queue insertion.
 */
class NotificationListener : NotificationListenerService() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var notificationRepository: NotificationRepository
    private lateinit var ruleRepository: RuleRepository
    private lateinit var queueRepository: QueueRepository
    private lateinit var parser: NotificationParser

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NotificationListener created")
        try {
            val database = AppDatabase.getDatabase(applicationContext)
            notificationRepository = NotificationRepositoryImpl(database.notificationDao())
            ruleRepository = RuleRepositoryImpl(database.ruleDao())
            queueRepository = QueueRepositoryImpl(database.queueDao())
            parser = NotificationParser(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing repositories in NotificationListener", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        scope.launch {
            try {
                // 1. Parse the incoming notification
                val notificationItem = parser.parse(sbn) ?: return@launch

                // Cache Notification ContentIntent for direct one-click navigation
                sbn.notification?.contentIntent?.let { intent ->
                    NotificationPendingIntentCache.put(
                        notificationItem.packageName,
                        notificationItem.sender,
                        intent
                    )
                }

                Log.d(TAG, "Parsed notification successfully: ${notificationItem.sender} - ${notificationItem.message}")
                AppLogger.info(
                    LogCategory.NOTIFICATION,
                    "Notification received from ${notificationItem.sender} (Package: ${notificationItem.packageName})",
                    "Message: ${notificationItem.message}"
                )

                // 2. Store notification in the database (Room Integration)
                val id = notificationRepository.saveNotification(notificationItem)
                Log.d(TAG, "Notification stored with ID: $id")

                // 3. Forward to Full Reply Orchestrator if active
                val orchestratorHandled = com.example.accessibility.imo.ReplyOrchestrator.getInstance()
                    ?.onNotificationReceived(
                        packageName = notificationItem.packageName,
                        sender = notificationItem.sender,
                        messageText = notificationItem.message
                    ) ?: false

                if (!orchestratorHandled) {
                    // Fallback queue insertion if orchestrator did not handle
                    val activeRules = ruleRepository.getActiveRules()
                    for (rule in activeRules) {
                        if (isRuleMatched(notificationItem.message, rule.keyword, rule.matchType)) {
                            Log.d(TAG, "Notification matched rule: ${rule.name}. Inserting to queue.")
                            AppLogger.success(
                                LogCategory.REPLY,
                                "Notification from ${notificationItem.sender} matched Rule: '${rule.name}'. Queueing reply: '${rule.replyText}'",
                                "Rule ID: ${rule.id}\nMatch Type: ${rule.matchType}\nMessage: ${notificationItem.message}"
                            )

                            val queueItem = QueueItem(
                                ruleId = rule.id,
                                senderName = notificationItem.sender,
                                incomingMessage = notificationItem.message,
                                replyText = rule.replyText,
                                packageName = notificationItem.packageName,
                                scheduledTime = System.currentTimeMillis() + rule.replyDelayMillis,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                                status = QueueStatus.PENDING
                            )
                            val queueId = queueRepository.saveQueueItem(queueItem)
                            Log.d(TAG, "Inserted queue item with ID: $queueId")
                            // Match only the first matching rule
                            break
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification in listener", e)
                AppLogger.critical(
                    LogCategory.NOTIFICATION,
                    "Error processing notification in listener",
                    Log.getStackTraceString(e)
                )
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // No-op.
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
        Log.d(TAG, "NotificationListener destroyed")
    }

    private fun isRuleMatched(message: String, keyword: String, matchType: MatchType): Boolean {
        val dummyRule = AutoReplyRule(
            name = "dummy",
            keyword = keyword,
            matchType = matchType,
            replyText = "placeholder"
        )
        return RuleMatcher().isMatch(message, dummyRule)
    }

    companion object {
        private const val TAG = "NotificationListener"
    }
}
