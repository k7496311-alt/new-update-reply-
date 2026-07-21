package com.example

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.example.database.AppDatabase
import com.example.database.NotificationEntity
import com.example.notification.NotificationParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationListenerTestNotificationListenerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val parser = NotificationParser(context)

    private fun createSbn(
        packageName: String,
        id: Int,
        title: String,
        text: String,
        category: String? = null,
        postTime: Long = 1000L
    ): StatusBarNotification {
        val builder = Notification.Builder(context)
            .setContentTitle(title)
            .setContentText(text)

        if (category != null) {
            builder.setCategory(category)
        }

        val notification = builder.build()

        return StatusBarNotification(
            packageName,
            packageName,
            id,
            null,
            1000,
            1000,
            0,
            notification,
            android.os.Process.myUserHandle(),
            postTime
        )
    }

    @Test
    fun testParseValidNotification() {
        val sbn = createSbn("com.whatsapp", 101, "John Doe", "Hey there!")
        val item = parser.parse(sbn)

        assertNotNull(item)
        assertEquals("com.whatsapp", item?.packageName)
        assertEquals("John Doe", item?.sender)
        assertEquals("Hey there!", item?.message)
        assertEquals(101, item?.notificationId)
        assertEquals(1000L, item?.timestamp)
        assertFalse(item?.isGroupMessage ?: true)
    }

    @Test
    fun testIgnoreMissedCall() {
        // Test Category-based missed call ignore
        val sbnCategory = createSbn("com.android.dialer", 102, "John Doe", "Missed call", Notification.CATEGORY_MISSED_CALL)
        val itemCategory = parser.parse(sbnCategory)
        assertNull(itemCategory)

        // Test text-based missed call ignore
        val sbnText = createSbn("com.google.android.dialer", 103, "Missed call", "Call from mom")
        val itemText = parser.parse(sbnText)
        assertNull(itemText)
    }

    @Test
    fun testIgnoreSystemNotification() {
        val sbn = createSbn("android", 104, "System", "Updating system", Notification.CATEGORY_SYSTEM)
        val item = parser.parse(sbn)
        assertNull(item)
    }

    @Test
    fun testIgnoreBatteryNotification() {
        val sbn = createSbn("com.android.systemui", 105, "Battery Low", "Connect charger")
        val item = parser.parse(sbn)
        assertNull(item)
    }

    @Test
    fun testIgnoreDownloadNotification() {
        val sbn = createSbn("com.android.providers.downloads", 106, "Downloading file", "10% completed")
        val item = parser.parse(sbn)
        assertNull(item)
    }

    @Test
    fun testRoomIntegration() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.notificationDao()

        val entity = NotificationEntity(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            sender = "John Doe",
            conversation = "John Doe",
            message = "Hey there!",
            timestamp = 1000L,
            notificationId = 101,
            isGroupMessage = false
        )

        val id = dao.insertNotification(entity)
        assertTrue(id > 0)

        db.close()
    }
}
