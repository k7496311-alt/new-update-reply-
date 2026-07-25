package com.example.accessibility.imo

import com.example.accessibility.AccessibilityManager
import com.example.model.MessageType
import com.example.model.NotificationData

/**
 * Unified coordinator that manages the high-level IMO UI layout states, scans, and user actions.
 * Simplifies complex sequences like finding a chat and typing a response.
 */
class IMOUIManager(
    private val accessibilityManager: AccessibilityManager,
    private val nodeScanner: IMONodeScanner,
    private val actionPerformer: IMOActionPerformer
) {

    /**
     * Checks if the active application in focus is IMO or IMO Lite.
     */
    fun isImoInFocus(): Boolean {
        val root = accessibilityManager.getRootNode()
        val pkg = root?.packageName?.toString() ?: ""
        root?.recycle()
        return pkg == IMONodeScanner.PACKAGE_IMO || pkg == IMONodeScanner.PACKAGE_IMO_LITE
    }

    /**
     * Checks if the current window is on the Chat List Screen.
     */
    fun isOnChatListScreen(): Boolean = actionPerformer.isOnChatListScreen()

    /**
     * Checks if the current window is on the Chat Conversation Screen.
     */
    fun isOnChatScreen(): Boolean = actionPerformer.isOnChatScreen()

    /**
     * Scans and returns all contacts, previews, timestamps, and indicators on the Chat List Screen.
     */
    fun scanChatList(): List<ImoChatListItem> {
        val root = accessibilityManager.getRootNode()
        val list = nodeScanner.scanChatListScreen(root)
        root?.recycle()
        return list
    }

    /**
     * Scans the active conversation screen layout for text inputs, buttons, bubbles, and content type.
     */
    fun scanActiveConversation(): ImoChatConversationScreenInfo? {
        val root = accessibilityManager.getRootNode()
        val info = nodeScanner.scanChatConversationScreen(root)
        root?.recycle()
        return info
    }

    /**
     * Action: Navigates to a chat by contact name, typing and sending a message.
     */
    suspend fun sendAutomaticReply(contactName: String, replyText: String): Boolean {
        // Step 1: Open chat
        val opened = actionPerformer.openChatByContactName(contactName)
        if (!opened) return false

        // Step 2: Type message
        val typed = actionPerformer.typeMessage(replyText)
        if (!typed) return false

        // Step 3: Click send
        val sent = actionPerformer.clickSendButton()
        if (!sent) return false

        // Step 4: Clean up back navigation
        actionPerformer.clickBackButton()
        return true
    }

    /**
     * Action: Performs a voice-to-text transcript retrieval.
     */
    suspend fun transcribeLastVoiceMessage(): String? {
        if (!isOnChatScreen()) return null

        val lastType = actionPerformer.detectLastMessageType()
        if (lastType != MessageType.VOICE_MESSAGE) return null

        val clicked = actionPerformer.clickVoiceToTextButton()
        if (!clicked) return null

        return actionPerformer.waitForTranscript()
    }

    /**
     * Proxy action performer accesses.
     */
    fun getActionPerformer(): IMOActionPerformer = actionPerformer

    /**
     * Proxy scanner accesses.
     */
    fun getNodeScanner(): IMONodeScanner = nodeScanner

    /**
     * Proxy accessibility manager accesses.
     */
    fun getAccessibilityManager(): AccessibilityManager = accessibilityManager
}
