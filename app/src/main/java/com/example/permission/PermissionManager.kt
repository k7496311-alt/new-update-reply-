package com.example.permission

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat

open class PermissionManager {

    /**
     * Checks if the Accessibility Service is enabled.
     */
    open fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
        val expectedComponentName = "${context.packageName}/${serviceClass.name}"
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if the Notification Listener Service is authorized.
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":")
            for (name in names) {
                if (name.contains(packageName)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Checks if the POST_NOTIFICATIONS runtime permission is granted (Android 13+).
     */
    fun isPostNotificationsGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks if the app is allowed to draw overlays.
     */
    fun isDrawOverlaysAllowed(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Checks if battery optimizations are ignored for this app.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else {
            true
        }
    }

    /**
     * Checks if the app has foreground service permission (API 28+).
     * It's technically declared in manifest, but we can verify here.
     */
    fun isForegroundServicePermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.FOREGROUND_SERVICE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Detects if the device manufacturer has placed strict background restrictions on the app.
     */
    fun isBackgroundRestricted(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activityManager?.isBackgroundRestricted == true
        } else {
            false
        }
    }

    /**
     * Returns the lowercase manufacturer name of the device.
     */
    fun getDeviceManufacturer(): String {
        return Build.MANUFACTURER.lowercase()
    }

    /**
     * Returns the lowercase brand name of the device.
     */
    fun getDeviceBrand(): String {
        return Build.BRAND.lowercase()
    }

    /**
     * Checks if the current manufacturer requires custom Autostart permission.
     */
    fun isAutostartApplicable(): Boolean {
        val manufacturer = getDeviceManufacturer()
        return manufacturer.contains("xiaomi") ||
                manufacturer.contains("redmi") ||
                manufacturer.contains("samsung") ||
                manufacturer.contains("oppo") ||
                manufacturer.contains("realme") ||
                manufacturer.contains("vivo") ||
                manufacturer.contains("motorola") ||
                manufacturer.contains("lenovo")
    }

    /**
     * Returns an Intent to open the Accessibility settings.
     */
    fun getAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    /**
     * Returns an Intent to open the Notification Listener settings page.
     */
    fun getNotificationListenerSettingsIntent(): Intent {
        return Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
    }

    /**
     * Returns an Intent to open Draw Overlays permission configuration.
     */
    fun getDrawOverlaySettingsIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            null
        }
    }

    /**
     * Returns an Intent to open the Battery Optimization ignore settings.
     */
    fun getBatteryOptimizationSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } catch (e: Exception) {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            }
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    /**
     * Returns an Intent to open the App's detailed info screen in Android Settings.
     */
    fun getAppDetailsSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /**
     * Obtains the manufacturer-specific autostart or auto-launch intent list.
     * Iterates through different packages to see if any can be resolved.
     */
    fun getAutostartSettingsIntents(context: Context): List<Intent> {
        val intents = mutableListOf<Intent>()
        val manufacturer = getDeviceManufacturer()

        // Xiaomi & Redmi
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi")) {
            intents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
            intents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.securitycenter.MainActivity")))
        }

        // Samsung
        if (manufacturer.contains("samsung")) {
            intents.add(Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")))
            intents.add(Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.activeuse.ActiveUseActivity")))
            intents.add(Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.MainActivity")))
        }

        // Oppo & Realme
        if (manufacturer.contains("oppo") || manufacturer.contains("realme")) {
            intents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
            intents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startupapp.StartupAppListActivity")))
            intents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.FakeActivity")))
            intents.add(Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")))
        }

        // Vivo
        if (manufacturer.contains("vivo")) {
            intents.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")))
            intents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")))
            intents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")))
        }

        // Motorola / Lenovo
        if (manufacturer.contains("motorola") || manufacturer.contains("lenovo")) {
            intents.add(Intent().setComponent(ComponentName("com.lenovo.security", "com.lenovo.security.purebackground.PureBackgroundActivity")))
        }

        // Standard App Info fallback
        intents.add(getAppDetailsSettingsIntent(context))

        return intents
    }

    /**
     * Resolves and returns the first callable Intent from a list.
     */
    fun getFirstCallableIntent(context: Context, intents: List<Intent>): Intent {
        val pm = context.packageManager
        for (intent in intents) {
            val resolveInfo = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolveInfo.isNotEmpty()) {
                return intent
            }
        }
        return getAppDetailsSettingsIntent(context)
    }
}
