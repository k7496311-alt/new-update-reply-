package com.example.model

/**
 * Domain model representing a whitelisted, blacklisted, or prioritized contact.
 */
data class Contact(
    val id: Long = 0L,
    val name: String,
    val phoneNumber: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: ContactStatus = ContactStatus.ACTIVE
)

enum class ContactStatus {
    ACTIVE,
    INACTIVE
}
