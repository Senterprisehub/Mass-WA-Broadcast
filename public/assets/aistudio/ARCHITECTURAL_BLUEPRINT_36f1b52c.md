# WhatsBroadcast — Complete Architectural Blueprint

**Application Name:** WhatsBroadcast  
**Application ID:** `com.aistudio.whatsappbroadcast.wxbqtp` (`namespace = "com.example"`)  
**Platform:** Android (Kotlin, Jetpack Compose, Material Design 3, Clean MVVM)  
**SDK Matrix:** `minSdk = 24` (Android 7.0) · `targetSdk = 36` · `compileSdk = 36` (Minor 1)

---

## 1. Executive Architecture Overview

**WhatsBroadcast** is an offline-first, multi-year hardened Android bulk WhatsApp messaging and campaign orchestration platform engineered specifically for Indian telecom formats, multi-instance OEM App Clones, and aggressive Indian OEM background execution restrictions (MIUI/HyperOS, ColorOS/Realme UI/OxygenOS, Funtouch OS/OriginOS, One UI, and Stock/Budget Android Go).

### Technology Stack (Pinned in `gradle/libs.versions.toml`)
| Layer | Library / Framework | Version | Purpose |
| :--- | :--- | :--- | :--- |
| **Build System** | Android Gradle Plugin / Kotlin / KSP | AGP `9.1.1` / Kotlin `2.2.10` / KSP `2.3.5` | Configuration-cached Kotlin DSL build + KSP Room codegen |
| **UI Toolkit** | Jetpack Compose BOM + Material 3 | `2024.09.00` | Adaptive phone/tablet Compose UI, custom charts & dialogs |
| **State & Lifecycle** | `Lifecycle` + `ViewModel` + Coroutines | `2.8.7` / `1.10.2` | `StateFlow` + `collectAsStateWithLifecycle` unidirectional flow |
| **Local Database** | AndroidX Room (SQLite WAL) | `2.7.0` | Version 3 indexed relational storage for contacts, campaigns & logs |
| **Secure Preferences** | Jetpack DataStore Preferences + Android Keystore | `1.1.7` | AES-256-GCM encrypted lock vault (`vaultDataStore`) |
| **Settings & Media** | `SharedPreferences` + `FileProvider` + Coil | Coil `2.7.0` | API/OEM settings, persistent internal media staging & image loading |
| **Networking** | OkHttp | `4.10.0` | Gateway health/dispatch client (`WhatsAppApiService`) |
| **Verification** | JUnit4 + Robolectric + Compose UI Test + Roborazzi | `4.16.1` / `1.59.0` | Multi-OEM, multi-year & 5,000-user JVM stress simulations |

### Layered System Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                         PRESENTATION LAYER (Compose M3)                      │
│  MainActivity (WhatsBroadcastApp) ──► NavigationBar (<600dp) / Rail (>=600dp)│
│  Screens: Dashboard │ Campaigns │ CreateCampaign │ CampaignDetail │ Contacts │
│           Templates                                                          │
│  Overlays: BroadcastProgressDialog │ PriorityBroadcastCard │ HealthDoctor    │
│            ScheduleAlertOverlayActivity (Lock-screen wake transparent host)  │
└───────────────────────────────────────┬──────────────────────────────────────┘
                                        │ StateFlow / Events
┌───────────────────────────────────────▼──────────────────────────────────────┐
│                         STATE & ORCHESTRATION LAYER                          │
│  BroadcastViewModel (AndroidViewModel)                                       │
│   ├── Computes 24h Daily Pacing & Overlap Warnings                           │
│   ├── Coordinates Live WhatsApp Picker Batch Imports                         │
│   └── Bridges UI actions to Service / Repository / Vault / Health Doctor     │
│                                                                              │
│  BroadcastExecutionCoordinator (Process-Wide Singleton SSOT)                 │
│   ├── Atomic execution lock (prevents concurrent campaign collisions)        │
│   ├── Persistent FIFO Queue (CampaignStatus.QUEUED) for overlapping runs     │
│   └── Deterministic Pause / Resume / Skip / Stop gates + Call Interrupts     │
└───────────────┬──────────────────────────────────────────┬───────────────────┘
                │                                          │
