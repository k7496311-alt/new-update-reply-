package com.example.repository

/**
 * Clean Architecture Repository interface for Crash Recovery.
 *
 * Requirements:
 * - If application process dies or Accessibility Service restarts, restore:
 *   1. Queue
 *   2. Current conversation
 *   3. Pending reply
 *   4. History
 * - Resume safely without sending duplicate replies.
 * - Emit exact required logs:
 *   - Recovery Started
 *   - Queue Restored
 *   - Conversation Restored
 *   - Recovery Finished
 */
interface CrashRecoveryRepository {

    /**
     * Executes crash recovery flow: restores Queue, Current Conversation, Pending Reply, and History.
     * Prevents duplicate reply sending and safely resumes queue execution.
     */
    suspend fun performCrashRecovery(
        criteria: CrashRecoveryCriteria = CrashRecoveryCriteria()
    ): CrashRecoveryResult

    /**
     * Checks if crash recovery is needed (e.g., pending queue items or interrupted PROCESSING states).
     */
    suspend fun isRecoveryNeeded(): Boolean
}
