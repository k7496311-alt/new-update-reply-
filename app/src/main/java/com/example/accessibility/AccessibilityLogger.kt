package com.example.accessibility

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.LogCategory

object AccessibilityLogger {
    private const val TAG = "AccessibilityFramework"

    fun d(category: String, message: String) {
        Log.d(TAG, "[$category] 🔍 $message")
        AppLogger.info(LogCategory.ACCESSIBILITY, "[$category] $message")
    }

    fun i(category: String, message: String) {
        Log.i(TAG, "[$category] ℹ️ $message")
        AppLogger.success(LogCategory.ACCESSIBILITY, "[$category] $message")
    }

    fun w(category: String, message: String) {
        Log.w(TAG, "[$category] ⚠️ $message")
        AppLogger.warning(LogCategory.ACCESSIBILITY, "[$category] $message")
    }

    fun e(category: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, "[$category] ❌ $message", throwable)
            AppLogger.critical(
                LogCategory.ACCESSIBILITY,
                "[$category] $message",
                Log.getStackTraceString(throwable)
            )
        } else {
            Log.e(TAG, "[$category] ❌ $message")
            AppLogger.critical(LogCategory.ACCESSIBILITY, "[$category] $message")
        }
    }
}
