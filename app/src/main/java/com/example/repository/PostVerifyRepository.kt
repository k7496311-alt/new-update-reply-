package com.example.repository

import com.example.reply.postverify.PostVerifyCriteria
import com.example.reply.postverify.PostVerifyResult

/**
 * Clean Architecture repository interface for post-send reply verification.
 */
interface PostVerifyRepository {
    /**
     * Scans latest outgoing message bubbles in conversation UI tree,
     * verifies exact text match, and marks conversation completed if verified.
     */
    suspend fun verifyAndComplete(
        criteria: PostVerifyCriteria
    ): PostVerifyResult
}
