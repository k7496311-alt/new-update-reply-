package com.example.settings

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "smart_auto_reply_prefs"
        private const val KEY_SERVICE_ENABLED = "key_service_enabled"
        private const val KEY_REPLY_TO_GROUPS = "key_reply_to_groups"
        private const val KEY_QUIET_MODE_ENABLED = "key_quiet_mode_enabled"
        
        private const val KEY_DARK_MODE = "key_dark_mode"
        private const val KEY_REPLY_DELAY = "key_reply_delay"
        private const val KEY_RETRY_COUNT = "key_retry_count"
        private const val KEY_QUEUE_SIZE = "key_queue_size"
        private const val KEY_MAX_DAILY_REPLY = "key_max_daily_reply"
        private const val KEY_DEFAULT_REPLY = "key_default_reply"
        private const val KEY_LANGUAGE = "key_language"
    }

    var isServiceEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_SERVICE_ENABLED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var shouldReplyToGroups: Boolean
        get() = sharedPreferences.getBoolean(KEY_REPLY_TO_GROUPS, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_REPLY_TO_GROUPS, value).apply()

    var isQuietModeEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_QUIET_MODE_ENABLED, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_QUIET_MODE_ENABLED, value).apply()

    var isDarkModeEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_DARK_MODE, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var replyDelaySecs: Int
        get() = sharedPreferences.getInt(KEY_REPLY_DELAY, 2)
        set(value) = sharedPreferences.edit().putInt(KEY_REPLY_DELAY, value).apply()

    var retryCount: Int
        get() = sharedPreferences.getInt(KEY_RETRY_COUNT, 3)
        set(value) = sharedPreferences.edit().putInt(KEY_RETRY_COUNT, value).apply()

    var queueSize: Int
        get() = sharedPreferences.getInt(KEY_QUEUE_SIZE, 50)
        set(value) = sharedPreferences.edit().putInt(KEY_QUEUE_SIZE, value).apply()

    var maxDailyReply: Int
        get() = sharedPreferences.getInt(KEY_MAX_DAILY_REPLY, 100)
        set(value) = sharedPreferences.edit().putInt(KEY_MAX_DAILY_REPLY, value).apply()

    var defaultReplyText: String
        get() = sharedPreferences.getString(KEY_DEFAULT_REPLY, "I am currently away. I will get back to you soon.") ?: "I am currently away. I will get back to you soon."
        set(value) = sharedPreferences.edit().putString(KEY_DEFAULT_REPLY, value).apply()

    var languageCode: String
        get() = sharedPreferences.getString(KEY_LANGUAGE, "en") ?: "en"
        set(value) = sharedPreferences.edit().putString(KEY_LANGUAGE, value).apply()
}
