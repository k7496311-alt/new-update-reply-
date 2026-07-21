package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.BlacklistEntry
import com.example.model.BlacklistStatus

@Entity(tableName = "blacklist")
data class BlacklistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val identifier: String,
    val reason: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: BlacklistStatus
) {
    fun toDomainModel(): BlacklistEntry {
        return BlacklistEntry(
            id = id,
            identifier = identifier,
            reason = reason,
            createdAt = createdAt,
            updatedAt = updatedAt,
            status = status
        )
    }

    companion object {
        fun fromDomainModel(entry: BlacklistEntry): BlacklistEntity {
            return BlacklistEntity(
                id = entry.id,
                identifier = entry.identifier,
                reason = entry.reason,
                createdAt = entry.createdAt,
                updatedAt = entry.updatedAt,
                status = entry.status
            )
        }
    }
}
