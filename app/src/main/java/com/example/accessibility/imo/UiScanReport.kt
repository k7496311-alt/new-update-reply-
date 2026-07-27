package com.example.accessibility.imo

/**
 * Complete in-memory scan report for the scanned Accessibility UI tree.
 */
data class UiScanReport(
    val rootNode: ScannedNodeModel?,
    val flatNodeList: List<ScannedNodeModel>,
    val totalNodeCount: Int,
    val maxTreeDepth: Int,
    val visibleNodesCount: Int,
    val editableNodesCount: Int,
    val clickableNodesCount: Int,
    val scanTimestamp: Long = System.currentTimeMillis()
)
