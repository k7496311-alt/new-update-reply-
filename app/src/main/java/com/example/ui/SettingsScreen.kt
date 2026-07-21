package com.example.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accessibility.AutoReplyAccessibilityService
import com.example.permission.PermissionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val permissionManager = remember { PermissionManager() }
    val scrollState = rememberScrollState()

    // Permission monitoring state
    var isAccessibilityGranted by remember {
        mutableStateOf(permissionManager.isAccessibilityServiceEnabled(context, AutoReplyAccessibilityService::class.java))
    }
    var isNotificationListenerGranted by remember {
        mutableStateOf(permissionManager.isNotificationListenerEnabled(context))
    }
    var isOverlayGranted by remember {
        mutableStateOf(permissionManager.isDrawOverlaysAllowed(context))
    }
    var isBatteryUnrestricted by remember {
        mutableStateOf(permissionManager.isIgnoringBatteryOptimizations(context))
    }

    // Refresh function for permissions
    val refreshPermissions = {
        isAccessibilityGranted = permissionManager.isAccessibilityServiceEnabled(context, AutoReplyAccessibilityService::class.java)
        isNotificationListenerGranted = permissionManager.isNotificationListenerEnabled(context)
        isOverlayGranted = permissionManager.isDrawOverlaysAllowed(context)
        isBatteryUnrestricted = permissionManager.isIgnoringBatteryOptimizations(context)
    }

    // Refresh on launch and periodically on recompositions
    LaunchedEffect(Unit) {
        refreshPermissions()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Configuration & Settings",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            refreshPermissions()
                            Toast.makeText(context, "Permissions Refreshed", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("btn_refresh_permissions")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Statuses"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Authorization Center (Status Panel)
            PermissionsStatusCard(
                context = context,
                permissionManager = permissionManager,
                isAccessibilityGranted = isAccessibilityGranted,
                isNotificationListenerGranted = isNotificationListenerGranted,
                isOverlayGranted = isOverlayGranted,
                isBatteryUnrestricted = isBatteryUnrestricted
            )

            // Category: General
            CategoryCard(
                title = "General Settings",
                icon = Icons.Default.Settings
            ) {
                // Dark Mode Switch
                SettingsSwitchRow(
                    title = "Dark Mode Theme",
                    subtitle = "Switch between light and high-contrast dark theme",
                    checked = viewModel.isDarkModeEnabled,
                    onCheckedChange = { viewModel.updateDarkModeEnabled(it) },
                    testTag = "switch_dark_mode",
                    icon = Icons.Default.BrightnessMedium
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

                // Language Selector
                SettingsLanguageRow(
                    selectedLanguage = viewModel.languageCode,
                    onLanguageSelected = { viewModel.updateLanguageCode(it) }
                )
            }

            // Category: Notification Controls
            CategoryCard(
                title = "Notification Configuration",
                icon = Icons.Default.Notifications
            ) {
                SettingsSwitchRow(
                    title = "System Service Automation",
                    subtitle = "Monitor background message notifications",
                    checked = viewModel.isServiceEnabled,
                    onCheckedChange = { viewModel.updateServiceEnabled(it) },
                    testTag = "switch_global_service",
                    icon = Icons.Default.PlayArrow
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

                SettingsSwitchRow(
                    title = "Reply to Group Chats",
                    subtitle = "Send replies to active multiplayer groups",
                    checked = viewModel.shouldReplyToGroups,
                    onCheckedChange = { viewModel.updateReplyToGroups(it) },
                    testTag = "switch_group_replies",
                    icon = Icons.Default.Group
                )
            }

            // Category: Accessibility Controls
            CategoryCard(
                title = "Accessibility Options",
                icon = Icons.Default.Accessibility
            ) {
                SettingsSwitchRow(
                    title = "Mute in Quiet Mode",
                    subtitle = "Temporarily suspend all triggered rules",
                    checked = viewModel.isQuietModeEnabled,
                    onCheckedChange = { viewModel.updateQuietModeEnabled(it) },
                    testTag = "switch_quiet_mode",
                    icon = Icons.Default.VolumeMute
                )
            }

            // Category: Reply Settings
            CategoryCard(
                title = "Reply Delivery Options",
                icon = Icons.Default.Reply
            ) {
                // Default Reply Edit
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        text = "Default Away Response Message",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = viewModel.defaultReplyText,
                        onValueChange = { viewModel.updateDefaultReplyText(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_default_reply"),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text("Enter custom default response...") },
                        maxLines = 4
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

                // Reply Delay Slider
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reply Delay",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${viewModel.replyDelaySecs}s",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Adjust the pause time before triggering the auto-response",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Slider(
                        value = viewModel.replyDelaySecs.toFloat(),
                        onValueChange = { viewModel.updateReplyDelaySecs(it.toInt()) },
                        valueRange = 0f..10f,
                        steps = 9,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("slider_reply_delay")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

                // Retry Count Slider
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transmission Retry Count",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${viewModel.retryCount} times",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Number of retry attempts if reply fails to send",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Slider(
                        value = viewModel.retryCount.toFloat(),
                        onValueChange = { viewModel.updateRetryCount(it.toInt()) },
                        valueRange = 1f..5f,
                        steps = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("slider_retry_count")
                    )
                }
            }

            // Category: History Configuration
            CategoryCard(
                title = "Queue & History Metrics",
                icon = Icons.Default.History
            ) {
                // Queue Size Input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Max Queue Size",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Limit buffer capacity of outgoing tasks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    OutlinedTextField(
                        value = viewModel.queueSize.toString(),
                        onValueChange = { newValue ->
                            val size = newValue.filter { it.isDigit() }.toIntOrNull() ?: 50
                            viewModel.updateQueueSize(size)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .width(80.dp)
                            .testTag("input_queue_size"),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

                // Max Daily Reply Input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Max Daily Responses",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Daily cap on outgoing auto-replies",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    OutlinedTextField(
                        value = viewModel.maxDailyReply.toString(),
                        onValueChange = { newValue ->
                            val max = newValue.filter { it.isDigit() }.toIntOrNull() ?: 100
                            viewModel.updateMaxDailyReply(max)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .width(80.dp)
                            .testTag("input_max_daily_reply"),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }
            }

            // Category: Backup & Restore
            CategoryCard(
                title = "Backup & Restore Utilities",
                icon = Icons.Default.Backup
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Data Operations",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Export active configurations or restore them to initial system defaults",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val configStr = "Language: ${viewModel.languageCode}, Delay: ${viewModel.replyDelaySecs}s, MaxDaily: ${viewModel.maxDailyReply}, DefaultMsg: '${viewModel.defaultReplyText}'"
                                clipboardManager.setText(AnnotatedString(configStr))
                                Toast.makeText(context, "Config backup copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_backup_settings")
                        ) {
                            Icon(imageVector = Icons.Default.Backup, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backup")
                        }

                        Button(
                            onClick = {
                                // Restore to factory defaults
                                viewModel.updateDarkModeEnabled(false)
                                viewModel.updateLanguageCode("en")
                                viewModel.updateReplyDelaySecs(2)
                                viewModel.updateRetryCount(3)
                                viewModel.updateQueueSize(50)
                                viewModel.updateMaxDailyReply(100)
                                viewModel.updateDefaultReplyText("I am currently away. I will get back to you soon.")
                                viewModel.updateServiceEnabled(true)
                                viewModel.updateReplyToGroups(false)
                                viewModel.updateQuietModeEnabled(false)
                                Toast.makeText(context, "Settings restored to factory defaults!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_restore_settings")
                        ) {
                            Icon(imageVector = Icons.Default.Restore, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore")
                        }
                    }
                }
            }

            // Category: Diagnostics & System Logs
            CategoryCard(
                title = "Diagnostics & Troubleshooting",
                icon = Icons.Default.BugReport
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "System Diagnostics Logs",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "View and filter real-time background service logs, accessibility actions, notifications received, and error traces to verify rules and queue operation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Button(
                        onClick = onNavigateToLogs,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .testTag("btn_navigate_to_logs")
                    ) {
                        Icon(imageVector = Icons.Default.MonitorHeart, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Diagnostics Log Viewer")
                    }
                }
            }

            // Category: About Detail
            CategoryCard(
                title = "About Application",
                icon = Icons.Default.Info
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Smart Auto Reply Pro",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Version 1.2.0 (Stable Release)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Built on clean MVVM Architecture, featuring unified Room persistence connected preferences, reactive coroutine tasks, and standard Material 3 visual assets.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PermissionsStatusCard(
    context: Context,
    permissionManager: PermissionManager,
    isAccessibilityGranted: Boolean,
    isNotificationListenerGranted: Boolean,
    isOverlayGranted: Boolean,
    isBatteryUnrestricted: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = CardDefaults.outlinedCardBorder(enabled = true)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "System Permissions Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Battery Optimization row
            PermissionRow(
                title = "Battery Optimization",
                description = "Disable restriction to prevent OS from killing background replies",
                isGranted = isBatteryUnrestricted,
                icon = Icons.Default.BatteryAlert,
                onRequest = {
                    context.startActivity(permissionManager.getBatteryOptimizationSettingsIntent(context))
                },
                testTag = "btn_grant_battery"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

            // Overlay row
            PermissionRow(
                title = "Overlay Permission",
                description = "Required to display custom notification details over other apps",
                isGranted = isOverlayGranted,
                icon = Icons.Default.Layers,
                onRequest = {
                    val intent = permissionManager.getDrawOverlaySettingsIntent(context)
                    if (intent != null) {
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(context, "Overlay Permission not required or supported", Toast.LENGTH_SHORT).show()
                    }
                },
                testTag = "btn_grant_overlay"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

            // Notification Listener row
            PermissionRow(
                title = "Notification Permission",
                description = "Required to scan incoming chat app notifications",
                isGranted = isNotificationListenerGranted,
                icon = Icons.Default.Notifications,
                onRequest = {
                    context.startActivity(permissionManager.getNotificationListenerSettingsIntent())
                },
                testTag = "btn_grant_notification"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

            // Accessibility row
            PermissionRow(
                title = "Accessibility Status",
                description = "Enables programmatic entry & sending in selected apps",
                isGranted = isAccessibilityGranted,
                icon = Icons.Default.Accessibility,
                onRequest = {
                    context.startActivity(permissionManager.getAccessibilitySettingsIntent())
                },
                testTag = "btn_grant_accessibility"
            )
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    onRequest: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (isGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        if (isGranted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Permission Granted",
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(24.dp)
            )
        } else {
            IconButton(
                onClick = onRequest,
                modifier = Modifier
                    .testTag(testTag)
                    .minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForwardIos,
                    contentDescription = "Grant Authorization",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun CategoryCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder(enabled = true)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier
                .testTag(testTag)
                .minimumInteractiveComponentSize()
        )
    }
}

@Composable
fun SettingsLanguageRow(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val languages = listOf(
        "en" to "English",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = "Application Language",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Select your preferred translation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Box {
            Button(
                onClick = { expanded = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("dropdown_language")
            ) {
                Text(languages.find { it.first == selectedLanguage }?.second ?: "English")
                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                languages.forEach { (code, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onLanguageSelected(code)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
