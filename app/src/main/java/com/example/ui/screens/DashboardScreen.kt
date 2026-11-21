package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.Campaign
import com.example.data.model.CampaignDashboardAggregate
import com.example.ui.components.CampaignCard
import com.example.ui.components.LiveBroadcastControlCard
import com.example.ui.components.LiveWhatsAppPickerBanner
import com.example.ui.components.MetricStatCard
import com.example.ui.components.PerformanceChartCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.util.AppSettings
import com.example.util.BroadcastExecutionState
import com.example.util.SystemHealthReport
import com.example.util.WhatsAppPickerState

@Composable
fun DashboardScreen(
    aggregate: CampaignDashboardAggregate,
    contactCount: Int,
    recentCampaigns: List<Campaign>,
    executionState: BroadcastExecutionState,
    pickerState: WhatsAppPickerState,
    healthReport: SystemHealthReport,
    appSettings: AppSettings,
    onCreateCampaign: () -> Unit,
    onOpenPhonebookPicker: () -> Unit,
    onOpenWhatsAppPicker: () -> Unit,
    onCommitWhatsAppPicker: () -> Unit,
    onCancelWhatsAppPicker: () -> Unit,
    onOpenSettingsHealth: () -> Unit,
    onOpenCampaignDetail: (Long) -> Unit,
    onLaunchCampaign: (Long) -> Unit,
    onPauseExecution: () -> Unit,
    onResumeExecution: () -> Unit,
    onSkipRecipient: () -> Unit,
    onStopExecution: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Brand Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mass WA Broadcast",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Sender: ${appSettings.defaultWhatsAppLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Offline local engine",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "OFFLINE ROOM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // System Health Readiness Banner (taps directly into Settings 1-Tap Permissions Hub)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSettingsHealth)
                    .testTag("dashboard_health_banner"),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = if (healthReport.allCriticalReady) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    }
                ),
                border = BorderStroke(
                    1.dp,
                    if (healthReport.allCriticalReady) StatusEmerald.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (healthReport.allCriticalReady) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                        contentDescription = "System readiness status",
                        tint = if (healthReport.allCriticalReady) StatusEmerald else StatusAmber,
                        modifier = Modifier.size(26.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (healthReport.allCriticalReady) {
                                "Device Ready (${healthReport.criticalGatesPassed}/${healthReport.totalCriticalGates} Gates)"
                            } else {
                                "Permissions Setup Needed (${healthReport.criticalGatesPassed}/${healthReport.totalCriticalGates})"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${healthReport.oemFamily.displayName} • Tap for 1-tap permissions & vault",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Live WhatsApp Multi-Picker Banner if active
        if (pickerState.isActive) {
            item {
                LiveWhatsAppPickerBanner(
                    pickerState = pickerState,
                    onCommit = onCommitWhatsAppPicker,
                    onCancel = onCancelWhatsAppPicker
                )
            }
        }

        // Live Broadcast Execution Card if running
        if (executionState.isRunning) {
            item {
                LiveBroadcastControlCard(
                    state = executionState,
                    onPause = onPauseExecution,
                    onResume = onResumeExecution,
                    onSkip = onSkipRecipient,
                    onStop = onStopExecution
                )
            }
        }

        // Primary Quick Actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onCreateCampaign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("dashboard_create_campaign_btn")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New broadcast", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create New Broadcast Campaign")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenPhonebookPicker,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dashboard_phonebook_picker_btn")
                    ) {
                        Icon(Icons.Filled.Contacts, contentDescription = "Phonebook", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Phonebook")
                    }
                    OutlinedButton(
                        onClick = onOpenWhatsAppPicker,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dashboard_wa_picker_btn")
                    ) {
                        Icon(Icons.Filled.TouchApp, contentDescription = "WA Multi-Picker", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WA Picker")
                    }
                }
            }
        }

        // 2x2 O(1) SQL KPI Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        label = "Messages Sent",
                        value = aggregate.totalSent.toString(),
                        icon = Icons.AutoMirrored.Filled.Send,
                        accentColor = StatusEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        label = "Scheduled / Queued",
                        value = "${aggregate.scheduledCount + aggregate.queuedCount}",
                        icon = Icons.Filled.Schedule,
                        accentColor = StatusAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        label = "Audience Targets",
                        value = contactCount.toString(),
                        icon = Icons.Filled.Groups,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        label = "Failed Dispatches",
                        value = aggregate.totalFailed.toString(),
                        icon = Icons.Filled.ErrorOutline,
                        accentColor = StatusCrimson,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Performance Chart
        item {
            PerformanceChartCard(campaigns = recentCampaigns)
        }

        // Recent Campaigns Section
        item {
            SectionHeader(
                title = "Recent Campaigns",
                subtitle = "${aggregate.totalCampaigns} total campaigns stored locally"
            )
        }

        if (recentCampaigns.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No campaigns yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Import contacts from your phonebook or pick chats & groups directly from WhatsApp, then launch your first campaign.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentCampaigns.take(5), key = { it.id }) { campaign ->
                CampaignCard(
                    campaign = campaign,
                    onClick = { onOpenCampaignDetail(campaign.id) },
                    onQuickLaunch = { onLaunchCampaign(campaign.id) }
                )
            }
        }
    }
}
