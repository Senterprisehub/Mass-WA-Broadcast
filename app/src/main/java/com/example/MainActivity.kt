package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ThemeMode
import com.example.ui.components.DevicePhonebookPickerDialog
import com.example.ui.components.LaunchWhatsAppPickerDialog
import com.example.ui.components.ZeroTapAccessibilityGuardDialog
import com.example.ui.screens.CampaignDetailScreen
import com.example.ui.screens.CampaignsScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.CreateCampaignScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TemplatesScreen
import com.example.ui.theme.MassWaBroadcastTheme
import com.example.util.NotificationHelper
import com.example.util.PermissionHealthManager
import com.example.viewmodel.BroadcastViewModel

private enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("Home", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    CAMPAIGNS("Campaigns", Icons.Filled.Campaign, Icons.Outlined.Campaign),
    CONTACTS("Audience", Icons.Filled.Contacts, Icons.Outlined.Contacts),
    TEMPLATES("Templates", Icons.Filled.Description, Icons.Outlined.Description),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

private sealed interface SubScreen {
    data object None : SubScreen
    data class CreateCampaign(val initialTemplate: String = "") : SubScreen
    data class CampaignDetail(val campaignId: Long) : SubScreen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannelsCreated(applicationContext)

        val deepLinkCampaignId = intent?.getLongExtra(NotificationHelper.EXTRA_CAMPAIGN_ID, -1L)
            ?.takeIf { it > 0L }

        setContent {
            val viewModel: BroadcastViewModel = viewModel()
            val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
            val isSystemDark = isSystemInDarkTheme()
            val useDarkTheme = when (appSettings.themeMode) {
                ThemeMode.SYSTEM -> isSystemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            LaunchedEffect(useDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (useDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (useDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
            }

            MassWaBroadcastTheme(themeMode = appSettings.themeMode) {
                MassWaBroadcastApp(
                    viewModel = viewModel,
                    initialCampaignDetailId = deepLinkCampaignId
                )
            }
        }
    }
}

@Composable
fun MassWaBroadcastApp(
    viewModel: BroadcastViewModel,
    initialCampaignDetailId: Long? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val vaultStatus by viewModel.vaultStatus.collectAsStateWithLifecycle()
    val healthReport by viewModel.healthReport.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val contactCount by viewModel.contactCount.collectAsStateWithLifecycle()
    val campaigns by viewModel.campaigns.collectAsStateWithLifecycle()
    val aggregate by viewModel.dashboardAggregate.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val totalLogCount by viewModel.totalLogCount.collectAsStateWithLifecycle()
    val executionState by viewModel.executionState.collectAsStateWithLifecycle()
    val accessibilityScopeMode by viewModel.accessibilityScopeMode.collectAsStateWithLifecycle()
    val pickerState by viewModel.whatsAppPickerState.collectAsStateWithLifecycle()
    val whatsAppInstances by viewModel.whatsAppInstances.collectAsStateWithLifecycle()
    val phonebookContacts by viewModel.devicePhonebookContacts.collectAsStateWithLifecycle()
    val isLoadingPhonebook by viewModel.isLoadingPhonebook.collectAsStateWithLifecycle()
    val pacingEstimate by viewModel.pacingEstimate.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MainTab.DASHBOARD) }
    var activeSubScreen by remember {
        mutableStateOf<SubScreen>(
            if (initialCampaignDetailId != null) {
                SubScreen.CampaignDetail(initialCampaignDetailId)
            } else {
                SubScreen.None
            }
        )
    }

    var showPhonebookDialog by remember { mutableStateOf(false) }
    var showWhatsAppPickerDialog by remember { mutableStateOf(false) }
    var showZeroTapGuardDialog by remember { mutableStateOf(false) }

    val guardZeroTapLaunch: (() -> Unit) -> Unit = { onAllowed ->
        if (viewModel.isAccessibilityReadyForZeroTap()) {
            onAllowed()
        } else {
            viewModel.refreshSystemHealthAndInstances()
            showZeroTapGuardDialog = true
        }
    }

    // Refresh OS permissions & discovered WhatsApp/Clone instances on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemHealthAndInstances()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // Runtime Permission Launchers
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshSystemHealthAndInstances()
        if (granted) {
            viewModel.loadDevicePhonebook()
            showPhonebookDialog = true
        } else {
            viewModel.showMessage("Contacts permission denied. Enable it in Settings -> Permissions.")
        }
    }

    val notificationsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshSystemHealthAndInstances()
        if (!granted) {
            PermissionHealthManager.openNotificationSettings(context)
        }
    }

    val phoneStatePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshSystemHealthAndInstances()
    }

    val triggerPhonebookPicker: () -> Unit = {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            viewModel.loadDevicePhonebook()
            showPhonebookDialog = true
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail && activeSubScreen == SubScreen.None) {
                    NavigationBar {
                        MainTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail && activeSubScreen == SubScreen.None) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        MainTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 1100.dp)
                    ) {
                        when (val sub = activeSubScreen) {
                            is SubScreen.CreateCampaign -> {
                                CreateCampaignScreen(
                                    groups = groups,
                                    contacts = contacts,
                                    templates = templates,
                                    whatsAppInstances = whatsAppInstances,
                                    defaultWhatsAppPackage = appSettings.defaultWhatsAppPackage,
                                    defaultWhatsAppIsClone = appSettings.defaultIsClone,
                                    defaultDelaySeconds = appSettings.defaultDelaySeconds,
                                    pacingEstimate = pacingEstimate,
                                    pickerState = pickerState,
                                    initialTemplateText = sub.initialTemplate,
                                    onBack = { activeSubScreen = SubScreen.None },
                                    onOpenPhonebookPicker = triggerPhonebookPicker,
                                    onOpenWhatsAppPicker = { showWhatsAppPickerDialog = true },
                                    onCommitWhatsAppPicker = { viewModel.commitCapturedWhatsAppTargets() },
                                    onCancelWhatsAppPicker = { viewModel.cancelWhatsAppPicker() },
                                    onStageMediaUri = { uri, cb -> viewModel.stageMediaAttachment(uri, cb) },
                                    onRefreshPacing = { count, delaySec, epoch ->
                                        viewModel.refreshDailyPacingEstimate(count, delaySec, epoch)
                                    },
                                    onSubmitCampaign = { title, msg, gIds, expContacts, epoch, delaySec, media, inst, launchNow, draft ->
                                        if (launchNow) {
                                            guardZeroTapLaunch {
                                                viewModel.createAndHandleCampaign(
                                                    title = title,
                                                    messageTemplate = msg,
                                                    targetGroupIds = gIds,
                                                    explicitContacts = expContacts,
                                                    scheduledTimeEpoch = epoch,
                                                    delaySeconds = delaySec,
                                                    mediaUri = media,
                                                    targetInstance = inst,
                                                    launchImmediately = true,
                                                    saveAsDraft = false
                                                ) { createdId ->
                                                    activeSubScreen = SubScreen.CampaignDetail(createdId)
                                                }
                                            }
                                        } else {
                                            viewModel.createAndHandleCampaign(
                                                title = title,
                                                messageTemplate = msg,
                                                targetGroupIds = gIds,
                                                explicitContacts = expContacts,
                                                scheduledTimeEpoch = epoch,
                                                delaySeconds = delaySec,
                                                mediaUri = media,
                                                targetInstance = inst,
                                                launchImmediately = false,
                                                saveAsDraft = draft
                                            ) { createdId ->
                                                activeSubScreen = SubScreen.CampaignDetail(createdId)
                                            }
                                        }
                                    }
                                )
                            }

                            is SubScreen.CampaignDetail -> {
                                val detailCampaign by viewModel.observeCampaignById(sub.campaignId)
                                    .collectAsStateWithLifecycle(initialValue = null)
                                val detailLogs by viewModel.observeLogsForCampaign(sub.campaignId)
                                    .collectAsStateWithLifecycle(initialValue = emptyList())
                                CampaignDetailScreen(
                                    campaign = detailCampaign,
                                    logs = detailLogs,
                                    onBack = { activeSubScreen = SubScreen.None },
                                    onLaunchNow = { id -> guardZeroTapLaunch { viewModel.launchCampaignNow(id) } },
                                    onCancelQueued = { viewModel.cancelQueuedCampaign(it) },
                                    onDeleteCampaign = { viewModel.deleteCampaign(it) }
                                )
                            }

                            SubScreen.None -> {
                                when (currentTab) {
                                    MainTab.DASHBOARD -> DashboardScreen(
                                        aggregate = aggregate,
                                        contactCount = contactCount,
                                        recentCampaigns = campaigns,
                                        executionState = executionState,
                                        pickerState = pickerState,
                                        healthReport = healthReport,
                                        appSettings = appSettings,
                                        onCreateCampaign = { activeSubScreen = SubScreen.CreateCampaign() },
                                        onOpenPhonebookPicker = triggerPhonebookPicker,
                                        onOpenWhatsAppPicker = { showWhatsAppPickerDialog = true },
                                        onCommitWhatsAppPicker = { viewModel.commitCapturedWhatsAppTargets() },
                                        onCancelWhatsAppPicker = { viewModel.cancelWhatsAppPicker() },
                                        onOpenSettingsHealth = { currentTab = MainTab.SETTINGS },
                                        onOpenCampaignDetail = { activeSubScreen = SubScreen.CampaignDetail(it) },
                                        onLaunchCampaign = { id -> guardZeroTapLaunch { viewModel.launchCampaignNow(id) } },
                                        onPauseExecution = { viewModel.pauseExecution() },
                                        onResumeExecution = { viewModel.resumeExecution() },
                                        onSkipRecipient = { viewModel.skipCurrentRecipient() },
                                        onStopExecution = { viewModel.stopExecution() }
                                    )

                                    MainTab.CAMPAIGNS -> CampaignsScreen(
                                        campaigns = campaigns,
                                        onCreateCampaign = { activeSubScreen = SubScreen.CreateCampaign() },
                                        onOpenCampaignDetail = { activeSubScreen = SubScreen.CampaignDetail(it) },
                                        onLaunchCampaign = { id -> guardZeroTapLaunch { viewModel.launchCampaignNow(id) } },
                                        onCancelQueuedCampaign = { viewModel.cancelQueuedCampaign(it) }
                                    )

                                    MainTab.CONTACTS -> ContactsScreen(
                                        groups = groups,
                                        contacts = contacts,
                                        pickerState = pickerState,
                                        onOpenPhonebookPicker = triggerPhonebookPicker,
                                        onOpenWhatsAppPicker = { showWhatsAppPickerDialog = true },
                                        onCommitWhatsAppPicker = { viewModel.commitCapturedWhatsAppTargets() },
                                        onCancelWhatsAppPicker = { viewModel.cancelWhatsAppPicker() },
                                        onCreateGroup = { name, desc -> viewModel.createGroup(name, desc) },
                                        onAddManualContact = { name, phone, comp, tag, gId, isGrp ->
                                            viewModel.addManualContact(name, phone, comp, tag, gId, isGrp)
                                        },
                                        onDeleteContact = { viewModel.deleteContact(it) }
                                    )

                                    MainTab.TEMPLATES -> TemplatesScreen(
                                        templates = templates,
                                        onSaveTemplate = { t, c, body -> viewModel.saveTemplate(t, c, body) },
                                        onDeleteTemplate = { viewModel.deleteTemplate(it) },
                                        onUseTemplateInCampaign = { body ->
                                            activeSubScreen = SubScreen.CreateCampaign(initialTemplate = body)
                                        }
                                    )

                                    MainTab.SETTINGS -> SettingsScreen(
                                        appSettings = appSettings,
                                        vaultStatus = vaultStatus,
                                        healthReport = healthReport,
                                        accessibilityScopeMode = accessibilityScopeMode,
                                        whatsAppInstances = whatsAppInstances,
                                        totalLogCount = totalLogCount,
                                        onSetThemeMode = { viewModel.setThemeMode(it) },
                                        onSelectWhatsAppInstance = { viewModel.setDefaultWhatsAppInstance(it) },
                                        onRequestContactsPermission = {
                                            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                        },
                                        onRequestNotificationsPermission = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                PermissionHealthManager.openNotificationSettings(context)
                                            }
                                        },
                                        onRequestPhoneStatePermission = {
                                            phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                                        },
                                        onSaveDeviceVault = { type, secret -> viewModel.saveDeviceLockVault(type, secret) },
                                        onSaveWhatsAppVault = { type, secret -> viewModel.saveWhatsAppLockVault(type, secret) },
                                        onSetVaultAutoUnlock = { viewModel.setVaultAutoUnlockEnabled(it) },
                                        onSetAutoExitWhatsApp = { viewModel.setAutoExitWhatsAppAfterBroadcast(it) },
                                        onSetAutoLockIfLocked = { viewModel.setAutoLockDeviceIfStartedLocked(it) },
                                        onWipeVault = { viewModel.wipeAllVaultCredentials() },
                                        onSetAutoDisableAccessibility = { viewModel.setAutoDisableAccessibility(it) },
                                        onDisableAccessibilityNow = { viewModel.disableAccessibilityNow() },
                                        onSetPauseOnIncomingCall = { viewModel.setPauseOnIncomingCall(it) },
                                        onSetAlertPrefs = { s, v -> viewModel.setAlertPreferences(s, v) },
                                        onSetLogRetentionDays = { viewModel.setLogRetentionDays(it) },
                                        onPruneLogsNow = { viewModel.pruneLogsNow() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPhonebookDialog) {
        DevicePhonebookPickerDialog(
            phonebookContacts = phonebookContacts,
            isLoading = isLoadingPhonebook,
            groups = groups,
            onDismiss = { showPhonebookDialog = false },
            onImport = { selected, groupId ->
                viewModel.importSelectedPhonebookContacts(selected, groupId)
                showPhonebookDialog = false
            }
        )
    }

    if (showWhatsAppPickerDialog) {
        LaunchWhatsAppPickerDialog(
            groups = groups,
            accessibilityEnabled = healthReport.accessibilityEnabled,
            onOpenAccessibilitySettings = {
                PermissionHealthManager.openAccessibilitySettings(context)
            },
            onDismiss = { showWhatsAppPickerDialog = false },
            onLaunch = { mode, groupId ->
                showWhatsAppPickerDialog = false
                viewModel.startLiveWhatsAppPicker(mode, groupId)
            }
        )
    }

    if (showZeroTapGuardDialog) {
        ZeroTapAccessibilityGuardDialog(
            onEnableAccessibility = {
                showZeroTapGuardDialog = false
                PermissionHealthManager.openAccessibilitySettings(context)
            },
            onOpenAppInfo = {
                PermissionHealthManager.openAppDetailsSettings(context)
            },
            onDismiss = { showZeroTapGuardDialog = false }
        )
    }
}
