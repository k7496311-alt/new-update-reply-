package com.example.accessibility.imo

/**
 * Extraction result containing sequence-ordered text messages and skip count metrics.
 */
data class TextExtractionResult(
    val status: TextExtractionStatus,
    val extractedMessages: List<ExtractedTextModel>,
    val finalTextCount: Int,
    val skippedStickerCount: Int,
    val skippedVoiceCount: Int,
    val skippedCallCount: Int,
    val skippedOtherCount: Int,
    val details: String = ""
)
