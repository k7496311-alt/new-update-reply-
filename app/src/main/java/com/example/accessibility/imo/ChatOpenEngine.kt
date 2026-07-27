package com.example.accessibility.imo

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.model.QueueItem
import com.example.repository.ChatOpenRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-grade Chat Open Engine.
 * Input: One Conversation Queue Item.
 * Opens ONLY the correct IMO chat. Never guesses. Never opens wrong conversation.
 * Verifies correct sender name is visible in chat header.
 * If wrong chat opens: closes it and retries ONCE. If still failed, returns failure.
 * Generates exact required logs:
 *   - Conversation Selected
 *   - Opening Chat
 *   - Chat Open Success
 *   - Wrong Chat
 *   - Retry
 *   - Open Failed
 */
class ChatOpenEngine(
    private val repository: ChatOpenRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun openChatForConversation(queueItem: QueueItem): ChatOpenResult = withContext(dispatcher) {
        val senderName = queueItem.senderName
        val packageName = queueItem.packageName
        val messageText = queueItem.incomingMessage
        val queueId = queueItem.id

        // 1. Log: Conversation Selected
        val selectedLog = """
            Conversation Selected
            Queue ID: $queueId
            Sender: $senderName
            Package: $packageName
            Message: $messageText
        """.trimIndent()

        Log.i(TAG, selectedLog)
        AppLogger.info(
            LogCategory.QUEUE,
            "Conversation Selected ($senderName)",
            "Queue ID: $queueId | Sender: $senderName | Package: $packageName | Message: '$messageText'"
        )

        // 2. Attempt 1: Opening Chat
        val openingLog1 = """
            Opening Chat
            Attempt: 1/2
            Sender: $senderName
            Package: $packageName
        """.trimIndent()

        Log.i(TAG, openingLog1)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Opening Chat ($senderName)",
            "Attempt: 1/2 | Sender: $senderName | Package: $packageName"
        )

        val firstResult = repository.openChat(queueItem)

        when (firstResult) {
            is ChatOpenResult.Success -> {
                logSuccess(senderName, firstResult.details)
                return@withContext firstResult
            }

            is ChatOpenResult.WrongChat -> {
                logWrongChat(senderName, firstResult.actualSender, firstResult.details)
                return@withContext performRetryWorkflow(queueItem, attemptNumber = 2)
            }

            is ChatOpenResult.Failed -> {
                Log.w(TAG, "First chat open attempt failed for '$senderName': ${firstResult.reason}")
                return@withContext performRetryWorkflow(queueItem, attemptNumber = 2)
            }
        }
    }

    private suspend fun performRetryWorkflow(
        queueItem: QueueItem,
        attemptNumber: Int
    ): ChatOpenResult {
        val senderName = queueItem.senderName

        // Log: Retry
        val retryLog = """
            Retry
            Attempt: $attemptNumber/2
            Target Sender: $senderName
            Action: Closing incorrect/failed chat window and retrying chat open
        """.trimIndent()

        Log.w(TAG, retryLog)
        AppLogger.warning(
            LogCategory.ACCESSIBILITY,
            "Retry ($senderName)",
            retryLog
        )

        // Close wrong or failed chat window
        repository.closeCurrentChat()

        // Log: Opening Chat (Attempt 2)
        val openingLog2 = """
            Opening Chat
            Attempt: $attemptNumber/2
            Sender: $senderName
            Package: ${queueItem.packageName}
        """.trimIndent()

        Log.i(TAG, openingLog2)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Opening Chat ($senderName)",
            "Attempt: $attemptNumber/2 | Sender: $senderName | Package: ${queueItem.packageName}"
        )

        val retryResult = repository.openChat(queueItem)

        return when (retryResult) {
            is ChatOpenResult.Success -> {
                logSuccess(senderName, retryResult.details)
                retryResult
            }

            is ChatOpenResult.WrongChat -> {
                logWrongChat(senderName, retryResult.actualSender, retryResult.details)
                logOpenFailed(senderName, "Opened wrong chat '${retryResult.actualSender}' after retry attempt")
                ChatOpenResult.Failed(
                    reason = "Opened wrong chat '${retryResult.actualSender}' instead of '$senderName' on retry",
                    details = retryResult.details
                )
            }

            is ChatOpenResult.Failed -> {
                logOpenFailed(senderName, retryResult.reason)
                retryResult
            }
        }
    }

    private fun logSuccess(senderName: String, details: String) {
        val successLog = """
            Chat Open Success
            Sender: $senderName
            Details: $details
        """.trimIndent()

        Log.i(TAG, successLog)
        AppLogger.success(
            LogCategory.ACCESSIBILITY,
            "Chat Open Success ($senderName)",
            successLog
        )
    }

    private fun logWrongChat(expectedSender: String, actualSender: String, details: String) {
        val wrongLog = """
            Wrong Chat
            Expected Sender: $expectedSender
            Actual Visible Sender: $actualSender
            Details: $details
        """.trimIndent()

        Log.w(TAG, wrongLog)
        AppLogger.warning(
            LogCategory.ACCESSIBILITY,
            "Wrong Chat ($expectedSender)",
            wrongLog
        )
    }

    private fun logOpenFailed(senderName: String, reason: String) {
        val failedLog = """
            Open Failed
            Sender: $senderName
            Reason: $reason
        """.trimIndent()

        Log.e(TAG, failedLog)
        AppLogger.critical(
            LogCategory.ACCESSIBILITY,
            "Open Failed ($senderName)",
            failedLog
        )
    }

    companion object {
        private const val TAG = "ChatOpenEngine"
    }
}
