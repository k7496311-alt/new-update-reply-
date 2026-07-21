package com.example.model

/**
 * Domain model representing a blocked/blacklisted contact, number, or package identifier.
 */
data class BlacklistEntry(
    val id: Long = 0L,
    val identifier: String,
    val reason: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: BlacklistStatus = BlacklistStatus.ACTIVE
)

enum class BlacklistStatus {
    ACTIVE,
    INACTIVE
}
