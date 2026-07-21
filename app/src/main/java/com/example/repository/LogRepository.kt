package com.example.repository

import com.example.model.LogCategory
import com.example.model.LogLevel
import com.example.model.LogItem
import kotlinx.coroutines.flow.Flow

interface LogRepository {
    fun getAllLogs(): Flow<List<LogItem>>
    suspend fun insertLog(category: LogCategory, level: LogLevel, message: String, extraData: String? = null): Long
    suspend fun deleteLogById(id: Long)
    suspend fun deleteLogsBefore(timestamp: Long)
    suspend fun clearAllLogs()
}
