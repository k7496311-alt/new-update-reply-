package com.example.repository

import com.example.accessibility.imo.UiScanReport

/**
 * Clean Architecture repository interface for complete Accessibility UI Scanning.
 */
interface UiScannerRepository {
    /**
     * Recursively scans the active AccessibilityNodeInfo tree and returns a structured UiScanReport.
     */
    suspend fun scanActiveUiTree(): UiScanReport

    /**
     * Returns the last cached in-memory scan report, if available.
     */
    fun getLatestScanReport(): UiScanReport?
}
