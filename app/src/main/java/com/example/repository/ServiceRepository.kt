package com.example.repository

import kotlinx.coroutines.flow.Flow

interface ServiceRepository {
    val isServiceRunning: Flow<Boolean>
    fun startService()
    fun stopService()
    fun restartService()
}
