package com.example.model

enum class PermissionStatus {
    GRANTED,      // Green
    RESTRICTED,   // Yellow (e.g. manufacturer limitations, warnings)
    NOT_GRANTED   // Red
}

data class PermissionItem(
    val id: String, // "notification", "accessibility", "overlay", "battery", "foreground", "autostart", "background_restriction"
    val title: String,
    val description: String,
    val status: PermissionStatus,
    val details: String = ""
)
