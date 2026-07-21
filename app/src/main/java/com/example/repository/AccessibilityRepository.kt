package com.example.repository

import kotlinx.coroutines.flow.Flow

interface AccessibilityRepository {
    fun isServiceEnabled(): Flow<Boolean>
    fun isServiceRunning(): Boolean
    fun openAccessibilitySettings()
    suspend fun <T> runWithService(
        timeoutMillis: Long = 10000L,
        block: suspend () -> T
    ): Result<T>
}
