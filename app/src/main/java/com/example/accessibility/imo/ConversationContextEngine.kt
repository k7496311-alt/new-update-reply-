package com.example.accessibility.imo

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.repository.ConversationContextRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-grade Conversation Context Engine.
 * Requirements:
 * - Collects latest readable text messages (excluding stickers, voice, calls, images, system msgs, separators).
 * - Combines multiple customer messages into ONE conversation context.
 * - Returns: Conversation Context, Last Message, Total Messages, Character Count, Message Order.
 * - Emits required logs:
 *   - Conversation Built
 *   - Message Combined
 *   - Conversation Length
 *   - Latest Text
 *   - Oldest Included
 * - NO reply generation, NO AI, NO keyword matching.
 */
class ConversationContextEngine(
    private val repository: ConversationContextRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun buildContext(
        extractedMessages: List<ExtractedTextModel>,
        maxMessagesToInclude: Int = 20
    ): ConversationContextModel = withContext(dispatcher) {

        val result = repository.buildConversationContext(extractedMessages, maxMessagesToInclude)

        if (result.totalMessages > 0) {
            // 1. Log: Conversation Built
            logConversationBuilt(result)

            // 2. Log: Message Combined
            logMessageCombined(result)

            // 3. Log: Conversation Length
            logConversationLength(result.characterCount, result.totalMessages)

            // 4. Log: Latest Text
            logLatestText(result.lastMessage)

            // 5. Log: Oldest Included
            logOldestIncluded(result.oldestIncludedText)
        } else {
            Log.d(TAG, "No readable messages to build conversation context.")
        }

        result
    }

    private fun logConversationBuilt(model: ConversationContextModel) {
        val logMsg = """
            Conversation Built
            Total Messages Included: ${model.totalMessages}
            Combined Context Character Count: ${model.characterCount}
            Unified Context String:
            "${model.conversationContext}"
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Conversation Built",
            logMsg
        )
    }

    private fun logMessageCombined(model: ConversationContextModel) {
        val sb = StringBuilder()
        sb.append("Message Combined (${model.totalMessages} messages joined):\n")
        model.messageOrder.forEachIndexed { idx, item ->
            val dir = if (item.isIncoming == true) "Incoming" else "Outgoing"
            sb.append("#${idx + 1} [$dir] \"${item.text}\"\n")
        }

        val logMsg = sb.toString().trimEnd()
        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Message Combined",
            logMsg
        )
    }

    private fun logConversationLength(charCount: Int, messageCount: Int) {
        val logMsg = "Conversation Length: $charCount characters across $messageCount message(s)"
        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Conversation Length",
            logMsg
        )
    }

    private fun logLatestText(text: String) {
        val logMsg = "Latest Text: \"$text\""
        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Latest Text",
            logMsg
        )
    }

    private fun logOldestIncluded(text: String) {
        val logMsg = "Oldest Included: \"$text\""
        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Oldest Included",
            logMsg
        )
    }

    companion object {
        private const val TAG = "ConversationContextEngine"
    }
}
