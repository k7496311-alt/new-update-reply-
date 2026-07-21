package com.example.data

import com.example.database.LogDao
import com.example.database.LogEntity
import com.example.model.LogCategory
import com.example.model.LogLevel
import com.example.model.LogItem
import com.example.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LogRepositoryImpl(private val logDao: LogDao) : LogRepository {
    override fun getAllLogs(): Flow<List<LogItem>> {
        return logDao.getAllLogsFlow().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun insertLog(
        category: LogCategory,
        level: LogLevel,
        message: String,
        extraData: String?
    ): Long {
        val entity = LogEntity(
            timestamp = System.currentTimeMillis(),
            category = category.name,
            level = level.name,
            message = message,
            extraData = extraData
        )
        return logDao.insertLog(entity)
    }

    override suspend fun deleteLogById(id: Long) {
        logDao.deleteLogById(id)
    }

    override suspend fun deleteLogsBefore(timestamp: Long) {
        logDao.deleteLogsBefore(timestamp)
    }

    override suspend fun clearAllLogs() {
        logDao.clearAllLogs()
    }
}
