package com.example.data

import com.example.database.SettingsDao
import com.example.database.SettingsEntity
import com.example.model.AppSetting
import com.example.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepositoryImpl(
    private val settingsDao: SettingsDao
) : SettingsRepository {

    override fun getAllSettings(): Flow<List<AppSetting>> {
        return settingsDao.getAllSettingsFlow().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getSettingByKey(key: String): AppSetting? {
        return settingsDao.getSettingByKey(key)?.toDomainModel()
    }

    override suspend fun saveSetting(setting: AppSetting) {
        val entity = SettingsEntity.fromDomainModel(setting)
        val existing = settingsDao.getSettingByKey(setting.key)
        if (existing == null) {
            settingsDao.insertSetting(entity)
        } else {
            settingsDao.updateSetting(entity)
        }
    }

    override suspend fun deleteSetting(setting: AppSetting) {
        settingsDao.deleteSetting(SettingsEntity.fromDomainModel(setting))
    }

    override suspend fun deleteSettingByKey(key: String) {
        settingsDao.deleteSettingByKey(key)
    }
}
