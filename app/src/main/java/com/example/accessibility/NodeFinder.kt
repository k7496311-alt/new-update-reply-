package com.example.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

data class SearchCriteria(
    val text: String? = null,
    val textContains: String? = null,
    val resourceId: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val isClickable: Boolean? = null,
    val isEditable: Boolean? = null
) {
    fun matches(node: AccessibilityNodeInfo): Boolean {
        if (text != null && node.text?.toString() != text) return false
        if (textContains != null && node.text?.toString()?.contains(textContains, ignoreCase = true) != true) return false
        if (resourceId != null && node.viewIdResourceName != resourceId) return false
        if (contentDescription != null && node.contentDescription?.toString() != contentDescription) return false
        if (className != null && node.className?.toString() != className) return false
        if (isClickable != null && node.isClickable != isClickable) return false
        if (isEditable != null && node.isEditable != isEditable) return false
        return true
    }
}

object NodeFinder {
    private const val CATEGORY = "NodeFinder"

    /**
     * Recursively find all nodes matching the given criteria starting from the root node.
     */
    fun findNodes(root: AccessibilityNodeInfo?, criteria: SearchCriteria): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        if (root == null) return results
        traverseAndCollect(root, criteria, results)
        return results
    }

    private fun traverseAndCollect(
        node: AccessibilityNodeInfo,
        criteria: SearchCriteria,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        if (criteria.matches(node)) {
            // Need to obtain/copy node so it is safe to hold onto, 
            // though the caller is responsible for recycling it eventually.
            results.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseAndCollect(child, criteria, results)
            child.recycle()
        }
    }

    /**
     * Find a single node matching the given criteria. Returns null if not found.
     */
    fun findFirstNode(root: AccessibilityNodeInfo?, criteria: SearchCriteria): AccessibilityNodeInfo? {
        if (root == null) return null
        if (criteria.matches(root)) {
            return AccessibilityNodeInfo.obtain(root)
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findFirstNode(child, criteria)
            child.recycle()
            if (found != null) {
                return found
            }
        }
        return null
    }

    /**
     * Waits for a node to appear using safe delay and retry mechanism within a timeout.
     */
    suspend fun waitForNode(
        rootProvider: () -> AccessibilityNodeInfo?,
        criteria: SearchCriteria,
        timeoutMillis: Long = 5000L,
        retryDelayMillis: Long = 300L
    ): AccessibilityNodeInfo? {
        AccessibilityLogger.d(CATEGORY, "Waiting for node matching criteria: $criteria")
        return withTimeoutOrNull(timeoutMillis) {
            var elapsed = 0L
            while (elapsed < timeoutMillis) {
                val root = rootProvider()
                if (root != null) {
                    val node = findFirstNode(root, criteria)
                    root.recycle()
                    if (node != null) {
                        AccessibilityLogger.d(CATEGORY, "Node found after ${elapsed}ms")
                        return@withTimeoutOrNull node
                    }
                }
                delay(retryDelayMillis)
                elapsed += retryDelayMillis
            }
            AccessibilityLogger.w(CATEGORY, "Timeout reached waiting for node matching criteria")
            null
        }
    }
}
