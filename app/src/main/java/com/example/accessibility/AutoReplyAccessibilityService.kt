package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.accessibility.imo.IMONodeScanner
import com.example.accessibility.imo.ReplyOrchestrator
import com.example.notification.NotificationPendingIntentCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Accessibility service that detects message content or window changes for automated actions.
 */
class AutoReplyAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default)

    companion object {
        private const val TAG = "AutoReplyAccessibilityService"

        @Volatile
        private var instance: AutoReplyAccessibilityService? = null

        /**
         * Checks whether the accessibility service is active and bound by the OS.
         */
        fun isActive(): Boolean = instance != null

        /**
         * Returns the running instance of the service, if active.
         */
        fun getInstance(): AutoReplyAccessibilityService? = instance
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.e("ACCESSIBILITY", "✅ SERVICE CONNECTED SUCCESSFULLY")
        try {
            Toast.makeText(this, "Accessibility Service Connected!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Toast: ${e.message}")
        }
        // Initialize and start background queue processor
        try {
            ReplyOrchestrator.getOrCreateInstance(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ReplyOrchestrator: ${e.message}", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: ""

        // Filter and capture Notification events for IMO and supported apps
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            if (IMONodeScanner.isImoPackage(pkgName)) {
                val notification = event.parcelableData as? Notification
                if (notification != null) {
                    val extras = notification.extras
                    val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
                    val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""

                    Log.d(TAG, "IMO Notification event detected! Sender: '$title', Message: '$text'")

                    if (title.isNotBlank()) {
                        // Cache Notification PendingIntent
                        notification.contentIntent?.let { intent ->
                            NotificationPendingIntentCache.put(pkgName, title, intent)

                            // Instantly click notification contentIntent via Accessibility privileges
                            try {
                                Log.i(TAG, "Instant Notification Click triggered for '$title'")
                                intent.send()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error clicking Notification contentIntent: ${e.message}", e)
                            }
                        }

                        // Forward to orchestrator queue processing
                        serviceScope.launch {
                            try {
                                ReplyOrchestrator.getOrCreateInstance(applicationContext)
                                    .onNotificationReceived(pkgName, title, text)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error forwarding notification event to orchestrator: ${e.message}", e)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted by system")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        Log.d(TAG, "Accessibility Service unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
        Log.d(TAG, "Accessibility Service destroyed")
    }
}
