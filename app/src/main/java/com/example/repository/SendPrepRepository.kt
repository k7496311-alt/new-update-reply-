package com.example.repository

import com.example.reply.sendprep.SendPrepCriteria
import com.example.reply.sendprep.SendPrepResult

/**
 * Clean Architecture repository interface for Send Preparation Verification.
 */
interface SendPrepRepository {
    /**
     * Verifies all 7 pre-sending requirements:
     * 1. Conversation still open
     * 2. Same customer
     * 3. Input box exists
     * 4. Accessibility alive
     * 5. Reply VALID
     * 6. Duplicate check passed
     * 7. Queue item active
     */
    suspend fun verifySendReadiness(
        criteria: SendPrepCriteria
    ): SendPrepResult
}
