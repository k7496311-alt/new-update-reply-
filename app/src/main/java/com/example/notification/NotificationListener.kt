package com.example.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.CapturedNotificationRepositoryImpl
import com.example.data.NotificationRepositoryImpl
import com.example.data.QueueRepositoryImpl
import com.example.database.AppDatabase
import com.example.logger.AppLogger
import com.example.model.CapturedNotification
import com.example.model.LogCategory
import com.example.repository.CapturedNotificationRepository
import com.example.repository.NotificationRepository
import com.example.repository.QueueRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Service to listen to system notification events.
 * Uses NotificationFilterEngine to filter non-imo, summary, empty, edited, and duplicate notifications.
 * Merges queue items for existing active senders.
 */
class NotificationListener : NotificationListenerService() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var capturedRepository: CapturedNotificationRepository
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var queueRepository: QueueRepository
    private lateinit var filterEngine: NotificationFilterEngine
    private lateinit var parser: NotificationParser

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NotificationListener created")
        try {
            capturedRepository = CapturedNotificationRepositoryImpl.getInstance()
            val database = AppDatabase.getDatabase(applicationContext)
            notificationRepository = NotificationRepositoryImpl(database.notificationDao())
            queueRepository = QueueRepositoryImpl(database.queueDao())
            filterEngine = NotificationFilterEngine(applicationContext, queueRepository)
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
                // 1. Process notification through Notification Filter Engine
                val filterResult = filterEngine.filterAndProcessNotification(sbn)

                when (filterResult) {
                    is FilterResult.Accepted -> {
                        // Cache PendingIntent for future reference
                        val queueItem = filterResult.queueItem
                        sbn.notification?.contentIntent?.let { intent ->
                            NotificationPendingIntentCache.put(
                                queueItem.packageName,
                                queueItem.senderName,
                                intent
                            )
                        }

                        val captured = CapturedNotification(
                            notificationId = sbn.id,
                            packageName = queueItem.packageName,
                            senderName = queueItem.senderName,
                            title = queueItem.senderName,
                            text = queueItem.incomingMessage,
                            postTime = sbn.postTime
                        )
                        capturedRepository.addNotification(captured)

                        val notificationItem = parser.parse(sbn)
                        if (notificationItem != null) {
                            notificationRepository.saveNotification(notificationItem)
                        }
                    }

                    is FilterResult.DuplicateMerged -> {
                        val updatedItem = filterResult.updatedQueueItem
                        sbn.notification?.contentIntent?.let { intent ->
                            NotificationPendingIntentCache.put(
                                updatedItem.packageName,
                                updatedItem.senderName,
                                intent
                            )
                        }

                        val captured = CapturedNotification(
                            notificationId = sbn.id,
                            packageName = updatedItem.packageName,
                            senderName = updatedItem.senderName,
                            title = updatedItem.senderName,
                            text = updatedItem.incomingMessage,
                            postTime = sbn.postTime
                        )
                        capturedRepository.addNotification(captured)
                    }

                    is FilterResult.Rejected -> {
                        // Ignored by filter engine
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error capturing notification in NotificationListener", e)
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
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
        Log.d(TAG, "NotificationListener destroyed")
    }

    companion object {
        private const val TAG = "NotificationListener"
    }
}
