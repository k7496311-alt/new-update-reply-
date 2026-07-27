package com.example.accessibility.imo

import android.graphics.Rect

/**
 * Model representing a classified chat message bubble node in chronological order.
 */
data class MessageBubbleModel(
    val nodeIndex: Int,
    val type: MessageBubbleType,
    val resourceId: String,
    val className: String,
    val text: String,
    val contentDescription: String,
    val bounds: Rect,
    val isIncoming: Boolean?,
    val rawNode: ScannedNodeModel
)
