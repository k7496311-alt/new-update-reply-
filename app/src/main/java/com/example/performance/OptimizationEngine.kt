package com.example.performance

import android.content.Context
import android.os.PowerManager
import android.util.LruCache
import com.example.accessibility.AccessibilityLogger
import com.example.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Optimization Engine responsible for resource efficiency, memory caching,
 * RegEx pattern caching, database cleanup/pruning, and adaptive battery saver handling.
 */
class OptimizationEngine(private val context: Context) {

    companion object {
        private const val TAG = "OptimizationEngine"
        private const val REGEX_CACHE_SIZE = 100
        private const val HISTORY_MAX_AGE_DAYS = 30L

        @Volatile
        private var instance: OptimizationEngine? = null

        fun getInstance(context: Context): OptimizationEngine {
            return instance ?: synchronized(this) {
                instance ?: OptimizationEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private val regexPatternCache = object : LruCache<String, Pattern>(REGEX_CACHE_SIZE) {}
    private val ruleMatchCache = ConcurrentHashMap<String, String>()

    /**
     * Pre-compiles or retrieves cached RegEx patterns to eliminate compilation overhead in matching loops.
     */
    fun getCompiledRegex(patternString: String, isCaseSensitive: Boolean = false): Pattern {
        val cacheKey = "${if (isCaseSensitive) "CS:" else "CI:"}$patternString"
        synchronized(regexPatternCache) {
            val cached = regexPatternCache.get(cacheKey)
            if (cached != null) return cached

            val flags = if (isCaseSensitive) 0 else Pattern.CASE_INSENSITIVE
            val compiled = Pattern.compile(patternString, flags)
            regexPatternCache.put(cacheKey, compiled)
            return compiled
        }
    }

    /**
     * Checks if a cached rule match exists for a given input message.
     */
    fun getCachedMatchResult(inputMessage: String): String? {
        return ruleMatchCache[inputMessage]
    }

    /**
     * Caches a successful rule match for fast repeating message evaluation.
     */
    fun cacheMatchResult(inputMessage: String, ruleName: String) {
        if (ruleMatchCache.size > 200) {
            ruleMatchCache.clear()
        }
        ruleMatchCache[inputMessage] = ruleName
    }

    /**
     * Clears internal memory caches.
     */
    fun clearCaches() {
        synchronized(regexPatternCache) {
            regexPatternCache.evictAll()
        }
        ruleMatchCache.clear()
        AccessibilityLogger.i(TAG, "Cleared pattern and match caches.")
    }

    /**
     * Prunes database logs and reply history older than 30 days to keep database compact and fast.
     */
    suspend fun pruneOldRecords() = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val thirtyDaysAgo = System.currentTimeMillis() - (HISTORY_MAX_AGE_DAYS * 24 * 60 * 60 * 1000L)

            // Delete old history entries
            val deletedHistory = db.historyDao().deleteHistoryOlderThan(thirtyDaysAgo)

            // Delete old logs
            val deletedLogs = db.logDao().deleteLogsOlderThan(thirtyDaysAgo)

            AccessibilityLogger.i(
                TAG,
                "Database optimization complete: Pruned $deletedHistory history entries and $deletedLogs log entries older than $HISTORY_MAX_AGE_DAYS days."
            )
        } catch (e: Exception) {
            AccessibilityLogger.e(TAG, "Failed to prune old records: ${e.message}", e)
        }
    }

    /**
     * Checks if system Power Saver / Battery Saver mode is active.
     */
    fun isPowerSaverActive(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isPowerSaveMode == true
    }

    /**
     * Suggests optimal delay factor based on power saving status.
     * When power saver is active, introduces additional throttling to conserve battery.
     */
    fun getAdaptiveDelayMultiplier(): Float {
        return if (isPowerSaverActive()) 1.5f else 1.0f
    }
}
