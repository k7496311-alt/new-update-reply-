package com.example.accessibility.imo

/**
 * Represents the outcome of a voice-to-text transcription action.
 *
 * @property originalText The raw transcribed text, or null if unsuccessful.
 * @property success True if the transcription succeeded, false otherwise.
 * @property errorMessage Present if the transcription failed or timed out.
 * @property processingTime The time taken to process the transcription in milliseconds.
 */
data class VoiceTranscriptResult(
    val originalText: String?,
    val success: Boolean,
    val errorMessage: String?,
    val processingTime: Long
)
