package com.example.data

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.accessibility.AccessibilityManager
import com.example.accessibility.imo.ScannedNodeModel
import com.example.accessibility.imo.UiScanReport
import com.example.repository.UiScannerRepository
import java.util.concurrent.atomic.AtomicInteger

/**
 * Concrete implementation of UiScannerRepository.
 * Performs complete recursive scanning of the active AccessibilityNodeInfo tree without skipping any nodes.
 * Stores the structured UI tree report in memory.
 */
class UiScannerRepositoryImpl(
    private val context: Context,
    private val accessibilityManager: AccessibilityManager = AccessibilityManager(context)
) : UiScannerRepository {

    @Volatile
    private var cachedReport: UiScanReport? = null

    override suspend fun scanActiveUiTree(): UiScanReport {
        val root = accessibilityManager.getRootNode() ?: return UiScanReport(
            rootNode = null,
            flatNodeList = emptyList(),
            totalNodeCount = 0,
            maxTreeDepth = 0,
            visibleNodesCount = 0,
            editableNodesCount = 0,
            clickableNodesCount = 0
        )

        try {
            val nodeCounter = AtomicInteger(0)
            val flatList = mutableListOf<ScannedNodeModel>()

            val rootModel = scanNodeRecursive(
                node = root,
                depth = 0,
                parentDescriptor = null,
                nodeCounter = nodeCounter,
                flatList = flatList
            )

            val totalNodes = flatList.size
            val maxDepth = flatList.maxOfOrNull { it.depth } ?: 0
            val visibleCount = flatList.count { it.visible }
            val editableCount = flatList.count { it.editable }
            val clickableCount = flatList.count { it.clickable }

            val report = UiScanReport(
                rootNode = rootModel,
                flatNodeList = flatList,
                totalNodeCount = totalNodes,
                maxTreeDepth = maxDepth,
                visibleNodesCount = visibleCount,
                editableNodesCount = editableCount,
                clickableNodesCount = clickableCount
            )

            cachedReport = report
            return report
        } finally {
            root.recycle()
        }
    }

    override fun getLatestScanReport(): UiScanReport? {
        return cachedReport
    }

    private fun scanNodeRecursive(
        node: AccessibilityNodeInfo,
        depth: Int,
        parentDescriptor: String?,
        nodeCounter: AtomicInteger,
        flatList: MutableList<ScannedNodeModel>
    ): ScannedNodeModel {
        val currentIndex = nodeCounter.getAndIncrement()

        val className = node.className?.toString() ?: ""
        val packageName = node.packageName?.toString() ?: ""
        val resourceId = node.viewIdResourceName ?: ""
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val isClickable = node.isClickable
        val isEditable = node.isEditable
        val isEnabled = node.isEnabled
        val isVisible = node.isVisibleToUser
        val isScrollable = node.isScrollable

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val childCount = node.childCount

        val currentDescriptor = if (resourceId.isNotBlank()) {
            resourceId
        } else if (className.isNotBlank()) {
            "$className#$currentIndex"
        } else {
            "Node#$currentIndex"
        }

        val childrenModels = mutableListOf<ScannedNodeModel>()
        for (i in 0 until childCount) {
            val childNode = node.getChild(i) ?: continue
            val childModel = scanNodeRecursive(
                node = childNode,
                depth = depth + 1,
                parentDescriptor = currentDescriptor,
                nodeCounter = nodeCounter,
                flatList = flatList
            )
            childrenModels.add(childModel)
            childNode.recycle()
        }

        val nodeModel = ScannedNodeModel(
            nodeIndex = currentIndex,
            className = className,
            packageName = packageName,
            resourceId = resourceId,
            text = text,
            contentDescription = contentDesc,
            clickable = isClickable,
            editable = isEditable,
            enabled = isEnabled,
            visible = isVisible,
            scrollable = isScrollable,
            bounds = bounds,
            childCount = childCount,
            parent = parentDescriptor,
            depth = depth,
            children = childrenModels
        )

        flatList.add(nodeModel)
        return nodeModel
    }
}
