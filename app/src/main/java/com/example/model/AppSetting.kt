package com.example.model

/**
 * Domain model representing a dynamic app preference or configuration stored in the database.
 */
data class AppSetting(
    val key: String,
    val value: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: SettingsStatus = SettingsStatus.ACTIVE
)

enum class SettingsStatus {
    ACTIVE,
    INACTIVE
}
