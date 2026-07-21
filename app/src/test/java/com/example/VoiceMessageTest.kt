package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.accessibility.AccessibilityManager
import com.example.accessibility.imo.*
import com.example.model.MessageType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceMessageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun testVoiceTranscriptResultDataClass() {
        val result = VoiceTranscriptResult(
            originalText = "Hello world",
            success = true,
            errorMessage = null,
            processingTime = 120L
        )

        assertEquals("Hello world", result.originalText)
        assertTrue(result.success)
        assertNull(result.errorMessage)
        assertEquals(120L, result.processingTime)
    }

    @Test
    fun testVoiceTranscriptManagerCache() {
        val manager = VoiceTranscriptManager()
        val key = "voice_test_1"

        assertFalse(manager.isTranscribed(key))
        assertNull(manager.getTranscript(key))

        manager.saveTranscript(key, "Welcome to imo auto reply")
        assertTrue(manager.isTranscribed(key))
        assertEquals("Welcome to imo auto reply", manager.getTranscript(key))

        // Privacy check: clearing cache must remove all data permanently
        manager.clearCache()
        assertFalse(manager.isTranscribed(key))
        assertNull(manager.getTranscript(key))
    }

    @Test
    fun testVoiceMessageRepositoryDirectProxy() {
        // Instantiate real components to assert behavior
        val mockAccessibilityManager = AccessibilityManager(context)
        val nodeScanner = IMONodeScanner()
        val transcriptManager = VoiceTranscriptManager()
        val handler = VoiceMessageHandler(context, mockAccessibilityManager, nodeScanner, transcriptManager)
        
        val repository = VoiceMessageRepository(handler, transcriptManager, null)

        val key = "sample_bubble_key"
        assertFalse(repository.isAlreadyTranscribed(key))
        
        repository.saveTranscript(key, "Testing transcript proxy")
        assertTrue(repository.isAlreadyTranscribed(key))
        assertEquals("Testing transcript proxy", repository.getCachedTranscript(key))

        repository.clearTranscripts()
        assertFalse(repository.isAlreadyTranscribed(key))
    }
}
