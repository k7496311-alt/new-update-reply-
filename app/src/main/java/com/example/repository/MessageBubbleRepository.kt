package com.example.repository

import com.example.accessibility.imo.MessageBubbleDetectionResult
import com.example.accessibility.imo.UiScanReport

/**
 * Clean Architecture repository interface for detecting and classifying chat message bubbles.
 */
interface MessageBubbleRepository {
    /**
     * Processes the scanned Accessibility tree (from Step 9), detects chat bubbles,
     * classifies each node deterministically, and returns chronologically ordered bubbles with metrics.
     */
    suspend fun detectAndClassifyMessageBubbles(scanReport: UiScanReport? = null): MessageBubbleDetectionResult
}
