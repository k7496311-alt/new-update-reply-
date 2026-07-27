package com.example.verification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionVerificationScreen(
    viewModel: ProductionVerificationViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val reportState by viewModel.verificationReport.collectAsState()
    val isExecuting by viewModel.isExecuting.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Verification Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Production Verification",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.runVerification() },
                        enabled = !isExecuting,
                        modifier = Modifier.testTag("run_verification_button")
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Rerun Verification")
                        }
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        val report = reportState

        if (isExecuting && report == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Running Automated Production Verification...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (report != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    VerdictHeaderCard(report)
                }

                item {
                    Text(
                        text = "Module Results (${report.totalTests} Verified)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(report.moduleResults, key = { it.id }) { moduleResult ->
                    ModuleResultCard(moduleResult)
                }
            }
        }
    }
}

@Composable
private fun VerdictHeaderCard(report: FullVerificationReport) {
    val (bgColor, textColor, icon) = when (report.overallVerdict) {
        VerificationStatus.PASS -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Icons.Default.CheckCircle)
        VerificationStatus.WARNING -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), Icons.Default.Warning)
        VerificationStatus.FAIL -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Icons.Default.Error)
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("verdict_header_card"),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = icon, contentDescription = null, tint = textColor, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VERDICT: ${report.overallVerdict}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = textColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricChip(label = "PASSED", count = report.passCount, color = Color(0xFF2E7D32))
                MetricChip(label = "WARNINGS", count = report.warningCount, color = Color(0xFFE65100))
                MetricChip(label = "FAILED", count = report.failCount, color = Color(0xFFC62828))
            }

            if (report.failedModules.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Failed Modules:",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFC62828)
                )
                report.failedModules.forEach { failedName ->
                    Text(
                        text = "• $failedName",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC62828),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, count: Int, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
        }
    }
}

@Composable
private fun ModuleResultCard(result: ModuleVerificationResult) {
    val (statusColor, statusBg) = when (result.status) {
        VerificationStatus.PASS -> Pair(Color(0xFF2E7D32), Color(0xFFE8F5E9))
        VerificationStatus.WARNING -> Pair(Color(0xFFE65100), Color(0xFFFFF3E0))
        VerificationStatus.FAIL -> Pair(Color(0xFFC62828), Color(0xFFFFEBEE))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "✔ ",
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = result.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Box(
                    modifier = Modifier
                        .background(statusBg, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = result.status.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = result.details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (result.failureReason != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Failure Reason: ${result.failureReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC62828),
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Execution time: ${result.durationMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
