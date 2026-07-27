package com.example.accessibility.input

/**
 * Contextual search criteria for identifying and validating the active message input node.
 */
data class MessageInputFinderCriteria(
    val targetPackageName: String = "com.imo.android.imoim",
    val allowLitePackage: Boolean = true,
    val requireBottomScreenPosition: Boolean = true,
    val bottomScreenThresholdRatio: Float = 0.4f, // Input should be in lower 60% of screen
    val screenHeightPx: Int = 2400,
    val screenWidthPx: Int = 1080
)
