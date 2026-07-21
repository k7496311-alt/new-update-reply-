package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.HistoryStatus
import com.example.repository.BlacklistRepository
import com.example.repository.ContactRepository
import com.example.repository.HistoryRepository
import com.example.repository.QueueRepository
import com.example.repository.RuleRepository
import com.example.settings.SettingsManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    private val ruleRepository: RuleRepository,
    private val historyRepository: HistoryRepository,
    private val queueRepository: QueueRepository,
    private val contactRepository: ContactRepository,
    private val blacklistRepository: BlacklistRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    // Global toggle status of the orchestrator service
    var isServiceEnabled by mutableStateOf(settingsManager.isServiceEnabled)
        private set

    fun updateServiceEnabled(enabled: Boolean) {
        settingsManager.isServiceEnabled = enabled
        isServiceEnabled = enabled
    }

    // Dynamic, reactive state flows connected directly to database streams
    val totalRulesCount: StateFlow<Int> = ruleRepository.getAllRules()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val totalSentRepliesCount: StateFlow<Int> = historyRepository.getAllHistory()
        .map { historyList -> historyList.count { it.status == HistoryStatus.SENT } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val totalFailedRepliesCount: StateFlow<Int> = historyRepository.getAllHistory()
        .map { historyList -> historyList.count { it.status == HistoryStatus.FAILED } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val queueItemsCount: StateFlow<Int> = queueRepository.getAllQueueItems()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val contactsCount: StateFlow<Int> = contactRepository.getAllContacts()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val blacklistCount: StateFlow<Int> = blacklistRepository.getAllBlacklistEntries()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
}
