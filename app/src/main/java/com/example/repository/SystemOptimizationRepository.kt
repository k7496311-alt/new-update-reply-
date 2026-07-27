package com.example.repository

import android.view.accessibility.AccessibilityNodeInfo
import com.example.accessibility.ScannedNode
import com.example.model.SystemOptimizationMetrics

/**
 * Clean Architecture repository interface for complete system optimization.
 *
 * Guarantees:
 * - Reduced accessibility scanning overhead via TTL caching & early exit.
 * - Reduced CPU & battery usage via adaptive throttling.
 * - Unnecessary scroll avoidance by verifying viewport visibility first.
 * - Safe UI node reuse with validation and proper node recycling to prevent memory leaks.
 * - Generates required logs:
 *   - Processing Time
 *   - Memory Usage
 *   - CPU Usage
 *   - Accessibility Scan Time
 *   - Queue Time
 */
interface SystemOptimizationRepository {

    /**
     * Safely scans accessibility tree with performance tracking & early termination.
     */
    suspend fun scanAccessibilityTreeSafely(root: AccessibilityNodeInfo?): List<ScannedNode>

    /**
     * Retrieves cached AccessibilityNodeInfo if valid (unexpired TTL and visible).
     */
    suspend fun getCachedNode(cacheKey: String): AccessibilityNodeInfo?

    /**
     * Safely caches an AccessibilityNodeInfo with TTL to avoid redundant deep tree scans.
     */
    suspend fun cacheNode(cacheKey: String, node: AccessibilityNodeInfo)

    /**
     * Evaluates if scrolling is actually necessary by checking if target node is already visible.
     */
    suspend fun shouldPerformScroll(root: AccessibilityNodeInfo?, targetIdentifier: String): Boolean

    /**
     * Emits required system performance logs:
     * - Processing Time
     * - Memory Usage
     * - CPU Usage
     * - Accessibility Scan Time
     * - Queue Time
     */
    suspend fun logSystemPerformance(
        processingTimeMs: Long,
        queueTimeMs: Long,
        accessibilityScanTimeMs: Long
    ): SystemOptimizationMetrics

    /**
     * Clears cached nodes and recycles them to prevent memory leaks.
     */
    suspend fun clearNodeCache()
}
