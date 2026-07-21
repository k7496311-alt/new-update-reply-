package com.example.data

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.accessibility.AutoReplyAccessibilityService
import com.example.permission.PermissionManager
import com.example.repository.AccessibilityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay

class AccessibilityRepositoryImpl(
    private val context: Context,
    private val permissionManager: PermissionManager = PermissionManager()
) : AccessibilityRepository {

    override fun isServiceEnabled(): Flow<Boolean> = flow {
        while (true) {
            val enabled = permissionManager.isAccessibilityServiceEnabled(
                context,
                AutoReplyAccessibilityService::class.java
            )
            emit(enabled)
            delay(2000) // Poll status every 2 seconds
        }
    }

    override fun isServiceRunning(): Boolean {
        return AutoReplyAccessibilityService.isActive()
    }

    override fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override suspend fun <T> runWithService(
        timeoutMillis: Long,
        block: suspend () -> T
    ): Result<T> {
        return try {
            withTimeout(timeoutMillis) {
                while (!isServiceRunning()) {
                    delay(200L)
                }
                Result.success(block())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
