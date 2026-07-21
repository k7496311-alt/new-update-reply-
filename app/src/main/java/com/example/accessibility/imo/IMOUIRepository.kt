package com.example.accessibility.imo

import com.example.model.MessageType
import com.example.model.NotificationData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Repository layer that provides clean access to the IMO UI Scanner and Automation components.
 * It coordinates with the controller and managers to fetch structural screen data or execute flows.
 */
class IMOUIRepository(
    private val controller: IMOAccessibilityController
) {

    /**
     * Checks if the active in-focus app is IMO or IMO Lite.
     */
    fun isImoInFocus(): Boolean {
        return controller.uiManager.isImoInFocus()
    }

    /**
     * Checks if we are currently looking at the Chat List Screen.
     */
    fun isOnChatListScreen(): Boolean {
        return controller.uiManager.isOnChatListScreen()
    }

    /**
     * Checks if we are currently looking at a Chat Conversation Screen.
     */
    fun isOnChatScreen(): Boolean {
        return controller.uiManager.isOnChatScreen()
    }

    /**
     * Scans and streams list of active chat items on the list screen.
     */
    fun getChatList(): Flow<List<ImoChatListItem>> = flow {
        val list = controller.uiManager.scanChatList()
        emit(list)
    }

    /**
     * Scans and returns active conversation screen info.
     */
    fun getActiveConversationInfo(): ImoChatConversationScreenInfo? {
        return controller.uiManager.scanActiveConversation()
    }

    /**
     * Executes automatic reply sequence (open chat -> type text -> send -> navigate back).
     */
    suspend fun sendAutomaticReply(contactName: String, text: String): Boolean {
        return controller.uiManager.sendAutomaticReply(contactName, text)
    }

    /**
     * Executes a voice transcription flow.
     */
    suspend fun transcribeVoiceMessage(contactName: String): String? {
        var result: String? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        
        controller.startVoiceTranscriptionFlowAsync(contactName) { transcript ->
            result = transcript
            latch.countDown()
        }
        
        latch.await()
        return result
    }

    /**
     * Cancels any active, running automation tasks.
     */
    fun cancelActiveAutomations() {
        controller.cancelActiveAutomations()
    }
}
