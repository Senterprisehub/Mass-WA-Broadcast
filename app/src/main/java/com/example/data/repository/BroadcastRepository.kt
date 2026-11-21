package com.example.data.repository

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import com.example.data.local.AppDatabase
import com.example.data.model.AutomationStep
import com.example.data.model.BroadcastLogEntry
import com.example.data.model.Campaign
import com.example.data.model.CampaignDashboardAggregate
import com.example.data.model.CampaignStatus
import com.example.data.model.Contact
import com.example.data.model.ContactGroup
import com.example.data.model.DailyPacingEstimate
import com.example.data.model.DeliveryStatus
import com.example.data.model.MessageTemplate
import com.example.util.AppSettingsPreferences
import com.example.util.BroadcastExecutionCoordinator
import com.example.util.CallDetectionManager
import com.example.util.CampaignScheduler
import com.example.util.DateUtils
import com.example.util.MediaAttachmentHelper
import com.example.util.MessageVariableParser
import com.example.util.NotificationHelper
import com.example.util.ScreenWakeManager
import com.example.util.WhatsAppAutomationController
import com.example.util.WhatsAppSender
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock

class BroadcastRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val settingsPreferences: AppSettingsPreferences = AppSettingsPreferences(context)
) {

    companion object {
        const val SQLITE_BIND_BATCH_SIZE = 500
        const val COUNT_FLUSH_BATCH_SIZE = 10
    }

    private val contactDao = database.contactDao()
    private val campaignDao = database.campaignDao()
    private val logDao = database.broadcastLogDao()
    private val templateDao = database.templateDao()

    val groupsFlow: Flow<List<ContactGroup>> = contactDao.observeGroups()
    val contactsFlow: Flow<List<Contact>> = contactDao.observeContacts()
    val contactCountFlow: Flow<Int> = contactDao.observeContactCount()
    val campaignsFlow: Flow<List<Campaign>> = campaignDao.observeCampaigns()
    val dashboardAggregateFlow: Flow<CampaignDashboardAggregate> = campaignDao.observeDashboardAggregate()
    val templatesFlow: Flow<List<MessageTemplate>> = templateDao.observeTemplates()
    val totalLogCountFlow: Flow<Int> = logDao.observeTotalLogCount()

    fun observeCampaignById(id: Long): Flow<Campaign?> = campaignDao.observeCampaignById(id)
    fun observeLogsForCampaign(campaignId: Long): Flow<List<BroadcastLogEntry>> =
        logDao.observeLogsForCampaign(campaignId)

    suspend fun seedStarterTemplatesIfEmpty() {
        val existingTemplates = templateDao.getAllTemplatesDirect()
        if (existingTemplates.isEmpty()) {
            templateDao.insertTemplates(
                listOf(
                    MessageTemplate(
                        title = "Festive Offer & Catalog",
                        category = "Promotions",
                        content = "Namaste {{first_name}}! Exclusive festive deals are now live at {{company}}. Reply YES to receive our latest catalog."
                    ),
                    MessageTemplate(
                        title = "Payment / Invoice Reminder",
                        category = "Billing",
                        content = "Hi {{name}}, friendly reminder for your upcoming renewal on {{date}}. Please ignore if already settled."
                    ),
                    MessageTemplate(
                        title = "Event / Webinar Invite",
                        category = "Community",
                        content = "Hello {{first_name}}, you are invited to our live session on {{date}}. Tag: {{custom_tag}}."
                    ),
                    MessageTemplate(
                        title = "Order Dispatch Update",
                        category = "Operations",
                        content = "Hi {{first_name}}, your order from {{company}} has been dispatched today ({{date}}). Thank you for choosing us!"
                    )
                )
            )
        }
        val existingGroups = contactDao.getAllGroupsDirect()
        if (existingGroups.isEmpty()) {
            contactDao.insertGroup(
                ContactGroup(
                    name = "VIP Customers",
                    colorHex = "#4F46E5",
                    description = "Priority clients & repeat buyers"
                )
            )
            contactDao.insertGroup(
                ContactGroup(
                    name = "WhatsApp Groups",
                    colorHex = "#10B981",
                    description = "Captured WhatsApp community & customer groups"
                )
            )
        }
    }

    // --- Groups & Chunked Contact Operations (>999 SQLite bind safety) ---

    suspend fun createGroup(name: String, description: String, colorHex: String = "#4F46E5"): Long {
        return contactDao.insertGroup(
            ContactGroup(name = name.trim(), description = description.trim(), colorHex = colorHex)
        )
    }

    suspend fun deleteGroup(group: ContactGroup) {
        contactDao.ungroupContacts(group.id)
        contactDao.deleteGroup(group)
    }

    suspend fun addContact(contact: Contact): Long {
        val normalized = if (contact.isGroup) {
            contact.phoneNumber
        } else {
            MessageVariableParser.normalizeIndianOrNriPhone(contact.phoneNumber) ?: contact.phoneNumber
        }
        return contactDao.insertContact(contact.copy(phoneNumber = normalized))
    }

    suspend fun importContactsList(contacts: List<Contact>): Int {
        if (contacts.isEmpty()) return 0
        val existing = contactDao.getAllContactsDirect()
        val existingKeys = existing.map { c ->
            if (c.isGroup) "group:${c.name.trim().lowercase()}" else "phone:${c.phoneNumber}"
        }.toMutableSet()

        val uniqueNew = mutableListOf<Contact>()
        for (raw in contacts) {
            val normalizedPhone = if (raw.isGroup) {
                raw.phoneNumber
            } else {
                MessageVariableParser.normalizeIndianOrNriPhone(raw.phoneNumber) ?: continue
            }
            val key = if (raw.isGroup) "group:${raw.name.trim().lowercase()}" else "phone:$normalizedPhone"
            if (existingKeys.add(key)) {
                uniqueNew.add(raw.copy(phoneNumber = normalizedPhone))
            }
        }

        uniqueNew.chunked(SQLITE_BIND_BATCH_SIZE).forEach { batch ->
            contactDao.insertContactsBatch(batch)
        }
        return uniqueNew.size
    }

    suspend fun getContactsByIdsChunked(ids: List<Long>): List<Contact> {
        if (ids.isEmpty()) return emptyList()
        return ids.distinct().chunked(SQLITE_BIND_BATCH_SIZE).flatMap { chunk ->
            contactDao.getContactsByIdsBatch(chunk)
        }
    }

    suspend fun getContactsByGroupIdsChunked(groupIds: List<Long>): List<Contact> {
        if (groupIds.isEmpty()) return emptyList()
        return groupIds.distinct().chunked(SQLITE_BIND_BATCH_SIZE).flatMap { chunk ->
            contactDao.getOptedInContactsByGroupIdsBatch(chunk)
        }
    }

    suspend fun deleteContactsByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        ids.distinct().chunked(SQLITE_BIND_BATCH_SIZE).forEach { chunk ->
            contactDao.deleteContactsByIdsBatch(chunk)
        }
    }

    suspend fun deleteContactById(id: Long) = contactDao.deleteContactById(id)

    // --- Templates ---

    suspend fun saveTemplate(template: MessageTemplate): Long = templateDao.insertTemplate(template)
    suspend fun deleteTemplate(template: MessageTemplate) = templateDao.deleteTemplate(template)
    suspend fun incrementTemplateUsage(id: Long) = templateDao.incrementUsage(id)

    // --- Campaign Creation, Scheduling & Pacing ---

    suspend fun createCampaignWithLogs(
        title: String,
        messageTemplate: String,
        targetGroupIds: List<Long>,
        explicitContacts: List<Contact>,
        scheduledTimeEpoch: Long,
        delaySeconds: Int,
        mediaUri: String?,
        targetPackage: String,
        targetUserSerial: Long,
        isCloneTarget: Boolean,
        initialStatus: CampaignStatus
    ): Long {
        val groupContacts = getContactsByGroupIdsChunked(targetGroupIds)
        val mergedRecipients = LinkedHashMap<String, Contact>()
        (groupContacts + explicitContacts).forEach { c ->
            val key = if (c.isGroup) "group:${c.name.trim().lowercase()}" else "phone:${c.phoneNumber}"
            mergedRecipients.putIfAbsent(key, c)
        }

        val recipients = mergedRecipients.values.toList()
        val campaign = Campaign(
            title = title.trim().ifBlank { "Broadcast ${DateUtils.formatDateOnly(System.currentTimeMillis())}" },
            messageTemplate = messageTemplate.trim(),
            targetGroupIds = targetGroupIds.joinToString(","),
            scheduledTimeEpoch = scheduledTimeEpoch,
            status = initialStatus,
            totalRecipients = recipients.size,
            delaySeconds = delaySeconds.coerceIn(2, 60),
            mediaUri = mediaUri,
            targetPackage = targetPackage,
            targetUserSerial = targetUserSerial,
            isCloneTarget = isCloneTarget
        )
        val campaignId = campaignDao.insertCampaign(campaign)

        val now = System.currentTimeMillis()
        val logEntries = recipients.map { contact ->
            BroadcastLogEntry(
                campaignId = campaignId,
                contactId = contact.id,
                contactName = contact.name,
                phoneNumber = contact.phoneNumber,
                resolvedMessage = MessageVariableParser.resolveTemplate(campaign.messageTemplate, contact, now),
                status = DeliveryStatus.PENDING,
                timestamp = now,
                isGroup = contact.isGroup
            )
        }

        logEntries.chunked(SQLITE_BIND_BATCH_SIZE).forEach { batch ->
            logDao.insertLogsBatch(batch)
        }

        if (initialStatus == CampaignStatus.SCHEDULED && scheduledTimeEpoch > System.currentTimeMillis()) {
            CampaignScheduler.syncRollingHorizon(context, campaignDao)
        }

        return campaignId
    }

    suspend fun computeDailyPacingEstimate(
        proposedRecipients: Int,
        proposedDelaySeconds: Int,
        targetEpochMs: Long
    ): DailyPacingEstimate {
        val (dayStart, dayEnd) = DateUtils.dayBoundsFor(
            if (targetEpochMs > 0L) targetEpochMs else System.currentTimeMillis()
        )
        val existingDayCampaigns = campaignDao.getCampaignsForDayDirect(dayStart, dayEnd)
        val perMessageOverheadSec = 4L

        val proposedDurationSec = proposedRecipients.toLong() * (proposedDelaySeconds.toLong() + perMessageOverheadSec)
        val proposedEndMs = targetEpochMs + (proposedDurationSec * 1000L)

        val overlaps = mutableListOf<String>()
        var cumulativeRecipients = proposedRecipients
        var cumulativeSeconds = proposedDurationSec

        for (other in existingDayCampaigns) {
            cumulativeRecipients += other.totalRecipients
            val otherDurationSec = other.totalRecipients.toLong() * (other.delaySeconds.toLong() + perMessageOverheadSec)
            cumulativeSeconds += otherDurationSec
            if (targetEpochMs > 0L && other.scheduledTimeEpoch > 0L) {
                val otherEndMs = other.scheduledTimeEpoch + (otherDurationSec * 1000L)
                if (targetEpochMs < otherEndMs && proposedEndMs > other.scheduledTimeEpoch) {
                    overlaps.add(other.title)
                }
            }
        }

        return DailyPacingEstimate(
            totalSameDayRecipients = cumulativeRecipients,
            estimatedTotalSeconds = cumulativeSeconds,
            overlappingCampaignTitles = overlaps,
            isHeavyLoad = cumulativeSeconds >= 18L * 3600L,
            isOverCapacity = cumulativeSeconds >= 24L * 3600L
        )
    }

    suspend fun deleteCampaign(campaign: Campaign) {
        CampaignScheduler.cancelCampaignAlarm(context, campaign.id)
        BroadcastExecutionCoordinator.removeFromQueue(campaign.id)
        logDao.deleteLogsForCampaign(campaign.id)
        campaignDao.deleteCampaign(campaign)
        runMediaOrphanGc()
    }

    suspend fun cancelQueuedCampaign(campaignId: Long) {
        BroadcastExecutionCoordinator.removeFromQueue(campaignId)
        CampaignScheduler.cancelCampaignAlarm(context, campaignId)
        NotificationHelper.cancelNotification(
            context,
            NotificationHelper.campaignQueuedNotificationId(campaignId)
        )
        campaignDao.updateStatus(campaignId, CampaignStatus.PAUSED, "Cancelled from FIFO queue")
    }

    private data class CampaignLoopResult(
        val finishedNormally: Boolean,
        val dispatchedAny: Boolean
    )

    suspend fun enqueueScheduledCampaign(campaignId: Long) {
        // P0 #1 Idempotency Guard: never re-queue or overwrite an already active or queued campaign
        if (BroadcastExecutionCoordinator.isCampaignActiveOrQueued(campaignId)) {
            return
        }
        CampaignScheduler.cancelCampaignAlarm(context, campaignId)
        campaignDao.updateStatus(campaignId, CampaignStatus.QUEUED, null)
        BroadcastExecutionCoordinator.enqueueCampaign(campaignId)
        val campaign = campaignDao.getCampaignByIdDirect(campaignId)
        if (campaign != null) {
            NotificationHelper.notifyCampaignStatus(
                context = context,
                notificationId = NotificationHelper.campaignQueuedNotificationId(campaignId),
                channelId = NotificationHelper.CHANNEL_CAMPAIGN_ALERTS,
                title = "Campaign Queued (FIFO)",
                message = "${campaign.title} will start automatically after the active broadcast completes.",
                campaignId = campaignId
            )
        }
    }

    // --- Core FIFO Broadcast Execution Loop ---

    suspend fun executeBroadcast(initialCampaignId: Long, dryRunForTest: Boolean = false) {
        // P0 #1 Duplicate launch guard: if this exact campaign is already running, do not enqueue it again
        if (BroadcastExecutionCoordinator.state.value.isRunning &&
            BroadcastExecutionCoordinator.state.value.activeCampaignId == initialCampaignId
        ) {
            return
        }

        val initialCampaign = campaignDao.getCampaignByIdDirect(initialCampaignId) ?: return

        val startedWhileLocked = if (dryRunForTest) {
            false
        } else {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            (km?.isKeyguardLocked == true) || (pm != null && !pm.isInteractive)
        }

        if (!BroadcastExecutionCoordinator.tryBeginExecution(
                campaignId = initialCampaign.id,
                title = initialCampaign.title,
                totalRecipients = initialCampaign.totalRecipients,
                startedWhileLocked = startedWhileLocked
            )
        ) {
            enqueueScheduledCampaign(initialCampaignId)
            return
        }

        val settings = settingsPreferences.getCurrentSettings()
        var anyCampaignExecuted = false
        var dispatchedAnyRecipient = false
        var pausedForCall = false
        var lastTargetKey: Pair<String, Boolean>? = null

        try {
            if (!dryRunForTest && startedWhileLocked) {
                ScreenWakeManager.acquireTemporaryWakeLock(context, 45_000L)
                BroadcastExecutionCoordinator.updateStep(
                    AutomationStep.SWIPING_LOCKSCREEN,
                    "Waking screen & unlocking device..."
                )
                delay(450L)
                WhatsAppAutomationController.performTwoPhaseDeviceUnlock(timeoutMs = 12_000L)
                delay(350L)
            }

            BroadcastExecutionCoordinator.executionMutex.withLock {
                var currentCampaignId: Long? = initialCampaignId

                while (currentCampaignId != null) {
                    val campaign = campaignDao.getCampaignByIdDirect(currentCampaignId)
                    if (campaign == null) {
                        currentCampaignId = BroadcastExecutionCoordinator.popNextQueuedCampaign()
                        continue
                    }

                    // P1 #5: When switching WhatsApp instances across queued FIFO campaigns (e.g., Primary -> 2nd Clone
                    // or Personal -> Business), briefly settle to Home so the previous WhatsApp task stack releases cleanly.
                    val targetKey = campaign.targetPackage to campaign.isCloneTarget
                    if (!dryRunForTest && lastTargetKey != null && lastTargetKey != targetKey) {
                        WhatsAppAutomationController.performHomeSettleForInstanceSwitch()
                        delay(450L)
                    }
                    lastTargetKey = targetKey

                    BroadcastExecutionCoordinator.switchToNextQueuedCampaign(
                        campaignId = campaign.id,
                        title = campaign.title,
                        totalRecipients = campaign.totalRecipients
                    )
                    anyCampaignExecuted = true

                    val loopResult = runSingleCampaignLoop(campaign, settings.pauseOnIncomingCall, dryRunForTest)
                    if (loopResult.dispatchedAny) {
                        dispatchedAnyRecipient = true
                    }

                    if (!dryRunForTest && settings.pauseOnIncomingCall && CallDetectionManager.isCellularCallActive(context)) {
                        pausedForCall = true
                    }

                    if (BroadcastExecutionCoordinator.isStopRequested) {
                        // Move remaining queued campaigns to PAUSED cleanly
                        val remainingQueued = campaignDao.getQueuedCampaignsDirect()
                        for (q in remainingQueued) {
                            campaignDao.updateStatus(q.id, CampaignStatus.PAUSED, "Stopped while queued")
                            BroadcastExecutionCoordinator.removeFromQueue(q.id)
                        }
                        break
                    }

                    if (!loopResult.finishedNormally) {
                        break
                    }

                    // Drain FIFO queue (in-memory first, then verify persisted Room QUEUED rows)
                    currentCampaignId = BroadcastExecutionCoordinator.popNextQueuedCampaign()
                        ?: campaignDao.getQueuedCampaignsDirect().firstOrNull()?.id
                }
            }
        } finally {
            // P2 #6: Only exit WhatsApp / auto-lock if at least one recipient was actually dispatched
            if (!dryRunForTest && anyCampaignExecuted && dispatchedAnyRecipient) {
                val stoppedByUser = BroadcastExecutionCoordinator.isStopRequested
                val shouldAutoLock = BroadcastExecutionCoordinator.shouldAutoLockAfterCompletion(
                    startedWhileLocked = startedWhileLocked,
                    autoLockEnabled = settings.autoLockDeviceIfStartedLocked,
                    stoppedByUser = stoppedByUser,
                    pausedForCall = pausedForCall
                )
                val shouldExitWhatsApp = settings.autoExitWhatsAppAfterBroadcast && !stoppedByUser && !pausedForCall
                if (shouldExitWhatsApp || shouldAutoLock) {
                    BroadcastExecutionCoordinator.updateStep(
                        step = if (shouldAutoLock) AutomationStep.LOCKING_DEVICE else AutomationStep.EXITING_WHATSAPP,
                        note = if (shouldAutoLock) {
                            "Exiting WhatsApp & auto-locking device..."
                        } else {
                            "Exiting WhatsApp to Home..."
                        }
                    )
                    WhatsAppAutomationController.performExitWhatsAppAndConditionalLock(
                        exitToHome = shouldExitWhatsApp,
                        lockDevice = shouldAutoLock
                    )
                }
            }
            WhatsAppAutomationController.endBroadcastSession(
                autoDisableAccessibility = settings.autoDisableAccessibilityAfterBroadcast
            )
            ScreenWakeManager.releaseWakeLock()
            BroadcastExecutionCoordinator.finishExecution(clearQueue = false)
            CampaignScheduler.syncRollingHorizon(context, campaignDao)
        }
    }

    private suspend fun runSingleCampaignLoop(
        campaign: Campaign,
        pauseOnCall: Boolean,
        dryRunForTest: Boolean
    ): CampaignLoopResult {
        campaignDao.updateStatus(campaign.id, CampaignStatus.RUNNING, null)
        val pendingLogs = logDao.getPendingLogsForCampaignDirect(campaign.id)
        val initialSummary = logDao.getCountSummaryForCampaign(campaign.id)

        var sentCount = initialSummary.sentCount
        var deliveredCount = initialSummary.deliveredCount
        var readCount = initialSummary.readCount
        var failedCount = initialSummary.failedCount
        var processedSinceFlush = 0
        var dispatchedAny = false

        // P2 #6: Sync live UI progress banner with already-completed Room counts on resume
        BroadcastExecutionCoordinator.syncInitialCounts(
            sentCount = sentCount,
            failedCount = failedCount
        )

        // Guard in case the screen re-locked between queued campaigns
        if (!dryRunForTest) {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (km?.isKeyguardLocked == true) {
                ScreenWakeManager.acquireTemporaryWakeLock(context, 30_000L)
                WhatsAppAutomationController.performTwoPhaseDeviceUnlock(timeoutMs = 10_000L)
                delay(300L)
            }
        }

        var prevName: String? = null
        var prevPhone: String? = null

        for ((idx, entry) in pendingLogs.withIndex()) {
            // 1. Honor Stop Request
            if (BroadcastExecutionCoordinator.isStopRequested) {
                campaignDao.updateCountsAndStatus(
                    id = campaign.id,
                    sentCount = sentCount,
                    deliveredCount = deliveredCount,
                    readCount = readCount,
                    failedCount = failedCount,
                    status = CampaignStatus.PAUSED,
                    completedAt = null
                )
                return CampaignLoopResult(finishedNormally = false, dispatchedAny = dispatchedAny)
            }

            // 2. Auto-pause on incoming cellular call
            if (!dryRunForTest && pauseOnCall && CallDetectionManager.isCellularCallActive(context)) {
                BroadcastExecutionCoordinator.pause("Auto-paused for active phone call")
            }

            // 3. Pause Gate Loop
            while (BroadcastExecutionCoordinator.state.value.isPaused &&
                !BroadcastExecutionCoordinator.isStopRequested
            ) {
                campaignDao.updateCountsAndStatus(
                    id = campaign.id,
                    sentCount = sentCount,
                    deliveredCount = deliveredCount,
                    readCount = readCount,
                    failedCount = failedCount,
                    status = CampaignStatus.PAUSED,
                    completedAt = null
                )
                delay(350L)
            }

            if (BroadcastExecutionCoordinator.isStopRequested) {
                return CampaignLoopResult(finishedNormally = false, dispatchedAny = dispatchedAny)
            }

            // Ensure status returns to RUNNING after resume
            campaignDao.updateStatus(campaign.id, CampaignStatus.RUNNING, null)

            // 4. Honor Skip Current Recipient
            if (BroadcastExecutionCoordinator.consumeSkipRequest()) {
                logDao.updateLogStatus(entry.id, DeliveryStatus.SKIPPED, System.currentTimeMillis(), "Skipped by user")
                continue
            }

            dispatchedAny = true

            val overallIndex = (campaign.totalRecipients - pendingLogs.size) + idx + 1
            BroadcastExecutionCoordinator.updateProgress(
                recipientIndex = overallIndex,
                recipientName = entry.contactName,
                recipientPhone = entry.phoneNumber,
                sentCount = sentCount,
                failedCount = failedCount,
                step = AutomationStep.OPENING_CHAT,
                note = "Dispatching to ${entry.contactName}"
            )

            // 5. Real On-Device WhatsApp / Business / Clone Dispatch
            // CRITICAL: Arm Accessibility scope + active watchdog BEFORE launching WhatsApp/Chooser
            // so the very first "Complete action using" or chat window event is never missed.
            var outcomeSuccess: Boolean
            var outcomeError: String?

            if (dryRunForTest) {
                outcomeSuccess = true
                outcomeError = null
            } else if (WhatsAppAutomationController.isServiceConnected) {
                val hasMedia = !campaign.mediaUri.isNullOrBlank()
                val armedTask = WhatsAppAutomationController.beginRecipientTask(
                    recipientName = entry.contactName,
                    recipientPhone = entry.phoneNumber,
                    previousRecipientName = prevName,
                    previousRecipientPhone = prevPhone,
                    message = entry.resolvedMessage,
                    isGroup = entry.isGroup,
                    hasMedia = hasMedia,
                    targetPackage = campaign.targetPackage,
                    isCloneTarget = campaign.isCloneTarget
                )

                val launchResult = WhatsAppSender.dispatchMessage(
                    context = context,
                    phoneNumber = entry.phoneNumber,
                    message = entry.resolvedMessage,
                    isGroupTarget = entry.isGroup,
                    mediaPathOrUri = campaign.mediaUri,
                    targetPackage = campaign.targetPackage,
                    isCloneTarget = campaign.isCloneTarget
                )

                if (!launchResult.launched) {
                    WhatsAppAutomationController.completeActiveTask(
                        armedTask.generation,
                        com.example.util.AutomationDispatchOutcome(false, launchResult.error ?: "Failed to launch WhatsApp")
                    )
                    outcomeSuccess = false
                    outcomeError = launchResult.error ?: "Failed to launch WhatsApp"
                } else {
                    val a11yOutcome = WhatsAppAutomationController.awaitRecipientOutcome(armedTask)
                    outcomeSuccess = a11yOutcome.success
                    outcomeError = a11yOutcome.errorMessage

                    // If media was sent on a screen without a caption box, automatically follow up with the text
                    if (outcomeSuccess && hasMedia && a11yOutcome.needsTextFollowUp && entry.resolvedMessage.isNotBlank()) {
                        BroadcastExecutionCoordinator.updateStep(
                            AutomationStep.SENDING_TEXT_FOLLOWUP,
                            "Sending text follow-up to ${entry.contactName}..."
                        )
                        delay(900L)
                        val followUpTask = WhatsAppAutomationController.beginRecipientTask(
                            recipientName = entry.contactName,
                            recipientPhone = entry.phoneNumber,
                            previousRecipientName = null,
                            previousRecipientPhone = null,
                            message = entry.resolvedMessage,
                            isGroup = entry.isGroup,
                            hasMedia = false,
                            targetPackage = campaign.targetPackage,
                            isCloneTarget = campaign.isCloneTarget
                        )
                        val followUpLaunch = WhatsAppSender.dispatchMessage(
                            context = context,
                            phoneNumber = entry.phoneNumber,
                            message = entry.resolvedMessage,
                            isGroupTarget = entry.isGroup,
                            mediaPathOrUri = null,
                            targetPackage = campaign.targetPackage,
                            isCloneTarget = campaign.isCloneTarget
                        )
                        if (followUpLaunch.launched) {
                            val followUpOutcome = WhatsAppAutomationController.awaitRecipientOutcome(followUpTask)
                            if (!followUpOutcome.success) {
                                outcomeError = "Media sent; text follow-up: ${followUpOutcome.errorMessage}"
                            }
                        } else {
                            WhatsAppAutomationController.completeActiveTask(
                                followUpTask.generation,
                                com.example.util.AutomationDispatchOutcome(false, followUpLaunch.error)
                            )
                        }
                    }
                }
            } else {
                val launchResult = WhatsAppSender.dispatchMessage(
                    context = context,
                    phoneNumber = entry.phoneNumber,
                    message = entry.resolvedMessage,
                    isGroupTarget = entry.isGroup,
                    mediaPathOrUri = campaign.mediaUri,
                    targetPackage = campaign.targetPackage,
                    isCloneTarget = campaign.isCloneTarget
                )
                outcomeSuccess = launchResult.launched
                outcomeError = if (launchResult.launched) null else (launchResult.error ?: "Failed to launch WhatsApp")
            }

            val now = System.currentTimeMillis()
            if (outcomeSuccess) {
                sentCount++
                deliveredCount++
                logDao.updateLogStatus(entry.id, DeliveryStatus.SENT, now, null)
            } else {
                failedCount++
                logDao.updateLogStatus(entry.id, DeliveryStatus.FAILED, now, outcomeError)
            }

            prevName = entry.contactName
            prevPhone = entry.phoneNumber
            processedSinceFlush++

            BroadcastExecutionCoordinator.updateProgress(
                recipientIndex = overallIndex,
                recipientName = entry.contactName,
                recipientPhone = entry.phoneNumber,
                sentCount = sentCount,
                failedCount = failedCount,
                step = AutomationStep.CONFIRMING_SENT,
                note = if (outcomeSuccess) "Sent to ${entry.contactName}" else (outcomeError ?: "Failed")
            )

            // WAL churn reduction: flush campaign aggregate counts every 10 recipients
            if (processedSinceFlush >= COUNT_FLUSH_BATCH_SIZE) {
                campaignDao.updateCountsAndStatus(
                    id = campaign.id,
                    sentCount = sentCount,
                    deliveredCount = deliveredCount,
                    readCount = readCount,
                    failedCount = failedCount,
                    status = CampaignStatus.RUNNING,
                    completedAt = null
                )
                processedSinceFlush = 0
            }

            // Anti-ban delay cooldown between recipients
            if (!dryRunForTest && idx < pendingLogs.lastIndex) {
                for (sec in campaign.delaySeconds downTo 1) {
                    if (BroadcastExecutionCoordinator.isStopRequested) break
                    BroadcastExecutionCoordinator.updateCooldown(sec)
                    delay(1_000L)
                }
                BroadcastExecutionCoordinator.updateCooldown(0)
            }
        }

        val finalStatus = if (failedCount > 0 && sentCount == 0) {
            CampaignStatus.FAILED
        } else {
            CampaignStatus.COMPLETED
        }

        campaignDao.updateCountsAndStatus(
            id = campaign.id,
            sentCount = sentCount,
            deliveredCount = deliveredCount,
            readCount = readCount,
            failedCount = failedCount,
            status = finalStatus,
            completedAt = System.currentTimeMillis()
        )

        val notifId = if (finalStatus == CampaignStatus.COMPLETED) {
            NotificationHelper.campaignSuccessNotificationId(campaign.id)
        } else {
            NotificationHelper.campaignFailedNotificationId(campaign.id)
        }
        NotificationHelper.notifyCampaignStatus(
            context = context,
            notificationId = notifId,
            channelId = NotificationHelper.CHANNEL_MESSAGE_STATUS,
            title = if (finalStatus == CampaignStatus.COMPLETED) "Broadcast Completed" else "Broadcast Failed",
            message = "${campaign.title}: $sentCount sent, $failedCount failed of ${campaign.totalRecipients}.",
            campaignId = campaign.id
        )

        return CampaignLoopResult(finishedNormally = true, dispatchedAny = dispatchedAny)
    }

    // --- Startup Recovery, Targeted Metric Repair & Multi-Year Hygiene ---

    suspend fun recoverInterruptedCampaignsAndRepairMetrics() {
        // Process-death guard: never clobber a live foreground service broadcast!
        if (BroadcastExecutionCoordinator.isActivelyExecuting) return

        val candidates = campaignDao.getCampaignsNeedingMetricRepairDirect()
        for (campaign in candidates) {
            val summary = logDao.getCountSummaryForCampaign(campaign.id)
            // P2 #6: Recover BOTH orphaned RUNNING and orphaned QUEUED campaigns after OS kill/reboot
            val isOrphanedActive = campaign.status == CampaignStatus.RUNNING ||
                campaign.status == CampaignStatus.QUEUED
            val resolvedStatus = when {
                isOrphanedActive && summary.pendingCount > 0 -> CampaignStatus.PAUSED
                isOrphanedActive && summary.pendingCount == 0 -> CampaignStatus.COMPLETED
                else -> campaign.status
            }
            campaignDao.updateCountsAndStatus(
                id = campaign.id,
                sentCount = summary.sentCount,
                deliveredCount = summary.deliveredCount,
                readCount = summary.readCount,
                failedCount = summary.failedCount,
                status = resolvedStatus,
                completedAt = if (resolvedStatus == CampaignStatus.COMPLETED) {
                    campaign.completedAt ?: System.currentTimeMillis()
                } else {
                    campaign.completedAt
                }
            )
        }
    }

    suspend fun pruneAgedLogs(retentionDays: Int): Int {
        if (retentionDays <= 0) return 0
        val cutoffEpoch = System.currentTimeMillis() - (retentionDays.toLong() * 24L * 60L * 60L * 1000L)
        return logDao.pruneLogsOlderThan(cutoffEpoch)
    }

    suspend fun runMediaOrphanGc(): Int {
        val referenced = campaignDao.getAllReferencedMediaUrisDirect().toSet()
        return MediaAttachmentHelper.collectOrphanedMedia(context, referenced)
    }
}
