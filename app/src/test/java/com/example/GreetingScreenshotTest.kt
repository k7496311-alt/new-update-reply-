package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.MainScreen
import com.example.ui.RulesViewModel
import com.example.ui.HistoryViewModel
import com.example.ui.SettingsViewModel
import com.example.ui.HomeViewModel
import com.example.repository.RuleRepository
import com.example.repository.HistoryRepository
import com.example.repository.SettingsRepository
import com.example.repository.QueueRepository
import com.example.repository.ContactRepository
import com.example.repository.BlacklistRepository
import com.example.settings.SettingsManager
import com.example.ui.theme.SmartAutoReplyTheme
import com.example.model.AutoReplyRule
import com.example.model.ReplyHistory
import com.example.model.AppSetting
import com.example.model.QueueItem
import com.example.model.Contact
import com.example.model.BlacklistEntry
import com.example.model.QueueStatus
import com.example.model.ContactStatus
import com.example.model.BlacklistStatus
import android.content.Context
import com.example.repository.PermissionRepository
import com.example.permission.PermissionManager
import com.example.ui.PermissionViewModel
import com.example.ui.LogViewModel
import com.example.ui.ServiceViewModel
import com.example.model.PermissionItem
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun greeting_screenshot() {
        val mockRuleRepository = object : RuleRepository {
            override fun getAllRules() = flowOf(
                listOf(
                    AutoReplyRule(id = 1L, name = "Welcome Rule", keyword = "hello", replyText = "Hi there!"),
                    AutoReplyRule(id = 2L, name = "Busy Rule", keyword = "busy", replyText = "I'll get back to you soon.", isEnabled = false)
                )
            )
            override suspend fun getActiveRules() = emptyList<AutoReplyRule>()
            override suspend fun getRuleById(id: Long) = null
            override suspend fun saveRule(rule: AutoReplyRule) = 0L
            override suspend fun deleteRule(rule: AutoReplyRule) {}
        }

        val mockHistoryRepository = object : HistoryRepository {
            override fun getAllHistory() = flowOf(emptyList<ReplyHistory>())
            override suspend fun saveHistory(history: ReplyHistory) = 0L
            override suspend fun clearHistory() {}
            override suspend fun deleteHistory(history: ReplyHistory) {}
            override suspend fun getReplyCountForRule(ruleId: Long) = 0
            override suspend fun getReplyCountForRuleSince(ruleId: Long, sinceTimestamp: Long) = 0
            override suspend fun getLastReplyTimestampForRule(ruleId: Long): Long? = null
        }

        val mockSettingsRepository = object : SettingsRepository {
            override fun getAllSettings() = flowOf(emptyList<AppSetting>())
            override suspend fun getSettingByKey(key: String): AppSetting? = null
            override suspend fun saveSetting(setting: AppSetting) {}
            override suspend fun deleteSetting(setting: AppSetting) {}
            override suspend fun deleteSettingByKey(key: String) {}
        }

        val mockQueueRepository = object : QueueRepository {
            override fun getAllQueueItems() = flowOf(emptyList<QueueItem>())
            override suspend fun getQueueItemsByStatus(status: QueueStatus) = emptyList<QueueItem>()
            override suspend fun getQueueItemsByStatuses(statuses: List<QueueStatus>) = emptyList<QueueItem>()
            override suspend fun getActiveQueueCount() = 0
            override suspend fun findDuplicate(packageName: String, senderName: String, incomingMessage: String): QueueItem? = null
            override suspend fun getQueueItemById(id: Long): QueueItem? = null
            override suspend fun saveQueueItem(item: QueueItem): Long = 0L
            override suspend fun deleteQueueItem(item: QueueItem) {}
            override suspend fun deleteQueueItemById(id: Long) {}
            override suspend fun clearQueue() {}
        }

        val mockContactRepository = object : ContactRepository {
            override fun getAllContacts() = flowOf(emptyList<Contact>())
            override suspend fun getContactsByStatus(status: ContactStatus) = emptyList<Contact>()
            override suspend fun getContactById(id: Long): Contact? = null
            override suspend fun getContactByPhoneNumber(phoneNumber: String): Contact? = null
            override suspend fun saveContact(contact: Contact): Long = 0L
            override suspend fun deleteContact(contact: Contact) {}
        }

        val mockBlacklistRepository = object : BlacklistRepository {
            override fun getAllBlacklistEntries() = flowOf(emptyList<BlacklistEntry>())
            override suspend fun getBlacklistEntriesByStatus(status: BlacklistStatus) = emptyList<BlacklistEntry>()
            override suspend fun getBlacklistById(id: Long): BlacklistEntry? = null
            override suspend fun isBlacklisted(identifier: String): Boolean = false
            override suspend fun saveBlacklistEntry(entry: BlacklistEntry): Long = 0L
            override suspend fun deleteBlacklistEntry(entry: BlacklistEntry) {}
            override suspend fun deleteBlacklistByIdentifier(identifier: String) {}
        }

        val mockPermissionRepository = object : PermissionRepository {
            override fun getPermissionsFlow(context: Context) = flowOf(emptyList<PermissionItem>())
            override fun checkPermissions(context: Context) = emptyList<PermissionItem>()
            override fun isAutostartConfirmed() = false
            override fun setAutostartConfirmed(confirmed: Boolean) {}
        }

        val context = RuntimeEnvironment.getApplication()
        val settingsManager = SettingsManager(context)
        val permissionManager = PermissionManager()
        
        val homeViewModel = HomeViewModel(
            ruleRepository = mockRuleRepository,
            historyRepository = mockHistoryRepository,
            queueRepository = mockQueueRepository,
            contactRepository = mockContactRepository,
            blacklistRepository = mockBlacklistRepository,
            settingsManager = settingsManager
        )
        val rulesViewModel = RulesViewModel(mockRuleRepository)
        val historyViewModel = HistoryViewModel(mockHistoryRepository)
        val settingsViewModel = SettingsViewModel(settingsManager, mockSettingsRepository)
        val permissionViewModel = PermissionViewModel(mockPermissionRepository, permissionManager, context)

        val mockServiceRepository = object : com.example.repository.ServiceRepository {
            override val isServiceRunning = kotlinx.coroutines.flow.flowOf(false)
            override fun startService() {}
            override fun stopService() {}
            override fun restartService() {}
        }
        val serviceViewModel = ServiceViewModel(mockServiceRepository)

        val mockLogRepository = object : com.example.repository.LogRepository {
            override fun getAllLogs() = kotlinx.coroutines.flow.flowOf(emptyList<com.example.model.LogItem>())
            override suspend fun insertLog(
                category: com.example.model.LogCategory,
                level: com.example.model.LogLevel,
                message: String,
                extraData: String?
            ): Long = 0L
            override suspend fun deleteLogById(id: Long) {}
            override suspend fun deleteLogsBefore(timestamp: Long) {}
            override suspend fun clearAllLogs() {}
        }
        val logViewModel = LogViewModel(mockLogRepository)

        composeTestRule.setContent {
            SmartAutoReplyTheme {
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

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    }
}
