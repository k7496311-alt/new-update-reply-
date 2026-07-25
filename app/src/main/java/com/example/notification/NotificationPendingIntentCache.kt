package com.example.notification

import android.app.PendingIntent
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory thread-safe cache storing recent Notification PendingIntents
 * keyed by "$packageName:$sender". Used by ReplyOrchestrator to launch
 * chats directly without manual screen navigation.
 */
object NotificationPendingIntentCache {
    private val cache = ConcurrentHashMap<String, PendingIntent>()

    fun put(packageName: String, sender: String, intent: PendingIntent?) {
        if (intent == null || packageName.isBlank() || sender.isBlank()) return
        val key = buildKey(packageName, sender)
        cache[key] = intent
    }

    fun get(packageName: String, sender: String): PendingIntent? {
        if (packageName.isBlank() || sender.isBlank()) return null
        val key = buildKey(packageName, sender)
        return cache[key]
    }

    fun remove(packageName: String, sender: String) {
        val key = buildKey(packageName, sender)
        cache.remove(key)
    }

    fun clear() {
        cache.clear()
    }

    private fun buildKey(packageName: String, sender: String): String {
        return "${packageName.lowercase().trim()}:${sender.lowercase().trim()}"
    }
}
