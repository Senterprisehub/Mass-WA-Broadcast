package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Campaign
import com.example.data.model.CampaignStatus
import com.example.data.model.DeliveryStatus
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusAmberContainerDark
import com.example.ui.theme.StatusAmberContainerLight
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusCrimsonContainerDark
import com.example.ui.theme.StatusCrimsonContainerLight
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusEmeraldContainerDark
import com.example.ui.theme.StatusEmeraldContainerLight
import com.example.util.DateUtils
import com.example.util.MessageVariableParser

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun MetricStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
fun CampaignStatusBadge(
    status: CampaignStatus,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val (bg, fg) = when (status) {
        CampaignStatus.COMPLETED -> (if (isDark) StatusEmeraldContainerDark else StatusEmeraldContainerLight) to StatusEmerald
        CampaignStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        CampaignStatus.SCHEDULED, CampaignStatus.QUEUED -> (if (isDark) StatusAmberContainerDark else StatusAmberContainerLight) to StatusAmber
        CampaignStatus.FAILED -> (if (isDark) StatusCrimsonContainerDark else StatusCrimsonContainerLight) to StatusCrimson
        CampaignStatus.PAUSED, CampaignStatus.DRAFT -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(fg)
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = fg
            )
        }
    }
}

@Composable
fun DeliveryStatusBadge(
    status: DeliveryStatus,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val (bg, fg) = when (status) {
        DeliveryStatus.SENT, DeliveryStatus.DELIVERED, DeliveryStatus.READ ->
            (if (isDark) StatusEmeraldContainerDark else StatusEmeraldContainerLight) to StatusEmerald
        DeliveryStatus.PENDING ->
            (if (isDark) StatusAmberContainerDark else StatusAmberContainerLight) to StatusAmber
        DeliveryStatus.FAILED ->
            (if (isDark) StatusCrimsonContainerDark else StatusCrimsonContainerLight) to StatusCrimson
        DeliveryStatus.SKIPPED ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = bg
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun CampaignCard(
    campaign: Campaign,
    onClick: () -> Unit,
    onQuickLaunch: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val progress = if (campaign.totalRecipients > 0) {
        ((campaign.sentCount + campaign.failedCount).toFloat() / campaign.totalRecipients).coerceIn(0f, 1f)
    } else {
        0f
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("campaign_card_${campaign.id}"),
        shape = MaterialTheme.shapes.medium,
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
                Text(
                    text = campaign.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                CampaignStatusBadge(status = campaign.status)
            }

            Text(
                text = campaign.messageTemplate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${campaign.sentCount}/${campaign.totalRecipients} sent",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (campaign.failedCount > 0) {
                    Text(
                        text = "${campaign.failedCount} failed",
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusCrimson
                    )
                }
                if (!campaign.mediaUri.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Filled.AttachFile,
                        contentDescription = "Media attached",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (campaign.scheduledTimeEpoch > 0L) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = "Scheduled time",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = DateUtils.formatDateTime(campaign.scheduledTimeEpoch),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (campaign.totalRecipients > 0 && campaign.status != CampaignStatus.DRAFT) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = if (campaign.status == CampaignStatus.FAILED) StatusCrimson else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            if (onQuickLaunch != null &&
                (campaign.status == CampaignStatus.DRAFT || campaign.status == CampaignStatus.PAUSED || campaign.status == CampaignStatus.SCHEDULED)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    AssistChip(
                        onClick = onQuickLaunch,
                        label = {
                            Text(
                                text = if (campaign.status == CampaignStatus.PAUSED) "Resume" else "Launch Now",
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Launch campaign",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VariableChipRow(
    onInsertVariable: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MessageVariableParser.SUPPORTED_VARIABLES.forEach { token ->
            AssistChip(
                onClick = { onInsertVariable(token) },
                label = {
                    Text(
                        text = token,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                modifier = Modifier.testTag("var_chip_$token")
            )
        }
    }
}

@Composable
fun WhatsAppPreviewCard(
    previewText: String,
    hasMedia: Boolean,
    targetInstanceLabel: String,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MESSAGE PREVIEW",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = targetInstanceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Surface(
                shape = RoundedCornerShape(
                    topStart = 14.dp,
                    topEnd = 4.dp,
                    bottomStart = 14.dp,
                    bottomEnd = 14.dp
                ),
                color = if (isDark) Color(0xFF1E1B4B) else Color(0xFFEEF2FF),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (hasMedia) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AttachFile,
                                contentDescription = "Media preview",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Attached Media Staged (FileProvider)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = previewText.ifBlank { "Type your campaign message above to preview variable resolution..." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun HealthStatusIcon(ok: Boolean, contentDescription: String) {
    Icon(
        imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
        contentDescription = contentDescription,
        tint = if (ok) StatusEmerald else StatusCrimson,
        modifier = Modifier.size(20.dp)
    )
}
