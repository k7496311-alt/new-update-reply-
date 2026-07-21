package com.example.logger

import android.util.Log
import com.example.model.LogCategory
import com.example.model.LogLevel
import com.example.repository.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AppLogger {
    private const val TAG = "AppLogger"
    private var repository: LogRepository? = null
    private val loggerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init(logRepository: LogRepository) {
        repository = logRepository
        info(LogCategory.APPLICATION, "AppLogger initialized successfully")
    }

    private fun writeLog(category: LogCategory, level: LogLevel, message: String, extraData: String? = null) {
        // Log to Logcat regardless
        val logMessage = "[$category] $message" + if (extraData != null) "\nExtra: $extraData" else ""
        when (level) {
            LogLevel.SUCCESS -> Log.i(TAG, "💚 SUCCESS: $logMessage")
            LogLevel.INFO -> Log.i(TAG, "ℹ️ INFO: $logMessage")
            LogLevel.WARNING -> Log.w(TAG, "⚠️ WARNING: $logMessage")
            LogLevel.CRITICAL -> Log.e(TAG, "🚨 CRITICAL: $logMessage")
        }

        // Write to database asynchronously
        repository?.let { repo ->
            loggerScope.launch {
                try {
                    repo.insertLog(category, level, message, extraData)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist log in database: ${e.message}", e)
                }
            }
        }
    }

    fun success(category: LogCategory, message: String, extraData: String? = null) {
        writeLog(category, LogLevel.SUCCESS, message, extraData)
    }

    fun info(category: LogCategory, message: String, extraData: String? = null) {
        writeLog(category, LogLevel.INFO, message, extraData)
    }

    fun warning(category: LogCategory, message: String, extraData: String? = null) {
        writeLog(category, LogLevel.WARNING, message, extraData)
    }

    fun critical(category: LogCategory, message: String, extraData: String? = null) {
        writeLog(category, LogLevel.CRITICAL, message, extraData)
    }

    /**
     * Handles unexpected crashes.
     */
    fun recordCrash(throwable: Throwable, contextMessage: String = "Unhandled Exception") {
        val stackTrace = Log.getStackTraceString(throwable)
        critical(
            category = LogCategory.CRASH,
            message = "$contextMessage: ${throwable.localizedMessage ?: throwable.message}",
            extraData = stackTrace
        )
    }

    /**
     * Measures the execution time of a block of code and writes a performance log.
     */
    inline fun <T> measurePerformance(category: LogCategory, actionName: String, block: () -> T): T {
        val startTime = System.nanoTime()
        val result = block()
        val durationMs = (System.nanoTime() - startTime) / 1_000_000.0
        val performanceMessage = "'$actionName' completed in ${String.format("%.2f", durationMs)} ms"
        
        info(category, performanceMessage)
        if (category != LogCategory.PERFORMANCE) {
            info(LogCategory.PERFORMANCE, "[$category] $performanceMessage")
        }
        return result
    }
}
