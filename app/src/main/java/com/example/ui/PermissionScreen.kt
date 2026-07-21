package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PermissionItem
import com.example.model.PermissionStatus
import com.example.permission.PermissionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(
    viewModel: PermissionViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permissions by viewModel.permissionsState.collectAsState()
    val permissionManager = remember { PermissionManager() }

    // Launcher for POST_NOTIFICATIONS runtime permission on Android 13+
    val postNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val msg = if (isGranted) "Notification permission granted!" else "Notification permission denied."
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    val launchIntent: (Intent) -> Unit = { intent ->
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open system settings: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Permission Management",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("permission_screen_title")
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.testTag("permission_top_app_bar")
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("permission_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Manufacturer Info Banner
            item {
                ManufacturerInfoCard(permissionManager)
            }

            // Overview Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Background service notice",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "To ensure Smart Auto Reply captures messages and runs reliably in the background, please grant the following permissions. Polled real-time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Permission Items List
            items(permissions, key = { it.id }) { item ->
                PermissionCard(
                    item = item,
                    onGrantClick = {
                        viewModel.grantPermission(
                            item = item,
                            onLaunchIntent = launchIntent,
                            onRequestPostNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                    },
                    onOpenSettingsClick = {
                        viewModel.openSettingsPage(item, launchIntent)
                    }
                )
            }
        }
    }
}

@Composable
fun ManufacturerInfoCard(permissionManager: PermissionManager) {
    val manufacturer = permissionManager.getDeviceManufacturer()
    val brand = permissionManager.getDeviceBrand()

    val (deviceLogo, deviceType) = when {
        manufacturer.contains("samsung") -> Icons.Default.PhoneAndroid to "Samsung Device Detected"
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> Icons.Default.Smartphone to "Xiaomi/Redmi Device Detected"
        manufacturer.contains("oppo") -> Icons.Default.Smartphone to "Oppo Device Detected"
        manufacturer.contains("realme") -> Icons.Default.Smartphone to "Realme Device Detected"
        manufacturer.contains("vivo") -> Icons.Default.Smartphone to "Vivo Device Detected"
        manufacturer.contains("motorola") || manufacturer.contains("lenovo") -> Icons.Default.PhoneAndroid to "Motorola/Lenovo Device Detected"
        manufacturer.contains("nothing") -> Icons.Default.Smartphone to "Nothing Phone Detected"
        manufacturer.contains("google") || manufacturer.contains("pixel") -> Icons.Default.Verified to "Google Pixel Device Detected"
        else -> Icons.Default.Devices to "Unknown/Generic Device Detected"
    }

    val warningMsg = when {
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
            "Xiaomi devices restrict background services strictly. Enabling Autostart is highly recommended."
        manufacturer.contains("samsung") ->
            "Samsung Device care puts inactive background apps to sleep. Please exclude this app from sleeping limits."
        manufacturer.contains("oppo") || manufacturer.contains("realme") ->
            "Oppo/Realme requires Manual App Startup toggle on to keep reply service running."
        manufacturer.contains("vivo") ->
            "Vivo background restrictions might block automatic replies. Ensure High Background Power Consumption is authorized."
        manufacturer.contains("motorola") || manufacturer.contains("lenovo") ->
            "Motorola background processing optimization can stop active reply listeners. Turn battery savers off."
        manufacturer.contains("nothing") ->
            "Ensure background activity is unrestricted in Nothing battery settings."
        manufacturer.contains("google") || manufacturer.contains("pixel") ->
            "Stock Android detected. Ensure Adaptive Battery optimization isn't restricting foreground tasks."
        else -> "Manufacturer-specific background restrictions may terminate active tasks."
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("manufacturer_info_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = deviceLogo,
                    contentDescription = "Device logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = deviceType,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Model: ${brand.uppercase()} (${manufacturer.uppercase()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = warningMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun PermissionCard(
    item: PermissionItem,
    onGrantClick: () -> Unit,
    onOpenSettingsClick: () -> Unit
) {
    val (statusText, cardColor, strokeColor, statusIcon) = when (item.status) {
        PermissionStatus.GRANTED -> {
            Quad(
                "Granted",
                Color(0xFFE8F5E9), // Light green background
                Color(0xFF2E7D32), // Dark green stroke
                Icons.Default.CheckCircle
            )
        }
        PermissionStatus.RESTRICTED -> {
            Quad(
                "Restricted / Action Recommended",
                Color(0xFFFFFDE7), // Light yellow background
                Color(0xFFFBC02D), // Dark yellow stroke
                Icons.Default.Warning
            )
        }
        PermissionStatus.NOT_GRANTED -> {
            Quad(
                "Not Granted",
                Color(0xFFFFEBEE), // Light red background
                Color(0xFFC62828), // Dark red stroke
                Icons.Default.Cancel
            )
        }
    }

    val itemIcon = when (item.id) {
        "notification" -> Icons.Default.Notifications
        "accessibility" -> Icons.Default.Accessibility
        "overlay" -> Icons.Default.Layers
        "battery" -> Icons.Default.BatteryAlert
        "foreground" -> Icons.Default.PlayArrow
        "autostart" -> Icons.Default.SettingsPower
        "background_restriction" -> Icons.Default.Block
        else -> Icons.Default.Settings
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("permission_card_${item.id}"),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        border = BorderStroke(1.5.dp, strokeColor.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: Icon, Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = itemIcon,
                        contentDescription = item.title,
                        tint = strokeColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = statusText,
                            tint = strokeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = strokeColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body: Description & Details
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.DarkGray
            )

            if (item.details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Status: ${item.details}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Actions: Grant Button and Open Settings Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Open Android Settings Button
                OutlinedButton(
                    onClick = onOpenSettingsClick,
                    border = BorderStroke(1.dp, strokeColor),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = strokeColor
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_settings_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Open Settings",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Settings",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Grant Button (Main Action)
                Button(
                    onClick = onGrantClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = strokeColor,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("btn_grant_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = "Grant Permission",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (item.status == PermissionStatus.GRANTED) "Verify / Check" else "Grant",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// Utility class to make quadruple returns easy and descriptive
data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
