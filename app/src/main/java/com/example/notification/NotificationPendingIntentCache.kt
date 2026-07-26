package com.example.notification

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.accessibility.AutoReplyAccessibilityService
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory thread-safe cache storing recent Notification PendingIntents
 * keyed by "$packageName:$sender". Used by ReplyOrchestrator to launch
 * chats directly without manual screen navigation even when app is in background.
 */
object NotificationPendingIntentCache {
    private const val TAG = "PendingIntentCache"
    private val cache = ConcurrentHashMap<String, PendingIntent>()
    private val latestByPackage = ConcurrentHashMap<String, PendingIntent>()

    fun put(packageName: String, sender: String, intent: PendingIntent?) {
        if (intent == null || packageName.isBlank()) return
        val pkgClean = packageName.lowercase().trim()
        latestByPackage[pkgClean] = intent

        if (sender.isNotBlank()) {
            val key = buildKey(packageName, sender)
            cache[key] = intent
        }
    }

    fun get(packageName: String, sender: String): PendingIntent? {
        if (packageName.isBlank()) return null
        val pkgClean = packageName.lowercase().trim()

        if (sender.isNotBlank()) {
            val exactKey = buildKey(packageName, sender)
            val exactIntent = cache[exactKey]
            if (exactIntent != null) return exactIntent

            // Fuzzy search by sender name substring
            val senderClean = sender.lowercase().trim()
            val matchedKey = cache.keys.find { key ->
                key.startsWith("$pkgClean:") && (key.contains(senderClean) || senderClean.contains(key.removePrefix("$pkgClean:")))
            }
            if (matchedKey != null) {
                return cache[matchedKey]
            }
        }

        // Fallback to latest pending intent for package
        return latestByPackage[pkgClean]
    }

    /**
     * Executes PendingIntent with background activity launch permission enabled.
     */
    fun sendPendingIntent(context: Context, intent: PendingIntent?): Boolean {
        if (intent == null) return false
        return try {
            val launchContext = AutoReplyAccessibilityService.getInstance() ?: context
            val options = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
            intent.send(launchContext, 0, null, null, null, null, options.toBundle())
            Log.i(TAG, "Successfully sent PendingIntent for background app launch")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send PendingIntent: ${e.message}", e)
            false
        }
    }

    fun remove(packageName: String, sender: String) {
        val key = buildKey(packageName, sender)
        cache.remove(key)
    }

    fun clear() {
        cache.clear()
        latestByPackage.clear()
    }

    private fun buildKey(packageName: String, sender: String): String {
        return "${packageName.lowercase().trim()}:${sender.lowercase().trim()}"
    }
}
