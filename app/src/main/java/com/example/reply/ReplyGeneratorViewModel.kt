package com.example.reply

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AutoReplyRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ReplyGeneratorViewModel(
    private val replyGeneratorRepository: ReplyGeneratorRepository
) : ViewModel() {

    private val replyGenerator = ReplyGenerator(replyGeneratorRepository)

    private val _generationResult = MutableStateFlow<FinalReply?>(null)
    val generationResult: StateFlow<FinalReply?> = _generationResult

    private val _defaultReplySetting = MutableStateFlow<String?>(null)
    val defaultReplySetting: StateFlow<String?> = _defaultReplySetting

    init {
        loadDefaultReply()
    }

    /**
     * Loads the current configured default reply setting.
     */
    fun loadDefaultReply() {
        viewModelScope.launch {
            _defaultReplySetting.value = replyGeneratorRepository.getDefaultReplySetting()
        }
    }

    /**
     * Updates/saves the default reply setting.
     */
    fun saveDefaultReply(reply: String) {
        viewModelScope.launch {
            replyGeneratorRepository.saveDefaultReplySetting(reply)
            _defaultReplySetting.value = reply
        }
    }

    /**
     * Triggers the reply generator for a specific rule, sender, and incoming message.
     */
    fun generate(rule: AutoReplyRule?, senderName: String, incomingMessage: String) {
        viewModelScope.launch {
            val result = replyGenerator.generateReply(rule, senderName, incomingMessage)
            _generationResult.value = result
        }
    }

    /**
     * Resets/clears the last generated result state.
     */
    fun clearResult() {
        _generationResult.value = null
    }
}
