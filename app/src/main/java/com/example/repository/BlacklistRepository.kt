package com.example.repository

import com.example.model.BlacklistEntry
import com.example.model.BlacklistStatus
import kotlinx.coroutines.flow.Flow

interface BlacklistRepository {
    fun getAllBlacklistEntries(): Flow<List<BlacklistEntry>>
    suspend fun getBlacklistEntriesByStatus(status: BlacklistStatus): List<BlacklistEntry>
    suspend fun getBlacklistById(id: Long): BlacklistEntry?
    suspend fun isBlacklisted(identifier: String): Boolean
    suspend fun saveBlacklistEntry(entry: BlacklistEntry): Long
    suspend fun deleteBlacklistEntry(entry: BlacklistEntry)
    suspend fun deleteBlacklistByIdentifier(identifier: String)
}
