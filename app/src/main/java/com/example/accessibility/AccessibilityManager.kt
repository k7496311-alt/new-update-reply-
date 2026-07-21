package com.example.accessibility

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityManager(private val context: Context) {
    private val CATEGORY = "AccessibilityManager"

    /**
     * Checks whether the helper accessibility service is currently active.
     */
    fun isServiceRunning(): Boolean {
        return AutoReplyAccessibilityService.isActive()
    }

    /**
     * Retrieves the running instance of the accessibility service.
     */
    fun getService(): AutoReplyAccessibilityService? {
        return AutoReplyAccessibilityService.getInstance()
    }

    /**
     * Retrieves the root node of the active window, if available.
     * Note: The caller must recycle this node after use to prevent memory leaks.
     */
    fun getRootNode(): AccessibilityNodeInfo? {
        val service = getService() ?: return null
        return service.rootInActiveWindow
    }

    /**
     * Scans and returns flat details of all nodes visible in the current window.
     */
    fun scanCurrentWindow(): List<ScannedNode> {
        val root = getRootNode()
        val result = NodeScanner.scanTree(root)
        root?.recycle()
        return result
    }

    /**
     * Dumps and logs the visual layout tree structure.
     */
    fun analyzeAndLogTree(): String {
        val root = getRootNode()
        val result = UiTreeAnalyzer.analyzeAndDump(root)
        root?.recycle()
        return result
    }

    /**
     * Runs structure diagnostic audits on the active UI tree.
     */
    fun runDiagnostics(): UiTreeAnalyzer.DiagnosticsReport {
        val root = getRootNode()
        val report = UiTreeAnalyzer.runDiagnostics(root)
        root?.recycle()
        return report
    }

    /**
     * Performs a standard back-button action.
     */
    fun performBack(): Boolean {
        AccessibilityLogger.i(CATEGORY, "Requesting global Back action")
        return AccessibilityActionHelper.safePerformBack(getService())
    }

    /**
     * Performs a standard home-button action.
     */
    fun performHome(): Boolean {
        AccessibilityLogger.i(CATEGORY, "Requesting global Home action")
        return AccessibilityActionHelper.safePerformHome(getService())
    }
}
