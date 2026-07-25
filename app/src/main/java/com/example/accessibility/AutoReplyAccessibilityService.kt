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
class AutoReplyAccessibilityService : AccessibilityService() {
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.e("ACCESSIBILITY", "✅ SERVICE CONNECTED SUCCESSFULLY")
        // Toast দেখান
        Toast.makeText(this, "Accessibility Service Connected!", Toast.LENGTH_LONG).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d("ACCESSIBILITY", "Event received: ${event?.eventType} from ${event?.packageName}")
    }

    override fun onInterrupt() {
        Log.e("ACCESSIBILITY", "Service Interrupted")
    }

    // আপনার openChat ফাংশনে যোগ করুন:
    fun openChat(contactName: String): Boolean {
        Log.d("ACCESSIBILITY", "Trying to open chat for: $contactName")
        
        val root = rootInActiveWindow
        if (root == null) {
            Log.e("ACCESSIBILITY", "❌ rootInActiveWindow is NULL!")
            return false
        }
        Log.d("ACCESSIBILITY", "✅ Root window found. Package: ${root.packageName}")
        
        // IMO খুলুন
        val intent = packageManager.getLaunchIntentForPackage("com.imo.android.imoim")
        if (intent == null) {
            Log.e("ACCESSIBILITY", "❌ Cannot find IMO launch intent!")
            return false
        }
        
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
            Log.d("ACCESSIBILITY", "✅ IMO launch intent fired!")
        } catch (e: Exception) {
            Log.e("ACCESSIBILITY", "❌ Failed to start IMO: ${e.message}")
            return false
        }
        
        // অপেক্ষা করুন
        Thread.sleep(2000)
        
        // আবার root নিন
        val newRoot = rootInActiveWindow
        if (newRoot == null) {
            Log.e("ACCESSIBILITY", "❌ Root still null after launching IMO")
            return false
        }
        
        Log.d("ACCESSIBILITY", "New root package: ${newRoot.packageName}")
        
        // Contact খুঁজুন
        val nodes = newRoot.findAccessibilityNodeInfosByText(contactName)
        Log.d("ACCESSIBILITY", "Found ${nodes.size} nodes with text '$contactName'")
        
        for (node in nodes) {
            Log.d("ACCESSIBILITY", "Node: clickable=${node.isClickable}, class=${node.className}")
            if (node.isClickable) {
                val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d("ACCESSIBILITY", "Click result: $clicked")
                return clicked
            }
        }
        
        Log.e("ACCESSIBILITY", "❌ No clickable node found for: $contactName")
        return false
    }
}
