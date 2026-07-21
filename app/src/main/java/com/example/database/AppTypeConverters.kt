package com.example.database

import androidx.room.TypeConverter
import com.example.model.*

class AppTypeConverters {

    @TypeConverter
    fun fromRuleStatus(status: RuleStatus): String = status.name

    @TypeConverter
    fun toRuleStatus(value: String): RuleStatus = try {
        RuleStatus.valueOf(value)
    } catch (e: IllegalArgumentException) {
        RuleStatus.ACTIVE
    }

    @TypeConverter
    fun fromHistoryStatus(status: HistoryStatus): String = status.name

    @TypeConverter
    fun toHistoryStatus(value: String): HistoryStatus = try {
        HistoryStatus.valueOf(value)
    } catch (e: IllegalArgumentException) {
        HistoryStatus.SENT
    }

    @TypeConverter
    fun fromQueueStatus(status: QueueStatus): String = status.name

    @TypeConverter
    fun toQueueStatus(value: String): QueueStatus = try {
        QueueStatus.valueOf(value)
    } catch (e: IllegalArgumentException) {
        QueueStatus.PENDING
    }

    @TypeConverter
    fun fromContactStatus(status: ContactStatus): String = status.name

    @TypeConverter
    fun toContactStatus(value: String): ContactStatus = try {
        ContactStatus.valueOf(value)
    } catch (e: IllegalArgumentException) {
        ContactStatus.ACTIVE
    }

    @TypeConverter
    fun fromSettingsStatus(status: SettingsStatus): String = status.name

    @TypeConverter
    fun toSettingsStatus(value: String): SettingsStatus = try {
        SettingsStatus.valueOf(value)
    } catch (e: IllegalArgumentException) {
        SettingsStatus.ACTIVE
    }

    @TypeConverter
    fun fromBlacklistStatus(status: BlacklistStatus): String = status.name

    @TypeConverter
    fun toBlacklistStatus(value: String): BlacklistStatus = try {
        BlacklistStatus.valueOf(value)
    } catch (e: IllegalArgumentException) {
        BlacklistStatus.ACTIVE
    }

    @TypeConverter
    fun fromConversationStatus(status: ConversationStatus): String = status.name

    @TypeConverter
    fun toConversationStatus(value: String): ConversationStatus = try {
        ConversationStatus.valueOf(value)
    } catch (e: IllegalArgumentException) {
        ConversationStatus.ACTIVE
    }

    @TypeConverter
    fun fromNullableQueueStatus(status: QueueStatus?): String? = status?.name

    @TypeConverter
    fun toNullableQueueStatus(value: String?): QueueStatus? = if (value == null) null else try {
        QueueStatus.valueOf(value)
    } catch (e: IllegalArgumentException) {
        QueueStatus.PENDING
    }
}
