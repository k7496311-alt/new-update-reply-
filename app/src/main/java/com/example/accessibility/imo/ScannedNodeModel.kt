package com.example.accessibility.imo

import android.graphics.Rect

/**
 * Structured UI model representing a single Accessibility node in the scanned tree.
 */
data class ScannedNodeModel(
    val nodeIndex: Int,
    val className: String,
    val packageName: String,
    val resourceId: String,
    val text: String,
    val contentDescription: String,
    val clickable: Boolean,
    val editable: Boolean,
    val enabled: Boolean,
    val visible: Boolean,
    val scrollable: Boolean,
    val bounds: Rect,
    val childCount: Int,
    val parent: String?,
    val depth: Int,
    val children: List<ScannedNodeModel> = emptyList()
) {
    fun toDetailString(): String {
        val attrList = mutableListOf<String>()
        if (clickable) attrList.add("clickable")
        if (editable) attrList.add("editable")
        if (enabled) attrList.add("enabled")
        if (visible) attrList.add("visible") else attrList.add("hidden")
        if (scrollable) attrList.add("scrollable")

        val attrStr = attrList.joinToString(", ")
        val parentStr = parent?.let { " [Parent: $it]" } ?: ""

        return "[$nodeIndex] Depth $depth | $className | ID: '$resourceId' | Text: '$text' | Desc: '$contentDescription' | Bounds: ${bounds.toShortString()} | Children: $childCount | Flags: [$attrStr]$parentStr"
    }
}
