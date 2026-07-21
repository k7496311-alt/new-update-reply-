package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Service to manage the background orchestration of the auto-reply system.
 * Implemented as a robust, battery-optimized, and lifecycle-aware Foreground Service.
 */
class AutoReplyService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var taskJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate called")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "onStartCommand action: $action")

        when (action) {
            ACTION_START -> {
                startForegroundServiceInternal()
            }
            ACTION_STOP -> {
                stopServiceInternal()
            }
            ACTION_RESTART -> {
                restartServiceInternal()
            }
        }

        // Return START_STICKY to enable automatic recovery by the OS if killed
        return START_STICKY
    }

    private fun startForegroundServiceInternal() {
        if (_isRunning.value) {
            Log.d(TAG, "Service is already running.")
            return
        }

        Log.d(TAG, "Starting Foreground Service...")
        
        // Build and display the Material styled persistent notification
        val notification = buildPersistentNotification("Smart Auto Reply service is active and listening.")
        startForeground(NOTIFICATION_ID, notification)
        
        _isRunning.value = true
        
        // Start background tasks with coroutines
        startBackgroundTask()
    }

    private fun stopServiceInternal() {
        Log.d(TAG, "Stopping Foreground Service...")
        _isRunning.value = false
        taskJob?.cancel()
        stopForeground(true)
        stopSelf()
    }

    private fun restartServiceInternal() {
        Log.d(TAG, "Restarting Foreground Service...")
        
        // Temporary state transition
        _isRunning.value = false
        taskJob?.cancel()
        
        // Update notification description
        val restartingNotification = buildPersistentNotification("Service is restarting...")
        startForeground(NOTIFICATION_ID, restartingNotification)
        
        serviceScope.launch {
            delay(1000) // Small cooldown for safety
            _isRunning.value = true
            val activeNotification = buildPersistentNotification("Smart Auto Reply service is active and listening.")
            startForeground(NOTIFICATION_ID, activeNotification)
            startBackgroundTask()
        }
    }

    private fun startBackgroundTask() {
        taskJob?.cancel()
        taskJob = serviceScope.launch {
            while (_isRunning.value) {
                // Determine battery state to implement low-battery optimization
                val isBatteryLow = checkBatteryStatus()
                val isPowerSaveMode = checkPowerSaveMode()
                
                val intervalMs = if (isBatteryLow || isPowerSaveMode) {
                    Log.d(TAG, "Low battery or Power Save mode active. Throttling background interval to 30s.")
                    30_000L // 30 seconds interval when battery is low
                } else {
                    Log.d(TAG, "Battery level normal. Standard background interval (5s).")
                    5_000L  // 5 seconds interval under normal conditions
                }

                // Perform memory-safe background heartbeat check
                performBackgroundHeartbeat()

                delay(intervalMs)
            }
        }
    }

    private fun performBackgroundHeartbeat() {
        Log.d(TAG, "Background task running. Free memory: ${Runtime.getRuntime().freeMemory() / 1024} KB")
        // No heavy allocations to maintain memory safety
    }

    /**
     * Checks if the device battery is currently low (under 15%).
     */
    private fun checkBatteryStatus(): Boolean {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = registerReceiver(null, intentFilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
            Log.d(TAG, "Current Battery Level: $percent%")
            percent < 15
        } catch (e: Exception) {
            Log.e(TAG, "Error checking battery status", e)
            false
        }
    }

    /**
     * Checks if Power Save Mode is active on the device.
     */
    private fun checkPowerSaveMode(): Boolean {
        return try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isPowerSave = powerManager?.isPowerSaveMode ?: false
            Log.d(TAG, "Power Save Mode Active: $isPowerSave")
            isPowerSave
        } catch (e: Exception) {
            Log.e(TAG, "Error checking power save mode", e)
            false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Smart Auto Reply Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors background message processes and automated replies"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildPersistentNotification(contentText: String): Notification {
        // Intent to open the main dashboard when notification is clicked
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to STOP the service directly from the notification
        val stopIntent = Intent(this, AutoReplyService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to RESTART the service directly from the notification
        val restartIntent = Intent(this, AutoReplyService::class.java).apply {
            action = ACTION_RESTART
        }
        val restartPendingIntent = PendingIntent.getService(
            this,
            2,
            restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // Use custom notification icon
            .setContentTitle("Smart Auto Reply Active")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(mainPendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    stopPendingIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_popup_sync,
                    "Restart",
                    restartPendingIntent
                ).build()
            )
            .build()
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy called. Cancelling scopes to prevent memory leaks.")
        _isRunning.value = false
        serviceJob.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AutoReplyService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "auto_reply_foreground_service"

        const val ACTION_START = "com.example.service.action.START"
        const val ACTION_STOP = "com.example.service.action.STOP"
        const val ACTION_RESTART = "com.example.service.action.RESTART"

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()
    }
}
