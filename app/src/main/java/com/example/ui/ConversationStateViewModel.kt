package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.Conversation
import com.example.repository.ConversationRepository
import com.example.reply.ConversationStateManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConversationStateViewModel(
    private val conversationRepository: ConversationRepository
) : ViewModel() {

    val manager = ConversationStateManager(conversationRepository)

    val conversations: StateFlow<List<Conversation>> = conversationRepository.getAllConversationsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun lockConversation(senderName: String, packageName: String) {
        viewModelScope.launch {
            manager.lock(senderName, packageName)
        }
    }

    fun unlockConversation(senderName: String, packageName: String) {
        viewModelScope.launch {
            manager.unlock(senderName, packageName)
        }
    }

    fun timeoutConversation(senderName: String, packageName: String) {
        viewModelScope.launch {
            manager.timeout(senderName, packageName)
        }
    }

    fun resumeConversation(senderName: String, packageName: String) {
        viewModelScope.launch {
            manager.resume(senderName, packageName)
        }
    }

    fun clearUnreadCount(senderName: String, packageName: String) {
        viewModelScope.launch {
            manager.clearUnread(senderName, packageName)
        }
    }

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(conversation)
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            conversationRepository.clearAllConversations()
        }
    }

    fun runTimeoutSweep(timeoutMillis: Long = 300_000L) {
        viewModelScope.launch {
            manager.checkInactivityTimeouts(timeoutMillis)
        }
    }
}
