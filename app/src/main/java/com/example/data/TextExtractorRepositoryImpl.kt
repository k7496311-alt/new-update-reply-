package com.example.data

import com.example.accessibility.imo.ExtractedTextModel
import com.example.accessibility.imo.MessageBubbleModel
import com.example.accessibility.imo.MessageBubbleType
import com.example.accessibility.imo.TextExtractionResult
import com.example.accessibility.imo.TextExtractionStatus
import com.example.repository.TextExtractorRepository

/**
 * Concrete implementation of TextExtractorRepository.
 * Filters classified chat bubbles, retains only readable text messages (English, Bangla, Unicode, Mixed, Emoji),
 * ignores non-text media/calls/stickers/system messages, and maintains strict chronological sequence.
 */
class TextExtractorRepositoryImpl : TextExtractorRepository {

    override suspend fun extractReadableText(bubbles: List<MessageBubbleModel>): TextExtractionResult {
        if (bubbles.isEmpty()) {
            return TextExtractionResult(
                status = TextExtractionStatus.EMPTY_RESULT,
                extractedMessages = emptyList(),
                finalTextCount = 0,
                skippedStickerCount = 0,
                skippedVoiceCount = 0,
                skippedCallCount = 0,
                skippedOtherCount = 0,
                details = "No message bubbles available for text extraction"
            )
        }

        val extractedList = mutableListOf<ExtractedTextModel>()
        var skippedSticker = 0
        var skippedVoice = 0
        var skippedCall = 0
        var skippedOther = 0

        bubbles.forEach { bubble ->
            when (bubble.type) {
                MessageBubbleType.STICKER -> {
                    skippedSticker++
                }
                MessageBubbleType.VOICE -> {
                    skippedVoice++
                }
                MessageBubbleType.MISSED_AUDIO_CALL, MessageBubbleType.MISSED_VIDEO_CALL -> {
                    skippedCall++
                }
                MessageBubbleType.IMAGE,
                MessageBubbleType.VIDEO,
                MessageBubbleType.AUDIO,
                MessageBubbleType.SYSTEM_MESSAGE,
                MessageBubbleType.DATE_SEPARATOR,
                MessageBubbleType.UNREAD_SEPARATOR -> {
                    skippedOther++
                }
                MessageBubbleType.INCOMING_MESSAGE,
                MessageBubbleType.OUTGOING_MESSAGE,
                MessageBubbleType.EMOJI,
                MessageBubbleType.UNKNOWN -> {
                    val rawText = bubble.text.ifBlank { bubble.contentDescription }
                    val cleanText = sanitizeText(rawText)

                    if (cleanText.isNotBlank() && !isDeletedMessage(cleanText, bubble.resourceId)) {
                        val scriptType = detectScriptType(cleanText)
                        val seqIndex = extractedList.size + 1

                        extractedList.add(
                            ExtractedTextModel(
                                sequenceIndex = seqIndex,
                                nodeIndex = bubble.nodeIndex,
                                text = cleanText,
                                isIncoming = bubble.isIncoming,
                                scriptType = scriptType,
                                rawBubble = bubble
                            )
                        )
                    } else {
                        skippedOther++
                    }
                }
            }
        }

        val finalCount = extractedList.size
        val status = if (finalCount > 0) TextExtractionStatus.SUCCESS else TextExtractionStatus.EMPTY_RESULT
        val detailMsg = if (finalCount > 0) {
            "Successfully extracted $finalCount readable text message(s)"
        } else {
            "No readable text messages found in chat bubbles"
        }

        return TextExtractionResult(
            status = status,
            extractedMessages = extractedList,
            finalTextCount = finalCount,
            skippedStickerCount = skippedSticker,
            skippedVoiceCount = skippedVoice,
            skippedCallCount = skippedCall,
            skippedOtherCount = skippedOther,
            details = detailMsg
        )
    }

    private fun sanitizeText(raw: String): String {
        return raw.trim()
            .replace("\uFFFD", "") // Remove replacement characters if any
            .replace(Regex("[\\s\\n\\r]+"), " ") // Normalize internal whitespace
    }

    private fun isDeletedMessage(text: String, resId: String): Boolean {
        val lowerText = text.lowercase()
        val lowerId = resId.lowercase()

        return lowerId.contains("deleted") ||
                lowerText.contains("this message was deleted") ||
                lowerText.contains("message deleted") ||
                lowerText.contains("deleted message") ||
                lowerText.contains("মেসেজটি মুছে ফেলা হয়েছে")
    }

    private fun detectScriptType(text: String): String {
        var hasBangla = false
        var hasLatin = false
        var hasEmoji = false

        text.codePoints().forEach { cp ->
            when {
                cp in 0x0980..0x09FF -> hasBangla = true
                (cp in 'a'.code..'z'.code) || (cp in 'A'.code..'Z'.code) -> hasLatin = true
                isEmojiCodePoint(cp) -> hasEmoji = true
            }
        }

        return when {
            hasBangla && hasLatin -> "Mixed (Bangla + English)"
            hasBangla -> if (hasEmoji) "Bangla + Emoji" else "Bangla"
            hasLatin -> if (hasEmoji) "English + Emoji" else "English"
            hasEmoji -> "Emoji Only / Unicode"
            else -> "Unicode / Mixed"
        }
    }

    private fun isEmojiCodePoint(cp: Int): Boolean {
        return (cp in 0x1F600..0x1F64F) || // Emoticons
                (cp in 0x1F300..0x1F5FF) || // Misc Symbols & Pictographs
                (cp in 0x1F680..0x1F6FF) || // Transport & Map
                (cp in 0x1F1E6..0x1F1FF) || // Flags
                (cp in 0x2600..0x26FF) ||   // Misc Symbols
                (cp in 0x2700..0x27BF)      // Dingbats
    }
}
