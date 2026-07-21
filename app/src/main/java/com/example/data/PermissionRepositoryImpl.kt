package com.example.data

import android.content.Context
import com.example.accessibility.AutoReplyAccessibilityService
import com.example.model.PermissionItem
import com.example.model.PermissionStatus
import com.example.permission.PermissionManager
import com.example.repository.PermissionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class PermissionRepositoryImpl(
    private val permissionManager: PermissionManager,
    context: Context
) : PermissionRepository {

    private val prefs = context.getSharedPreferences("permission_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_AUTOSTART_CONFIRMED = "autostart_confirmed"
    }

    override fun isAutostartConfirmed(): Boolean {
        return prefs.getBoolean(KEY_AUTOSTART_CONFIRMED, false)
    }

    override fun setAutostartConfirmed(confirmed: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOSTART_CONFIRMED, confirmed).apply()
    }

    override fun getPermissionsFlow(context: Context): Flow<List<PermissionItem>> = flow {
        while (true) {
            emit(checkPermissions(context))
            kotlinx.coroutines.delay(2000) // Poll every 2 seconds to auto-detect permission grants in real time
        }
    }

    override fun checkPermissions(context: Context): List<PermissionItem> {
        val list = mutableListOf<PermissionItem>()

        // 1. Notification Permission
        val hasPostNotifications = permissionManager.isPostNotificationsGranted(context)
        val hasNotificationListener = permissionManager.isNotificationListenerEnabled(context)
        val notificationStatus = if (hasPostNotifications && hasNotificationListener) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.NOT_GRANTED // Red
        }
        val notificationDetails = when {
            hasPostNotifications && hasNotificationListener -> "All notification privileges granted."
            !hasPostNotifications && !hasNotificationListener -> "Please grant notification runtime permission and authorize listener access."
            !hasPostNotifications -> "Notification runtime permission is missing."
            else -> "Notification Listener Service authorization is missing."
        }
        list.add(
            PermissionItem(
                id = "notification",
                title = "Notification Privileges",
                description = "Required to capture incoming messages from other applications and post replies.",
                status = notificationStatus,
                details = notificationDetails
            )
        )

        // 2. Accessibility Service
        val hasAccessibility = permissionManager.isAccessibilityServiceEnabled(
            context,
            AutoReplyAccessibilityService::class.java
        )
        list.add(
            PermissionItem(
                id = "accessibility",
                title = "Accessibility Assistant",
                description = "Allows typing and triggering the 'Send' action inside messaging application UI directly.",
                status = if (hasAccessibility) PermissionStatus.GRANTED else PermissionStatus.NOT_GRANTED,
                details = if (hasAccessibility) "Service helper is active." else "Helper service is currently inactive."
            )
        )

        // 3. Draw Overlays
        val hasOverlay = permissionManager.isDrawOverlaysAllowed(context)
        list.add(
            PermissionItem(
                id = "overlay",
                title = "Display Over Other Apps",
                description = "Allows displaying floating action badges and manual reply overlays above other app windows.",
                status = if (hasOverlay) PermissionStatus.GRANTED else PermissionStatus.NOT_GRANTED,
                details = if (hasOverlay) "Overlay rendering is authorized." else "Overlay authorization missing."
            )
        )

        // 4. Ignore Battery Optimization
        val ignoringBattery = permissionManager.isIgnoringBatteryOptimizations(context)
        list.add(
            PermissionItem(
                id = "battery",
                title = "Battery Optimization Bypass",
                description = "Prevents Android from putting the auto-reply background processes to sleep during inactivity.",
                status = if (ignoringBattery) PermissionStatus.GRANTED else PermissionStatus.RESTRICTED, // Yellow warning if restricted
                details = if (ignoringBattery) "Bypass active. Reliable background processing guaranteed." else "Optimizing battery. Background services may terminate arbitrarily."
            )
        )

        // 5. Foreground Service Permission
        val hasForeground = permissionManager.isForegroundServicePermissionGranted(context)
        list.add(
            PermissionItem(
                id = "foreground",
                title = "Foreground Service Permission",
                description = "Enables launching persistent, system-notified background services for auto replying.",
                status = if (hasForeground) PermissionStatus.GRANTED else PermissionStatus.NOT_GRANTED,
                details = if (hasForeground) "System foreground service permission granted." else "Foreground service permission is missing."
            )
        )

        // 6. Autostart Check
        val manufacturer = permissionManager.getDeviceManufacturer()
        val brand = permissionManager.getDeviceBrand()
        val autostartApplicable = permissionManager.isAutostartApplicable()
        val autostartStatus = if (autostartApplicable) {
            if (isAutostartConfirmed()) PermissionStatus.GRANTED else PermissionStatus.RESTRICTED // Yellow
        } else {
            PermissionStatus.GRANTED // Green on Pixel / Nothing / etc.
        }
        val autostartDetails = if (autostartApplicable) {
            if (isAutostartConfirmed()) {
                "Autostart checked. Device: ${manufacturer.uppercase()} / ${brand.uppercase()}."
            } else {
                "Attention: ${manufacturer.uppercase()} devices restrict background services unless Autostart/Auto-Launch is enabled."
            }
        } else {
            "Not strictly required on stock Android (${manufacturer.uppercase()})."
        }
        list.add(
            PermissionItem(
                id = "autostart",
                title = "Autostart Permission",
                description = "Permits the application to automatically boot up when the phone restarts, keeping the service alive.",
                status = autostartStatus,
                details = autostartDetails
            )
        )

        // 7. Background Restriction Check
        val isRestricted = permissionManager.isBackgroundRestricted(context)
        list.add(
            PermissionItem(
                id = "background_restriction",
                title = "Background Execution Status",
                description = "Monitors if the system has restricted background network or CPU processing for this app.",
                status = if (isRestricted) PermissionStatus.RESTRICTED else PermissionStatus.GRANTED,
                details = if (isRestricted) "Background restriction detected! Please disable app restrictions in Settings." else "No background constraints imposed by OS."
            )
        )

        return list
    }
}
