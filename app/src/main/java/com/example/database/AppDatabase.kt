package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RuleEntity::class,
        HistoryEntity::class,
        QueueEntity::class,
        ContactEntity::class,
        SettingsEntity::class,
        BlacklistEntity::class,
        NotificationEntity::class,
        LogEntity::class,
        ConversationEntity::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun historyDao(): HistoryDao
    abstract fun queueDao(): QueueDao
    abstract fun contactDao(): ContactDao
    abstract fun settingsDao(): SettingsDao
    abstract fun blacklistDao(): BlacklistDao
    abstract fun notificationDao(): NotificationDao
    abstract fun logDao(): LogDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Alter reply_rules table to add updatedAt and status
                db.execSQL("ALTER TABLE reply_rules ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reply_rules ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
                // Update existing columns
                db.execSQL("UPDATE reply_rules SET updatedAt = createdAt")

                // 2. Alter reply_history table to add createdAt, updatedAt, and status
                db.execSQL("ALTER TABLE reply_history ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reply_history ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reply_history ADD COLUMN status TEXT NOT NULL DEFAULT 'SENT'")
                // Update existing columns
                db.execSQL("UPDATE reply_history SET createdAt = timestamp, updatedAt = timestamp")
                db.execSQL("UPDATE reply_history SET status = 'SENT' WHERE isSuccessfullySent = 1")
                db.execSQL("UPDATE reply_history SET status = 'FAILED' WHERE isSuccessfullySent = 0")

                // 3. Create reply_queue table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `reply_queue` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `ruleId` INTEGER NOT NULL, 
                        `senderName` TEXT NOT NULL, 
                        `incomingMessage` TEXT NOT NULL, 
                        `replyText` TEXT NOT NULL, 
                        `packageName` TEXT NOT NULL, 
                        `scheduledTime` INTEGER NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `status` TEXT NOT NULL
                    )
                """.trimIndent())

                // 4. Create contacts table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `contacts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `phoneNumber` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `status` TEXT NOT NULL
                    )
                """.trimIndent())

                // 5. Create app_settings table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `app_settings` (
                        `key` TEXT PRIMARY KEY NOT NULL, 
                        `value` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `status` TEXT NOT NULL
                    )
                """.trimIndent())

                // 6. Create blacklist table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `blacklist` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `identifier` TEXT NOT NULL, 
                        `reason` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `status` TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reply_rules ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reply_rules ADD COLUMN cooldownMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reply_rules ADD COLUMN maxReplies INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reply_rules ADD COLUMN category TEXT NOT NULL DEFAULT 'General'")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reply_history ADD COLUMN reason TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reply_queue ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reply_queue ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reply_queue ADD COLUMN maxRetries INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE reply_queue ADD COLUMN errorMessage TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `application_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        `category` TEXT NOT NULL, 
                        `level` TEXT NOT NULL, 
                        `message` TEXT NOT NULL, 
                        `extraData` TEXT
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reply_rules ADD COLUMN isCaseSensitive INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reply_rules ADD COLUMN shouldTrimSpaces INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE reply_rules ADD COLUMN shouldIgnoreEmoji INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reply_rules ADD COLUMN shouldIgnoreSymbols INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reply_rules ADD COLUMN shouldIgnoreMultipleSpaces INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reply_rules ADD COLUMN dailyLimit INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reply_rules ADD COLUMN globalLimit INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `conversations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `senderName` TEXT NOT NULL, 
                        `packageName` TEXT NOT NULL, 
                        `lastMessage` TEXT NOT NULL, 
                        `lastReply` TEXT, 
                        `lastReplyTime` INTEGER NOT NULL, 
                        `unreadCount` INTEGER NOT NULL, 
                        `queueStatus` TEXT, 
                        `status` TEXT NOT NULL,
                        `lastActivityTime` INTEGER NOT NULL,
                        `isLocked` INTEGER NOT NULL,
                        `lockTimestamp` INTEGER NOT NULL,
                        `lastIncomingMessage` TEXT NOT NULL,
                        `repliedToLastMessage` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_conversations_senderName_packageName` ON `conversations` (`senderName`, `packageName`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_auto_reply_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
