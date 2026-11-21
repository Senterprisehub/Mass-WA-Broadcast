package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.model.AccessibilityScopeMode
import com.example.data.model.LockType
import com.example.data.model.ThemeMode
import com.example.data.model.WhatsAppInstance
import com.example.ui.components.EncryptedVaultEditorDialog
import com.example.ui.components.HealthStatusIcon
import com.example.ui.components.SectionHeader
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.util.AppSettings
import com.example.util.OemDetector
import com.example.util.PermissionHealthManager
import com.example.util.SystemHealthReport
import com.example.util.VaultStatus

private enum class VaultEditorTarget { DEVICE, WHATSAPP }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    vaultStatus: VaultStatus,
    healthReport: SystemHealthReport,
    accessibilityScopeMode: AccessibilityScopeMode,
    whatsAppInstances: List<WhatsAppInstance>,
    totalLogCount: Int,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSelectWhatsAppInstance: (WhatsAppInstance) -> Unit,
    onRequestContactsPermission: () -> Unit,
    onRequestNotificationsPermission: () -> Unit,
    onRequestPhoneStatePermission: () -> Unit,
    onSaveDeviceVault: (LockType, String) -> Unit,
    onSaveWhatsAppVault: (LockType, String) -> Unit,
    onSetVaultAutoUnlock: (Boolean) -> Unit,
    onSetAutoExitWhatsApp: (Boolean) -> Unit,
    onSetAutoLockIfLocked: (Boolean) -> Unit,
    onWipeVault: () -> Unit,
    onSetAutoDisableAccessibility: (Boolean) -> Unit,
    onDisableAccessibilityNow: () -> Unit,
    onSetPauseOnIncomingCall: (Boolean) -> Unit,
    onSetAlertPrefs: (Boolean, Boolean) -> Unit,
    onSetLogRetentionDays: (Int) -> Unit,
    onPruneLogsNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeVaultEditor by remember { mutableStateOf<VaultEditorTarget?>(null) }
    val oemRecipes = remember(healthReport.oemFamily) {
        OemDetector.getOemSurvivalRecipes(context, healthReport.oemFamily)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Settings & System Hub",
                subtitle = "Theme, 1-Tap Permissions, Indian OEM Survival & AES-256-GCM Vault"
            )
        }

        // 1. Theme Settings (System / Light / Dark)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Palette, contentDescription = "Theme mode", tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Appearance Theme", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Minimalist Monochrome with Indigo accents",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = appSettings.themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                modifier = Modifier.testTag("theme_option_${mode.name.lowercase()}")
                            ) {
                                Text(mode.label)
                            }
                        }
                    }
                }
            }
        }

        // 2. All Needed Permissions — One-Tap Setting Buttons
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Security, contentDescription = "Permissions", tint = MaterialTheme.colorScheme.primary)
                            Text("All Permissions (1-Tap Settings)", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = "${healthReport.criticalGatesPassed}/${healthReport.totalCriticalGates} Ready",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (healthReport.allCriticalReady) StatusEmerald else MaterialTheme.colorScheme.primary
                        )
                    }

                    PermissionOneTapRow(
                        title = "Accessibility Auto-Sender",
                        subtitle = "Required for unattended sending & live WhatsApp multi-picker",
                        granted = healthReport.accessibilityEnabled,
                        buttonLabel = "Open",
                        testTag = "perm_btn_accessibility",
                        onTap = { PermissionHealthManager.openAccessibilitySettings(context) }
                    )
                    HorizontalDivider()

                    PermissionOneTapRow(
                        title = "Exact Scheduled Alarms",
                        subtitle = "Powers 48h rolling schedule triggers across OEM standby",
                        granted = healthReport.exactAlarmsAllowed,
                        buttonLabel = "Allow",
                        testTag = "perm_btn_exact_alarms",
                        onTap = { PermissionHealthManager.openExactAlarmSettings(context) }
                    )
                    HorizontalDivider()

                    PermissionOneTapRow(
                        title = "Notifications & Live Controls",
                        subtitle = "Persistent Pause / Resume / Stop progress notifications",
                        granted = healthReport.notificationsEnabled,
                        buttonLabel = if (healthReport.notificationsEnabled) "Settings" else "Grant",
                        testTag = "perm_btn_notifications",
                        onTap = {
                            if (healthReport.notificationsEnabled) {
                                PermissionHealthManager.openNotificationSettings(context)
                            } else {
                                onRequestNotificationsPermission()
                            }
                        }
                    )
                    HorizontalDivider()

                    PermissionOneTapRow(
                        title = "Full-Screen Lockscreen Wake",
                        subtitle = "Shows scheduled alarm overlay on Android 14+ locked screens",
                        granted = healthReport.fullScreenIntentAllowed,
                        buttonLabel = "Allow",
                        testTag = "perm_btn_fullscreen",
                        onTap = { PermissionHealthManager.openFullScreenIntentSettings(context) }
                    )
                    HorizontalDivider()

                    PermissionOneTapRow(
                        title = "Unrestricted Battery Usage",
                        subtitle = "Prevents MIUI, ColorOS, Funtouch & One UI background kills",
                        granted = healthReport.batteryOptimizationIgnored,
                        buttonLabel = "Unrestrict",
                        testTag = "perm_btn_battery",
                        onTap = { PermissionHealthManager.openBatteryOptimizationSettings(context) }
                    )
                    HorizontalDivider()

                    PermissionOneTapRow(
                        title = "Display Over Other Apps",
                        subtitle = "Allows background wake transitions & overlay alerts",
                        granted = healthReport.overlayAllowed,
                        buttonLabel = "Allow",
                        testTag = "perm_btn_overlay",
                        onTap = { PermissionHealthManager.openOverlaySettings(context) }
                    )
                    HorizontalDivider()

                    PermissionOneTapRow(
                        title = "Device Phonebook (Contacts)",
                        subtitle = "Imports pan-India contacts directly from device address book",
                        granted = healthReport.contactsPermissionGranted,
                        buttonLabel = if (healthReport.contactsPermissionGranted) "Settings" else "Grant",
                        testTag = "perm_btn_contacts",
                        onTap = {
                            if (healthReport.contactsPermissionGranted) {
                                PermissionHealthManager.openAppDetailsSettings(context)
                            } else {
                                onRequestContactsPermission()
                            }
                        }
                    )
                    HorizontalDivider()

                    PermissionOneTapRow(
                        title = "Phone Call Auto-Pause",
                        subtitle = "Detects incoming cellular calls to pause broadcasts safely",
                        granted = healthReport.phoneStatePermissionGranted,
                        buttonLabel = if (healthReport.phoneStatePermissionGranted) "Settings" else "Grant",
                        testTag = "perm_btn_phone_state",
                        onTap = {
                            if (healthReport.phoneStatePermissionGranted) {
                                PermissionHealthManager.openAppDetailsSettings(context)
                            } else {
                                onRequestPhoneStatePermission()
                            }
                        }
                    )
                }
            }
        }

        // 3. Pan-India OEM Survival Settings (Xiaomi, Oppo/Realme/OnePlus, Vivo/iQOO, Samsung)
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.PhoneAndroid, contentDescription = "OEM Survival", tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Indian OEM Background Survival", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = healthReport.oemFamily.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    oemRecipes.forEach { recipe ->
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(recipe.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = recipe.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = { OemDetector.launchFirstAvailableIntent(context, recipe.intents) }
                            ) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open")
                            }
                        }
                    }
                }
            }
        }

        // 4. Encrypted Auto Device / WhatsApp Lock & Unlock Vault (AES-256-GCM)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = "Encrypted Vault", tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Encrypted Lock / Unlock Vault", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Android Keystore AES-256-GCM • PIN, 3×3 Pattern, Password & Slide Lock",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Auto-Unlock for Scheduled Runs", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Swipes up Indian OEM cover screens & decrypts into zero-wiped memory only on wakeup",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = vaultStatus.autoUnlockEnabled,
                            onCheckedChange = onSetVaultAutoUnlock,
                            modifier = Modifier.testTag("vault_auto_unlock_switch")
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Exit WhatsApp After Broadcast", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Leaves WhatsApp to Home after final message so WhatsApp App Lock re-engages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = appSettings.autoExitWhatsAppAfterBroadcast,
                            onCheckedChange = onSetAutoExitWhatsApp,
                            modifier = Modifier.testTag("auto_exit_wa_switch")
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Lock Device If Started Locked", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Locks screen via Accessibility (Android 9+) only when run started while locked",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = appSettings.autoLockDeviceIfStartedLocked,
                            onCheckedChange = onSetAutoLockIfLocked,
                            modifier = Modifier.testTag("auto_lock_if_locked_switch")
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Device Screen Lock", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Mode: ${vaultStatus.deviceLockType.label}" +
                                    if (vaultStatus.hasDeviceSecret) " (AES-256 Encrypted)" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (vaultStatus.deviceLockType != LockType.NONE) StatusEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { activeVaultEditor = VaultEditorTarget.DEVICE },
                            modifier = Modifier.testTag("configure_device_vault_btn")
                        ) {
                            Text("Configure")
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("WhatsApp App Lock", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Mode: ${vaultStatus.whatsAppLockType.label}" +
                                    if (vaultStatus.hasWhatsAppSecret) " (AES-256 Encrypted)" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (vaultStatus.whatsAppLockType != LockType.NONE) StatusEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { activeVaultEditor = VaultEditorTarget.WHATSAPP },
                            modifier = Modifier.testTag("configure_wa_vault_btn")
                        ) {
                            Text("Configure")
                        }
                    }

                    if (vaultStatus.isAnyVaultConfigured) {
                        OutlinedButton(
                            onClick = onWipeVault,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wipe_vault_btn")
                        ) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = StatusCrimson, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Wipe Encrypted Vault Credentials", color = StatusCrimson)
                        }
                    }
                }
            }
        }

        // 5. WhatsApp / WhatsApp Business / Dual App Clone Selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Default WhatsApp / Business / Clone Target", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "On-device dispatch strictly via official WhatsApp, WhatsApp Business & OEM Dual App profiles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    whatsAppInstances.forEach { instance ->
                        val selected = instance.packageName == appSettings.defaultWhatsAppPackage &&
                            instance.isClone == appSettings.defaultIsClone
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { onSelectWhatsAppInstance(instance) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(instance.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${instance.packageName} • User #${instance.userSerial}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Privacy Isolation, Call Protection & Multi-Year Log Pruning
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Shield, contentDescription = "Banking-safe privacy", tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Privacy & Storage Hygiene", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Accessibility Scope: ${accessibilityScopeMode.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusEmerald
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Disable Accessibility After Broadcast", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Turns off accessibility service automatically once queue finishes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = appSettings.autoDisableAccessibilityAfterBroadcast,
                            onCheckedChange = onSetAutoDisableAccessibility
                        )
                    }

                    if (healthReport.accessibilityEnabled) {
                        OutlinedButton(
                            onClick = onDisableAccessibilityNow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disable Accessibility Service Now")
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Pause on Incoming Phone Call", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Pauses active broadcast cleanly during cellular calls",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = appSettings.pauseOnIncomingCall,
                            onCheckedChange = onSetPauseOnIncomingCall
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Scheduled Alarm Sound", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = appSettings.alertSoundEnabled,
                            onCheckedChange = { onSetAlertPrefs(it, appSettings.alertVibrateEnabled) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Scheduled Alarm Vibration", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = appSettings.alertVibrateEnabled,
                            onCheckedChange = { onSetAlertPrefs(appSettings.alertSoundEnabled, it) }
                        )
                    }

                    HorizontalDivider()

                    Text(
                        text = "Delivery Log Retention ($totalLogCount rows stored locally)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(30 to "30 Days", 90 to "90 Days", 180 to "180 Days", 0 to "Keep All").forEach { (days, label) ->
                            FilterChip(
                                selected = appSettings.logRetentionDays == days,
                                onClick = { onSetLogRetentionDays(days) },
                                label = { Text(label) }
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onPruneLogsNow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prune_logs_now_btn")
                    ) {
                        Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Prune Aged Logs & Orphan Media Now")
                    }

                    HorizontalDivider()
                    Text(
                        text = "Mass WA Broadcast v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • Offline Room v3",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    when (activeVaultEditor) {
        VaultEditorTarget.DEVICE -> {
            EncryptedVaultEditorDialog(
                title = "Device Screen Lock Vault",
                initialLockType = vaultStatus.deviceLockType,
                onDismiss = { activeVaultEditor = null },
                onSave = { lockType, secret ->
                    onSaveDeviceVault(lockType, secret)
                    activeVaultEditor = null
                }
            )
        }
        VaultEditorTarget.WHATSAPP -> {
            EncryptedVaultEditorDialog(
                title = "WhatsApp App Lock Vault",
                initialLockType = vaultStatus.whatsAppLockType,
                onDismiss = { activeVaultEditor = null },
                onSave = { lockType, secret ->
                    onSaveWhatsAppVault(lockType, secret)
                    activeVaultEditor = null
                }
            )
        }
        null -> Unit
    }
}

@Composable
private fun PermissionOneTapRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    buttonLabel: String,
    testTag: String,
    onTap: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HealthStatusIcon(ok = granted, contentDescription = if (granted) "$title granted" else "$title required")
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (granted) {
            OutlinedButton(
                onClick = onTap,
                modifier = Modifier.testTag(testTag)
            ) {
                Text(buttonLabel)
            }
        } else {
            Button(
                onClick = onTap,
                modifier = Modifier.testTag(testTag)
            ) {
                Text(buttonLabel)
            }
        }
    }
}
