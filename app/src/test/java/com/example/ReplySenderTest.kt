package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.accessibility.AccessibilityManager
import com.example.accessibility.imo.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReplySenderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun testSendResultTypes() {
        val success = SendResult.Success(123456789L)
        assertEquals(123456789L, success.timestamp)

        val failed = SendResult.Failed("Network error")
        assertEquals("Network error", failed.reason)

        val timeout = SendResult.Timeout("Too slow")
        assertEquals("Too slow", timeout.message)

        val cancelled = SendResult.Cancelled("User touched")
        assertEquals("User touched", cancelled.message)
    }

    @Test
    fun testReplySenderInputValidation() = runBlocking {
        val accessibilityManager = AccessibilityManager(context)
        val nodeScanner = IMONodeScanner()
        val actionPerformer = IMOActionPerformer(context, accessibilityManager, nodeScanner)
        val sender = ReplySender(context, accessibilityManager, nodeScanner, actionPerformer)

        // Empty message check
        val emptyResult = sender.sendReply("John Doe", "")
        assertTrue(emptyResult is SendResult.Failed)
        assertEquals("Cannot send empty message", (emptyResult as SendResult.Failed).reason)

        // Too long message check (>2000 chars)
        val longMessage = "A".repeat(2001)
        val longResult = sender.sendReply("John Doe", longMessage)
        assertTrue(longResult is SendResult.Failed)
        assertEquals("Message exceeds maximum length of 2000 characters", (longResult as SendResult.Failed).reason)
    }

    @Test
    fun testMessageSenderRepositoryAndUseCaseIntegration() = runBlocking {
        val accessibilityManager = AccessibilityManager(context)
        val nodeScanner = IMONodeScanner()
        val actionPerformer = IMOActionPerformer(context, accessibilityManager, nodeScanner)
        val sender = ReplySender(context, accessibilityManager, nodeScanner, actionPerformer)
        val repository = MessageSenderRepository(sender)
        val useCase = SendMessageUseCase(repository)

        // Verify we can set user interference which propagates down
        useCase.setUserInterfering(true)

        // Because user is interfering, sending a message must return Cancelled
        val result = useCase("Jane Doe", "Hello Jane")
        assertTrue(result is SendResult.Cancelled)
        assertTrue((result as SendResult.Cancelled).message.contains("User interaction in progress"))
    }
}
