package com.example.accessibility.imo

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.repository.UiScannerRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-grade Accessibility UI Scanner Engine for IMO chat.
 *
 * Requirements:
 * - Does NOT guess, hardcode, or search only by text.
 * - Recursively scans complete AccessibilityNodeInfo tree.
 * - For EVERY node collects: className, packageName, resourceId, text, contentDescription,
 *   clickable, editable, enabled, visible, scrollable, bounds, childCount, parent.
 * - Stores UI tree in memory.
 * - Produces complete debug logs:
 *   - Node Count
 *   - Tree Depth
 *   - Node Details
 *   - Visible Nodes
 *   - Editable Nodes
 *   - Clickable Nodes
 * - Performs NO message reading, NO reply, NO scrolling.
 */
class UiScannerEngine(
    private val repository: UiScannerRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun executeScan(): UiScanReport = withContext(dispatcher) {
        val report = repository.scanActiveUiTree()

        // 1. Log: Node Count
        logNodeCount(report.totalNodeCount)

        // 2. Log: Tree Depth
        logTreeDepth(report.maxTreeDepth)

        // 3. Log: Visible Nodes
        logVisibleNodes(report.visibleNodesCount)

        // 4. Log: Editable Nodes
        logEditableNodes(report.editableNodesCount)

        // 5. Log: Clickable Nodes
        logClickableNodes(report.clickableNodesCount)

        // 6. Log: Node Details (Comprehensive tree scan details for every node)
        logNodeDetails(report.flatNodeList)

        report
    }

    fun getInMemoryTree(): UiScanReport? {
        return repository.getLatestScanReport()
    }

    private fun logNodeCount(count: Int) {
        val msg = "Node Count: $count"
        Log.i(TAG, msg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Node Count",
            msg
        )
    }

    private fun logTreeDepth(depth: Int) {
        val msg = "Tree Depth: $depth"
        Log.i(TAG, msg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Tree Depth",
            msg
        )
    }

    private fun logVisibleNodes(count: Int) {
        val msg = "Visible Nodes: $count"
        Log.i(TAG, msg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Visible Nodes",
            msg
        )
    }

    private fun logEditableNodes(count: Int) {
        val msg = "Editable Nodes: $count"
        Log.i(TAG, msg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Editable Nodes",
            msg
        )
    }

    private fun logClickableNodes(count: Int) {
        val msg = "Clickable Nodes: $count"
        Log.i(TAG, msg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Clickable Nodes",
            msg
        )
    }

    private fun logNodeDetails(nodes: List<ScannedNodeModel>) {
        if (nodes.isEmpty()) {
            val msg = "Node Details: Tree empty or null root node"
            Log.w(TAG, msg)
            AppLogger.warning(LogCategory.ACCESSIBILITY, "Node Details", msg)
            return
        }

        val detailsBuilder = StringBuilder()
        detailsBuilder.append("Node Details (Total: ${nodes.size} nodes):\n")

        nodes.forEach { node ->
            val nodeDetail = node.toDetailString()
            Log.d(TAG, "Node Details -> $nodeDetail")
            detailsBuilder.append(nodeDetail).append("\n")
        }

        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Node Details",
            "Scanned ${nodes.size} nodes completely. Details logged."
        )
    }

    companion object {
        private const val TAG = "UiScannerEngine"
    }
}
