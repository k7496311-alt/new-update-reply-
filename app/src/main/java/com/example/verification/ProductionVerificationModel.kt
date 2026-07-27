package com.example.verification

/**
 * Status of an individual verification module or overall verification suite.
 */
enum class VerificationStatus {
    PASS,
    WARNING,
    FAIL
}

/**
 * Result for a single verified module.
 */
data class ModuleVerificationResult(
    val id: String,
    val displayName: String,
    val status: VerificationStatus,
    val durationMs: Long,
    val details: String,
    val failureReason: String? = null
)

/**
 * Comprehensive production verification report covering all 19 required test targets.
 */
data class FullVerificationReport(
    val overallVerdict: VerificationStatus,
    val totalTests: Int,
    val passCount: Int,
    val warningCount: Int,
    val failCount: Int,
    val failedModules: List<String>,
    val moduleResults: List<ModuleVerificationResult>,
    val timestamp: Long = System.currentTimeMillis()
)
