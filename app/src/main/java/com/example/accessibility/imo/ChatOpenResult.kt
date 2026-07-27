package com.example.accessibility.imo

sealed class ChatOpenResult {
    data class Success(
        val senderName: String,
        val packageName: String,
        val details: String = ""
    ) : ChatOpenResult()

    data class WrongChat(
        val expectedSender: String,
        val actualSender: String,
        val details: String = ""
    ) : ChatOpenResult()

    data class Failed(
        val reason: String,
        val details: String = ""
    ) : ChatOpenResult()
}
