package com.example.accessibility.imo

import com.example.accessibility.AccessibilityLogger

/**
 * UseCase that encapsulates the core business workflow of sending an automated reply message.
 * Adheres to Clean Architecture principles by providing a single action boundary for UI clients.
 */
class SendMessageUseCase(
    private val repository: MessageSenderRepository
) {

    companion object {
        private const val TAG = "SendMessageUseCase"
    }

    /**
     * Executes the send message action.
     *
     * @param contactName The target contact name in IMO to send the message to.
     * @param text The text body of the reply message.
     * @return The final [SendResult] containing success, failure, timeout, or cancellation info.
     */
    suspend operator fun invoke(contactName: String, text: String): SendResult {
        AccessibilityLogger.i(TAG, "Invoking SendMessageUseCase for contact: '$contactName'")
        return repository.sendReply(contactName, text)
    }

    /**
     * Propagates user interference / interruption status to the repository and underlying controllers.
     */
    fun setUserInterfering(interfering: Boolean) {
        repository.setUserInterfering(interfering)
    }
}
