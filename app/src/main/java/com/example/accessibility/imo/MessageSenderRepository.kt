package com.example.accessibility.imo

import com.example.accessibility.AccessibilityLogger

/**
 * Repository layer that manages message dispatching operations via the Accessibility Service.
 * Follows the repository pattern to mediate between the Use Cases (domain logic) and the low-level UI Automation Sender.
 */
class MessageSenderRepository(
    private val replySender: ReplySender
) {

    companion object {
        private const val TAG = "MessageSenderRepository"
    }

    /**
     * Executes the reply sending workflow and returns a [SendResult].
     */
    suspend fun sendReply(contactName: String, text: String): SendResult {
        AccessibilityLogger.d(TAG, "Request to send reply received for contact: '$contactName'")
        return replySender.sendReply(contactName, text)
    }

    /**
     * Updates user interference/interaction status to pause or skip automations.
     */
    fun setUserInterfering(interfering: Boolean) {
        replySender.setUserInterfering(interfering)
    }
}
