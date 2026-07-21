package com.example.history

import com.example.model.ReplyHistory
import com.example.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow

class HistoryManager(private val historyRepository: HistoryRepository) {

    fun getHistoryFlow(): Flow<List<ReplyHistory>> {
        return historyRepository.getAllHistory()
    }

    suspend fun logReply(
        ruleId: Long,
        ruleName: String,
        senderName: String,
        incomingMessage: String,
        repliedMessage: String,
        packageName: String,
        isSuccess: Boolean
    ): Long {
        val historyItem = ReplyHistory(
            ruleId = ruleId,
            ruleName = ruleName,
            senderName = senderName,
            incomingMessage = incomingMessage,
            repliedMessage = repliedMessage,
            packageName = packageName,
            isSuccessfullySent = isSuccess
        )
        return historyRepository.saveHistory(historyItem)
    }

    suspend fun clearAllLogs() {
        historyRepository.clearHistory()
    }
}
