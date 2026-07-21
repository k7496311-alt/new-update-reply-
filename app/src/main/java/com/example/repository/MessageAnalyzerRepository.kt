package com.example.repository

import com.example.model.AnalyzedMessage
import com.example.model.MessageType

interface MessageAnalyzerRepository {
    /**
     * Analyzes an incoming message to detect its type and normalize its content.
     */
    fun analyze(message: String): AnalyzedMessage

    /**
     * Checks if a message type is supported. Unsupported message types should be ignored.
     */
    fun isSupported(messageType: MessageType): Boolean
}
