package com.example.accessibility.imo

/**
 * Result data holder for Bottom Position Verification Engine.
 */
data class BottomVerificationResult(
    val status: BottomVerificationStatus,
    val isAtBottom: Boolean,
    val scrollCount: Int,
    val visibleMessageCount: Int,
    val currentPosition: String,
    val details: String = ""
)
