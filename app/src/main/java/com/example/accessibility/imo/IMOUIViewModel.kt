package com.example.accessibility.imo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessibility.AccessibilityLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the IMO UI Scanner and Automation screen/module.
 * Exposes observable states representing current app-focus, active screen status,
 * scanned chat lists, conversation trees, and ongoing automation sequence progress.
 */
class IMOUIViewModel(
    private val repository: IMOUIRepository
) : ViewModel() {

    companion object {
        private const val TAG = "IMOUIViewModel"
    }

    private val _isImoInFocus = MutableStateFlow(false)
    val isImoInFocus: StateFlow<Boolean> = _isImoInFocus.asStateFlow()

    private val _isOnChatListScreen = MutableStateFlow(false)
    val isOnChatListScreen: StateFlow<Boolean> = _isOnChatListScreen.asStateFlow()

    private val _isOnChatScreen = MutableStateFlow(false)
    val isOnChatScreen: StateFlow<Boolean> = _isOnChatScreen.asStateFlow()

    private val _chatList = MutableStateFlow<List<ImoChatListItem>>(emptyList())
    val chatList: StateFlow<List<ImoChatListItem>> = _chatList.asStateFlow()

    private val _activeConversation = MutableStateFlow<ImoChatConversationScreenInfo?>(null)
    val activeConversation: StateFlow<ImoChatConversationScreenInfo?> = _activeConversation.asStateFlow()

    private val _automationStatus = MutableStateFlow("Idle")
    val automationStatus: StateFlow<String> = _automationStatus.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        refreshStates()
    }

    /**
     * Polls or updates the current focused app state and active screen markers.
     */
    fun refreshStates() {
        _isImoInFocus.value = repository.isImoInFocus()
        _isOnChatListScreen.value = repository.isOnChatListScreen()
        _isOnChatScreen.value = repository.isOnChatScreen()
        AccessibilityLogger.d(TAG, "Refreshed states: imoInFocus=${_isImoInFocus.value}, chatList=${_isOnChatListScreen.value}, chatScreen=${_isOnChatScreen.value}")
    }

    /**
     * Scans the active screen for any IMO Chat lists.
     */
    fun scanChatList() {
        if (_isScanning.value) return
        _isScanning.value = true
        _automationStatus.value = "Scanning chat list..."
        
        viewModelScope.launch {
            try {
                repository.getChatList().collect { items ->
                    _chatList.value = items
                    _automationStatus.value = "Chat list scanned. Found ${items.size} chats."
                }
            } catch (e: Exception) {
                AccessibilityLogger.e(TAG, "Error scanning chat list", e)
                _automationStatus.value = "Failed to scan chat list: ${e.localizedMessage}"
            } finally {
                _isScanning.value = false
                refreshStates()
            }
        }
    }

    /**
     * Scans layout details of the active conversation, extracting texts and bubble structures.
     */
    fun scanActiveConversation() {
        if (_isScanning.value) return
        _isScanning.value = true
        _automationStatus.value = "Scanning conversation..."

        try {
            val info = repository.getActiveConversationInfo()
            _activeConversation.value = info
            if (info != null) {
                _automationStatus.value = "Conversation scanned for '${info.contactName}' successfully."
            } else {
                _automationStatus.value = "Active conversation not found or not currently on a chat screen."
            }
        } catch (e: Exception) {
            AccessibilityLogger.e(TAG, "Error scanning active conversation", e)
            _automationStatus.value = "Failed to scan active conversation: ${e.localizedMessage}"
        } finally {
            _isScanning.value = false
            refreshStates()
        }
    }

    /**
     * Triggers the full automation pipeline to open a contact, type a message, send it, and return.
     */
    fun sendAutomatedReply(contactName: String, text: String) {
        _automationStatus.value = "Starting automation: replying to '$contactName'..."
        viewModelScope.launch {
            try {
                val success = repository.sendAutomaticReply(contactName, text)
                if (success) {
                    _automationStatus.value = "Reply automation completed successfully."
                } else {
                    _automationStatus.value = "Reply automation failed."
                }
            } catch (e: Exception) {
                _automationStatus.value = "Automation error: ${e.localizedMessage}"
                AccessibilityLogger.e(TAG, "Error during automated reply task", e)
            } finally {
                refreshStates()
            }
        }
    }

    /**
     * Starts the voice message transcription pipeline.
     */
    fun transcribeVoiceMessage(contactName: String) {
        _automationStatus.value = "Starting voice-to-text transcript flow..."
        viewModelScope.launch {
            try {
                val result = repository.transcribeVoiceMessage(contactName)
                if (result != null) {
                    _automationStatus.value = "Transcription success: '$result'"
                } else {
                    _automationStatus.value = "Transcription flow failed or no transcript found."
                }
            } catch (e: Exception) {
                _automationStatus.value = "Transcription error: ${e.localizedMessage}"
                AccessibilityLogger.e(TAG, "Error during voice transcription flow", e)
            } finally {
                refreshStates()
            }
        }
    }

    /**
     * Aborts any ongoing background automation runs.
     */
    fun cancelActiveAutomations() {
        repository.cancelActiveAutomations()
        _automationStatus.value = "Automation cancelled by user."
        _isScanning.value = false
        refreshStates()
    }
}