┌───────────────▼────────────────────┐    ┌────────────────▼───────────────────┐
│        DATA & PERSISTENCE          │    │     ANDROID OS & ENGINE LAYER      │
│  BroadcastRepository               │    │  • CampaignScheduler (48h Horizon) │
│   ├── Chunked queries (batch=500)  │    │  • ScheduledBroadcastService (FGS) │
│   ├── O(1) targeted metric repair  │    │  • BootReceiver & AlarmReceiver    │
│   └── 30/90/180d log auto-pruning  │    │  • CallDetectionManager & Receiver │
│                                    │    │  • WhatsAppSender & Discovery      │
│  AppDatabase (Room v3, WAL)        │    │  • WhatsAppAutomationController    │
│   ├── ContactGroup & Contact       │    │  • WhatsAppAutoSendAccessibilitySvc│
│   ├── Campaign (indexed)           │    │  • KeystoreCryptoManager & Vault   │
│   ├── BroadcastLogEntry (indexed)  │    │  • OemDetector & PermissionHealth  │
│   └── MessageTemplate              │    │  • MediaAttachmentHelper & GC      │
└────────────────────────────────────┘    └────────────────────────────────────┘
```

---

## 2. Package & Module Topology (`app/src/main/java/com/example/`)

```text
com.example/
├── MainActivity.kt                     # Single-activity host, edge-to-edge, runtime permissions, notification deep links
├── data/
│   ├── model/Models.kt                 # Room entities & CampaignStatus / DeliveryStatus enums
│   ├── local/
│   │   ├── AppDatabase.kt              # Room DB v3, MIGRATION_1_2, MIGRATION_2_3, destructive fallback
│   │   ├── ContactDao.kt               # Contacts & ContactGroups CRUD + batch IN queries
│   │   ├── CampaignDao.kt              # Indexed queue/schedule queries + SQL CampaignDashboardAggregate
│   │   ├── BroadcastLogDao.kt          # Indexed campaign logs + aged log pruning (pruneLogsOlderThan)
│   │   └── TemplateDao.kt              # Reusable personalized templates + usageCount tracking
│   └── repository/
│       └── BroadcastRepository.kt      # Single source of truth, chunked ops, FIFO drain loop, dispatch loop
├── viewmodel/
│   └── BroadcastViewModel.kt           # UI StateFlows, SQL metrics transform, 24h pacing estimator, priority card loop
├── service/
│   └── ScheduledBroadcastService.kt    # Foreground service (specialUse), Pause/Resume/Stop actions, wake + 6s OEM fallback
├── receiver/
│   ├── ScheduledCampaignReceiver.kt    # AlarmManager wake receiver (execute campaign or replenish 48h horizon)
│   ├── BootReceiver.kt                 # Re-arms pending alarms & handles 15m grace window after reboot / app update
│   └── CallStateReceiver.kt            # Telephony PHONE_STATE listener triggering auto-pause on incoming calls
├── ui/
│   ├── activity/ScheduleAlertOverlayActivity.kt  # Lock-screen alarm overlay (showWhenLocked / turnScreenOn)
│   ├── theme/                          # Material 3 color scheme, typography & WhatsApp accent tokens
│   ├── screens/                        # DashboardScreen, CampaignsScreen, CreateCampaignScreen,
│   │                                   # CampaignDetailScreen, ContactsScreen, TemplatesScreen
│   └── components/                     # BroadcastProgressDialog, CampaignCard, ContactPicker1000Dialog,
│                                       # DeviceContactsImportDialog, PatternLockPad, PerformanceChart,
│                                       # PriorityBroadcastCard, RelaunchCampaignDialog, StatCard, StatusBadge,
│                                       # SystemHealthDialog, VariableChipRow, WhatsAppApiSettingsDialog,
│                                       # WhatsAppAppChooserDialog, WhatsAppPreviewCard
└── util/
    ├── BroadcastExecutionCoordinator.kt # Mutex + AtomicBoolean lock + FIFO queue state
    ├── CampaignScheduler.kt             # Rolling 48h exact alarm horizon + 15m boot grace classifier
    ├── WhatsAppSender.kt                # Direct whatsapp://, api.whatsapp.com, wa.me, ACTION_SEND media/group router
    ├── WhatsAppInstanceDiscovery.kt     # LauncherApps + UserManager multi-profile / clone enumerator
    ├── WhatsAppAutomationController.kt  # Step state machine + dynamic package scoping (banking-safe isolation)
    ├── WhatsAppAutoSendAccessibilityService.kt # 13-language accessibility automation, safety filters & gestures
    ├── WhatsAppPickerCoordinator.kt     # Live WhatsApp chat/group capture state & deduplication
    ├── CallDetectionManager.kt          # Active cellular call detector & coordinator auto-pause
    ├── MessageVariableParser.kt         # Variable substitution & pan-India/NRI E.164 phone normalizer
    ├── MediaAttachmentHelper.kt         # Internal filesDir/campaign_media staging, FileProvider & orphan GC
    ├── KeystoreCryptoManager.kt         # Hardware-backed AES-256-GCM encryption + zero-fill memory wipes
    ├── VaultPreferences.kt              # Jetpack DataStore encrypted vault + legacy plaintext migration
    ├── OemDetector.kt                   # Xiaomi, Oppo/Realme/OnePlus, Vivo/iQOO, Samsung & Stock deep-link recipes
    ├── PermissionHealthManager.kt       # OS health gate evaluator (Accessibility, Alarms, Battery, FSI, Vault)
    ├── NotificationHelper.kt            # Channels + collision-free stride IDs for progress/alerts/missed/queued
    ├── NotificationAlertManager.kt      # High-priority scheduled audio/vibration alarm controller
    ├── ScreenWakeManager.kt             # CPU/Screen WakeLock + keyguard dismissal helpers
    ├── DeviceContactsHelper.kt          # ContactsContract import with Indian phone deduplication
    ├── WhatsAppApiConfig.kt             # SharedPreferences engine config, target clone & retention policy
    ├── WhatsAppApiService.kt            # OkHttp gateway service client
    ├── DateUtils.kt & SeedData.kt       # Time formatting helpers & initial sample seeding
