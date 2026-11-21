package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.AccessibilityScopeMode
import com.example.data.model.BroadcastLogEntry
import com.example.data.model.Campaign
import com.example.data.model.CampaignDashboardAggregate
import com.example.data.model.CampaignStatus
import com.example.data.model.Contact
import com.example.data.model.ContactGroup
import com.example.data.model.DailyPacingEstimate
import com.example.data.model.LockType
import com.example.data.model.MessageTemplate
import com.example.data.model.ThemeMode
import com.example.data.model.WhatsAppInstance
import com.example.data.repository.BroadcastRepository
import com.example.service.ScheduledBroadcastService
import com.example.util.AppSettings
import com.example.util.AppSettingsPreferences
import com.example.util.BroadcastExecutionCoordinator
import com.example.util.BroadcastExecutionState
import com.example.util.DeviceContactsHelper
import com.example.util.DevicePhonebookContact
import com.example.util.KeystoreCryptoManager
import com.example.util.MediaAttachmentHelper
import com.example.util.PermissionHealthManager
import com.example.util.SystemHealthReport
import com.example.util.VaultPreferences
import com.example.util.VaultStatus
import com.example.util.WhatsAppAutomationController
import com.example.util.WhatsAppInstanceDiscovery
import com.example.util.WhatsAppPickerCoordinator
import com.example.util.WhatsAppPickerMode
import com.example.util.WhatsAppPickerState
import com.example.util.WhatsAppSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BroadcastViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val settingsPrefs = AppSettingsPreferences(appContext)
    private val vaultPrefs = VaultPreferences(appContext)
    private val repository = BroadcastRepository(appContext, database, settingsPrefs)

    val appSettings: StateFlow<AppSettings> = settingsPrefs.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val vaultStatus: StateFlow<VaultStatus> = vaultPrefs.vaultStatusFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultStatus())

    val groups: StateFlow<List<ContactGroup>> = repository.groupsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val contacts: StateFlow<List<Contact>> = repository.contactsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val contactCount: StateFlow<Int> = repository.contactCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val campaigns: StateFlow<List<Campaign>> = repository.campaignsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dashboardAggregate: StateFlow<CampaignDashboardAggregate> = repository.dashboardAggregateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CampaignDashboardAggregate())

    val templates: StateFlow<List<MessageTemplate>> = repository.templatesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalLogCount: StateFlow<Int> = repository.totalLogCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val executionState: StateFlow<BroadcastExecutionState> = BroadcastExecutionCoordinator.state
    val accessibilityScopeMode: StateFlow<AccessibilityScopeMode> = WhatsAppAutomationController.scopeMode
    val whatsAppPickerState: StateFlow<WhatsAppPickerState> = WhatsAppPickerCoordinator.state

    private val _healthReport = MutableStateFlow(SystemHealthReport())
    val healthReport: StateFlow<SystemHealthReport> = _healthReport.asStateFlow()

    private val _whatsAppInstances = MutableStateFlow<List<WhatsAppInstance>>(emptyList())
    val whatsAppInstances: StateFlow<List<WhatsAppInstance>> = _whatsAppInstances.asStateFlow()

    private val _devicePhonebookContacts = MutableStateFlow<List<DevicePhonebookContact>>(emptyList())
    val devicePhonebookContacts: StateFlow<List<DevicePhonebookContact>> = _devicePhonebookContacts.asStateFlow()

    private val _isLoadingPhonebook = MutableStateFlow(false)
    val isLoadingPhonebook: StateFlow<Boolean> = _isLoadingPhonebook.asStateFlow()

    private val _pacingEstimate = MutableStateFlow(DailyPacingEstimate())
    val pacingEstimate: StateFlow<DailyPacingEstimate> = _pacingEstimate.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            vaultPrefs.migrateLegacyPlaintextIfNeeded()
            repository.seedStarterTemplatesIfEmpty()
            repository.recoverInterruptedCampaignsAndRepairMetrics()
            val settings = settingsPrefs.getCurrentSettings()
            if (settings.logRetentionDays > 0) {
                repository.pruneAgedLogs(settings.logRetentionDays)
            }
            refreshSystemHealthAndInstances()
        }
    }

    fun refreshSystemHealthAndInstances() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentVault = vaultStatus.value
            _healthReport.value = PermissionHealthManager.evaluate(appContext, currentVault)
            _whatsAppInstances.value = WhatsAppInstanceDiscovery.discoverInstances(appContext)
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    // --- Theme & App Settings ---

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsPrefs.setThemeMode(mode)
        }
    }

    fun setDefaultWhatsAppInstance(instance: WhatsAppInstance) {
        viewModelScope.launch {
            settingsPrefs.setDefaultWhatsAppInstance(
                packageName = instance.packageName,
                userSerial = instance.userSerial,
                label = instance.label,
                isClone = instance.isClone
            )
            _snackbarMessage.value = "Default sender set to ${instance.label}"
        }
    }

    fun setDefaultDelaySeconds(seconds: Int) {
        viewModelScope.launch { settingsPrefs.setDefaultDelaySeconds(seconds) }
    }

    fun setAutoDisableAccessibility(enabled: Boolean) {
        viewModelScope.launch { settingsPrefs.setAutoDisableAccessibility(enabled) }
    }

    fun setAutoExitWhatsAppAfterBroadcast(enabled: Boolean) {
        viewModelScope.launch { settingsPrefs.setAutoExitWhatsAppAfterBroadcast(enabled) }
    }

    fun setAutoLockDeviceIfStartedLocked(enabled: Boolean) {
        viewModelScope.launch { settingsPrefs.setAutoLockDeviceIfStartedLocked(enabled) }
    }

    fun isAccessibilityReadyForZeroTap(): Boolean {
        return PermissionHealthManager.isAccessibilityServiceEnabled(appContext) ||
            WhatsAppAutomationController.isServiceConnected
    }

    fun setPauseOnIncomingCall(enabled: Boolean) {
        viewModelScope.launch { settingsPrefs.setPauseOnIncomingCall(enabled) }
    }

    fun setAlertPreferences(sound: Boolean, vibrate: Boolean) {
        viewModelScope.launch { settingsPrefs.setAlertPreferences(sound, vibrate) }
    }

    fun setLogRetentionDays(days: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsPrefs.setLogRetentionDays(days)
            if (days > 0) {
                val pruned = repository.pruneAgedLogs(days)
                _snackbarMessage.value = if (pruned > 0) {
                    "Retention set to ${days}d ($pruned old logs pruned)"
                } else {
                    "Log retention updated to ${days} days"
                }
            } else {
                _snackbarMessage.value = "Log retention set to Keep All"
            }
        }
    }

    fun pruneLogsNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val days = appSettings.value.logRetentionDays.takeIf { it > 0 } ?: 90
            val prunedLogs = repository.pruneAgedLogs(days)
            val prunedMedia = repository.runMediaOrphanGc()
            _snackbarMessage.value = "Cleaned $prunedLogs aged logs & $prunedMedia orphaned media files"
        }
    }

    fun disableAccessibilityNow() {
        WhatsAppAutomationController.requestDisableServiceNow()
        refreshSystemHealthAndInstances()
        _snackbarMessage.value = "Accessibility service disabled"
    }

    // --- Encrypted Security Vault (PIN, Pattern, Password, Slide Lock) ---

    fun saveDeviceLockVault(lockType: LockType, secretInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val chars = secretInput.toCharArray()
            try {
                vaultPrefs.saveDeviceLock(lockType, chars)
            } finally {
                KeystoreCryptoManager.zeroFill(chars)
            }
            refreshSystemHealthAndInstances()
            _snackbarMessage.value = "Device ${lockType.label} encrypted in Android Keystore"
        }
    }

    fun saveWhatsAppLockVault(lockType: LockType, secretInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val chars = secretInput.toCharArray()
            try {
                vaultPrefs.saveWhatsAppLock(lockType, chars)
            } finally {
                KeystoreCryptoManager.zeroFill(chars)
            }
            refreshSystemHealthAndInstances()
            _snackbarMessage.value = "WhatsApp ${lockType.label} encrypted in Android Keystore"
        }
    }

    fun setVaultAutoUnlockEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            vaultPrefs.setAutoUnlockEnabled(enabled)
            refreshSystemHealthAndInstances()
        }
    }

    fun wipeAllVaultCredentials() {
        viewModelScope.launch(Dispatchers.IO) {
            vaultPrefs.wipeAllVaultCredentials()
            refreshSystemHealthAndInstances()
            _snackbarMessage.value = "Encrypted vault wiped securely"
        }
    }

    // --- Contacts, Device Phonebook & Live WhatsApp Picker ---

    fun createGroup(name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.createGroup(name, description)
            _snackbarMessage.value = "Group \"$name\" created"
        }
    }

    fun deleteGroup(group: ContactGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteGroup(group)
            _snackbarMessage.value = "Group \"${group.name}\" removed"
        }
    }

    fun addManualContact(
        name: String,
        phone: String,
        company: String,
        customTag: String,
        groupId: Long,
        isGroup: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addContact(
                Contact(
                    groupId = groupId,
                    name = name.trim(),
                    phoneNumber = phone.trim(),
                    company = company.trim(),
                    customTag = customTag.trim(),
                    isGroup = isGroup
                )
            )
            _snackbarMessage.value = if (isGroup) "WhatsApp group added" else "Contact saved"
        }
    }

    fun deleteContact(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteContactById(id)
        }
    }

    fun loadDevicePhonebook() {
        viewModelScope.launch {
            _isLoadingPhonebook.value = true
            _devicePhonebookContacts.value = DeviceContactsHelper.loadDeviceContacts(appContext)
            _isLoadingPhonebook.value = false
        }
    }

    fun importSelectedPhonebookContacts(selected: List<DevicePhonebookContact>, groupId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val mapped = selected.map { item ->
                Contact(
                    groupId = groupId,
                    name = item.displayName,
                    phoneNumber = item.normalizedPhone,
                    isGroup = false
                )
            }
            val importedCount = repository.importContactsList(mapped)
            _snackbarMessage.value = "Imported $importedCount unique phonebook contacts"
        }
    }

    fun startLiveWhatsAppPicker(mode: WhatsAppPickerMode, targetGroupId: Long) {
        WhatsAppPickerCoordinator.startSession(mode, targetGroupId)
        val targetPkg = appSettings.value.defaultWhatsAppPackage
        val launched = WhatsAppSender.launchWhatsAppForPicker(appContext, targetPkg)
        if (!launched) {
            _snackbarMessage.value = "Picker armed! Open WhatsApp/Business to tap contacts & groups, or add manually."
        } else {
            _snackbarMessage.value = "Tap multiple chats & groups in WhatsApp, then return here to import."
        }
    }

    fun commitCapturedWhatsAppTargets(overrideGroupId: Long? = null, onCommitted: ((List<Contact>) -> Unit)? = null) {
        val sessionGroupId = overrideGroupId ?: whatsAppPickerState.value.targetGroupId
        val captured = WhatsAppPickerCoordinator.consumeAndStopSession()
        if (captured.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val mapped = captured.map { target ->
                Contact(
                    groupId = sessionGroupId,
                    name = target.name,
                    phoneNumber = target.phoneNumber,
                    customTag = if (target.isGroup) "WA Group" else "WhatsApp",
                    isGroup = target.isGroup
                )
            }
            val added = repository.importContactsList(mapped)
            _snackbarMessage.value = "Imported $added WhatsApp ${if (added == 1) "target" else "targets"}"
            onCommitted?.invoke(mapped)
        }
    }

    fun cancelWhatsAppPicker() {
        WhatsAppPickerCoordinator.cancelSession()
    }

    // --- Templates ---

    fun saveTemplate(title: String, category: String, content: String, existingId: Long = 0L) {
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveTemplate(
                MessageTemplate(
                    id = existingId,
                    title = title.trim(),
                    category = category.trim().ifBlank { "General" },
                    content = content.trim()
                )
            )
            _snackbarMessage.value = "Template saved"
        }
    }

    fun deleteTemplate(template: MessageTemplate) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTemplate(template)
            _snackbarMessage.value = "Template deleted"
        }
    }

    // --- Campaigns & Execution ---

    fun observeCampaignById(id: Long): Flow<Campaign?> = repository.observeCampaignById(id)
    fun observeLogsForCampaign(id: Long): Flow<List<BroadcastLogEntry>> = repository.observeLogsForCampaign(id)

    fun refreshDailyPacingEstimate(recipientCount: Int, delaySeconds: Int, scheduledEpoch: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _pacingEstimate.value = repository.computeDailyPacingEstimate(
                proposedRecipients = recipientCount,
                proposedDelaySeconds = delaySeconds,
                targetEpochMs = scheduledEpoch
            )
        }
    }

    fun stageMediaAttachment(uri: Uri, onStaged: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val stagedPath = MediaAttachmentHelper.stageMediaToInternalStorage(appContext, uri)
            onStaged(stagedPath)
        }
    }

    fun createAndHandleCampaign(
        title: String,
        messageTemplate: String,
        targetGroupIds: List<Long>,
        explicitContacts: List<Contact>,
        scheduledTimeEpoch: Long,
        delaySeconds: Int,
        mediaUri: String?,
        targetInstance: WhatsAppInstance,
        launchImmediately: Boolean,
        saveAsDraft: Boolean,
        onCreated: (Long) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val status = when {
                saveAsDraft -> CampaignStatus.DRAFT
                launchImmediately -> CampaignStatus.QUEUED
                scheduledTimeEpoch > System.currentTimeMillis() -> CampaignStatus.SCHEDULED
                else -> CampaignStatus.DRAFT
            }
            val campaignId = repository.createCampaignWithLogs(
                title = title,
                messageTemplate = messageTemplate,
                targetGroupIds = targetGroupIds,
                explicitContacts = explicitContacts,
                scheduledTimeEpoch = scheduledTimeEpoch,
                delaySeconds = delaySeconds,
                mediaUri = mediaUri,
                targetPackage = targetInstance.packageName,
                targetUserSerial = targetInstance.userSerial,
                isCloneTarget = targetInstance.isClone,
                initialStatus = status
            )
            if (launchImmediately) {
                ScheduledBroadcastService.startExecuteNow(appContext, campaignId)
                _snackbarMessage.value = "Broadcast launched via ${targetInstance.label}"
            } else if (status == CampaignStatus.SCHEDULED) {
                _snackbarMessage.value = "Campaign scheduled in 48h rolling alarm horizon"
            } else {
                _snackbarMessage.value = "Campaign saved as draft"
            }
            onCreated(campaignId)
        }
    }

    fun launchCampaignNow(campaignId: Long) {
        ScheduledBroadcastService.startExecuteNow(appContext, campaignId)
        _snackbarMessage.value = "Dispatch started"
    }

    fun cancelQueuedCampaign(campaignId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.cancelQueuedCampaign(campaignId)
            _snackbarMessage.value = "Removed from FIFO queue"
        }
    }

    fun deleteCampaign(campaign: Campaign) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCampaign(campaign)
            _snackbarMessage.value = "Campaign deleted"
        }
    }

    fun pauseExecution() = BroadcastExecutionCoordinator.pause("Paused by user")
    fun resumeExecution() = BroadcastExecutionCoordinator.resume()
    fun skipCurrentRecipient() = BroadcastExecutionCoordinator.requestSkipCurrent()
    fun stopExecution() = BroadcastExecutionCoordinator.requestStop()
}
