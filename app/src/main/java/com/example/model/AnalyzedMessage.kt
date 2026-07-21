package com.example.model

data class AnalyzedMessage(
    val originalText: String,
    val normalizedText: String,
    val messageType: MessageType,
    val extractedValue: String? = null
)
