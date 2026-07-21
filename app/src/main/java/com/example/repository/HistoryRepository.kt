package com.example.repository

import com.example.model.ReplyHistory
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getAllHistory(): Flow<List<ReplyHistory>>
    suspend fun saveHistory(history: ReplyHistory): Long
    suspend fun deleteHistory(history: ReplyHistory)
    suspend fun clearHistory()
    suspend fun getReplyCountForRule(ruleId: Long): Int
    suspend fun getReplyCountForRuleSince(ruleId: Long, sinceTimestamp: Long): Int
    suspend fun getLastReplyTimestampForRule(ruleId: Long): Long?
}
