package com.example.accessibility.input.inserter

/**
 * Result model holding insertion outcome, verified character count, and retry attempts.
 */
data class MessageInputInsertResult(
    val status: MessageInputInsertStatus,
    val isSuccess: Boolean,
    val expectedText: String,
    val actualInsertedText: String,
    val insertedCharacterCount: Int,
    val attemptsCount: Int,
    val verificationPassed: Boolean,
    val usedFallbackStrategy: Boolean,
    val reason: String = "",
    val details: String = ""
)