```

---

## 3. Data Architecture & Multi-Year Scale Hardening (`AppDatabase` v3)

### Entity Schema & Composite Indexes
1. **`contact_groups`**: `id (PK)`, `name`, `colorHex`, `description`, `createdAt`.
2. **`contacts`**: `id (PK)`, `groupId`, `name`, `phoneNumber`, `company`, `customTag`, `notes`, `isOptedIn`, `createdAt`, `isGroup`.
3. **`campaigns`**: `id (PK)`, `title`, `messageTemplate`, `targetGroupIds`, `scheduledTimeEpoch`, `status`, `totalRecipients`, `sentCount`, `deliveredCount`, `readCount`, `failedCount`, `delaySeconds`, `mediaUri`, `createdAt`, `completedAt`, `targetPackage`, `targetUserSerial`, `isCloneTarget`, `failureDiagnostic`.
   - **Composite Index:** `index_campaigns_status_scheduledTimeEpoch` on `(status, scheduledTimeEpoch)` — accelerates scheduler horizon scans, FIFO queue lookups, and reboot recovery without full table scans.
4. **`broadcast_logs`**: `id (PK)`, `campaignId`, `contactId`, `contactName`, `phoneNumber`, `resolvedMessage`, `status`, `timestamp`, `errorMessage`, `isGroup`.
   - **Composite Indexes:**
     - `index_broadcast_logs_campaignId_status` on `(campaignId, status)` — accelerates campaign detail filtering and resume scans.
     - `index_broadcast_logs_timestamp` on `(timestamp)` — enables O(log N) aged log pruning.
5. **`message_templates`**: `id (PK)`, `title`, `category`, `content`, `usageCount`.

### Schema Evolution & Multi-Year Storage Protections
- **`MIGRATION_1_2`**: Adds `contacts.isGroup`, `campaigns.targetPackage`, `campaigns.targetUserSerial`, `campaigns.isCloneTarget`, `campaigns.failureDiagnostic`, and `broadcast_logs.isGroup`.
- **`MIGRATION_2_3`**: Creates the 3 composite SQLite indexes on `campaigns` and `broadcast_logs`.
- **SQLite 999 Bind-Variable Protection**: Any `IN (:ids)` query or bulk batch insert/delete is chunked by `SQLITE_BIND_BATCH_SIZE = 500` in `BroadcastRepository` (`getContactsByIdsChunked`, `getContactsByGroupIdsChunked`, `deleteContactsByIds`, `importContactsList`).
- **O(1) Dashboard Aggregation**: `CampaignDao.getDashboardAggregate()` computes totals (`SUM(sentCount)`, `SUM(deliveredCount)`, `SUM(readCount)`, `SUM(failedCount)`, `scheduledCount`, `queuedCount`) directly inside SQLite instead of streaming millions of `BroadcastLogEntry` rows into JVM heap.
- **WAL Churn Reduction & Targeted Metric Repair**: Dispatch loop flushes aggregate counts every `COUNT_FLUSH_BATCH_SIZE = 10` recipients (and at completion). Startup `repairAndSyncDeliveryMetrics()` scans only `RUNNING`, `PAUSED`, `QUEUED`, or unsynced `COMPLETED` campaigns (`getCampaignsNeedingMetricRepairDirect()`).
- **Aged Log Pruning (`pruneLogsOlderThan`)**: Deletes stale per-recipient log entries older than user-configured `logRetentionDays` (`30`, `90`, `180`, or `0` = Keep All; default `90`) while leaving `Campaign` summary counters permanently intact.
- **Auto Backup Exclusion**: `res/xml/backup_rules.xml` & `data_extraction_rules.xml` exclude `whatsbroadcast_db*` and `campaign_media` from cloud backups so multi-year logs and sensitive media never overflow Android's 25 MB backup quota.

---

## 4. Broadcast Execution, FIFO Queueing & Scheduling Pipeline

### Rolling 48-Hour Exact Alarm Horizon (`CampaignScheduler`)
Android and Indian OEMs (notably MIUI ~100 alarms, ColorOS/Funtouch ~120–150, AOSP 500) cap concurrent active exact alarms per app.  
To support multi-year schedules (e.g., 20 schedules/day × 730 days = 14,600 campaigns) without breaching OEM limits:
1. Only `SCHEDULED` campaigns within **`now + 48 hours` (`ROLLING_ALARM_HORIZON_MS`)** are armed in OS `AlarmManager` via `setAlarmClock()` (falls back cleanly to `setAndAllowWhileIdle()` if `SecurityException` occurs).
2. Any campaign beyond 48h remains safely persisted in Room, and a single **`ACTION_REPLENISH_ALARM_HORIZON`** (`HORIZON_ALARM_REQUEST_CODE = 99001`) alarm is scheduled every **36 hours** (`HORIZON_REPLENISH_INTERVAL_MS`) — or halfway to the next campaign — to call `syncRollingHorizon()` and promote incoming schedules into OS `AlarmManager`.
3. **Reboot & Grace Classification (`BootReceiver` -> `classifyBootSchedule`)**:
   - `scheduledEpoch > now`: `REARM_FUTURE` (re-arm if within 48h horizon).
   - `0 <= (now - scheduledEpoch) <= 15 minutes`: `FIRE_IMMEDIATELY_WITHIN_GRACE`.
   - Overdue past 15 minutes or invalid epoch: `MARK_FAILED_MISSED_WINDOW` with diagnostic & missed-campaign Resend notification.

### Overlapping Schedule Serialization & FIFO Queue (`BroadcastExecutionCoordinator` + `BroadcastRepository`)
```
  [Scheduled Alarm Fires / User Taps Send]
                   │
                   ▼
     BroadcastExecutionCoordinator.beginCampaign(id)
        ├── Unlocked (Idle) ──► Acquire activeExecutionFlag + executionMutex
        │                       Run active loop:
        │                         while (activeId != null):
        │                           1. Execute all PENDING recipients
        │                           2. If Stopped -> move QUEUED rows to PAUSED & exit
        │                           3. If Paused  -> persist counters & exit loop cleanly
        │                           4. Else dequeue next ID from FIFO queue & continue
        │
        └── Already Running ──► enqueueScheduledCampaign(id)
                                 • Cancels redundant OS alarm/missed notifs
                                 • Updates Room Campaign.status = QUEUED
                                 • Appends ID to BroadcastExecutionCoordinator FIFO list
                                 • Posts collision-free Queued Notification
