package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log

/**
 * Accessibility service that detects message content or window changes for automated actions.
 */
class AutoReplyAccessibilityService : AccessibilityService() {

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
        Log.d(TAG, "Accessibility Service successfully connected and stored")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // Do not automate any app yet. Framework only.
        Log.v(TAG, "onAccessibilityEvent: type=${event.eventType} package=${event.packageName}")
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
