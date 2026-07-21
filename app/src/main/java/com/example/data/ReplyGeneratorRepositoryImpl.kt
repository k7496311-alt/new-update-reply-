package com.example.data

import com.example.model.AppSetting
import com.example.repository.HistoryRepository
import com.example.repository.SettingsRepository
import com.example.reply.ReplyGeneratorRepository

class ReplyGeneratorRepositoryImpl(
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository
) : ReplyGeneratorRepository {

    override suspend fun getLastReplyTimestamp(ruleId: Long): Long? {
        return historyRepository.getLastReplyTimestampForRule(ruleId)
    }

    override suspend fun getGlobalReplyCount(ruleId: Long): Int {
        return historyRepository.getReplyCountForRule(ruleId)
    }

    override suspend fun getReplyCountSince(ruleId: Long, sinceTimestamp: Long): Int {
        return historyRepository.getReplyCountForRuleSince(ruleId, sinceTimestamp)
    }

    override suspend fun getSequentialIndex(ruleId: Long): Int {
        val key = "reply_seq_index_$ruleId"
        val setting = settingsRepository.getSettingByKey(key)
        return setting?.value?.toIntOrNull() ?: 0
    }

    override suspend fun saveSequentialIndex(ruleId: Long, index: Int) {
        val key = "reply_seq_index_$ruleId"
        val setting = AppSetting(key = key, value = index.toString())
        settingsRepository.saveSetting(setting)
    }

    override suspend fun getDefaultReplySetting(): String? {
        val setting = settingsRepository.getSettingByKey("default_reply")
        return setting?.value?.trim()?.ifEmpty { null }
    }

    override suspend fun saveDefaultReplySetting(reply: String) {
        val key = "default_reply"
        val setting = AppSetting(key = key, value = reply)
        settingsRepository.saveSetting(setting)
    }
}
