package com.example.accessibility.imo

/**
 * Result data class for Jump To Latest operations.
 */
data class JumpToLatestResult(
    val status: JumpToLatestStatus,
    val buttonFound: Boolean,
    val buttonClicked: Boolean,
    val verificationSuccess: Boolean,
    val details: String = ""
)
