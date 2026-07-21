package com.example.accessibility.imo

import com.example.accessibility.AccessibilityLogger
import com.example.model.QueueItem
import com.example.queue.QueueEngine

/**
 * Repository layer that coordinates with VoiceMessageHandler and VoiceTranscriptManager.
 * It serves as a unified boundary for performing voice-to-text conversion and integrates
 * the results back to the Reply Engine and the Queue System.
 */
class VoiceMessageRepository(
    private val voiceMessageHandler: VoiceMessageHandler,
    private val transcriptManager: VoiceTranscriptManager,
    private val queueEngine: QueueEngine? = null // Extensible connection to Queue System
) {

    companion object {
        private const val TAG = "VoiceMessageRepository"
    }

    /**
     * Attempts to transcribe the last visible voice message on the screen.
     */
    suspend fun transcribeLastVoiceMessage(): VoiceTranscriptResult {
        AccessibilityLogger.d(TAG, "Request to transcribe last voice message received.")
        return voiceMessageHandler.transcribeLastVoiceMessage()
    }

    /**
     * Cache lookups: check if a message has already been transcribed.
     */
    fun isAlreadyTranscribed(messageKey: String): Boolean {
        return transcriptManager.isTranscribed(messageKey)
    }

    /**
     * Save/cache transcript manually if needed.
     */
    fun saveTranscript(messageKey: String, transcript: String) {
        transcriptManager.saveTranscript(messageKey, transcript)
    }

    /**
     * Retrieve cached transcription.
     */
    fun getCachedTranscript(messageKey: String): String? {
        return transcriptManager.getTranscript(messageKey)
    }

    /**
     * Clears temporary transcript caches to ensure user privacy.
     */
    fun clearTranscripts() {
        transcriptManager.clearCache()
    }

    /**
     * Integration Hook: Connects to the Queue System.
     * Enqueues a reply action specifically triggered or informed by a voice message transcription.
     */
    suspend fun enqueueReplyFromVoiceMessage(
        ruleId: Long,
        senderName: String,
        voiceMessageKey: String,
        replyText: String,
        packageName: String
    ): Result<Long> {
        val transcript = getCachedTranscript(voiceMessageKey) ?: "[Voice Message - Untranscribed]"
        AccessibilityLogger.i(TAG, "Queue Integration: Enqueuing action derived from voice message transcription.")
        
        return if (queueEngine != null) {
            queueEngine.enqueue(
                ruleId = ruleId,
                senderName = senderName,
                incomingMessage = "🎙️ [Voice: $transcript]",
                replyText = replyText,
                packageName = packageName
            )
        } else {
            val error = "QueueEngine is not registered in VoiceMessageRepository"
            AccessibilityLogger.e(TAG, error)
            Result.failure(IllegalStateException(error))
        }
    }
}
