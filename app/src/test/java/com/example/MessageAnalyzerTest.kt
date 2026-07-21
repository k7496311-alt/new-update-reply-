package com.example

import com.example.data.MessageAnalyzerRepositoryImpl
import com.example.model.MessageType
import org.junit.Assert.*
import org.junit.Test

class MessageAnalyzerTest {

    private val repository = MessageAnalyzerRepositoryImpl()

    @Test
    fun testEmptyMessage() {
        val result = repository.analyze("   ")
        assertEquals(MessageType.EMPTY, result.messageType)
        assertEquals("", result.normalizedText)
    }

    @Test
    fun testEmojiOnlyMessage() {
        val result1 = repository.analyze("👋😊")
        assertEquals(MessageType.EMOJI_ONLY, result1.messageType)

        val result2 = repository.analyze("  👍  🔥  ")
        assertEquals(MessageType.EMOJI_ONLY, result2.messageType)
        assertEquals("👍 🔥", result2.normalizedText)

        val mixed = repository.analyze("hello 👋")
        assertNotEquals(MessageType.EMOJI_ONLY, mixed.messageType)
    }

    @Test
    fun testEmailDetection() {
        val result = repository.analyze("Please contact support@example.com for help")
        assertEquals(MessageType.EMAIL, result.messageType)
        assertEquals("support@example.com", result.extractedValue)

        val direct = repository.analyze("john.doe+test@gmail.com")
        assertEquals(MessageType.EMAIL, direct.messageType)
        assertEquals("john.doe+test@gmail.com", direct.extractedValue)
    }

    @Test
    fun testPhoneNumberDetection() {
        val result = repository.analyze("My phone is +1-555-123-4567")
        assertEquals(MessageType.PHONE_NUMBER, result.messageType)
        assertEquals("+1-555-123-4567", result.extractedValue)

        val direct = repository.analyze("09123456789")
        assertEquals(MessageType.PHONE_NUMBER, direct.messageType)
        assertEquals("09123456789", direct.extractedValue)
    }

    @Test
    fun testLocationDetection() {
        val result1 = repository.analyze("Meet me here: https://maps.google.com/?q=37.7749,-122.4194")
        assertEquals(MessageType.LOCATION, result1.messageType)
        assertEquals("https://maps.google.com/?q=37.7749,-122.4194", result1.extractedValue)

        val result2 = repository.analyze("📍 Coordinates: 40.7128,-74.0060")
        assertEquals(MessageType.LOCATION, result2.messageType)
        assertEquals("40.7128,-74.0060", result2.extractedValue)

        val result3 = repository.analyze("Shared location with you")
        assertEquals(MessageType.LOCATION, result3.messageType)
    }

    @Test
    fun testLinkDetection() {
        val result = repository.analyze("Check out our website at https://github.com/google/ai-studio")
        assertEquals(MessageType.LINK, result.messageType)
        assertEquals("https://github.com/google/ai-studio", result.extractedValue)
    }

    @Test
    fun testUnsupportedMessage() {
        val result1 = repository.analyze("Unsupported message type received")
        assertEquals(MessageType.UNSUPPORTED, result1.messageType)
        assertFalse(repository.isSupported(result1.messageType))

        val result2 = repository.analyze("Decryption failed. Cannot view message.")
        assertEquals(MessageType.UNSUPPORTED, result2.messageType)
    }

    @Test
    fun testVoiceMessageDetection() {
        val result = repository.analyze("🎤 (0:45) Voice Note")
        assertEquals(MessageType.VOICE_MESSAGE, result.messageType)
    }

    @Test
    fun testImageDetection() {
        val result = repository.analyze("📷 Photo from Alice")
        assertEquals(MessageType.IMAGE, result.messageType)
    }

    @Test
    fun testGifDetection() {
        val result = repository.analyze("Sent a funny GIF")
        assertEquals(MessageType.GIF, result.messageType)
    }

    @Test
    fun testVideoDetection() {
        val result = repository.analyze("🎥 Family_Video.mp4")
        assertEquals(MessageType.VIDEO, result.messageType)
    }

    @Test
    fun testStickerDetection() {
        val result = repository.analyze("[Sticker]")
        assertEquals(MessageType.STICKER, result.messageType)
    }

    @Test
    fun testFileDetection() {
        val result = repository.analyze("📄 Monthly_Report.pdf")
        assertEquals(MessageType.FILE, result.messageType)
    }

    @Test
    fun testContactDetection() {
        val result = repository.analyze("👤 Contact card: John Smith")
        assertEquals(MessageType.CONTACT, result.messageType)
    }

    @Test
    fun testPlainTextFallback() {
        val result = repository.analyze("Hello there, how are you doing today?")
        assertEquals(MessageType.PLAIN_TEXT, result.messageType)
    }

    @Test
    fun testNormalization() {
        val original = "  HELLO   WORLD  "
        val result = repository.analyze(original)
        assertEquals("hello world", result.normalizedText)
    }
}
