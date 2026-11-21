package com.example.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.model.AccessibilityScopeMode
import com.example.data.model.AutomationStep
import com.example.data.model.LockType
import kotlinx.coroutines.CompletableDeferred

/**
 * Production hands-free zero-tap Accessibility automation engine hardened for pan-India OEMs:
 * - Banking-safe dynamic package scoping (zero events from UPI/banking when idle)
 * - Active 450ms task watchdog + multi-window scan so OEM choosers & static preview screens never stall
 * - Universal Primary & 2nd Clone chooser selection (Vivo II·WhatsApp, MIUI Dual App, ColorOS, Samsung)
 * - Two-phase OEM lockscreen unlock (Phase A: Swipe Up cover screen -> Phase B: PIN keypad/Password/3x3 Pattern)
 * - Multi-stage ContactPicker -> MediaPreview (auto-caption or text follow-up) -> Conversation Send -> Confirm Cleared
 * - 13-script Pan-India Send label matcher with Voice-note & View-Once blockers
 * - Post-broadcast WhatsApp Exit (BACK -> HOME) + Conditional Device Auto-Lock (GLOBAL_ACTION_LOCK_SCREEN)
 * - Live WhatsApp multi-contact & multi-group picker capture
 */
class WhatsAppAutoSendAccessibilityService : AccessibilityService() {

