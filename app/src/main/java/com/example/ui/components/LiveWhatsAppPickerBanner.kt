package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ContactGroup
import com.example.util.WhatsAppPickerCoordinator
import com.example.util.WhatsAppPickerMode
import com.example.util.WhatsAppPickerState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveWhatsAppPickerBanner(
    pickerState: WhatsAppPickerState,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!pickerState.isActive) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_whatsapp_picker_banner"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TouchApp,
                        contentDescription = "Live WhatsApp Picker active",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "WhatsApp Live Picker Active (${pickerState.capturedTargets.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Mode: ${pickerState.mode.label} • Tap chats/groups in WhatsApp then commit here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cancel picker session",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (pickerState.capturedTargets.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    pickerState.capturedTargets.take(12).forEach { target ->
                        InputChip(
                            selected = true,
                            onClick = { WhatsAppPickerCoordinator.removeCaptured(target.dedupeKey) },
                            label = { Text(target.name, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (target.isGroup) Icons.Filled.Group else Icons.Filled.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove target",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onCommit,
                    enabled = pickerState.capturedTargets.isNotEmpty(),
                    modifier = Modifier.testTag("commit_whatsapp_picker_button")
                ) {
                    Text("Import ${pickerState.capturedTargets.size} Captured")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LaunchWhatsAppPickerDialog(
    groups: List<ContactGroup>,
    accessibilityEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onDismiss: () -> Unit,
    onLaunch: (WhatsAppPickerMode, Long) -> Unit
) {
    var mode by remember { mutableStateOf(WhatsAppPickerMode.CONTACTS_AND_GROUPS) }
    var targetGroupId by remember(groups) { mutableStateOf(groups.firstOrNull()?.id ?: 0L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.TouchApp,
                contentDescription = "Direct WhatsApp Multi-Picker",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = "Pick Directly from WhatsApp", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Opens your selected WhatsApp / Business / Clone instance. Simply tap as many individual chats and WhatsApp groups as you want — each tap is captured and deduplicated live.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!accessibilityEnabled) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Accessibility Service Required",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Enable Mass WA Broadcast Auto-Sender so tapped chats & groups inside WhatsApp are captured automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            OutlinedButton(onClick = onOpenAccessibilitySettings) {
                                Text("Enable Accessibility (1-Tap)")
                            }
                        }
                    }
                }

                Text(text = "Capture Mode", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WhatsAppPickerMode.entries.forEach { option ->
                        FilterChip(
                            selected = mode == option,
                            onClick = { mode = option },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("wa_picker_mode_${option.name.lowercase()}")
                        )
                    }
                }

                if (groups.isNotEmpty()) {
                    Text(text = "Save Captured Targets To Group", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        groups.forEach { g ->
                            FilterChip(
                                selected = targetGroupId == g.id,
                                onClick = { targetGroupId = g.id },
                                label = { Text(g.name) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onLaunch(mode, targetGroupId) },
                modifier = Modifier.testTag("launch_wa_picker_confirm")
            ) {
                Text("Open WhatsApp Picker")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
