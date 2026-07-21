package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.PermissionItem
import com.example.permission.PermissionManager
import com.example.repository.PermissionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class PermissionViewModel(
    private val permissionRepository: PermissionRepository,
    private val permissionManager: PermissionManager,
    private val context: Context
) : ViewModel() {

    val permissionsState: StateFlow<List<PermissionItem>> = permissionRepository
        .getPermissionsFlow(context)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = permissionRepository.checkPermissions(context)
        )

    /**
     * Executes the main permission grant flow.
     */
    fun grantPermission(
        item: PermissionItem,
        onLaunchIntent: (Intent) -> Unit,
        onRequestPostNotifications: () -> Unit
    ) {
        when (item.id) {
            "notification" -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    !permissionManager.isPostNotificationsGranted(context)
                ) {
                    onRequestPostNotifications()
                } else {
                    onLaunchIntent(permissionManager.getNotificationListenerSettingsIntent())
                }
            }
            "accessibility" -> {
                onLaunchIntent(permissionManager.getAccessibilitySettingsIntent())
            }
            "overlay" -> {
                permissionManager.getDrawOverlaySettingsIntent(context)?.let {
                    onLaunchIntent(it)
                }
            }
            "battery" -> {
                onLaunchIntent(permissionManager.getBatteryOptimizationSettingsIntent(context))
            }
            "foreground" -> {
                onLaunchIntent(permissionManager.getAppDetailsSettingsIntent(context))
            }
            "autostart" -> {
                permissionRepository.setAutostartConfirmed(true)
                val intents = permissionManager.getAutostartSettingsIntents(context)
                val callable = permissionManager.getFirstCallableIntent(context, intents)
                onLaunchIntent(callable)
            }
            "background_restriction" -> {
                onLaunchIntent(permissionManager.getAppDetailsSettingsIntent(context))
            }
        }
    }

    /**
     * Opens the general Android system configuration for the given permission or falls back to App Info details.
     */
    fun openSettingsPage(item: PermissionItem, onLaunchIntent: (Intent) -> Unit) {
        onLaunchIntent(permissionManager.getAppDetailsSettingsIntent(context))
    }
}
