package com.example.accessibility.sendbutton

import android.graphics.Rect

/**
 * Contextual search criteria for locating and verifying the active chat send button.
 */
data class SendButtonDetectorCriteria(
    val targetPackageName: String = "com.imo.android.imoim",
    val composerInputBounds: Rect? = null,
    val maxHorizontalDistancePx: Int = 300, // Distance to right or left of composer
    val maxVerticalOffsetPx: Int = 150,     // Vertical alignment with composer input
    val screenWidthPx: Int = 1080,
    val screenHeightPx: Int = 2400
)
