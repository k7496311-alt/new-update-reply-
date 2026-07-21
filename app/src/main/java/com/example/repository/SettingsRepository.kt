package com.example.repository

import com.example.model.AppSetting
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getAllSettings(): Flow<List<AppSetting>>
    suspend fun getSettingByKey(key: String): AppSetting?
    suspend fun saveSetting(setting: AppSetting)
    suspend fun deleteSetting(setting: AppSetting)
    suspend fun deleteSettingByKey(key: String)
}
