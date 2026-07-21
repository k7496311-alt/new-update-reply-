package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.LogCategory
import com.example.model.LogLevel
import com.example.model.LogItem

@Entity(tableName = "application_logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val category: String,
    val level: String,
    val message: String,
    val extraData: String? = null
) {
    fun toDomainModel(): LogItem {
        val cat = try {
            LogCategory.valueOf(category)
        } catch (e: Exception) {
            LogCategory.APPLICATION
        }
        val lvl = try {
            LogLevel.valueOf(level)
        } catch (e: Exception) {
            LogLevel.INFO
        }
        return LogItem(
            id = id,
            timestamp = timestamp,
            category = cat,
            level = lvl,
            message = message,
            extraData = extraData
        )
    }

    companion object {
        fun fromDomainModel(item: LogItem): LogEntity {
            return LogEntity(
                id = item.id,
                timestamp = item.timestamp,
                category = item.category.name,
                level = item.level.name,
                message = item.message,
                extraData = item.extraData
            )
        }
    }
}