    private data class TaskStageTracker(
        val generation: Long,
        var chooserTappedAtMs: Long = 0L,
        var appLockAttemptedAtMs: Long = 0L,
        var pickerSearchTappedAtMs: Long = 0L,
        var pickerQueryEnteredAtMs: Long = 0L,
        var pickerRowTappedAtMs: Long = 0L,
        var pickerNextTappedAtMs: Long = 0L,
        var captionInjected: Boolean = false,
        var captionMissingOnMediaPreview: Boolean = false,
        var textInjected: Boolean = false,
        var finalSendTappedAtMs: Long = 0L,
        var finalSendTapCount: Int = 0,
        var popupDismissedAtMs: Long = 0L,
        var lastEvalAtMs: Long = 0L
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var stageTracker: TaskStageTracker? = null
    private var activeWatchdogRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        WhatsAppAutomationController.scopeUpdateListener = { mode ->
            applyDynamicPackageScope(mode)
        }
        WhatsAppAutomationController.taskStartedListener = { task ->
            mainHandler.post {
                resetStageForTask(task.generation)
                startTaskWatchdog(task.generation)
            }
        }
        WhatsAppAutomationController.disableSelfListener = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching { disableSelf() }
            }
        }
        WhatsAppAutomationController.twoPhaseUnlockListener = { deferred ->
            executeTwoPhaseDeviceUnlock(deferred)
        }
        WhatsAppAutomationController.exitAndLockListener = { exitToHome, lockDevice, deferred ->
            executeExitWhatsAppAndConditionalLock(exitToHome, lockDevice, deferred)
        }
        WhatsAppAutomationController.navigateHomeForInstanceSwitchListener = { deferred ->
            mainHandler.post {
                runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }
                mainHandler.postDelayed({ deferred.complete(true) }, 350L)
            }
        }
        WhatsAppAutomationController.onServiceConnected()
    }

    override fun onDestroy() {
        stopTaskWatchdog()
        mainHandler.removeCallbacksAndMessages(null)
        WhatsAppAutomationController.scopeUpdateListener = null
        WhatsAppAutomationController.taskStartedListener = null
        WhatsAppAutomationController.disableSelfListener = null
        WhatsAppAutomationController.twoPhaseUnlockListener = null
        WhatsAppAutomationController.exitAndLockListener = null
        WhatsAppAutomationController.navigateHomeForInstanceSwitchListener = null
        WhatsAppAutomationController.onServiceDisconnected()
        super.onDestroy()
    }

    override fun onInterrupt() {
        stopTaskWatchdog()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun applyDynamicPackageScope(mode: AccessibilityScopeMode) {
        val info = serviceInfo ?: return
        info.packageNames = WhatsAppAutomationController.resolveAllowedPackages(mode, packageName)
        runCatching { setServiceInfo(info) }
    }

    private fun resetStageForTask(generation: Long): TaskStageTracker {
        val tracker = TaskStageTracker(generation = generation)
        stageTracker = tracker
        return tracker
    }

    private fun trackerFor(generation: Long): TaskStageTracker {
        val current = stageTracker
        return if (current != null && current.generation == generation) {
            current
        } else {
            resetStageForTask(generation)
        }
    }

    private fun startTaskWatchdog(generation: Long) {
        stopTaskWatchdog()
        val runnable = object : Runnable {
            override fun run() {
                val task = WhatsAppAutomationController.activeTask
                if (task == null || task.generation != generation) {
                    activeWatchdogRunnable = null
                    return
                }
                evaluateActiveTaskWindows(task, fromWatchdog = true)
                if (WhatsAppAutomationController.activeTask?.generation == generation) {
                    mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
                } else {
                    activeWatchdogRunnable = null
                }
            }
        }
        activeWatchdogRunnable = runnable
        mainHandler.postDelayed(runnable, 320L)
    }

    private fun stopTaskWatchdog() {
        activeWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        activeWatchdogRunnable = null
    }

    // --- Two-Phase Indian OEM Lockscreen Unlock ---

    private fun executeTwoPhaseDeviceUnlock(resultDeferred: CompletableDeferred<Boolean>) {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km?.isKeyguardLocked != true) {
            resultDeferred.complete(true)
            return
        }

        val vault = VaultPreferences(applicationContext)
        val lockType = vault.decryptForDispatchBlocking().use { it.deviceLockType }
        val autoUnlockEnabled = vault.decryptForDispatchBlocking().use { it.autoUnlockEnabled }

        if (!autoUnlockEnabled || lockType == LockType.NONE) {
            performSlideUnlockGesture {
                mainHandler.postDelayed({
                    resultDeferred.complete(km.isKeyguardLocked.not())
                }, 700L)
            }
            return
        }

        BroadcastExecutionCoordinator.updateStep(
            AutomationStep.SWIPING_LOCKSCREEN,
            "Swiping cover lockscreen to reveal unlock bouncer..."
        )

        performSlideUnlockGesture {
            if (lockType == LockType.SLIDE) {
                mainHandler.postDelayed({
                    resultDeferred.complete(km.isKeyguardLocked.not())
                }, 700L)
                return@performSlideUnlockGesture
            }

            BroadcastExecutionCoordinator.updateStep(
                AutomationStep.UNLOCKING_KEYGUARD,
                "Unlocking device via encrypted ${lockType.label}..."
            )
            attemptBouncerUnlockWithRetries(km, attempt = 0, resultDeferred = resultDeferred)
        }
    }

    private fun attemptBouncerUnlockWithRetries(
        km: KeyguardManager,
        attempt: Int,
        resultDeferred: CompletableDeferred<Boolean>
    ) {
        if (resultDeferred.isCompleted) return
        if (!km.isKeyguardLocked) {
            resultDeferred.complete(true)
            return
        }
        if (attempt >= MAX_UNLOCK_ATTEMPTS) {
            resultDeferred.complete(false)
            return
        }

        mainHandler.postDelayed({
            if (resultDeferred.isCompleted) return@postDelayed
            if (!km.isKeyguardLocked) {
                resultDeferred.complete(true)
                return@postDelayed
            }
            val root = rootInActiveWindow
            if (root != null) {
                attemptVaultUnlock(root, isWhatsAppAppLock = false)
            }
            mainHandler.postDelayed({
                if (!km.isKeyguardLocked) {
                    resultDeferred.complete(true)
                } else {
                    attemptBouncerUnlockWithRetries(km, attempt + 1, resultDeferred)
                }
            }, 1_100L)
        }, if (attempt == 0) 750L else 900L)
    }

    // --- Post-Broadcast Exit WhatsApp & Conditional Lock ---

    private fun executeExitWhatsAppAndConditionalLock(
        exitToHome: Boolean,
        lockDevice: Boolean,
        resultDeferred: CompletableDeferred<Boolean>
    ) {
        stopTaskWatchdog()
        if (exitToHome) {
            BroadcastExecutionCoordinator.updateStep(
                AutomationStep.EXITING_WHATSAPP,
                "Exiting WhatsApp to Home screen..."
            )
            runCatching { performGlobalAction(GLOBAL_ACTION_BACK) }
            mainHandler.postDelayed({
                runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }
                if (lockDevice) {
                    scheduleScreenLock(resultDeferred)
                } else {
                    resultDeferred.complete(true)
                }
            }, 450L)
        } else if (lockDevice) {
            scheduleScreenLock(resultDeferred)
        } else {
            resultDeferred.complete(true)
        }
    }

    private fun scheduleScreenLock(resultDeferred: CompletableDeferred<Boolean>) {
        mainHandler.postDelayed({
            BroadcastExecutionCoordinator.updateStep(
                AutomationStep.LOCKING_DEVICE,
                "Auto-locking device..."
            )
            val locked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }.getOrDefault(false)
            } else {
                ScreenWakeManager.releaseWakeLock()
                true
            }
            resultDeferred.complete(locked)
        }, 550L)
    }

    // --- Core Accessibility Event + Multi-Window Evaluation Engine ---

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString().orEmpty()

        // 1. Live WhatsApp Multi-Contact & Group Picker Mode
        if (WhatsAppPickerCoordinator.state.value.isActive &&
            pkg in WhatsAppInstanceDiscovery.OFFICIAL_WHATSAPP_PACKAGES
        ) {
            rootInActiveWindow?.let { captureCurrentChatForPicker(it) }
            return
        }

        // 2. Active Zero-Tap Broadcast Dispatch Mode
        val task = WhatsAppAutomationController.activeTask ?: return
        evaluateActiveTaskWindows(task, fromWatchdog = false)
    }

    private fun evaluateActiveTaskWindows(task: ActiveDispatchTask, fromWatchdog: Boolean) {
        val now = SystemClock.uptimeMillis()
        val tracker = trackerFor(task.generation)
        if (!fromWatchdog && now - tracker.lastEvalAtMs < EVENT_DEBOUNCE_MS) {
            return
        }
        tracker.lastEvalAtMs = now

        val candidateRoots = collectInspectableWindowRoots()
        if (candidateRoots.isEmpty()) return

        // Stage 1: Detect & auto-tap "Complete action using" / OEM Dual-App chooser (Primary OR 2nd Clone!)
        for (windowRoot in candidateRoots) {
            val windowPkg = windowRoot.packageName?.toString().orEmpty()
            val summaryLower = collectWindowSummary(windowRoot, maxNodes = 80).lowercase()
            if (WhatsAppChooserAndScreenMatcher.looksLikeChooserWindow(windowPkg, summaryLower)) {
                if (now - tracker.chooserTappedAtMs >= CHOOSER_RETRY_COOLDOWN_MS) {
                    if (trySelectChooserTarget(windowRoot, task)) {
                        tracker.chooserTappedAtMs = now
                        BroadcastExecutionCoordinator.updateStep(
                            AutomationStep.CHOOSER_SELECTION,
                            if (task.isCloneTarget) {
                                "Selected 2nd Clone WhatsApp..."
                            } else {
                                "Selected Primary WhatsApp..."
                            }
                        )
                    }
                }
                return
            }
        }

        // Locate the active WhatsApp window root
        val waRoot = candidateRoots.firstOrNull { root ->
            root.packageName?.toString().orEmpty() in WhatsAppInstanceDiscovery.OFFICIAL_WHATSAPP_PACKAGES
        } ?: return

        // Stage 2: Check WhatsApp App Lock if present
        if (looksLikeWhatsAppAppLock(waRoot)) {
            if (now - tracker.appLockAttemptedAtMs >= 1_200L) {
                tracker.appLockAttemptedAtMs = now
                BroadcastExecutionCoordinator.updateStep(
                    AutomationStep.UNLOCKING_WHATSAPP_LOCK,
                    "Unlocking WhatsApp App Lock..."
                )
                attemptVaultUnlock(waRoot, isWhatsAppAppLock = true)
            }
            return
        }

        // Stage 3: Dialog-only invalid recipient check (never triggered by chat message copy)
        detectModalInvalidRecipientDialog(waRoot)?.let { dialogError ->
            WhatsAppAutomationController.completeActiveTask(
                task.generation,
                AutomationDispatchOutcome(false, dialogError)
            )
            return
        }

        // Stage 3b: Auto-dismiss non-error WhatsApp/Business promotional or nudge popups ("Not now" / "Later")
        if (now - tracker.popupDismissedAtMs >= 1_500L && tryDismissNonErrorWhatsAppPopup(waRoot)) {
            tracker.popupDismissedAtMs = now
            return
        }

        // Stage 4: Classify exact WhatsApp screen (ContactPicker vs MainChats vs MediaPreview vs Conversation)
        val screenKind = detectWhatsAppScreenKind(waRoot, task.hasMedia)
        when (screenKind) {
            WhatsAppScreenKind.CONTACT_PICKER,
            WhatsAppScreenKind.MAIN_CHATS -> {
                handleContactPickerScreen(waRoot, task, tracker, now)
            }
            WhatsAppScreenKind.MEDIA_PREVIEW -> {
                handleMediaPreviewScreen(waRoot, task, tracker, now)
            }
            WhatsAppScreenKind.CONVERSATION -> {
                handleConversationScreen(waRoot, task, tracker, now)
            }
            WhatsAppScreenKind.OTHER -> {
                // Handle simple media confirmation dialogs ("Send <file> to <name>?" -> Send / OK)
                if (task.hasMedia && now - tracker.finalSendTappedAtMs >= 1_200L) {
                    val confirmBtn = findDialogSendOrOkButton(waRoot)
                    if (confirmBtn != null && clickOrTapNode(confirmBtn)) {
                        tracker.finalSendTappedAtMs = now
                        tracker.captionMissingOnMediaPreview = true
                        scheduleMediaCompletionCheck(task, tracker)
                    }
                }
            }
        }
    }

    private fun collectInspectableWindowRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots.add(it) }
        runCatching {
            val interactiveWindows = windows
            if (interactiveWindows != null) {
                // Check higher-layer overlay / dialog windows first (such as OEM chooser bottom sheets)
                for (w in interactiveWindows.sortedByDescending { it.layer }) {
                    val r = w.root ?: continue
                    if (roots.none { it == r }) {
                        roots.add(r)
                    }
                }
            }
        }
        return roots
    }

    // --- Stage 1: Universal Primary & 2nd Clone Chooser Selection ---

    private fun trySelectChooserTarget(root: AccessibilityNodeInfo, task: ActiveDispatchTask): Boolean {
        val isBusinessTarget = task.targetPackage == WhatsAppInstanceDiscovery.PKG_WHATSAPP_BUSINESS
        val rootRect = Rect().also { root.getBoundsInScreen(it) }

        val nodeEntries = mutableListOf<Pair<AccessibilityNodeInfo, ChooserCandidate>>()
        val capturedBounds = mutableListOf<Rect>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 200) {
            val node = queue.removeFirst()
            visited++
            val rawLabel = node.text?.toString().orEmpty().trim()
            val rawDesc = node.contentDescription?.toString().orEmpty().trim()
            val quickMatch = WhatsAppChooserAndScreenMatcher.buildCandidate(rawLabel, rawDesc)
            if (quickMatch != null) {
                // P1 #4: Resolve the enclosing clickable tile container (up to 3 ancestor levels) and aggregate
                // all sibling badge text + contentDescription (e.g., "2nd", "Dual", "Twin", "II·") inside the tile
                // so OEM MRU reordering never loses the clone badge attached to a sibling ImageView/TextView.
                val tileContainer = findEnclosingChooserTileContainer(node, rootRect)
                val tileRect = Rect().also { tileContainer.getBoundsInScreen(it) }
                val alreadyCaptured = capturedBounds.any { existingRect ->
                    existingRect == tileRect || Rect.intersects(existingRect, tileRect)
                }
                if (!alreadyCaptured) {
                    val (aggText, aggDesc) = aggregateTileLabels(tileContainer)
                    val aggregatedCandidate = WhatsAppChooserAndScreenMatcher.buildCandidate(
                        aggText.ifBlank { rawLabel },
                        aggDesc.ifBlank { rawDesc }
                    ) ?: quickMatch
                    nodeEntries.add(tileContainer to aggregatedCandidate)
                    capturedBounds.add(tileRect)
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        if (nodeEntries.isEmpty()) {
            // Some choosers already have the icon selected and show a "Just once" button
            val justOnce = findJustOnceButton(root)
            return justOnce != null && clickOrTapNode(justOnce)
        }

        // Sort visually top-to-bottom, left-to-right so Primary (left) is index 0 and 2nd Clone (right) is index 1
        val sortedEntries = nodeEntries.sortedWith(
            compareBy<Pair<AccessibilityNodeInfo, ChooserCandidate>> { (n, _) ->
                Rect().also { n.getBoundsInScreen(it) }.top / 80
            }.thenBy { (n, _) ->
                Rect().also { n.getBoundsInScreen(it) }.left
            }
        )

        val chosenIdx = WhatsAppChooserAndScreenMatcher.selectChooserCandidateIndex(
            candidates = sortedEntries.map { it.second },
            isBusinessTarget = isBusinessTarget,
            isCloneTarget = task.isCloneTarget
        )
        if (chosenIdx < 0) return false

        val chosenNode = sortedEntries[chosenIdx].first
        val tapped = clickOrTapNode(chosenNode)
        if (tapped) {
            // If OEM chooser requires a follow-up "Just once" tap after selecting the app tile
            mainHandler.postDelayed({
                if (WhatsAppAutomationController.activeTask?.generation == task.generation) {
                    collectInspectableWindowRoots().forEach { r ->
                        findJustOnceButton(r)?.let { clickOrTapNode(it) }
                    }
                }
            }, 420L)
        }
        return tapped
    }

    private fun findEnclosingChooserTileContainer(
        node: AccessibilityNodeInfo,
        rootRect: Rect
    ): AccessibilityNodeInfo {
        var current: AccessibilityNodeInfo? = node
        var best: AccessibilityNodeInfo = node
        var depth = 0
        val maxTileWidth = if (rootRect.width() > 0) (rootRect.width() * 0.72f).toInt() else Int.MAX_VALUE
        while (current != null && depth < 3) {
            val rect = Rect().also { current.getBoundsInScreen(it) }
            val reasonableTile = rect.width() in 1..maxTileWidth && current.childCount <= 6
            if (current.isClickable && reasonableTile) {
                return current
            }
            if (reasonableTile && current.childCount in 1..4) {
                best = current
            }
            current = current.parent
            depth++
        }
        return best
    }

    private fun aggregateTileLabels(container: AccessibilityNodeInfo): Pair<String, String> {
        val texts = mutableListOf<String>()
        val descs = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(container)
        var visited = 0
        while (queue.isNotEmpty() && visited < 12) {
            val cur = queue.removeFirst()
            visited++
            cur.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }
            cur.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { descs.add(it) }
            for (i in 0 until cur.childCount) {
                cur.getChild(i)?.let { queue.add(it) }
            }
        }
        return texts.distinct().joinToString(" ") to descs.distinct().joinToString(" ")
    }

    private fun findJustOnceButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName.orEmpty().lowercase()
            val text = node.text?.toString().orEmpty().trim().lowercase()
            if ((id.endsWith(":id/button_once") || text == "just once" || text == "once") && node.isEnabled) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    // --- Stage 4: Screen Classification & Multi-Stage Automation ---

    private fun detectWhatsAppScreenKind(root: AccessibilityNodeInfo, hasMediaTask: Boolean): WhatsAppScreenKind {
        val hasConversationHeader = findNodeByViewIdSuffix(root, "conversation_contact_name") != null
        val hasEntryBox = findNodeByViewIdSuffix(root, "entry") != null
        val hasCaptionBox = findMediaCaptionInput(root) != null

        val summaryLower = collectWindowSummary(root, maxNodes = 70).lowercase()
        val hasMediaPreviewControls = findNodeByViewIdSuffix(root, "media_preview") != null ||
            findNodeByViewIdSuffix(root, "add_media") != null ||
            findNodeByViewIdSuffix(root, "caption_entry") != null ||
            findNodeByViewIdSuffix(root, "crop_button") != null ||
            findNodeByViewIdSuffix(root, "doodle_button") != null ||
            summaryLower.contains("add a caption") ||
            summaryLower.contains("कैप्शन")

        val hasContactPickerSignals = findNodeByViewIdSuffix(root, "search_src_text") != null ||
            findNodeByViewIdSuffix(root, "menuitem_search") != null ||
            findNodeByViewIdSuffix(root, "selected_list") != null ||
            summaryLower.contains("send to") ||
            summaryLower.contains("forward to") ||
            summaryLower.contains("frequently contacted") ||
            summaryLower.contains("recent chats") ||
            summaryLower.contains("other contacts")

        val hasMainChatsSignals = !hasConversationHeader && !hasEntryBox && (
            (summaryLower.contains("chats") &&
                (summaryLower.contains("updates") ||
                    summaryLower.contains("calls") ||
                    summaryLower.contains("status") ||
                    summaryLower.contains("communities"))) ||
                (findNodeByViewIdSuffix(root, "fab") != null && findNodeByViewIdSuffix(root, "menuitem_search") != null)
            )

        return WhatsAppChooserAndScreenMatcher.classifyWhatsAppScreen(
            hasConversationHeader = hasConversationHeader,
            hasEntryBox = hasEntryBox,
            hasCaptionBox = hasCaptionBox,
            hasMediaPreviewControls = hasMediaPreviewControls,
            hasContactPickerSignals = hasContactPickerSignals,
            hasMediaTask = hasMediaTask,
            hasMainChatsSignals = hasMainChatsSignals
        )
    }

    private fun tryDismissNonErrorWhatsAppPopup(root: AccessibilityNodeInfo): Boolean {
        val dismissLabels = setOf("not now", "later", "skip", "dismiss", "no thanks")
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        var candidateDismissButton: AccessibilityNodeInfo? = null
        var sawDialogMarker = false

        while (queue.isNotEmpty() && visited < 80) {
            val node = queue.removeFirst()
            visited++
            val cls = node.className?.toString().orEmpty()
            val id = node.viewIdResourceName.orEmpty().lowercase()
            val text = node.text?.toString().orEmpty().trim().lowercase()

            if (cls.contains("Dialog", ignoreCase = true) ||
                id.contains("alerttitle") ||
                id.endsWith(":id/button2") ||
                id.contains("bottom_sheet")
            ) {
                sawDialogMarker = true
            }

            if (candidateDismissButton == null && text in dismissLabels && node.isEnabled) {
                candidateDismissButton = node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        if (sawDialogMarker && candidateDismissButton != null) {
            return clickOrTapNode(candidateDismissButton)
        }
        return false
    }

    private fun handleContactPickerScreen(
        root: AccessibilityNodeInfo,
        task: ActiveDispatchTask,
        tracker: TaskStageTracker,
        now: Long
    ) {
        BroadcastExecutionCoordinator.updateStep(
            AutomationStep.SEARCHING_RECIPIENT,
            "Selecting ${task.recipientName} in WhatsApp..."
        )

        val searchQuery = task.recipientName.trim().ifBlank {
            task.recipientPhone.filter { it.isDigit() }.takeLast(10)
        }

        // Sub-step 3: Row already selected -> click ContactPicker's green Next / Forward FAB
        if (tracker.pickerRowTappedAtMs > 0L) {
            if (now - tracker.pickerNextTappedAtMs >= 1_200L) {
                val pickerNextFab = findSafeSendButton(root, hasMedia = true)
                    ?: findPickerNextButton(root)
                if (pickerNextFab != null && clickOrTapNode(pickerNextFab)) {
                    tracker.pickerNextTappedAtMs = now
                    BroadcastExecutionCoordinator.updateStep(
                        AutomationStep.OPENING_CHAT,
                        "Opening chat for ${task.recipientName}..."
                    )
                }
            }
            return
        }

        // Sub-step 1 & 2: Ensure search box is open, type target name, then tap matching row
        val searchBox = findPickerSearchInput(root)
        if (searchBox == null) {
            if (now - tracker.pickerSearchTappedAtMs >= 900L) {
                val searchIcon = findPickerSearchIcon(root)
                if (searchIcon != null && clickOrTapNode(searchIcon)) {
                    tracker.pickerSearchTappedAtMs = now
                    return
                }
            }
            // Fallback: if recipient is already visible in "Frequently contacted" / "Recent chats", tap directly
            val directRow = findMatchingPickerRecipientRow(root, searchQuery, task.recipientPhone)
            if (directRow != null && clickOrTapNode(directRow)) {
                tracker.pickerRowTappedAtMs = now
            }
            return
        }

        val currentQuery = extractNonHintEntryText(searchBox.text?.toString(), searchBox.hintText?.toString())
        if (!currentQuery.equals(searchQuery, ignoreCase = true)) {
            if (now - tracker.pickerQueryEnteredAtMs >= 800L) {
                searchBox.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, searchQuery)
                }
                searchBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                tracker.pickerQueryEnteredAtMs = now
            }
            return
        }

        // Search query matches -> wait briefly for filter results then tap the matching chat/group row
        if (now - tracker.pickerQueryEnteredAtMs < 350L) return
        val matchingRow = findMatchingPickerRecipientRow(root, searchQuery, task.recipientPhone)
        if (matchingRow != null && clickOrTapNode(matchingRow)) {
            tracker.pickerRowTappedAtMs = now
        }
    }

    private fun handleMediaPreviewScreen(
        root: AccessibilityNodeInfo,
        task: ActiveDispatchTask,
        tracker: TaskStageTracker,
        now: Long
    ) {
        // 1. Verify or paste caption when campaign has message text
        if (task.message.isNotBlank() && !tracker.captionInjected && !tracker.captionMissingOnMediaPreview) {
            val captionField = findMediaCaptionInput(root)
            if (captionField != null) {
                val currentCaption = extractNonHintEntryText(
                    captionField.text?.toString(),
                    captionField.hintText?.toString()
                )
                if (currentCaption.isEmpty()) {
                    BroadcastExecutionCoordinator.updateStep(
                        AutomationStep.FILLING_CAPTION,
                        "Pasting caption for ${task.recipientName}..."
                    )
                    captionField.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            MessageVariableParser.sanitizeMediaCaption(task.message, maxChars = 1024)
                        )
                    }
                    captionField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    tracker.captionInjected = true
                    return // Let UI settle one watchdog tick (450ms) before tapping Send
                } else {
                    tracker.captionInjected = true
                }
            } else {
                // No caption field on this media preview (e.g., certain document/PDF previews) -> send text right after
                tracker.captionMissingOnMediaPreview = true
            }
        }

        // 2. Click final Media Preview Send button
        if (now - tracker.finalSendTappedAtMs < 1_100L) return
        val sendNode = findSafeSendButton(root, hasMedia = true)
        if (sendNode != null) {
            BroadcastExecutionCoordinator.updateStep(
                AutomationStep.PASTING_AND_SENDING,
                "Sending media to ${task.recipientName}..."
            )
            if (clickOrTapNode(sendNode)) {
                tracker.finalSendTappedAtMs = now
                tracker.finalSendTapCount++
                scheduleMediaCompletionCheck(task, tracker)
            }
        }
    }

    private fun scheduleMediaCompletionCheck(task: ActiveDispatchTask, tracker: TaskStageTracker) {
        val gen = task.generation
        mainHandler.postDelayed({
            if (WhatsAppAutomationController.activeTask?.generation != gen) return@postDelayed
            val currentRoot = rootInActiveWindow
            val stillOnMediaPreview = currentRoot != null &&
                detectWhatsAppScreenKind(currentRoot, hasMediaTask = true) == WhatsAppScreenKind.MEDIA_PREVIEW

            if (stillOnMediaPreview && tracker.finalSendTapCount < 2) {
                // Retry Send button click once if preview didn't dismiss yet
                findSafeSendButton(currentRoot, hasMedia = true)?.let { clickOrTapNode(it) }
                tracker.finalSendTapCount++
                mainHandler.postDelayed({
                    if (WhatsAppAutomationController.activeTask?.generation == gen) {
                        WhatsAppAutomationController.completeActiveTask(
                            gen,
                            AutomationDispatchOutcome(
                                success = true,
                                needsTextFollowUp = tracker.captionMissingOnMediaPreview && task.message.isNotBlank()
                            )
                        )
                    }
                }, 900L)
            } else {
                WhatsAppAutomationController.completeActiveTask(
                    gen,
                    AutomationDispatchOutcome(
                        success = true,
                        needsTextFollowUp = tracker.captionMissingOnMediaPreview && task.message.isNotBlank()
                    )
                )
            }
        }, 1_050L)
    }

    private fun handleConversationScreen(
        root: AccessibilityNodeInfo,
        task: ActiveDispatchTask,
        tracker: TaskStageTracker,
        now: Long
    ) {
        // Guard against stale conversation header for 1:1 deep links
        if (!task.isGroup) {
            val headerTitle = extractChatHeaderTitle(root)
            if (headerTitle != null && !doesOpenChatMatchRecipient(
                    headerTitle = headerTitle,
                    expectedName = task.recipientName,
                    expectedPhone = task.recipientPhone,
                    previousName = task.previousRecipientName,
                    previousPhone = task.previousRecipientPhone
                )
            ) {
                return
            }
        }

        // If message box is empty (e.g., after ContactPicker or if WhatsApp dropped deep-link text), inject text!
        if (!task.hasMedia && task.message.isNotBlank() && !tracker.textInjected && tracker.finalSendTapCount == 0) {
            val entryBox = findNodeByViewIdSuffix(root, "entry")
            if (entryBox != null) {
                val existingText = extractNonHintEntryText(
                    entryBox.text?.toString(),
                    entryBox.hintText?.toString()
                )
                if (existingText.isEmpty()) {
                    BroadcastExecutionCoordinator.updateStep(
                        AutomationStep.PASTING_AND_SENDING,
                        "Pasting message for ${task.recipientName}..."
                    )
                    entryBox.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, task.message)
                    }
                    entryBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    tracker.textInjected = true
                    return // Let WhatsApp swap Microphone icon to Send button on next tick
                } else {
                    tracker.textInjected = true
                }
            }
        }

        // Click final Conversation Send button
        if (now - tracker.finalSendTappedAtMs < 1_000L) return
        val sendNode = findSafeSendButton(root, hasMedia = task.hasMedia)
        if (sendNode != null) {
            BroadcastExecutionCoordinator.updateStep(
                AutomationStep.PASTING_AND_SENDING,
                "Sending message to ${task.recipientName}..."
            )
            if (clickOrTapNode(sendNode)) {
                tracker.finalSendTappedAtMs = now
                tracker.finalSendTapCount++
                val gen = task.generation
                mainHandler.postDelayed({
                    if (WhatsAppAutomationController.activeTask?.generation == gen) {
                        confirmSentAndComplete(gen, task.hasMedia)
                    }
                }, 850L)
            }
        }
    }

    private fun confirmSentAndComplete(generation: Long, hasMedia: Boolean) {
        val currentRoot = rootInActiveWindow
        if (currentRoot != null && !hasMedia) {
            val entry = findNodeByViewIdSuffix(currentRoot, "entry")
            val remainingText = extractNonHintEntryText(entry?.text?.toString(), entry?.hintText?.toString())
            if (remainingText.isNotEmpty()) {
                // Retry one final click if message text did not clear on first tap
                findSafeSendButton(currentRoot, hasMedia = false)?.let { clickOrTapNode(it) }
                mainHandler.postDelayed({
                    if (WhatsAppAutomationController.activeTask?.generation == generation) {
                        WhatsAppAutomationController.completeActiveTask(
                            generation,
                            AutomationDispatchOutcome(true)
                        )
                    }
                }, 700L)
                return
            }
        }
        WhatsAppAutomationController.completeActiveTask(
            generation,
            AutomationDispatchOutcome(true)
        )
    }

    // --- Live WhatsApp Multi-Contact & Group Picker Capture ---

    private fun captureCurrentChatForPicker(root: AccessibilityNodeInfo) {
        val headerTitle = extractChatHeaderTitle(root) ?: return
        val subtitle = extractChatSubtitle(root).orEmpty()

        val looksLikeGroup = subtitle.contains(",") ||
            subtitle.contains("tap here for group info", ignoreCase = true) ||
            subtitle.contains("members", ignoreCase = true) ||
            subtitle.contains("participants", ignoreCase = true)

        if (looksLikeGroup) {
            WhatsAppPickerCoordinator.recordCapturedGroup(headerTitle)
        } else {
            val phoneCandidate = listOf(headerTitle, subtitle)
                .firstNotNullOfOrNull { MessageVariableParser.normalizeIndianOrNriPhone(it) }
            if (phoneCandidate != null) {
                WhatsAppPickerCoordinator.recordCapturedContact(headerTitle, phoneCandidate)
            } else {
                // Keep exact chat title so our ContactPicker search opens this conversation cleanly
                WhatsAppPickerCoordinator.recordCapturedGroup(headerTitle)
            }
        }
    }

    // --- Vault Unlock (PIN / Pattern / Password / Slide) ---

    private fun attemptVaultUnlock(root: AccessibilityNodeInfo, isWhatsAppAppLock: Boolean) {
        val vault = VaultPreferences(applicationContext)
        vault.decryptForDispatchBlocking().use { creds ->
            if (!creds.autoUnlockEnabled) return
            val lockType = if (isWhatsAppAppLock) creds.whatsAppLockType else creds.deviceLockType
            val secret = if (isWhatsAppAppLock) creds.whatsAppSecret else creds.deviceSecret

            when (lockType) {
                LockType.NONE -> return
                LockType.SLIDE -> performSlideUnlockGesture()
                LockType.PIN -> {
                    val digits = secret?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() } ?: return
                    val keypadTappedAll = digits.all { digit ->
                        val keyNode = findKeypadDigitNode(root, digit)
                        keyNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                    }
                    if (keypadTappedAll) {
                        findConfirmKeyNode(root)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    } else {
                        val inputField = findPasswordOrPinField(root)
                        if (inputField != null) {
                            val args = Bundle().apply {
                                putCharSequence(
                                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                    String(digits.toCharArray())
                                )
                            }
                            inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                            findConfirmKeyNode(root)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }
                    }
                }
                LockType.PASSWORD -> {
                    val value = secret?.concatToString()?.takeIf { it.isNotBlank() } ?: return
                    val inputField = findPasswordOrPinField(root)
                    if (inputField != null) {
                        val args = Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                        }
                        inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        findConfirmKeyNode(root)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                }
                LockType.PATTERN -> {
                    val patternStr = secret?.concatToString()?.takeIf { it.isNotBlank() } ?: return
                    val nodes = patternStr.split(",").mapNotNull { it.trim().toIntOrNull() }
                    val patternView = findPatternLockBounds(root)
                    if (nodes.size >= 4 && patternView != null) {
                        performPatternGesture(patternView, nodes)
                    }
                }
            }
        }
    }

    private fun performSlideUnlockGesture(onCompleted: (() -> Unit)? = null) {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.5f
        val startY = metrics.heightPixels * 0.84f
        val endY = metrics.heightPixels * 0.22f
        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 380L))
            .build()
        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onCompleted?.invoke()
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onCompleted?.invoke()
                }
            },
            mainHandler
        )
        if (!dispatched) {
            onCompleted?.invoke()
        }
    }

    private fun performPatternGesture(bounds: Rect, nodeIndices: List<Int>) {
        val centers = computePatternNodeCenters(bounds)
        val path = Path()
        nodeIndices.forEachIndexed { idx, cell ->
            val pt = centers.getOrNull(cell) ?: return@forEachIndexed
            if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 680L))
            .build()
        dispatchGesture(gesture, null, null)
    }

    // --- Node Finders & Safe Click / Coordinate Tap Fallback ---

    private fun clickOrTapNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 6) {
            if (current.isClickable && current.isEnabled) {
                if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
            current = current.parent
            depth++
        }
        // Fallback: dispatch precise single tap at the center of the node's screen bounds
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty || bounds.width() <= 2 || bounds.height() <= 2) return false
        val path = Path().apply {
            moveTo(bounds.exactCenterX(), bounds.exactCenterY())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 80L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun findPickerSearchInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findNodeByViewIdSuffix(root, "search_src_text")
            ?: findNodeByViewIdSuffix(root, "search_input")
            ?: findFirstEditableMatchingHint(root, listOf("search", "खोज", "शोध", "খুঁজ"))
    }

    private fun findPickerSearchIcon(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        findNodeByViewIdSuffix(root, "menuitem_search")?.let { return it }
        findNodeByViewIdSuffix(root, "search_icon")?.let { return it }
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val desc = node.contentDescription?.toString().orEmpty().trim().lowercase()
            if (desc == "search" || desc == "search..." || desc == "खोजें" || desc == "शोध") {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findMatchingPickerRecipientRow(
        root: AccessibilityNodeInfo,
        queryName: String,
        phone: String
    ): AccessibilityNodeInfo? {
        val cleanName = queryName.trim().lowercase()
        val phoneLast10 = phone.filter { it.isDigit() }.takeLast(10)
        val ignoredHeaders = setOf(
            "frequently contacted", "recent chats", "other contacts", "contacts on whatsapp",
            "send to", "send to…", "send to...", "forward to", "chats", "search"
        )

        var exactMatch: AccessibilityNodeInfo? = null
        var partialMatch: AccessibilityNodeInfo? = null

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (!node.isEditable) {
                val id = node.viewIdResourceName.orEmpty().lowercase()
                val text = node.text?.toString().orEmpty().trim()
                val textLower = text.lowercase()
                val textDigits = text.filter { it.isDigit() }
                val isSearchBarNode = id.contains("search_src_text") || id.contains("search_input")

                if (!isSearchBarNode && text.isNotEmpty() && textLower !in ignoredHeaders) {
                    if (cleanName.isNotEmpty() && textLower == cleanName) {
                        exactMatch = node
                        break
                    }
                    if (partialMatch == null && (
                            (cleanName.isNotEmpty() && textLower.contains(cleanName)) ||
                                (phoneLast10.length >= 8 && textDigits.endsWith(phoneLast10))
                            )
                    ) {
                        partialMatch = node
                    }
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return exactMatch ?: partialMatch
    }

    private fun findPickerNextButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        findNodeByViewIdSuffix(root, "next_btn")?.let { return it }
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val desc = node.contentDescription?.toString().orEmpty().trim().lowercase()
            if ((desc == "next" || desc == "forward" || desc == "send") && node.isEnabled) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findMediaCaptionInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findNodeByViewIdSuffix(root, "caption")
            ?: findNodeByViewIdSuffix(root, "media_caption")
            ?: findNodeByViewIdSuffix(root, "caption_entry")
            ?: findFirstEditableMatchingHint(root, listOf("caption", "कैप्शन", "ক্যাপশন"))
    }

    private fun findFirstEditableMatchingHint(
        root: AccessibilityNodeInfo,
        hintKeywords: List<String>
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isEditable) {
                val hint = "${node.hintText?.toString().orEmpty()} ${node.text?.toString().orEmpty()}".lowercase()
                if (hintKeywords.any { hint.contains(it) }) {
                    return node
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findDialogSendOrOkButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName.orEmpty().lowercase()
            val text = node.text?.toString().orEmpty().trim().lowercase()
            if ((id.endsWith(":id/button1") && (text == "send" || text == "ok")) ||
                (node.isClickable && text in PAN_INDIA_SEND_LABELS)
            ) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findKeypadDigitNode(root: AccessibilityNodeInfo, digit: Char): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isClickable && matchesKeypadDigit(
                    viewId = node.viewIdResourceName.orEmpty(),
                    contentDescription = node.contentDescription?.toString().orEmpty(),
                    text = node.text?.toString().orEmpty(),
                    digit = digit
                )
            ) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun looksLikeWhatsAppAppLock(root: AccessibilityNodeInfo): Boolean {
        val text = collectWindowSummary(root, maxNodes = 40).lowercase()
        return text.contains("whatsapp locked") ||
            text.contains("unlock whatsapp") ||
            text.contains("confirm fingerprint") ||
            text.contains("enter app lock")
    }

    private fun findPasswordOrPinField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isEditable || node.isPassword) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findConfirmKeyNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName.orEmpty().lowercase()
            val desc = node.contentDescription?.toString().orEmpty().lowercase()
            val text = node.text?.toString().orEmpty().lowercase()
            if (id.contains("key_enter") || id.contains("btn_ok") ||
                desc == "enter" || desc == "ok" || desc == "done" ||
                text == "ok" || text == "enter" || text == "done"
            ) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findPatternLockBounds(root: AccessibilityNodeInfo): Rect? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName.orEmpty().lowercase()
            val cls = node.className?.toString().orEmpty().lowercase()
            if (id.contains("lockpatternview") || cls.contains("lockpatternview") || id.contains("pattern_view")) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (!rect.isEmpty) return rect
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun extractChatHeaderTitle(root: AccessibilityNodeInfo): String? {
        val node = findNodeByViewIdSuffix(root, "conversation_contact_name")
            ?: findNodeByViewIdSuffix(root, "contact_name")
        return node?.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extractChatSubtitle(root: AccessibilityNodeInfo): String? {
        val node = findNodeByViewIdSuffix(root, "conversation_contact_status")
            ?: findNodeByViewIdSuffix(root, "subtitle")
        return node?.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun findSafeSendButton(root: AccessibilityNodeInfo, hasMedia: Boolean): AccessibilityNodeInfo? {
        var fallbackMatch: AccessibilityNodeInfo? = null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName.orEmpty().lowercase()
            val desc = node.contentDescription?.toString().orEmpty()
            val text = node.text?.toString().orEmpty()

            if (isBlockedVoiceOrViewOnceControl(id, desc, text)) {
                continue
            }

            if (isSafeSendLabelOrId(id, desc, text, hasMedia) && node.isEnabled) {
                if (node.isClickable) {
                    return node
                }
                if (fallbackMatch == null && hasClickableAncestor(node)) {
                    fallbackMatch = node
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return fallbackMatch
    }

    private fun hasClickableAncestor(node: AccessibilityNodeInfo): Boolean {
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 4) {
            if (parent.isClickable && parent.isEnabled) return true
            parent = parent.parent
            depth++
        }
        return false
    }

    private fun detectModalInvalidRecipientDialog(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var insideAlertDialog = false
        val dialogTexts = mutableListOf<String>()

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val cls = node.className?.toString().orEmpty()
            val id = node.viewIdResourceName.orEmpty()
            if (cls.contains("AlertDialog", ignoreCase = true) || id.contains("alertTitle") || id.endsWith(":id/message")) {
                insideAlertDialog = true
            }
            if (insideAlertDialog && !node.text.isNullOrBlank()) {
                dialogTexts.add(node.text.toString())
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return classifyInvalidRecipientDialog(insideAlertDialog, dialogTexts)
    }

    private fun findNodeByViewIdSuffix(root: AccessibilityNodeInfo, suffix: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val resId = node.viewIdResourceName
            if (resId != null && (resId.endsWith(":id/$suffix") || resId.endsWith("/$suffix"))) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun collectWindowSummary(root: AccessibilityNodeInfo, maxNodes: Int = 50): String {
        val sb = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < maxNodes) {
            val node = queue.removeFirst()
            visited++
            node.text?.let { sb.append(it).append(' ') }
            node.contentDescription?.let { sb.append(it).append(' ') }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return sb.toString()
    }

    companion object {
        const val WATCHDOG_INTERVAL_MS = 450L
        const val EVENT_DEBOUNCE_MS = 180L
        const val CHOOSER_RETRY_COOLDOWN_MS = 1_200L
        const val MAX_UNLOCK_ATTEMPTS = 3

        val PAN_INDIA_SEND_LABELS = setOf(
            "send",
            "भेजें", "सेंड करें",
            "पाठवा",
            "পাঠান",
            "ભેજો", "મોકલો",
            "ਭੇਜੋ",
            "ପଠାନ୍ତୁ",
            "పంపు",
            "அனுப்பு",
            "ಕಳುಹಿಸಿ",
            "അയയ്ക്കുക",
            "بھیجیں"
        )

        private val COMMON_ENTRY_HINTS = setOf(
            "message", "type a message", "add a caption", "add a caption...", "add a caption…",
            "search", "search...", "search…", "संदेश", "मैसेज लिखें", "বার্তা", "સંદેશ"
        )

        fun matchesKeypadDigit(
            viewId: String,
            contentDescription: String,
            text: String,
            digit: Char
        ): Boolean {
            if (!digit.isDigit()) return false
            val idLower = viewId.lowercase()
            val d = digit.toString()
            if (idLower.endsWith(":id/key$d") ||
                idLower.endsWith(":id/digit_$d") ||
                idLower.endsWith(":id/num_$d") ||
                idLower.endsWith(":id/btn_$d")
            ) {
                return true
            }
            val descTrim = contentDescription.trim()
            val textTrim = text.trim()
            return descTrim == d || textTrim == d
        }

        fun isBlockedVoiceOrViewOnceControl(
            viewId: String,
            contentDescription: String,
            text: String
        ): Boolean {
            val combined = "$viewId $contentDescription $text".lowercase()
            return combined.contains("voice_note") ||
                combined.contains("voice message") ||
                combined.contains("record") ||
                combined.contains("microphone") ||
                combined.contains("view once") ||
                combined.contains("view_once") ||
                combined.contains("discard") ||
                combined.contains("voice_note_trash") ||
                combined.contains("voice_note_send")
        }

        fun isSafeSendLabelOrId(
            viewId: String,
            contentDescription: String,
            text: String,
            hasMedia: Boolean
        ): Boolean {
            if (isBlockedVoiceOrViewOnceControl(viewId, contentDescription, text)) return false
            val idLower = viewId.lowercase()
            if (idLower.endsWith(":id/send") ||
                idLower.endsWith(":id/send_button") ||
                idLower.endsWith(":id/send_media_btn")
            ) {
                return true
            }
            if (hasMedia && isMediaPreviewClassification(viewId, contentDescription)) {
                return true
            }
            val descNorm = contentDescription.trim().lowercase()
            val textNorm = text.trim().lowercase()
            return PAN_INDIA_SEND_LABELS.any { label ->
                descNorm == label || textNorm == label
            }
        }

        fun isMediaPreviewClassification(viewId: String, contentDescription: String): Boolean {
            val id = viewId.lowercase()
            val desc = contentDescription.lowercase()
            return (id.contains("media_preview") || id.contains("caption_send")) &&
                !desc.contains("view once")
        }

        fun extractNonHintEntryText(rawText: String?, hintText: String?): String {
            val text = rawText?.trim().orEmpty()
            if (text.isEmpty()) return ""
            if (!hintText.isNullOrBlank() && text.equals(hintText.trim(), ignoreCase = true)) {
                return ""
            }
            if (text.lowercase() in COMMON_ENTRY_HINTS) return ""
            return text
        }

        fun doesOpenChatMatchRecipient(
            headerTitle: String,
            expectedName: String,
            expectedPhone: String,
            previousName: String?,
            previousPhone: String?
        ): Boolean {
            val headerClean = headerTitle.trim().lowercase()
            val headerDigits = headerTitle.filter { it.isDigit() }
            val expectedNameClean = expectedName.trim().lowercase()
            val expectedDigits = expectedPhone.filter { it.isDigit() }.takeLast(10)

            val matchesExpected = (expectedNameClean.isNotEmpty() && headerClean.contains(expectedNameClean)) ||
                (expectedDigits.length >= 8 && headerDigits.endsWith(expectedDigits))

            if (matchesExpected) return true

            val prevNameClean = previousName?.trim()?.lowercase().orEmpty()
            val prevDigits = previousPhone?.filter { it.isDigit() }?.takeLast(10).orEmpty()
            val matchesPrevious = (prevNameClean.isNotEmpty() && headerClean.contains(prevNameClean)) ||
                (prevDigits.length >= 8 && headerDigits.endsWith(prevDigits))

            return !matchesPrevious
        }

        fun classifyInvalidRecipientDialog(
            isAlertDialogContext: Boolean,
            dialogTexts: List<String>
        ): String? {
            if (!isAlertDialogContext) return null
            val merged = dialogTexts.joinToString(" ").lowercase()
            return when {
                merged.contains("isn't on whatsapp") ||
                    merged.contains("not on whatsapp") ||
                    merged.contains("not registered") ||
                    merged.contains("phone number shared via url is invalid") -> {
                    "Recipient number is not registered on WhatsApp"
                }
                else -> null
            }
        }

        fun computePatternNodeCenters(gridBounds: Rect): List<PointF> {
            val cellW = gridBounds.width() / 3f
            val cellH = gridBounds.height() / 3f
            return List(9) { index ->
                val row = index / 3
                val col = index % 3
                PointF(
                    gridBounds.left + (col + 0.5f) * cellW,
                    gridBounds.top + (row + 0.5f) * cellH
                )
            }
        }
    }
}
