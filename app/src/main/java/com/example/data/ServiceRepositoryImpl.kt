package com.example.data

import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.repository.ServiceRepository
import com.example.service.AutoReplyService
import kotlinx.coroutines.flow.Flow

class ServiceRepositoryImpl(private val context: Context) : ServiceRepository {

    override val isServiceRunning: Flow<Boolean> = AutoReplyService.isRunning

    override fun startService() {
        val intent = Intent(context, AutoReplyService::class.java).apply {
            action = AutoReplyService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    override fun stopService() {
        val intent = Intent(context, AutoReplyService::class.java).apply {
            action = AutoReplyService.ACTION_STOP
        }
        context.startService(intent)
    }

    override fun restartService() {
        val intent = Intent(context, AutoReplyService::class.java).apply {
            action = AutoReplyService.ACTION_RESTART
        }
        context.startService(intent)
    }
}
