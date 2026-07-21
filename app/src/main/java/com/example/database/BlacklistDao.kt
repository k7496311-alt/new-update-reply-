package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BlacklistDao {
    @Query("SELECT * FROM blacklist ORDER BY identifier ASC")
    fun getAllBlacklistFlow(): Flow<List<BlacklistEntity>>

    @Query("SELECT * FROM blacklist WHERE status = :statusName ORDER BY identifier ASC")
    suspend fun getBlacklistByStatus(statusName: String): List<BlacklistEntity>

    @Query("SELECT * FROM blacklist WHERE id = :id LIMIT 1")
    suspend fun getBlacklistById(id: Long): BlacklistEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM blacklist WHERE identifier = :identifier AND status = 'ACTIVE' LIMIT 1)")
    suspend fun isBlacklisted(identifier: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlacklistEntry(entry: BlacklistEntity): Long

    @Update
    suspend fun updateBlacklistEntry(entry: BlacklistEntity)

    @Delete
    suspend fun deleteBlacklistEntry(entry: BlacklistEntity)

    @Query("DELETE FROM blacklist WHERE identifier = :identifier")
    suspend fun deleteBlacklistByIdentifier(identifier: String)
}
