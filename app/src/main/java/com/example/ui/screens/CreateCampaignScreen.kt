package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.Contact
import com.example.data.model.ContactGroup
import com.example.data.model.DailyPacingEstimate
import com.example.data.model.MessageTemplate
import com.example.data.model.WhatsAppInstance
import com.example.ui.components.LiveWhatsAppPickerBanner
import com.example.ui.components.ScheduleDateTimePickerSection
import com.example.ui.components.VariableChipRow
import com.example.ui.components.WhatsAppPreviewCard
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCrimson
import com.example.util.DateUtils
import com.example.util.MessageVariableParser
import com.example.util.WhatsAppPickerState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateCampaignScreen(
    groups: List<ContactGroup>,
    contacts: List<Contact>,
    templates: List<MessageTemplate>,
    whatsAppInstances: List<WhatsAppInstance>,
    defaultWhatsAppPackage: String = "com.whatsapp",
    defaultWhatsAppIsClone: Boolean = false,
    defaultDelaySeconds: Int,
    pacingEstimate: DailyPacingEstimate,
    pickerState: WhatsAppPickerState,
    initialTemplateText: String = "",
    onBack: () -> Unit,
    onOpenPhonebookPicker: () -> Unit,
    onOpenWhatsAppPicker: () -> Unit,
    onCommitWhatsAppPicker: () -> Unit,
    onCancelWhatsAppPicker: () -> Unit,
    onStageMediaUri: (Uri, (String?) -> Unit) -> Unit,
    onRefreshPacing: (Int, Int, Long) -> Unit,
    onSubmitCampaign: (
        title: String,
        message: String,
        groupIds: List<Long>,
        explicitContacts: List<Contact>,
        scheduledEpoch: Long,
        delaySeconds: Int,
        mediaPath: String?,
        targetInstance: WhatsAppInstance,
        launchNow: Boolean,
        saveAsDraft: Boolean
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var title by remember { mutableStateOf("") }
    var messageTemplate by remember(initialTemplateText) { mutableStateOf(initialTemplateText) }
    var selectedGroupIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedContactIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var delaySlider by remember(defaultDelaySeconds) { mutableFloatStateOf(defaultDelaySeconds.toFloat()) }
    var scheduleEnabled by remember { mutableStateOf(false) }
    var scheduledEpoch by remember { mutableLongStateOf(System.currentTimeMillis() + 15L * 60L * 1000L) }
    var stagedMediaPath by remember { mutableStateOf<String?>(null) }
    var selectedInstance by remember(whatsAppInstances, defaultWhatsAppPackage, defaultWhatsAppIsClone) {
        mutableStateOf(
            whatsAppInstances.firstOrNull {
                it.packageName == defaultWhatsAppPackage && it.isClone == defaultWhatsAppIsClone
            } ?: whatsAppInstances.firstOrNull {
                it.packageName == defaultWhatsAppPackage
            } ?: whatsAppInstances.firstOrNull() ?: WhatsAppInstance(
                packageName = defaultWhatsAppPackage,
                label = if (defaultWhatsAppIsClone) "WhatsApp (2nd Clone / Dual App)" else "WhatsApp (Primary)",
                isClone = defaultWhatsAppIsClone
            )
        )
    }

    // Zero-permission Android Photo Picker (Play Policy compliant)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onStageMediaUri(uri) { stagedPath ->
                stagedMediaPath = stagedPath
            }
        }
    }

    val chosenGroupContacts = remember(contacts, selectedGroupIds) {
        contacts.filter { it.groupId in selectedGroupIds && it.isOptedIn }
    }
    val chosenExplicitContacts = remember(contacts, selectedContactIds) {
        contacts.filter { it.id in selectedContactIds }
    }
    val totalUniqueRecipients = remember(chosenGroupContacts, chosenExplicitContacts) {
        (chosenGroupContacts + chosenExplicitContacts)
            .distinctBy { if (it.isGroup) "g:${it.name.lowercase()}" else "p:${it.phoneNumber}" }
            .size
    }

    val sampleContact = remember(chosenGroupContacts, chosenExplicitContacts, contacts) {
        chosenExplicitContacts.firstOrNull()
            ?: chosenGroupContacts.firstOrNull()
            ?: contacts.firstOrNull()
            ?: Contact(name = "Aarav Sharma", phoneNumber = "919876543210", company = "Sharma Textiles", customTag = "VIP")
    }
    val previewResolved = remember(messageTemplate, sampleContact) {
        MessageVariableParser.resolveTemplate(messageTemplate, sampleContact)
    }

    LaunchedEffect(totalUniqueRecipients, delaySlider, scheduleEnabled, scheduledEpoch) {
        onRefreshPacing(
            totalUniqueRecipients,
            delaySlider.roundToInt(),
            if (scheduleEnabled) scheduledEpoch else System.currentTimeMillis()
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Create Broadcast", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("create_campaign_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("create_campaign_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (pickerState.isActive) {
                item {
                    LiveWhatsAppPickerBanner(
                        pickerState = pickerState,
                        onCommit = onCommitWhatsAppPicker,
                        onCancel = onCancelWhatsAppPicker
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Campaign Title") },
                    placeholder = { Text("e.g. Diwali VIP Offer Blast") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("campaign_title_input")
                )
            }

            // Target WhatsApp / Business / Clone Instance
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Dispatch Account (WhatsApp / Business / Clone)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        whatsAppInstances.forEach { instance ->
                            FilterChip(
                                selected = selectedInstance.stableId == instance.stableId,
                                onClick = { selectedInstance = instance },
                                label = { Text(instance.label) },
                                modifier = Modifier.testTag("target_wa_${instance.packageName}_${instance.userSerial}")
                            )
                        }
                    }
                }
            }

            // Audience Picker Section (Groups + Phonebook + Direct WhatsApp Picker + Individual contacts/groups)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Audience Selection", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "$totalUniqueRecipients recipients",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onOpenPhonebookPicker,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("composer_phonebook_btn")
                            ) {
                                Icon(Icons.Filled.Contacts, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Phonebook")
                            }
                            OutlinedButton(
                                onClick = onOpenWhatsAppPicker,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("composer_wa_picker_btn")
                            ) {
                                Icon(Icons.Filled.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("WhatsApp Picker")
                            }
                        }

                        if (groups.isNotEmpty()) {
                            Text(
                                text = "Select Contact Groups",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                groups.forEach { group ->
                                    val count = contacts.count { it.groupId == group.id }
                                    FilterChip(
                                        selected = group.id in selectedGroupIds,
                                        onClick = {
                                            selectedGroupIds = if (group.id in selectedGroupIds) {
                                                selectedGroupIds - group.id
                                            } else {
                                                selectedGroupIds + group.id
                                            }
                                        },
                                        label = { Text("${group.name} ($count)") }
                                    )
                                }
                            }
                        }

                        if (contacts.isNotEmpty()) {
                            Text(
                                text = "Or Pick Individual Contacts / WhatsApp Groups",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                contacts.take(30).forEach { contact ->
                                    FilterChip(
                                        selected = contact.id in selectedContactIds,
                                        onClick = {
                                            selectedContactIds = if (contact.id in selectedContactIds) {
                                                selectedContactIds - contact.id
                                            } else {
                                                selectedContactIds + contact.id
                                            }
                                        },
                                        label = {
                                            Text(
                                                if (contact.isGroup) "[Group] ${contact.name}" else contact.name
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Message Composer + Templates + Variables + Photo Picker Media
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (templates.isNotEmpty()) {
                        Text("Quick Apply Template", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            templates.forEach { tmpl ->
                                FilterChip(
                                    selected = false,
                                    onClick = { messageTemplate = tmpl.content },
                                    label = { Text(tmpl.title) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = messageTemplate,
                        onValueChange = { messageTemplate = it },
                        label = { Text("Broadcast Message") },
                        placeholder = { Text("Namaste {{first_name}}! Write your personalized message...") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("campaign_message_input")
                    )

                    VariableChipRow(
                        onInsertVariable = { token ->
                            messageTemplate = if (messageTemplate.isEmpty() || messageTemplate.endsWith(" ")) {
                                "$messageTemplate$token"
                            } else {
                                "$messageTemplate $token"
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            modifier = Modifier.testTag("attach_media_button")
                        ) {
                            Icon(Icons.Filled.AttachFile, contentDescription = "Attach media", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (stagedMediaPath == null) "Attach Photo / Video" else "Replace Media")
                        }
                        if (stagedMediaPath != null) {
                            IconButton(onClick = { stagedMediaPath = null }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove media")
                            }
                        }
                    }

                    WhatsAppPreviewCard(
                        previewText = previewResolved,
                        hasMedia = stagedMediaPath != null,
                        targetInstanceLabel = selectedInstance.label
                    )
                }
            }

            // Anti-Ban Delay, Scheduling & 24h Pacing Advisor
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Anti-Ban Delay Between Messages", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${delaySlider.roundToInt()}s",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = delaySlider,
                            onValueChange = { delaySlider = it },
                            valueRange = 2f..30f,
                            steps = 27
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Schedule, contentDescription = "Schedule toggle", modifier = Modifier.size(18.dp))
                                Column {
                                    Text("Schedule for Later", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = if (scheduleEnabled) DateUtils.formatDateTime(scheduledEpoch) else "Launch immediately when ready",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = scheduleEnabled,
                                onCheckedChange = { scheduleEnabled = it },
                                modifier = Modifier.testTag("schedule_toggle_switch")
                            )
                        }

                        if (scheduleEnabled) {
                            ScheduleDateTimePickerSection(
                                scheduledEpoch = scheduledEpoch,
                                onScheduledEpochChange = { scheduledEpoch = it }
                            )
                        }

                        // 24h Daily Pacing Advisor
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    pacingEstimate.isOverCapacity -> MaterialTheme.colorScheme.errorContainer
                                    pacingEstimate.isHeavyLoad || pacingEstimate.overlappingCampaignTitles.isNotEmpty() ->
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WarningAmber,
                                    contentDescription = "24h daily pacing advisor",
                                    tint = when {
                                        pacingEstimate.isOverCapacity -> StatusCrimson
                                        pacingEstimate.isHeavyLoad -> StatusAmber
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "24h Pacing Estimate: ${DateUtils.formatDurationSeconds(pacingEstimate.estimatedTotalSeconds)} (${pacingEstimate.totalSameDayRecipients} msgs today)",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    if (pacingEstimate.overlappingCampaignTitles.isNotEmpty()) {
                                        Text(
                                            text = "Overlaps with ${pacingEstimate.overlappingCampaignTitles.joinToString()} — will serialize safely in FIFO queue.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else if (pacingEstimate.isOverCapacity) {
                                        Text(
                                            text = "Exceeds 24h daily runtime. Split audience across multiple days.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = StatusCrimson
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Submit Buttons
            item {
                val scheduleValid = !scheduleEnabled || scheduledEpoch > System.currentTimeMillis()
                val canSubmit = messageTemplate.isNotBlank() && totalUniqueRecipients > 0 && scheduleValid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onSubmitCampaign(
                                title,
                                messageTemplate,
                                selectedGroupIds.toList(),
                                chosenExplicitContacts,
                                if (scheduleEnabled) scheduledEpoch else 0L,
                                delaySlider.roundToInt(),
                                stagedMediaPath,
                                selectedInstance,
                                false,
                                true
                            )
                        },
                        enabled = messageTemplate.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("save_draft_button")
                    ) {
                        Text("Save Draft")
                    }

                    Button(
                        onClick = {
                            onSubmitCampaign(
                                title,
                                messageTemplate,
                                selectedGroupIds.toList(),
                                chosenExplicitContacts,
                                if (scheduleEnabled) scheduledEpoch else 0L,
                                delaySlider.roundToInt(),
                                stagedMediaPath,
                                selectedInstance,
                                !scheduleEnabled,
                                false
                            )
                        },
                        enabled = canSubmit,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(50.dp)
                            .testTag("submit_campaign_button")
                    ) {
                        Icon(
                            imageVector = if (scheduleEnabled) Icons.Filled.Schedule else Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (scheduleEnabled) "Schedule Broadcast" else "Launch Now")
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