```
- **Resume Support**: Resumes skipping already `SENT` / `DELIVERED` / `READ` logs cleanly without duplicate sends.
- **Process-Death Guard**: `recoverInterruptedCampaigns()` checks `BroadcastExecutionCoordinator.isActivelyExecuting` first so `onAppResumed()` lifecycle callbacks never clobber a live foreground service broadcast.

### Foreground Service & Background Activity Launch Bridge (`ScheduledBroadcastService`)
- Declared with `foregroundServiceType="specialUse"` (`android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE`).
- Exposes persistent notification controls (`ACTION_PAUSE`, `ACTION_RESUME`, `ACTION_STOP`) synced reactively to `BroadcastExecutionCoordinator.state`.
- On `ACTION_WAKE_AND_ALERT`: wakes screen (`ScreenWakeManager`), triggers audible/vibrate alert (`NotificationAlertManager`), fires high-priority full-screen intent notification, and launches `ScheduleAlertOverlayActivity`.
- **OEM Headless Fallback (6-second watchdog)**: If MIUI/Vivo/ColorOS suppresses background overlay window launches while locked, after 6,000 ms with no user response, `ScheduledBroadcastService` stops the alert and initiates unattended headless `executeSimulatedBroadcast(campaignId)`.

---

## 5. Accessibility Automation, Banking-Safe Scoping & Guardrails

### Dynamic Package Allowlisting (`WhatsAppAutomationController`)
Configured dynamically at runtime via `AccessibilityServiceInfo.packageNames` (defaults in `accessibility_service_config.xml` to `com.whatsapp,com.whatsapp.w4b`):
| `AccessibilityScopeMode` | Condition | `packageNames` Scope | Privacy Guarantee |
| :--- | :--- | :--- | :--- |
| **`DISABLED`** | Service off in Android settings or `disableSelf()` called | None | Service unbound |
| **`IDLE_ISOLATED`** | App running normally, no active broadcast or picker | `[ownPackageName]` only | **Zero accessibility events** received from UPI/banking, browsers, OTPs, or other apps |
| **`SCOPED_WHATSAPP`** | Active unlocked broadcast or live contact/group picker | Selected/discovered WhatsApp + clone packages + OEM resolvers | Third-party/banking apps and `com.android.systemui` remain excluded |
| **`SCOPED_LOCK_UNLOCK`** | Scheduled broadcast triggered while device keyguard is locked | WhatsApp + clones + resolvers + `com.android.systemui` | `com.android.systemui` is **removed immediately** once keyguard clears or campaign finishes |

An optional **`autoDisableAccessibilityAfterBroadcast`** setting automatically invokes `disableSelf()` at the end of a completed broadcast session.

### Recipient Dispatch State Machine (`AutomationStep`)
`IDLE` ➔ `UNLOCKING_KEYGUARD` ➔ `AWAITING_DISPATCH` ➔ `CHOOSER_SELECTION` (OEM Dual-App / Resolver) ➔ `SEARCHING_RECIPIENT` (Group/Clone Picker) ➔ `OPENING_CHAT` ➔ `PASTING_AND_SENDING` ➔ `CONFIRMING_SENT` ➔ `RETURNING_TO_MAIN` ➔ `RESTING` (2s post-exit interval) / `PAUSED`.

- Every recipient increments an atomic **generation token** (`generationCounter`), preventing stale delayed accessibility retry callbacks from affecting the next recipient.
- **15-second Send retry window** (`MAX_SEND_RETRY_ATTEMPTS * SEND_RETRY_INTERVAL_MS`), bounded by an **18s** timeout (primary) or **22s** (Group/Clone).

### Production Safety Filters (`WhatsAppAutoSendAccessibilityService`)
1. **13-Script Pan-India Send Matching**: Recognizes localized WhatsApp Send labels across English, Hindi (`भेजें`, `सेंड करें`), Marathi (`पाठवा`), Bengali (`পাঠান`), Gujarati (`ભેજો`, `મોકલો`), Punjabi (`ਭੇਜੋ`), Odia (`ପଠାନ୍ତୁ`), Telugu (`పంపు`), Tamil (`அனுப்பு`), Kannada (`ಕಳುಹಿಸಿ`), Malayalam (`അയയ്ക്കുക`), and Urdu (`بھیجیں`).
2. **Voice-Note & View-Once Blocker**: Strictly rejects mic buttons, `"View Once"`, and WhatsApp's locked hands-free recording bar (`voice_note_send`, `voice_note_trash`, timer + `"Pause"`/`"Discard"`).
3. **Hint-Placeholder Guard (`extractNonHintEntryText`)**: Prevents treating Android EditText hints (`"Message"`, `"Type a message"`, `"संदेश"`) as unsent typed text after dispatch succeeds.
4. **Media Preview Guard (`isMediaPreviewClassification`)**: Ensures plain text chats or historical media bubbles never trigger fallback coordinate taps on media preview controls.
5. **Stale Chat Header Verification (`doesOpenChatMatchRecipient`)**: Compares toolbar header contact/phone against both current target and previous recipient to abort accidental cross-recipient sends during slow transitions.
6. **Dialog-Only Invalid Recipient Detection**: Prevents campaign promo copy containing words like `"invalid"` inside message bubbles from triggering false error aborts; only real modal `AlertDialog` error popups fail the recipient.
7. **3×3 Pattern Unlock Geometry (`computePatternNodeCenters`)**: Computes exact screen touch coordinates across `Rect` grid bounds for automated Keyguard & WhatsApp App Lock pattern gestures.
8. **Live Multi-Picker (`WhatsAppPickerCoordinator`)**: Stays active across multiple chat taps inside WhatsApp, extracts E.164 phone or group title, deduplicates normalized contacts live, and emits the full batch on return to `WhatsBroadcast`.

---

## 6. Pan-India OEM Survival, Clones & Phone Normalization

### Indian OEM Matrix (`OemDetector` & `PermissionHealthManager`)
| OEM Family | Detected Brands / UI | Targeted System Actions & Guidance |
| :--- | :--- | :--- |
| **`XIAOMI`** | Xiaomi, Redmi, POCO (MIUI / HyperOS) | `AutoStartManagementActivity` (Autostart) + `PermissionsEditorActivity` (Background Pop-ups & Lock Screen) |
| **`OPPO_REALME_ONEPLUS`** | Oppo, Realme, OnePlus (ColorOS / Realme UI / OxygenOS) | ColorOS / Oplus `StartupAppListActivity` (Auto-launch & Associated launch) |
| **`VIVO_IQOO`** | Vivo, iQOO (Funtouch OS / OriginOS) | Vivo `BgStartUpManagerActivity` & iQOO `BgStartUpManager` (Autostart + High Background Power) |
| **`SAMSUNG`** | Samsung (One UI / One UI Core) | App Battery Unrestricted + Deep Sleeping Apps exclusion (`ACTION_APPLICATION_DETAILS_SETTINGS`) |
| **`OTHER`** | Motorola, Nothing, Google Pixel, Lava, Nokia, Tecno, Infinix | Standard Unrestricted Battery verification |
| **Android 13+ (API 33+)** | All OEMs | Dedicated `restricted_settings` guidance for sideloaded APK Accessibility toggle unlock |

`PermissionHealthManager.evaluate()` produces a `SystemHealthReport` scoring **5 critical gates** (`accessibilityEnabled`, `exactAlarmsAllowed`, `notificationsEnabled`, `fullScreenIntentAllowed`, `batteryOptimizationIgnored`), plus overlay status, keyguard security, and encrypted Vault readiness.

### Multi-Account Clone Discovery & Dispatch (`WhatsAppInstanceDiscovery` & `WhatsAppSender`)
- Enumerates official `com.whatsapp` and `com.whatsapp.w4b` across all `UserHandle` profiles via `LauncherApps.getActivityList()` + `UserManager.getSerialNumberForUser()` (detecting User `#0` Primary vs `#999` Dual App/App Cloner, `#95` Samsung Dual Messenger, `#10` Android Work Profile) **without requiring broad `QUERY_ALL_PACKAGES` permission** (using least-privilege manifest `<queries>`).
- **4-Tier Dispatch Fallback**:
  1. **Group / Picker Route**: `Intent.ACTION_SEND` (triggers WhatsApp "Send to..." picker, searched automatically by Accessibility).
  2. **Media Route**: `Intent.ACTION_SEND` + `EXTRA_STREAM` (`FileProvider` URI from `filesDir/campaign_media/`) + `jid = "<phone>@s.whatsapp.net"` + sanitized caption (capped cleanly at `1024` chars).
  3. **Direct Deep Link**: `whatsapp://send?phone=...&text=...` (binds `setPackage(targetPackage)` for User `0`, omits package for `userSerial > 0` so Accessibility resolves the OEM Dual App sheet).
  4. **Web Fallback Chain**: `https://api.whatsapp.com/send?...` ➔ `https://wa.me/...`

