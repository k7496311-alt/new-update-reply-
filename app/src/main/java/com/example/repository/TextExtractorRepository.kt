package com.example.repository

import com.example.accessibility.imo.MessageBubbleModel
import com.example.accessibility.imo.TextExtractionResult

/**
 * Clean Architecture repository interface for Chat Text Extraction.
 */
interface TextExtractorRepository {
    /**
     * Extracts readable text (English, Bangla, Unicode, Mixed, Emojis) from classified bubbles while filtering out non-text items
     * (stickers, voice, calls, media, system messages, dates) and preserving chronological message sequence.
     */
    suspend fun extractReadableText(bubbles: List<MessageBubbleModel>): TextExtractionResult
}
