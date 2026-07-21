package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.accessibility.AutoReplyAccessibilityService
import com.example.permission.PermissionManager
import kotlinx.coroutines.delay

import androidx.lifecycle.LiveData
import androidx.compose.runtime.State
import androidx.compose.foundation.BorderStroke

@Composable
fun <T> LiveData<T>.observeAsState(initial: T): State<T> {
    val state = remember { mutableStateOf(initial) }
    DisposableEffect(this) {
        val observer = androidx.lifecycle.Observer<T> { value ->
            state.value = value
        }
        observeForever(observer)
        onDispose {
            removeObserver(observer)
        }
    }
    return state
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    serviceViewModel: ServiceViewModel,
    onNavigateToTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionManager = remember { PermissionManager() }

    val isServiceRunningState by serviceViewModel.isServiceRunning.observeAsState(initial = false)

    // State for local permission checks
    var isNotificationEnabled by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isOverlayEnabled by remember { mutableStateOf(false) }

    // Function to refresh permission states
    fun refreshPermissions() {
        isNotificationEnabled = permissionManager.isNotificationListenerEnabled(context)
        isAccessibilityEnabled = permissionManager.isAccessibilityServiceEnabled(
            context,
            AutoReplyAccessibilityService::class.java
        )
        isOverlayEnabled = permissionManager.isDrawOverlaysAllowed(context)
    }

    // Refresh permissions on active screen transitions/resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Collect real-time stats from the view model
    val totalRules by viewModel.totalRulesCount.collectAsState()
    val totalSent by viewModel.totalSentRepliesCount.collectAsState()
    val totalFailed by viewModel.totalFailedRepliesCount.collectAsState()
    val queueSize by viewModel.queueItemsCount.collectAsState()
    val contactsCount by viewModel.contactsCount.collectAsState()
    val blacklistCount by viewModel.blacklistCount.collectAsState()

    val isServiceActive = viewModel.isServiceEnabled

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SmartButton,
                            contentDescription = "Smart Auto Reply Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Smart Auto Reply",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("home_title")
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Service Orchestrator / Control Card
            ServiceControlCard(
                isActive = isServiceRunningState,
                onStart = { serviceViewModel.startService() },
                onStop = { serviceViewModel.stopService() },
                onRestart = { serviceViewModel.restartService() }
            )

            // 2. Permission Status List/Grid
            PermissionStatusPanel(
                isNotificationEnabled = isNotificationEnabled,
                isAccessibilityEnabled = isAccessibilityEnabled,
                isOverlayEnabled = isOverlayEnabled,
                onRequestNotification = {
                    context.startActivity(permissionManager.getNotificationListenerSettingsIntent())
                },
                onRequestAccessibility = {
                    context.startActivity(permissionManager.getAccessibilitySettingsIntent())
                },
                onRequestOverlay = {
                    permissionManager.getDrawOverlaySettingsIntent(context)?.let {
                        context.startActivity(it)
                    }
                }
            )

            // 3. Statistics Header
            Text(
                text = "System Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // 4. Grid of Statistics Cards
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("stats_grid")
            ) {
                item {
                    StatCard(
                        title = "Active Rules",
                        value = totalRules.toString(),
                        icon = Icons.Default.Rule,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigateToTab("rules") }
                    )
                }
                item {
                    StatCard(
                        title = "Replies Sent",
                        value = totalSent.toString(),
                        icon = Icons.Default.CheckCircle,
                        iconTint = Color(0xFF4CAF50),
                        onClick = { onNavigateToTab("history") }
                    )
                }
                item {
                    StatCard(
                        title = "Queue Size",
                        value = queueSize.toString(),
                        icon = Icons.Default.HourglassEmpty,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = { /* No-op, visual statistics card */ }
                    )
                }
                item {
                    StatCard(
                        title = "Failed Replies",
                        value = totalFailed.toString(),
                        icon = Icons.Default.Error,
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = { onNavigateToTab("history") }
                    )
                }
                item {
                    StatCard(
                        title = "Contacts",
                        value = contactsCount.toString(),
                        icon = Icons.Default.Contacts,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        onClick = { /* No-op */ }
                    )
                }
                item {
                    StatCard(
                        title = "Blacklist",
                        value = blacklistCount.toString(),
                        icon = Icons.Default.Block,
                        iconTint = MaterialTheme.colorScheme.outline,
                        onClick = { onNavigateToTab("settings") }
                    )
                }
            }

            // 5. Bottom Navigation Shortcuts Footer
            QuickActionShortcuts(onNavigateToTab = onNavigateToTab)

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun ServiceControlCard(
    isActive: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(durationMillis = 300)
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("service_control_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Status indicator with heartbeat pulse
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.CenterVertically),
                    contentAlignment = Alignment.Center
                ) {
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0x404CAF50))
                        )
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Orchestrator Service",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isActive) "Service is Running" else "Service is Stopped",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onStart,
                        enabled = !isActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0x304CAF50),
                            disabledContentColor = Color.White.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("btn_start_service")
                    ) {
                        Text("Start", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onStop,
                        enabled = isActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            disabledContentColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.38f)
                        ),
                        modifier = Modifier.testTag("btn_stop_service")
                    ) {
                        Text("Stop", fontWeight = FontWeight.SemiBold)
                    }
                }
                
                OutlinedButton(
                    onClick = onRestart,
                    enabled = isActive,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                    ),
                    border = BorderStroke(1.dp, if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    modifier = Modifier.testTag("btn_restart_service")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Restart Service",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restart", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun PermissionStatusPanel(
    isNotificationEnabled: Boolean,
    isAccessibilityEnabled: Boolean,
    isOverlayEnabled: Boolean,
    onRequestNotification: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("permission_panel"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder(enabled = true)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "System Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            PermissionItem(
                title = "Notification Listener",
                description = "Required to capture incoming chat notifications.",
                isGranted = isNotificationEnabled,
                onRequest = onRequestNotification,
                tag = "permission_notification"
            )

            PermissionItem(
                title = "Accessibility Assistant",
                description = "Required to send automatic dispatch replies.",
                isGranted = isAccessibilityEnabled,
                onRequest = onRequestAccessibility,
                tag = "permission_accessibility"
            )

            PermissionItem(
                title = "Overlay System",
                description = "Enables floating assistant status overlays.",
                isGranted = isOverlayEnabled,
                onRequest = onRequestOverlay,
                tag = "permission_overlay"
            )
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Warning,
                    contentDescription = if (isGranted) "Granted" else "Requires Action",
                    tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFFF9800),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (!isGranted) {
            Button(
                onClick = onRequest,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .testTag("grant_btn_$tag")
                    .height(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text("Grant", style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x154CAF50))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Granted",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF388E3C),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("stat_card_${title.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder(enabled = true)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun QuickActionShortcuts(
    onNavigateToTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ShortcutButton(
            title = "Rules",
            icon = Icons.Default.Rule,
            onClick = { onNavigateToTab("rules") },
            modifier = Modifier
                .weight(1f)
                .testTag("shortcut_rules")
        )
        ShortcutButton(
            title = "History",
            icon = Icons.Default.History,
            onClick = { onNavigateToTab("history") },
            modifier = Modifier
                .weight(1f)
                .testTag("shortcut_history")
        )
        ShortcutButton(
            title = "Settings",
            icon = Icons.Default.Settings,
            onClick = { onNavigateToTab("settings") },
            modifier = Modifier
                .weight(1f)
                .testTag("shortcut_settings")
        )
    }
}

@Composable
fun ShortcutButton(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
