package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.LockType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EncryptedVaultEditorDialog(
    title: String,
    initialLockType: LockType,
    onDismiss: () -> Unit,
    onSave: (LockType, String) -> Unit
) {
    var selectedType by remember { mutableStateOf(initialLockType) }
    var secretText by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }
    var patternNodes by remember { mutableStateOf<List<Int>>(emptyList()) }

    val canSave = when (selectedType) {
        LockType.NONE, LockType.SLIDE -> true
        LockType.PIN -> secretText.length >= 4 && secretText.all { it.isDigit() }
        LockType.PASSWORD -> secretText.length >= 4
        LockType.PATTERN -> patternNodes.size >= 4
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Encrypted Keystore Vault",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Stored strictly via Android Keystore AES-256-GCM with randomized IVs. Decrypted buffers are zero-filled immediately after unlock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LockType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                                secretText = ""
                                patternNodes = emptyList()
                            },
                            label = { Text(type.label) },
                            modifier = Modifier.testTag("vault_lock_type_${type.name.lowercase()}")
                        )
                    }
                }

                when (selectedType) {
                    LockType.NONE -> {
                        Text(
                            text = "Auto-unlock disabled for this target. Existing encrypted secret will be removed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LockType.SLIDE -> {
                        Text(
                            text = "Slide Lock selected: Accessibility performs a smooth upward swipe gesture to clear swipe-only lockscreens when scheduled campaigns wake.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    LockType.PIN -> {
                        OutlinedTextField(
                            value = secretText,
                            onValueChange = { input ->
                                secretText = input.filter { it.isDigit() }.take(12)
                            },
                            label = { Text("Numeric PIN (4–12 digits)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showSecret = !showSecret }) {
                                    Icon(
                                        imageVector = if (showSecret) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = "Toggle PIN visibility"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vault_pin_input")
                        )
                    }

                    LockType.PASSWORD -> {
                        OutlinedTextField(
                            value = secretText,
                            onValueChange = { secretText = it.take(64) },
                            label = { Text("Alphanumeric Password (min 4 chars)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showSecret = !showSecret }) {
                                    Icon(
                                        imageVector = if (showSecret) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = "Toggle password visibility"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vault_password_input")
                        )
                    }

                    LockType.PATTERN -> {
                        Text(
                            text = "Draw or tap at least 4 nodes on the 3×3 grid (${patternNodes.size} selected):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PatternLockPad(
                            selectedNodes = patternNodes,
                            onPatternChanged = { patternNodes = it }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (patternNodes.isEmpty()) "No nodes drawn" else "Nodes: ${patternNodes.joinToString("-") { (it + 1).toString() }}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(onClick = { patternNodes = emptyList() }) {
                                Text("Clear Pattern")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val payload = when (selectedType) {
                        LockType.PATTERN -> patternNodes.joinToString(",")
                        LockType.PIN, LockType.PASSWORD -> secretText
                        LockType.SLIDE -> "SLIDE"
                        LockType.NONE -> ""
                    }
                    onSave(selectedType, payload)
                },
                enabled = canSave,
                modifier = Modifier.testTag("vault_save_button")
            ) {
                Text("Encrypt & Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