### Pan-India Phone Normalization & Template Variables (`MessageVariableParser`)
- **Supported Placeholders**: `{{name}}`, `{{first_name}}`, `{{company}}`, `{{phone}}`, `{{custom_tag}}`, `{{date}}`.
- **Indian/NRI Normalization**:
  - Converts Unicode regional numerals (Devanagari `०-९`, Bengali `০-৯`, Arabic-Indic) to ASCII.
  - Strips invisible Unicode directional/Bidi markers (`\u200E`, `\u202A`..`\u202C`, `\uFEFF`).
  - Fixes leading trunk zero (`09876543210` ➔ `919876543210`), `0091...`, and `+91 09876543210` typos.
  - Protects domestic 10-digit numbers starting with `91` (e.g., `9123456789` ➔ `919123456789`).
  - Preserves explicit `+` NRI country codes (`+971`, `+1`, `+44`, `+65`) and rejects emergency shortcodes (`100`, `112`).

---

## 7. Security Vault, Privacy & Notifications

### Keystore Encrypted Credential Vault (`KeystoreCryptoManager` + `VaultPreferences`)
- Generates/retrieves a 256-bit `AES/GCM/NoPadding` key (`128-bit` auth tag, randomized IV per encryption) inside `AndroidKeyStore` (`whatsbroadcast_vault_aes_v1`).
- Encrypts optional Device Lock (PIN / Password / 3×3 Pattern) and WhatsApp App Lock (PIN / 3×3 Pattern) as `"<ivBase64>:<cipherTextBase64>"` in Jetpack DataStore (`keystore_vault_prefs`).
- `migrateLegacyPlaintextIfNeeded()` automatically upgrades legacy plaintext `SharedPreferences` secrets into AES-256-GCM storage and permanently removes plaintext keys.
- `decryptForDispatchBlocking().use { ... }` returns a `DecryptedDispatchCredentials : AutoCloseable` holding transient `CharArray` buffers that are zero-filled (`'\u0000'`) immediately on `.close()`.

