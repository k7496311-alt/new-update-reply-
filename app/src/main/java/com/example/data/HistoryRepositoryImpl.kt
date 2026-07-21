package com.example.data

import com.example.database.HistoryDao
import com.example.database.HistoryEntity
import com.example.model.ReplyHistory
import com.example.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HistoryRepositoryImpl(
    private val historyDao: HistoryDao
) : HistoryRepository {

    override fun getAllHistory(): Flow<List<ReplyHistory>> {
        return historyDao.getAllHistoryFlow().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun saveHistory(history: ReplyHistory): Long {
        return historyDao.insertHistory(HistoryEntity.fromDomainModel(history))
    }

    override suspend fun deleteHistory(history: ReplyHistory) {
        historyDao.deleteHistory(HistoryEntity.fromDomainModel(history))
    }

    override suspend fun clearHistory() {
        historyDao.clearAllHistory()
    }

    override suspend fun getReplyCountForRule(ruleId: Long): Int {
        return historyDao.getReplyCountForRule(ruleId)
    }

    override suspend fun getReplyCountForRuleSince(ruleId: Long, sinceTimestamp: Long): Int {
        return historyDao.getReplyCountForRuleSince(ruleId, sinceTimestamp)
    }

    override suspend fun getLastReplyTimestampForRule(ruleId: Long): Long? {
        return historyDao.getLastReplyTimestampForRule(ruleId)
    }
}
