package com.example.accessibility

import android.view.accessibility.AccessibilityNodeInfo

data class ScannedNode(
    val className: String,
    val text: String,
    val resourceId: String,
    val contentDescription: String,
    val isClickable: Boolean,
    val isScrollable: Boolean,
    val isEditable: Boolean,
    val isVisibleToUser: Boolean,
    val bounds: String
)

object NodeScanner {
    private const val CATEGORY = "NodeScanner"

    /**
     * Recursively scans the tree from the root node, extracting essential details.
     */
    fun scanTree(root: AccessibilityNodeInfo?): List<ScannedNode> {
        val list = mutableListOf<ScannedNode>()
        if (root == null) {
            AccessibilityLogger.w(CATEGORY, "Scan requested but root node is null")
            return list
        }
        traverseAndScan(root, list)
        AccessibilityLogger.d(CATEGORY, "Scanned ${list.size} nodes from root")
        return list
    }

    private fun traverseAndScan(node: AccessibilityNodeInfo, list: MutableList<ScannedNode>) {
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)

        list.add(
            ScannedNode(
                className = node.className?.toString() ?: "",
                text = node.text?.toString() ?: "",
                resourceId = node.viewIdResourceName ?: "",
                contentDescription = node.contentDescription?.toString() ?: "",
                isClickable = node.isClickable,
                isScrollable = node.isScrollable,
                isEditable = node.isEditable,
                isVisibleToUser = node.isVisibleToUser,
                bounds = bounds.toShortString()
            )
        )

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseAndScan(child, list)
            child.recycle()
        }
    }
}
