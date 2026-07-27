package com.example.model

/**
 * Data model holding system optimization and telemetry performance metrics.
 */
data class SystemOptimizationMetrics(
    val processingTimeMs: Long = 0L,
    val memoryUsageMb: Float = 0f,
    val totalMemoryMb: Float = 0f,
    val cpuUsagePercent: Float = 0f,
    val accessibilityScanTimeMs: Long = 0L,
    val queueTimeMs: Long = 0L,
    val cachedNodeReuseCount: Int = 0,
    val avoidedScrollCount: Int = 0,
    val isPowerSaverActive: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
