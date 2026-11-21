package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ContactGroup
import com.example.util.DevicePhonebookContact
import com.example.util.MessageVariableParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePhonebookPickerDialog(
    phonebookContacts: List<DevicePhonebookContact>,
    isLoading: Boolean,
    groups: List<ContactGroup>,
    onDismiss: () -> Unit,
    onImport: (List<DevicePhonebookContact>, Long) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedPhones by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedGroupId by remember(groups) { mutableStateOf(groups.firstOrNull()?.id ?: 0L) }
    var groupMenuExpanded by remember { mutableStateOf(false) }

    val filtered by remember(phonebookContacts, searchQuery) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            if (q.isEmpty()) {
                phonebookContacts
            } else {
                phonebookContacts.filter {
                    it.displayName.lowercase().contains(q) || it.normalizedPhone.contains(q)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Contacts,
                contentDescription = "Phonebook import",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Import from Device Phonebook",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search name or phone (+91)...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search phonebook") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("phonebook_search_input")
                )

                if (groups.isNotEmpty()) {
                    val activeGroupName = groups.firstOrNull { it.id == selectedGroupId }?.name ?: "No Group"
                    ExposedDropdownMenuBox(
                        expanded = groupMenuExpanded,
                        onExpandedChange = { groupMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = activeGroupName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Assign to Group") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = groupMenuExpanded,
                            onDismissRequest = { groupMenuExpanded = false }
                        ) {
                            groups.forEach { group ->
                                DropdownMenuItem(
                                    text = { Text(group.name) },
                                    onClick = {
                                        selectedGroupId = group.id
                                        groupMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedPhones.size} selected of ${filtered.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row {
                        TextButton(
                            onClick = {
                                selectedPhones = filtered.take(1000).map { it.normalizedPhone }.toSet()
                            }
                        ) {
                            Text("Select All")
                        }
                        TextButton(onClick = { selectedPhones = emptySet() }) {
                            Text("Clear")
                        }
                    }
                }

                HorizontalDivider()

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    filtered.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No phonebook contacts found matching filter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            items(filtered, key = { it.normalizedPhone }) { contact ->
                                val isChecked = contact.normalizedPhone in selectedPhones
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPhones = if (isChecked) {
                                                selectedPhones - contact.normalizedPhone
                                            } else {
                                                selectedPhones + contact.normalizedPhone
                                            }
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedPhones = if (checked) {
                                                selectedPhones + contact.normalizedPhone
                                            } else {
                                                selectedPhones - contact.normalizedPhone
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = MessageVariableParser.formatDisplayPhone(contact.normalizedPhone),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = phonebookContacts.filter { it.normalizedPhone in selectedPhones }
                    onImport(chosen, selectedGroupId)
                },
                enabled = selectedPhones.isNotEmpty(),
                modifier = Modifier.testTag("phonebook_import_confirm_button")
            ) {
                Text("Import (${selectedPhones.size})")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
