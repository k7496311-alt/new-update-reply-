package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.AppSetting
import com.example.model.SettingsStatus

@Entity(tableName = "app_settings")
data class SettingsEntity(
    @PrimaryKey val key: String,
    val value: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: SettingsStatus
) {
    fun toDomainModel(): AppSetting {
        return AppSetting(
            key = key,
            value = value,
            createdAt = createdAt,
            updatedAt = updatedAt,
            status = status
        )
    }

    companion object {
        fun fromDomainModel(setting: AppSetting): SettingsEntity {
            return SettingsEntity(
                key = setting.key,
                value = setting.value,
                createdAt = setting.createdAt,
                updatedAt = setting.updatedAt,
                status = setting.status
            )
        }
    }
}
