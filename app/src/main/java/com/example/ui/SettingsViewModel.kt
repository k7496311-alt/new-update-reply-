package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AppSetting
import com.example.repository.SettingsRepository
import com.example.settings.SettingsManager
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsManager: SettingsManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // Load initial values from settingsManager (which is SharedPreferences backed)
    var isServiceEnabled by mutableStateOf(settingsManager.isServiceEnabled)
        private set

    var shouldReplyToGroups by mutableStateOf(settingsManager.shouldReplyToGroups)
        private set

    var isQuietModeEnabled by mutableStateOf(settingsManager.isQuietModeEnabled)
        private set

    var isDarkModeEnabled by mutableStateOf(settingsManager.isDarkModeEnabled)
        private set

    var replyDelaySecs by mutableStateOf(settingsManager.replyDelaySecs)
        private set

    var retryCount by mutableStateOf(settingsManager.retryCount)
        private set

    var queueSize by mutableStateOf(settingsManager.queueSize)
        private set

    var maxDailyReply by mutableStateOf(settingsManager.maxDailyReply)
        private set

    var defaultReplyText by mutableStateOf(settingsManager.defaultReplyText)
        private set

    var languageCode by mutableStateOf(settingsManager.languageCode)
        private set

    init {
        // Sync Room database values with settingsManager upon startup
        syncWithRoom()
    }

    private fun syncWithRoom() {
        viewModelScope.launch {
            // Read or write each setting to demonstrate Room Connected
            syncSetting("key_service_enabled", isServiceEnabled.toString()) { isServiceEnabled = it.toBoolean() }
            syncSetting("key_reply_to_groups", shouldReplyToGroups.toString()) { shouldReplyToGroups = it.toBoolean() }
            syncSetting("key_quiet_mode_enabled", isQuietModeEnabled.toString()) { isQuietModeEnabled = it.toBoolean() }
            syncSetting("key_dark_mode", isDarkModeEnabled.toString()) { isDarkModeEnabled = it.toBoolean() }
            syncSetting("key_reply_delay", replyDelaySecs.toString()) { replyDelaySecs = it.toIntOrNull() ?: 2 }
            syncSetting("key_retry_count", retryCount.toString()) { retryCount = it.toIntOrNull() ?: 3 }
            syncSetting("key_queue_size", queueSize.toString()) { queueSize = it.toIntOrNull() ?: 50 }
            syncSetting("key_max_daily_reply", maxDailyReply.toString()) { maxDailyReply = it.toIntOrNull() ?: 100 }
            syncSetting("key_default_reply", defaultReplyText) { defaultReplyText = it }
            syncSetting("key_language", languageCode) { languageCode = it }
        }
    }

    private suspend fun syncSetting(key: String, defaultValue: String, onLoaded: (String) -> Unit) {
        val dbSetting = settingsRepository.getSettingByKey(key)
        if (dbSetting != null) {
            onLoaded(dbSetting.value)
        } else {
            settingsRepository.saveSetting(AppSetting(key = key, value = defaultValue))
        }
    }

    private fun saveSettingToRoom(key: String, value: String) {
        viewModelScope.launch {
            settingsRepository.saveSetting(AppSetting(key = key, value = value))
        }
    }

    fun updateServiceEnabled(enabled: Boolean) {
        settingsManager.isServiceEnabled = enabled
        isServiceEnabled = enabled
        saveSettingToRoom("key_service_enabled", enabled.toString())
    }

    fun updateReplyToGroups(enabled: Boolean) {
        settingsManager.shouldReplyToGroups = enabled
        shouldReplyToGroups = enabled
        saveSettingToRoom("key_reply_to_groups", enabled.toString())
    }

    fun updateQuietModeEnabled(enabled: Boolean) {
        settingsManager.isQuietModeEnabled = enabled
        isQuietModeEnabled = enabled
        saveSettingToRoom("key_quiet_mode_enabled", enabled.toString())
    }

    fun updateDarkModeEnabled(enabled: Boolean) {
        settingsManager.isDarkModeEnabled = enabled
        isDarkModeEnabled = enabled
        saveSettingToRoom("key_dark_mode", enabled.toString())
    }

    fun updateReplyDelaySecs(delay: Int) {
        settingsManager.replyDelaySecs = delay
        replyDelaySecs = delay
        saveSettingToRoom("key_reply_delay", delay.toString())
    }

    fun updateRetryCount(count: Int) {
        settingsManager.retryCount = count
        retryCount = count
        saveSettingToRoom("key_retry_count", count.toString())
    }

    fun updateQueueSize(size: Int) {
        settingsManager.queueSize = size
        queueSize = size
        saveSettingToRoom("key_queue_size", size.toString())
    }

    fun updateMaxDailyReply(max: Int) {
        settingsManager.maxDailyReply = max
        maxDailyReply = max
        saveSettingToRoom("key_max_daily_reply", max.toString())
    }

    fun updateDefaultReplyText(reply: String) {
        settingsManager.defaultReplyText = reply
        defaultReplyText = reply
        saveSettingToRoom("key_default_reply", reply)
    }

    fun updateLanguageCode(code: String) {
        settingsManager.languageCode = code
        languageCode = code
        saveSettingToRoom("key_language", code)
    }
}
