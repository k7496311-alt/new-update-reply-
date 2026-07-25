package com.example.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM reply_history ORDER BY timestamp DESC")
    fun getAllHistoryFlow(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM reply_history ORDER BY timestamp DESC")
    suspend fun getAllHistoryList(): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity): Long

    @Delete
    suspend fun deleteHistory(history: HistoryEntity)

    @Query("DELETE FROM reply_history WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteHistoryOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM reply_history")
    suspend fun clearAllHistory()

    @Query("SELECT COUNT(*) FROM reply_history WHERE ruleId = :ruleId")
    suspend fun getReplyCountForRule(ruleId: Long): Int

    @Query("SELECT COUNT(*) FROM reply_history WHERE ruleId = :ruleId AND timestamp >= :sinceTimestamp")
    suspend fun getReplyCountForRuleSince(ruleId: Long, sinceTimestamp: Long): Int

    @Query("SELECT MAX(timestamp) FROM reply_history WHERE ruleId = :ruleId")
    suspend fun getLastReplyTimestampForRule(ruleId: Long): Long?
}
