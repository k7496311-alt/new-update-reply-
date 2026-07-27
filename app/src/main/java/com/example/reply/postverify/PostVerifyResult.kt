package com.example.reply.postverify

/**
 * Result model returned by Post-Send Verification Engine.
 */
data class PostVerifyResult(
    val status: PostVerifyStatus,
    val isCompleted: Boolean,
    val conversationId: String,
    val expectedReplyText: String,
    val matchedText: String? = null,
    val outgoingBubblesDetectedCount: Int = 0,
    val outgoingBubbleFound: Boolean = false,
    val replyMatched: Boolean = false,
    val queueItemMarkedCompleted: Boolean = false,
    val reason: String = "",
    val details: String = ""
)
