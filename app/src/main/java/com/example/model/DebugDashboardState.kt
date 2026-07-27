package com.example.model

/**
 * Data model representing real-time status parameters for the Debug Dashboard.
 */
data class DebugDashboardState(
    val isDebugModeEnabled: Boolean = true,
    val currentQueueCount: Int = 0,
    val currentCustomer: String = "None (Idle)",
    val currentStep: String = "Idle",
    val currentChat: String = "com.imo.android.imoim",
    val lastReadMessages: List<String> = emptyList(),
    val matchedRule: String = "None",
    val generatedReply: String = "None",
    val insertStatus: String = "PENDING",
    val sendStatus: String = "PENDING",
    val accessibilityStatus: String = "Connected",
    val nodeCount: Int = 0,
    val latestError: String = "None",
    val latestLog: String = "System initialized and monitoring.",
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
