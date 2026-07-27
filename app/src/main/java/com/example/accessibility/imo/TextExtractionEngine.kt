package com.example.accessibility.imo

import android.util.Log
import com.example.logger.AppLogger
import com.example.model.LogCategory
import com.example.repository.TextExtractorRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-grade Chat Text Extraction Engine.
 * Requirements:
 * - Reads English, Bangla, Unicode, Mixed language, and Emojis inside text.
 * - Ignores: Sticker, Image, GIF, Voice Message, Missed Calls, Deleted Messages, System Messages, Dates, Unread Separators.
 * - Collects ONLY readable text messages while maintaining strict chronological sequence.
 * - Returns EMPTY_RESULT if no readable message exists.
 * - Emits required logs:
 *   - Extracted Text
 *   - Skipped Sticker
 *   - Skipped Voice
 *   - Skipped Call
 *   - Final Text Count
 * - Performs NO reply, NO keyword matching, ONLY text extraction.
 */
class TextExtractionEngine(
    private val repository: TextExtractorRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun extractText(
        bubbles: List<MessageBubbleModel>
    ): TextExtractionResult = withContext(dispatcher) {

        val result = repository.extractReadableText(bubbles)

        // 1. Log: Extracted Text (Log each extracted message maintaining sequence)
        if (result.extractedMessages.isNotEmpty()) {
            result.extractedMessages.forEach { msg ->
                logExtractedText(msg)
            }
        } else {
            Log.d(TAG, "Extracted Text: None found")
        }

        // 2. Log: Skipped Sticker
        if (result.skippedStickerCount > 0) {
            logSkippedSticker(result.skippedStickerCount)
        }

        // 3. Log: Skipped Voice
        if (result.skippedVoiceCount > 0) {
            logSkippedVoice(result.skippedVoiceCount)
        }

        // 4. Log: Skipped Call
        if (result.skippedCallCount > 0) {
            logSkippedCall(result.skippedCallCount)
        }

        // 5. Log: Final Text Count
        logFinalTextCount(result.finalTextCount)

        result
    }

    private fun logExtractedText(msg: ExtractedTextModel) {
        val directionStr = when (msg.isIncoming) {
            true -> "Incoming"
            false -> "Outgoing"
            null -> "Unknown Direction"
        }

        val logMsg = """
            Extracted Text
            Sequence: #${msg.sequenceIndex}
            Direction: $directionStr
            Script Type: ${msg.scriptType}
            Text: "${msg.text}"
        """.trimIndent()

        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Extracted Text",
            logMsg
        )
    }

    private fun logSkippedSticker(count: Int) {
        val logMsg = "Skipped Sticker: $count sticker bubble(s) ignored"
        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Skipped Sticker",
            logMsg
        )
    }

    private fun logSkippedVoice(count: Int) {
        val logMsg = "Skipped Voice: $count voice message bubble(s) ignored"
        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Skipped Voice",
            logMsg
        )
    }

    private fun logSkippedCall(count: Int) {
        val logMsg = "Skipped Call: $count missed call entry/entries ignored"
        Log.i(TAG, logMsg)
        AppLogger.info(
            LogCategory.ACCESSIBILITY,
            "Skipped Call",
            logMsg
        )
    }

    private fun logFinalTextCount(count: Int) {
        val logMsg = "Final Text Count: $count readable message(s) collected"
        Log.i(TAG, logMsg)
        AppLogger.success(
            LogCategory.ACCESSIBILITY,
            "Final Text Count",
            logMsg
        )
    }

    companion object {
        private const val TAG = "TextExtractionEngine"
    }
}
