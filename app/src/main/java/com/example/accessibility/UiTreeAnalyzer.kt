package com.example.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import java.lang.StringBuilder

object UiTreeAnalyzer {
    private const val CATEGORY = "UiTreeAnalyzer"

    /**
     * Generates a beautifully indented, text-based visual representation of the active UI tree.
     */
    fun analyzeAndDump(root: AccessibilityNodeInfo?): String {
        if (root == null) {
            AccessibilityLogger.w(CATEGORY, "UI Tree dump requested, but root is null")
            return "Root is null"
        }
        val builder = StringBuilder()
        builder.append("=== UI TREE DUMP ===\n")
        buildTreeRepresentation(root, builder, 0)
        builder.append("====================\n")
        val result = builder.toString()
        AccessibilityLogger.i(CATEGORY, "UI tree analyzed:\n$result")
        return result
    }

    private fun buildTreeRepresentation(node: AccessibilityNodeInfo, builder: StringBuilder, depth: Int) {
        val indent = "  ".repeat(depth)
        val className = node.className?.toString()?.split('.')?.lastOrNull() ?: "Unknown"
        val text = node.text?.toString() ?: ""
        val id = node.viewIdResourceName?.split('/')?.lastOrNull() ?: ""
        val desc = node.contentDescription?.toString() ?: ""

        val attributes = mutableListOf<String>()
        if (node.isClickable) attributes.add("clickable")
        if (node.isScrollable) attributes.add("scrollable")
        if (node.isEditable) attributes.add("editable")
        if (node.isSelected) attributes.add("selected")
        if (!node.isVisibleToUser) attributes.add("hidden")

        val attrStr = if (attributes.isNotEmpty()) " [${attributes.joinToString(", ")}]" else ""

        builder.append(indent)
            .append("• ")
            .append(className)
            .append(if (id.isNotEmpty()) " (id: $id)" else "")
            .append(if (text.isNotEmpty()) " text:\"$text\"" else "")
            .append(if (desc.isNotEmpty()) " desc:\"$desc\"" else "")
            .append(attrStr)
            .append("\n")

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            buildTreeRepresentation(child, builder, depth + 1)
            child.recycle()
        }
    }

    /**
     * Scans the tree to run diagnostic checks (e.g. check for visible input fields, check for clickable buttons).
     */
    fun runDiagnostics(root: AccessibilityNodeInfo?): DiagnosticsReport {
        if (root == null) return DiagnosticsReport(0, 0, 0, emptyList())
        
        var totalNodes = 0
        var inputFields = 0
        var clickableElements = 0
        val warnings = mutableListOf<String>()

        fun traverse(node: AccessibilityNodeInfo) {
            totalNodes++
            if (node.isEditable) {
                inputFields++
            }
            if (node.isClickable) {
                clickableElements++
                // Check for accessibility touch target recommendation
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                val widthDp = bounds.width()
                val heightDp = bounds.height()
                if (widthDp < 48 || heightDp < 48) {
                    warnings.add("Small touch target: ${node.className} (id: ${node.viewIdResourceName}) is clickable but small (${widthDp}x${heightDp}px)")
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child)
                child.recycle()
            }
        }

        traverse(root)
        return DiagnosticsReport(totalNodes, inputFields, clickableElements, warnings)
    }

    data class DiagnosticsReport(
        val totalNodesCount: Int,
        val inputFieldsCount: Int,
        val clickableElementsCount: Int,
        val warnings: List<String>
    )
}
