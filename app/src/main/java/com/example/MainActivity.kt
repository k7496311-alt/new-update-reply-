package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.HistoryRepositoryImpl
import com.example.data.RuleRepositoryImpl
import com.example.data.QueueRepositoryImpl
import com.example.data.ContactRepositoryImpl
import com.example.data.BlacklistRepositoryImpl
import com.example.data.SettingsRepositoryImpl
import com.example.database.AppDatabase
import com.example.settings.SettingsManager
import com.example.ui.HomeViewModel
import com.example.ui.HistoryViewModel
import com.example.ui.MainScreen
import com.example.ui.RulesViewModel
import com.example.ui.SettingsViewModel
import com.example.ui.theme.SmartAutoReplyTheme
import com.example.permission.PermissionManager
import com.example.data.PermissionRepositoryImpl
import com.example.ui.PermissionViewModel
import com.example.data.ServiceRepositoryImpl
import com.example.ui.ServiceViewModel
import com.example.data.LogRepositoryImpl
import com.example.logger.AppLogger
import com.example.ui.LogViewModel
import com.example.model.LogCategory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize local persistent database and preferences
        val database = AppDatabase.getDatabase(applicationContext)
        val settingsManager = SettingsManager(applicationContext)

        // Initialize logging repository and global AppLogger helper
        val logRepository = LogRepositoryImpl(database.logDao())
        AppLogger.init(logRepository)

        // Record database opened successfully and measure performance
        AppLogger.measurePerformance(LogCategory.DATABASE, "Establish database connection") {
            database.openHelper.writableDatabase
        }
        AppLogger.success(LogCategory.APPLICATION, "Smart Auto Reply Pro application started successfully")

        // Set up crash handler to capture unhandled exceptions
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.recordCrash(throwable, "Uncaught Exception in thread ${thread.name}")
            try {
                Thread.sleep(150)
            } catch (e: Exception) {
                // Ignore interruption
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // 2. Initialize repository abstractions
        val ruleRepository = RuleRepositoryImpl(database.ruleDao())
        val historyRepository = HistoryRepositoryImpl(database.historyDao())
        val queueRepository = QueueRepositoryImpl(database.queueDao())
        val contactRepository = ContactRepositoryImpl(database.contactDao())
        val blacklistRepository = BlacklistRepositoryImpl(database.blacklistDao())
        val settingsRepository = SettingsRepositoryImpl(database.settingsDao())
        
        val permissionManager = PermissionManager()
        val permissionRepository = PermissionRepositoryImpl(permissionManager, applicationContext)
        val serviceRepository = ServiceRepositoryImpl(applicationContext)

        // 3. Create ViewModel factories for simple, direct constructor injection
        val homeViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    ruleRepository = ruleRepository,
                    historyRepository = historyRepository,
                    queueRepository = queueRepository,
                    contactRepository = contactRepository,
                    blacklistRepository = blacklistRepository,
                    settingsManager = settingsManager
                ) as T
            }
        })[HomeViewModel::class.java]

        val rulesViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RulesViewModel(ruleRepository) as T
            }
        })[RulesViewModel::class.java]

        val historyViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HistoryViewModel(historyRepository) as T
            }
        })[HistoryViewModel::class.java]

        val settingsViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(settingsManager, settingsRepository) as T
            }
        })[SettingsViewModel::class.java]

        val permissionViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PermissionViewModel(permissionRepository, permissionManager, applicationContext) as T
            }
        })[PermissionViewModel::class.java]

        val serviceViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ServiceViewModel(serviceRepository) as T
            }
        })[ServiceViewModel::class.java]

        val logViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LogViewModel(logRepository) as T
            }
        })[LogViewModel::class.java]

        // 4. Set Content View
        setContent {
            val isDark = settingsViewModel.isDarkModeEnabled
            SmartAutoReplyTheme(darkTheme = isDark) {
                MainScreen(
                    homeViewModel = homeViewModel,
                    rulesViewModel = rulesViewModel,
                    historyViewModel = historyViewModel,
                    settingsViewModel = settingsViewModel,
                    permissionViewModel = permissionViewModel,
                    serviceViewModel = serviceViewModel,
                    logViewModel = logViewModel
                )
            }
        }
    }
}
