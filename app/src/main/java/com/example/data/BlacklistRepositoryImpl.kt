package com.example.data

import com.example.database.BlacklistDao
import com.example.database.BlacklistEntity
import com.example.model.BlacklistEntry
import com.example.model.BlacklistStatus
import com.example.repository.BlacklistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BlacklistRepositoryImpl(
    private val blacklistDao: BlacklistDao
) : BlacklistRepository {

    override fun getAllBlacklistEntries(): Flow<List<BlacklistEntry>> {
        return blacklistDao.getAllBlacklistFlow().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getBlacklistEntriesByStatus(status: BlacklistStatus): List<BlacklistEntry> {
        return blacklistDao.getBlacklistByStatus(status.name).map { it.toDomainModel() }
    }

    override suspend fun getBlacklistById(id: Long): BlacklistEntry? {
        return blacklistDao.getBlacklistById(id)?.toDomainModel()
    }

    override suspend fun isBlacklisted(identifier: String): Boolean {
        return blacklistDao.isBlacklisted(identifier)
    }

    override suspend fun saveBlacklistEntry(entry: BlacklistEntry): Long {
        val entity = BlacklistEntity.fromDomainModel(entry)
        return if (entity.id == 0L) {
            blacklistDao.insertBlacklistEntry(entity)
        } else {
            blacklistDao.updateBlacklistEntry(entity)
            entity.id
        }
    }

    override suspend fun deleteBlacklistEntry(entry: BlacklistEntry) {
        blacklistDao.deleteBlacklistEntry(BlacklistEntity.fromDomainModel(entry))
    }

    override suspend fun deleteBlacklistByIdentifier(identifier: String) {
        blacklistDao.deleteBlacklistByIdentifier(identifier)
    }
}
