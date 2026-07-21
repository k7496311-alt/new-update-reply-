package com.example.model

enum class LogCategory {
    APPLICATION,
    ACCESSIBILITY,
    NOTIFICATION,
    QUEUE,
    REPLY,
    CRASH,
    DATABASE,
    PERFORMANCE
}

enum class LogLevel {
    SUCCESS,
    WARNING,
    CRITICAL,
    INFO
}

data class LogItem(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val category: LogCategory,
    val level: LogLevel,
    val message: String,
    val extraData: String? = null
)
