package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.Campaign
import com.example.data.model.CampaignStatus
import com.example.ui.components.CampaignCard
import com.example.ui.components.SectionHeader

@Composable
fun CampaignsScreen(
    campaigns: List<Campaign>,
    onCreateCampaign: () -> Unit,
    onOpenCampaignDetail: (Long) -> Unit,
    onLaunchCampaign: (Long) -> Unit,
    onCancelQueuedCampaign: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf<CampaignStatus?>(null) }

    val filteredCampaigns by remember(campaigns, searchQuery, selectedStatus) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            campaigns.filter { c ->
                val statusOk = selectedStatus == null || c.status == selectedStatus
                val queryOk = q.isEmpty() ||
                    c.title.lowercase().contains(q) ||
                    c.messageTemplate.lowercase().contains(q)
                statusOk && queryOk
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("campaigns_screen"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader(
                    title = "Campaigns",
                    subtitle = "${campaigns.size} broadcasts • Rolling 48h scheduler & FIFO queue"
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search campaigns or message text...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search campaigns") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("campaigns_search_input")
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedStatus == null,
                        onClick = { selectedStatus = null },
                        label = { Text("All (${campaigns.size})") }
                    )
                    CampaignStatus.entries.forEach { status ->
                        val count = campaigns.count { it.status == status }
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = if (selectedStatus == status) null else status },
                            label = { Text("${status.label} ($count)") },
                            modifier = Modifier.testTag("campaign_filter_${status.name.lowercase()}")
                        )
                    }
                }
            }

            if (filteredCampaigns.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No campaigns match this filter",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Tap the + button below to create an immediate or scheduled WhatsApp broadcast.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredCampaigns, key = { it.id }) { campaign ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CampaignCard(
                            campaign = campaign,
                            onClick = { onOpenCampaignDetail(campaign.id) },
                            onQuickLaunch = { onLaunchCampaign(campaign.id) }
                        )
                        if (campaign.status == CampaignStatus.QUEUED) {
                            OutlinedButton(
                                onClick = { onCancelQueuedCampaign(campaign.id) },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Remove from FIFO Queue")
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onCreateCampaign,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("campaigns_fab_create")
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Create campaign")
        }
    }
}
