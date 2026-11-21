package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BroadcastLogEntry
import com.example.data.model.Campaign
import com.example.data.model.CampaignStatus
import com.example.data.model.DeliveryStatus
import com.example.ui.components.CampaignStatusBadge
import com.example.ui.components.DeliveryStatusBadge
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.util.DateUtils
import com.example.util.MessageVariableParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignDetailScreen(
    campaign: Campaign?,
    logs: List<BroadcastLogEntry>,
    onBack: () -> Unit,
    onLaunchNow: (Long) -> Unit,
    onCancelQueued: (Long) -> Unit,
    onDeleteCampaign: (Campaign) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var selectedFilter by remember { mutableStateOf<DeliveryStatus?>(null) }
    val filteredLogs = remember(logs, selectedFilter) {
        if (selectedFilter == null) logs else logs.filter { it.status == selectedFilter }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = campaign?.title ?: "Campaign Details",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("campaign_detail_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (campaign != null) {
                    IconButton(
                        onClick = {
                            onDeleteCampaign(campaign)
                            onBack()
                        },
                        modifier = Modifier.testTag("campaign_detail_delete")
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete campaign", tint = StatusCrimson)
                    }
                }
            }
        )

        if (campaign == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Campaign not found", style = MaterialTheme.typography.titleMedium)
            }
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("campaign_detail_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CampaignStatusBadge(status = campaign.status)
                            Text(
                                text = "${campaign.targetPackage} • ${campaign.delaySeconds}s delay",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = campaign.messageTemplate,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("Total: ${campaign.totalRecipients}", style = MaterialTheme.typography.labelMedium)
                            Text("Sent: ${campaign.sentCount}", style = MaterialTheme.typography.labelMedium, color = StatusEmerald)
                            Text("Failed: ${campaign.failedCount}", style = MaterialTheme.typography.labelMedium, color = StatusCrimson)
                        }

                        if (campaign.scheduledTimeEpoch > 0L) {
                            Text(
                                text = "Scheduled: ${DateUtils.formatDateTime(campaign.scheduledTimeEpoch)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!campaign.failureDiagnostic.isNullOrBlank()) {
                            Text(
                                text = "Diagnostic: ${campaign.failureDiagnostic}",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusCrimson
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (campaign.status == CampaignStatus.QUEUED) {
                                OutlinedButton(onClick = { onCancelQueued(campaign.id) }) {
                                    Text("Cancel from FIFO Queue")
                                }
                            } else if (campaign.status != CampaignStatus.RUNNING) {
                                Button(
                                    onClick = { onLaunchNow(campaign.id) },
                                    modifier = Modifier.testTag("detail_launch_campaign_btn")
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (campaign.status == CampaignStatus.PAUSED) "Resume Dispatch" else "Launch / Relaunch")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("All (${logs.size})") }
                    )
                    DeliveryStatus.entries.forEach { st ->
                        val count = logs.count { it.status == st }
                        if (count > 0) {
                            FilterChip(
                                selected = selectedFilter == st,
                                onClick = { selectedFilter = if (selectedFilter == st) null else st },
                                label = { Text("${st.label} ($count)") }
                            )
                        }
                    }
                }
            }

            items(filteredLogs, key = { it.id }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (entry.isGroup) Icons.Filled.Group else Icons.Filled.Person,
                            contentDescription = if (entry.isGroup) "WhatsApp group" else "Contact",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.contactName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (entry.isGroup) "WhatsApp Group" else MessageVariableParser.formatDisplayPhone(entry.phoneNumber),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!entry.errorMessage.isNullOrBlank()) {
                                Text(
                                    text = entry.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusCrimson
                                )
                            }
                        }
                        DeliveryStatusBadge(status = entry.status)
                    }
                }
            }
        }
    }
}
