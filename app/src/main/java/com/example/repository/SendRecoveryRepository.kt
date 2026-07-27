package com.example.repository

import com.example.reply.recovery.SendRecoveryCriteria
import com.example.reply.recovery.SendRecoveryResult

/**
 * Clean Architecture repository interface for recovering failed sending operations.
 */
interface SendRecoveryRepository {
    /**
     * Executes single retry attempt while ensuring no duplicate replies are generated.
     * If retry fails, updates queue status to FAILED, moves to Failed Queue, and signals worker to continue.
     */
    suspend fun recoverFailedSend(
        criteria: SendRecoveryCriteria
    ): SendRecoveryResult
}
