package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.example.data.model.Contact
import com.example.data.model.ContactGroup
import com.example.ui.components.LiveWhatsAppPickerBanner
import com.example.ui.components.SectionHeader
import com.example.util.MessageVariableParser
import com.example.util.WhatsAppPickerState

@Composable
fun ContactsScreen(
    groups: List<ContactGroup>,
    contacts: List<Contact>,
    pickerState: WhatsAppPickerState,
    onOpenPhonebookPicker: () -> Unit,
    onOpenWhatsAppPicker: () -> Unit,
    onCommitWhatsAppPicker: () -> Unit,
    onCancelWhatsAppPicker: () -> Unit,
    onCreateGroup: (String, String) -> Unit,
    onAddManualContact: (name: String, phone: String, company: String, tag: String, groupId: Long, isGroup: Boolean) -> Unit,
    onDeleteContact: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedGroupFilter by remember { mutableStateOf<Long?>(null) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }

    val filteredContacts by remember(contacts, searchQuery, selectedGroupFilter) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            contacts.filter { c ->
                val groupMatch = selectedGroupFilter == null || c.groupId == selectedGroupFilter
                val textMatch = q.isEmpty() ||
                    c.name.lowercase().contains(q) ||
                    c.phoneNumber.contains(q) ||
                    c.company.lowercase().contains(q)
                groupMatch && textMatch
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("contacts_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "Audience & Pickers",
                subtitle = "${contacts.size} targets (${contacts.count { it.isGroup }} WA groups)",
                trailing = {
                    OutlinedButton(
                        onClick = { showCreateGroupDialog = true },
                        modifier = Modifier.testTag("contacts_new_group_btn")
                    ) {
                        Icon(Icons.Filled.GroupAdd, contentDescription = "Create group", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Group")
                    }
                }
            )
        }

        if (pickerState.isActive) {
            item {
                LiveWhatsAppPickerBanner(
                    pickerState = pickerState,
                    onCommit = onCommitWhatsAppPicker,
                    onCancel = onCancelWhatsAppPicker
                )
            }
        }

        // Direct Pickers Action Row: Phonebook + Direct WhatsApp Contact & Group Picker + Manual Add
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenPhonebookPicker,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("contacts_phonebook_import_btn")
                    ) {
                        Icon(Icons.Filled.Contacts, contentDescription = "Phonebook import", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Phonebook")
                    }
                    FilledTonalButton(
                        onClick = onOpenWhatsAppPicker,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("contacts_wa_multi_picker_btn")
                    ) {
                        Icon(Icons.Filled.TouchApp, contentDescription = "WhatsApp picker", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WA Multi-Picker")
                    }
                }

                OutlinedButton(
                    onClick = { showAddContactDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("contacts_add_manual_btn")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add contact manually", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Individual Contact or WhatsApp Group Manually")
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search name, +91 number, or WhatsApp group...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search contacts") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("contacts_search_input")
            )
        }

        // Group Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedGroupFilter == null,
                    onClick = { selectedGroupFilter = null },
                    label = { Text("All (${contacts.size})") }
                )
                groups.forEach { group ->
                    val count = contacts.count { it.groupId == group.id }
                    FilterChip(
                        selected = selectedGroupFilter == group.id,
                        onClick = {
                            selectedGroupFilter = if (selectedGroupFilter == group.id) null else group.id
                        },
                        label = { Text("${group.name} ($count)") }
                    )
                }
            }
        }

        if (filteredContacts.isEmpty()) {
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
                        Text("No contacts or groups yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Use Phonebook Import for Indian mobile numbers or WA Multi-Picker to capture WhatsApp chats & groups directly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredContacts, key = { it.id }) { contact ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (contact.isGroup) Icons.Filled.Group else Icons.Filled.Person,
                            contentDescription = if (contact.isGroup) "WhatsApp Group" else "Contact",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(contact.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (contact.isGroup) {
                                    "WhatsApp Group Target"
                                } else {
                                    MessageVariableParser.formatDisplayPhone(contact.phoneNumber)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (contact.company.isNotBlank() || contact.customTag.isNotBlank()) {
                                Text(
                                    text = listOf(contact.company, contact.customTag).filter { it.isNotBlank() }.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { onDeleteContact(contact.id) }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete contact",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddContactDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var company by remember { mutableStateOf("") }
        var tag by remember { mutableStateOf("") }
        var isWaGroup by remember { mutableStateOf(false) }
        val defaultGroupId = groups.firstOrNull()?.id ?: 0L

        AlertDialog(
            onDismissRequest = { showAddContactDialog = false },
            title = { Text(if (isWaGroup) "Add WhatsApp Group" else "Add Contact") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("This target is a WhatsApp Group", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isWaGroup, onCheckedChange = { isWaGroup = it })
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(if (isWaGroup) "Exact WhatsApp Group Title" else "Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!isWaGroup) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone (+91 or 10-digit Indian)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = company,
                            onValueChange = { company = it },
                            label = { Text("Company (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = tag,
                        onValueChange = { tag = it },
                        label = { Text("Custom Tag (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalPhone = if (isWaGroup) {
                            "GROUP:${name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_")}"
                        } else {
                            phone
                        }
                        onAddManualContact(name, finalPhone, company, tag, defaultGroupId, isWaGroup)
                        showAddContactDialog = false
                    },
                    enabled = name.isNotBlank() && (isWaGroup || MessageVariableParser.normalizeIndianOrNriPhone(phone) != null)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddContactDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCreateGroupDialog) {
        var groupName by remember { mutableStateOf("") }
        var groupDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Create Contact Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = groupDesc,
                        onValueChange = { groupDesc = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateGroup(groupName, groupDesc)
                        showCreateGroupDialog = false
                    },
                    enabled = groupName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCreateGroupDialog = false }) { Text("Cancel") }
            }
        )
    }
}
