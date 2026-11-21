package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.example.data.model.Campaign
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald

@Composable
fun PerformanceChartCard(
    campaigns: List<Campaign>,
    modifier: Modifier = Modifier
) {
    val recent = campaigns.take(7).reversed()
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "RECENT DELIVERY THROUGHPUT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Last ${recent.size} campaigns",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (recent.isEmpty()) {
                Text(
                    text = "Launch your first broadcast to track real-time sent vs. failed throughput here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxVal = recent.maxOf { maxOf(it.totalRecipients, 1) }.toFloat()
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    val slotWidth = size.width / recent.size
                    val barWidth = (slotWidth * 0.48f).coerceAtMost(36.dp.toPx())
                    val radius = CornerRadius(6.dp.toPx(), 6.dp.toPx())

                    recent.forEachIndexed { idx, campaign ->
                        val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                        // Background track bar
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(left, 0f),
                            size = Size(barWidth, size.height),
                            cornerRadius = radius
                        )
                        val sentRatio = (campaign.sentCount / maxVal).coerceIn(0f, 1f)
                        val failRatio = (campaign.failedCount / maxVal).coerceIn(0f, 1f)
                        val sentH = size.height * sentRatio
                        val failH = size.height * failRatio

                        if (sentH > 0f) {
                            drawRoundRect(
                                color = StatusEmerald,
                                topLeft = Offset(left, size.height - sentH),
                                size = Size(barWidth, sentH),
                                cornerRadius = radius
                            )
                        }
                        if (failH > 0f) {
                            drawRoundRect(
                                color = StatusCrimson,
                                topLeft = Offset(left, (size.height - sentH - failH).coerceAtLeast(0f)),
                                size = Size(barWidth, failH),
                                cornerRadius = radius
                            )
                        }
                        if (sentH == 0f && failH == 0f) {
                            val totalH = (size.height * (campaign.totalRecipients / maxVal)).coerceAtLeast(8f)
                            drawRoundRect(
                                color = primaryColor.copy(alpha = 0.45f),
                                topLeft = Offset(left, size.height - totalH),
                                size = Size(barWidth, totalH),
                                cornerRadius = radius
                            )
                        }
                    }
                }
            }
        }
    }
}
