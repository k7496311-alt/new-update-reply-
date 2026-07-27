package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DebugDashboardState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugDashboardScreen(
    viewModel: DebugDashboardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Debug Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Debug Dashboard",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.simulateTestExecution() },
                        modifier = Modifier.testTag("debug_test_sim_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Test Simulation")
                    }
                    IconButton(
                        onClick = { viewModel.resetDashboard() },
                        modifier = Modifier.testTag("debug_reset_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset State")
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Debug Mode Status Banner
            DebugModeBannerCard(
                isDebugEnabled = state.isDebugModeEnabled,
                lastUpdated = state.lastUpdatedTimestamp,
                onToggleDebug = { viewModel.toggleDebugMode(it) }
            )

            if (state.isDebugModeEnabled) {
                // Section 1: Execution Pipeline Overview
                ExecutionOverviewCard(state)

                // Section 2: Rule & Message Processing
                MessageRuleCard(state)

                // Section 3: Status & Accessibility Metrics
                StatusMetricsCard(state)

                // Section 4: Diagnostics & Logs
                DiagnosticsLogCard(state)
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Debug Mode Disabled",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Enable debug mode above to monitor real-time queue, step execution, and accessibility node counts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DebugModeBannerCard(
    isDebugEnabled: Boolean,
    lastUpdated: Long,
    onToggleDebug: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("debug_banner_card"),
        colors = CardDefaults.cardColors(
            containerColor = if (isDebugEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isDebugEnabled) "Debug Mode Active" else "Debug Mode Standby",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isDebugEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                val formattedTime = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(lastUpdated))
                Text(
                    text = "Last updated: $formattedTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Switch(
                checked = isDebugEnabled,
                onCheckedChange = onToggleDebug,
                modifier = Modifier.testTag("debug_mode_switch")
            )
        }
    }
}

@Composable
private fun ExecutionOverviewCard(state: DebugDashboardState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Queue & Execution Pipeline",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Divider(modifier = Modifier.padding(vertical = 12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DebugMetricItem(label = "Current Queue", value = "${state.currentQueueCount} item(s)")
                DebugMetricItem(label = "Current Step", value = state.currentStep, highlight = true)
            }

            Spacer(modifier = Modifier.height(12.dp))

            DebugMetricItem(label = "Current Customer", value = state.currentCustomer)
            Spacer(modifier = Modifier.height(8.dp))
            DebugMetricItem(label = "Current Chat Target", value = state.currentChat)
        }
    }
}

@Composable
private fun MessageRuleCard(state: DebugDashboardState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Message & Rule Engine",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Divider(modifier = Modifier.padding(vertical = 12.dp))

            Text(
                text = "Last Read Messages:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
            if (state.lastReadMessages.isEmpty()) {
                Text(
                    text = "None read yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                state.lastReadMessages.forEach { msg ->
                    Text(
                        text = "• $msg",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            DebugMetricItem(label = "Matched Rule", value = state.matchedRule)

            Spacer(modifier = Modifier.height(12.dp))
            DebugMetricItem(label = "Generated Reply", value = state.generatedReply)
        }
    }
}

@Composable
private fun StatusMetricsCard(state: DebugDashboardState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Action & Accessibility Status",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Divider(modifier = Modifier.padding(vertical = 12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatusBadge(label = "Insert Status", status = state.insertStatus)
                StatusBadge(label = "Send Status", status = state.sendStatus)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DebugMetricItem(label = "Accessibility Service", value = state.accessibilityStatus)
                DebugMetricItem(label = "Scanned Node Count", value = "${state.nodeCount} nodes")
            }
        }
    }
}

@Composable
private fun DiagnosticsLogCard(state: DebugDashboardState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Diagnostics & Logs",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.error
            )
            Divider(modifier = Modifier.padding(vertical = 12.dp))

            Text(
                text = "Latest Error:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = state.latestError,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (state.latestError != "None") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Latest System Log:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = state.latestLog,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun DebugMetricItem(label: String, value: String, highlight: Boolean = false) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

@Composable
private fun StatusBadge(label: String, status: String) {
    val bgColor = when (status.uppercase()) {
        "SUCCESS", "SENT", "CONNECTED" -> Color(0xFFE8F5E9)
        "FAILED", "ERROR" -> Color(0xFFFFEBEE)
        else -> Color(0xFFFFF3E0)
    }
    val textColor = when (status.uppercase()) {
        "SUCCESS", "SENT", "CONNECTED" -> Color(0xFF2E7D32)
        "FAILED", "ERROR" -> Color(0xFFC62828)
        else -> Color(0xFFE65100)
    }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .background(bgColor, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = textColor
            )
        }
    }
}
