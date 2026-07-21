package com.example.accessibility.imo

/**
 * Represents the final outcome of an automated accessibility message sending workflow.
 */
sealed class SendResult {
    /**
     * Sent successfully.
     * @property timestamp The unix time in milliseconds when the message was successfully dispatched and verified.
     */
    data class Success(val timestamp: Long) : SendResult()

    /**
     * Sent failed with a descriptive reason.
     * @property reason The details of why the message send flow failed.
     */
    data class Failed(val reason: String) : SendResult()

    /**
     * Sending took too long and exceeded the overall safety constraints.
     * @property message Timeout error details.
     */
    data class Timeout(val message: String) : SendResult()

    /**
     * Workflow was intentionally stopped or cancelled because of user activity or other high-priority interference.
     * @property message Reason for cancellation.
     */
    data class Cancelled(val message: String) : SendResult()
}
