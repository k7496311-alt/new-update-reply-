package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntity): Long

    @Query("SELECT * FROM application_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<LogEntity>>

    @Query("SELECT * FROM application_logs ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<LogEntity>

    @Query("DELETE FROM application_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    @Query("DELETE FROM application_logs WHERE timestamp < :timestamp")
    suspend fun deleteLogsBefore(timestamp: Long)

    @Query("DELETE FROM application_logs WHERE timestamp < :timestamp")
    suspend fun deleteLogsOlderThan(timestamp: Long): Int

    @Query("DELETE FROM application_logs")
    suspend fun clearAllLogs()
}