### Collision-Free Notifications (`NotificationHelper`)
- Manages distinct notification channels (`CHANNEL_CAMPAIGN_ALERTS`, `CHANNEL_MESSAGE_STATUS`, `CHANNEL_SCREEN_WAKE`, Service notification channel) with custom sound URI, max-volume playback option, and vibration toggles.
- Assigns non-overlapping stride-based notification IDs per campaign lifecycle (`missedNotificationId(id, attempt)`, `campaignSuccessNotificationId`, `campaignFailedNotificationId`, `campaignQueuedNotificationId`, `scheduledReadyNotificationId`).

---

## 8. Adaptive Compose UI, Daily Pacing & Build Automation

### Responsive UI Architecture
- `WhatsBroadcastApp` uses `BoxWithConstraints` to switch between bottom `NavigationBar` (`< 600.dp` phones) and left `NavigationRail` (`>= 600.dp` foldables/tablets), constrained to `widthIn(max = 1100.dp)` centered content.
- **UI Controls Supporting High-Volume Workflows**:
  - **24h Daily Pacing Advisor (`BroadcastViewModel.computeDailyPacingEstimate`)**: Estimates same-day cumulative dispatch runtime across recipients & anti-ban delays, highlighting overlap collisions, `>= 18h` heavy daily loads, and `>= 24h` over-capacity schedules (`CreateCampaignScreen`).
  - **FIFO Queue Management**: Real-time queued pills in `BroadcastProgressDialog`, Queue filter chip in `CampaignsScreen`, Queue cancel banners in `DashboardScreen` & `CampaignDetailScreen`.
  - **Privacy & Log Retention Controls**: Accessibility privacy card (`SystemHealthDialog` & `WhatsAppApiSettingsDialog`), `Auto-Disable Accessibility` toggle, instant `Disable Now` action, and `30d / 90d / 180d / Keep All` log retention selector with manual `Prune Now`.

