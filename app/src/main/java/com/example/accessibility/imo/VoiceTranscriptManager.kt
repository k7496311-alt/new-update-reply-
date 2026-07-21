package com.example.accessibility.imo

import com.example.accessibility.AccessibilityLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the in-memory, privacy-preserving cache for voice message transcriptions.
 * Ensures transcripts are stored only temporarily and are never persisted to disk.
 */
class VoiceTranscriptManager {

    companion object {
        private const val TAG = "VoiceTranscriptManager"
    }

    // Thread-safe in-memory cache to temporarily map voice message identifiers to their transcribed text
    private val inMemoryTranscripts = ConcurrentHashMap<String, String>()

    // Track processed voice message hashes or bounds to avoid re-transcribing
    private val transcribedMessageKeys = ConcurrentHashMap.newKeySet<String>()

    /**
     * Stores a transcript in memory.
     * @param messageKey A unique key identifying the specific voice message (e.g. contactName + bounds/timestamp).
     * @param transcript The transcribed text content.
     */
    fun saveTranscript(messageKey: String, transcript: String) {
        AccessibilityLogger.d(TAG, "Saving voice transcript to memory cache for key: $messageKey")
        inMemoryTranscripts[messageKey] = transcript
        transcribedMessageKeys.add(messageKey)
    }

    /**
     * Retrieves a transcript from memory.
     * @param messageKey The unique key identifying the voice message.
     * @return The cached transcript text, or null if not found.
     */
    fun getTranscript(messageKey: String): String? {
        return inMemoryTranscripts[messageKey]
    }

    /**
     * Verifies if a voice message has already been transcribed.
     */
    fun isTranscribed(messageKey: String): Boolean {
        return transcribedMessageKeys.contains(messageKey) || inMemoryTranscripts.containsKey(messageKey)
    }

    /**
     * Clears all temporary transcripts from memory to respect user privacy.
     */
    fun clearCache() {
        AccessibilityLogger.i(TAG, "Clearing in-memory voice transcript cache (privacy preservation)")
        inMemoryTranscripts.clear()
        transcribedMessageKeys.clear()
    }
}
