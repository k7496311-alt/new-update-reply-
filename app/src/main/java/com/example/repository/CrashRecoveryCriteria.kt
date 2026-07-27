package com.example.repository

/**
 * Options/Criteria passed during crash recovery execution.
 */
data class CrashRecoveryCriteria(
    val autoResumeQueue: Boolean = true,
    val checkDuplicateHistory: Boolean = true,
    val packageName: String = "com.imo.android.imoim"
)