### Automated Root APK Build Sync (`app/build.gradle.kts`)
`SyncApkToRootTask` hooks into `assemble*` and `package*` variants via `finalizedBy` and emits two files into `/apk/` on every successful build:
- `apk/WhatsBroadcast-<variant>.apk` — latest compiled variant APK.
- `apk/build-manifest.json` — lightweight JSON metadata (`applicationId`, `variant`, `versionName`, `versionCode`, `apkFile`, `sizeBytes`, `sha256`, `updatedAtUtc`).

---

## 9. Verification & Stress Test Architecture (`app/src/test/java/com/example/`)

| Test Suite | Verified Coverage |
| :--- | :--- |
| **`PanIndia5000UsersAllDevicesStressSimulationTest`** | 15-device Pan-India Android matrix (SDK 31–35), 5,000-user multi-script onboarding/dedupe/SQLite chunking, incoming call interrupts, Accessibility 13-language/safety guards, clone routing, 10-item FIFO queue, rolling 48h alarms, 90d log pruning, 24h pacing estimator & Compose UI at 5,000-step scale |
| **`IndianDevicesTwoYearStressSimulationTest`** | 2-year simulation (20 schedules/day × 500 recipients × 730 days = 14,600 campaigns), SQLite v3 index verification, O(1) repair, 48h cap over 500 schedules, collision-free notification IDs |
| **`IndiaOemAndDeviceStateTest`** | OEM detection, genuine WhatsApp/W4B discovery, call pause/idle safety, media staging, `BroadcastProgressDialog` Compose interaction |
| **`EnterpriseSecurityAndSchedulingTest`** | AES-256-GCM randomized IVs, tamper rejection, vault plaintext migration & zeroing, 15m boot grace classifier, `MIGRATION_1_2`, media orphan GC |
| **`WhatsAppAccessibilityLogicTest`** | 13 Indian scripts, View Once & locked voice-bar blockers, hint stripping, stale chat header mismatch, picker batch capture, dynamic package scoping |
| **`LargeScaleBroadcastRepositoryTest`** | 1,200+ contact SQLite chunking (>999 bind limit), truthful delivery counters, coordinator lock & recovery guard |
| **`IndianPhoneNormalizationTest`** | Indian/NRI formats, Devanagari/Bengali numerals, Bidi markers, trunk-`0` cleanup, shortcode rejection |
| **`ExampleRobolectricTest` / `GreetingScreenshotTest` / `ExampleUnitTest`** | App resources, variable parser, channels, caption truncation, Roborazzi Compose snapshot |
