package com.example

import android.content.Context
import android.telephony.TelephonyManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.AccessibilityScopeMode
import com.example.data.model.AutomationStep
import com.example.data.model.BootScheduleDecision
import com.example.data.model.CampaignStatus
import com.example.data.model.Contact
import com.example.data.model.DeliveryStatus
import com.example.data.model.LockType
import com.example.data.model.OemFamily
import com.example.data.model.WhatsAppInstance
import com.example.data.repository.BroadcastRepository
import com.example.util.BroadcastExecutionCoordinator
import com.example.util.CallDetectionManager
import com.example.util.CampaignScheduler
import com.example.util.ChooserCandidate
import com.example.util.KeystoreCryptoManager
import com.example.util.MessageVariableParser
import com.example.util.OemDetector
import com.example.util.VaultPreferences
import com.example.util.WhatsAppAutoSendAccessibilityService
import com.example.util.WhatsAppAutomationController
import com.example.util.WhatsAppChooserAndScreenMatcher
import com.example.util.WhatsAppInstanceDiscovery
import com.example.util.WhatsAppScreenKind
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive Pan-India Multi-OEM, Multi-Campaign, Multi-WhatsApp Stress & Audit Suite.
 * Simulates all major Indian Android OEMs with up to 4 WhatsApp accounts per device,
 * high-volume multi-campaign FIFO queues (2,400+ recipients), 120-alarm horizon stress,
 * AES-256-GCM vault cycles, chooser disambiguation, and failure/recovery scenarios.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PanIndiaProductionStressTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: BroadcastRepository

    private data class IndianDeviceProfile(
        val deviceName: String,
        val manufacturer: String,
        val brand: String,
        val expectedOem: OemFamily,
        val chooserPackage: String,
        val waPrimaryLabel: String,
        val waCloneLabel: String,
        val waBizPrimaryLabel: String,
        val waBizCloneLabel: String,
        val keypadIdFormat: (Char) -> String
    )

    private val indianDeviceMatrix = listOf(
        IndianDeviceProfile(
            deviceName = "Xiaomi Redmi Note 13 Pro / POCO X6 (HyperOS)",
            manufacturer = "Xiaomi",
            brand = "Redmi",
            expectedOem = OemFamily.XIAOMI,
            chooserPackage = "com.miui.securitycore",
            waPrimaryLabel = "WhatsApp",
            waCloneLabel = "Dual WhatsApp",
            waBizPrimaryLabel = "WhatsApp Business",
            waBizCloneLabel = "Dual WhatsApp Business",
            keypadIdFormat = { d -> "com.android.systemui:id/digit_$d" }
        ),
        IndianDeviceProfile(
            deviceName = "Vivo V30 / iQOO Neo 9 Pro (Funtouch OS)",
            manufacturer = "vivo",
            brand = "iQOO",
            expectedOem = OemFamily.VIVO_IQOO,
            chooserPackage = "com.vivo.appclone",
            waPrimaryLabel = "WhatsApp",
            waCloneLabel = "II·WhatsApp",
            waBizPrimaryLabel = "WhatsApp Business",
            waBizCloneLabel = "II·WhatsApp Business",
            keypadIdFormat = { d -> "com.android.systemui:id/key$d" }
        ),
        IndianDeviceProfile(
            deviceName = "Oppo Reno 12 / Realme GT 6 / OnePlus 12R (ColorOS/OxygenOS)",
            manufacturer = "OPPO",
            brand = "realme",
            expectedOem = OemFamily.OPPO_REALME_ONEPLUS,
            chooserPackage = "com.oplus.multiapp",
            waPrimaryLabel = "WhatsApp",
            waCloneLabel = "WhatsApp (Clone)",
            waBizPrimaryLabel = "WhatsApp Business",
            waBizCloneLabel = "WhatsApp Business (Clone)",
            keypadIdFormat = { d -> "com.oplus.systemui:id/btn_$d" }
        ),
        IndianDeviceProfile(
            deviceName = "Samsung Galaxy M35 / S24 (One UI Dual Messenger)",
            manufacturer = "samsung",
            brand = "samsung",
            expectedOem = OemFamily.SAMSUNG,
            chooserPackage = "com.samsung.android.da.daagent",
            waPrimaryLabel = "WhatsApp",
            waCloneLabel = "WhatsApp (Dual Messenger)",
            waBizPrimaryLabel = "WhatsApp Business",
            waBizCloneLabel = "WhatsApp Business (2)",
            keypadIdFormat = { d -> "com.samsung.android.systemui:id/num_$d" }
        ),
        IndianDeviceProfile(
            deviceName = "OnePlus Nord CE 4 (OxygenOS Parallel Apps)",
            manufacturer = "OnePlus",
            brand = "OnePlus",
            expectedOem = OemFamily.OPPO_REALME_ONEPLUS,
            chooserPackage = "com.android.intentresolver",
            waPrimaryLabel = "WhatsApp",
            waCloneLabel = "Parallel WhatsApp",
            waBizPrimaryLabel = "WhatsApp Business",
            waBizCloneLabel = "Parallel WhatsApp Business",
            keypadIdFormat = { d -> "com.android.systemui:id/key$d" }
        ),
        IndianDeviceProfile(
            deviceName = "Motorola Edge 50 / Nothing Phone 2a / Pixel (Stock + Work Profile)",
            manufacturer = "motorola",
            brand = "motorola",
            expectedOem = OemFamily.OTHER,
            chooserPackage = "com.android.intentresolver",
            waPrimaryLabel = "WhatsApp",
            waCloneLabel = "Work WhatsApp",
            waBizPrimaryLabel = "WhatsApp Business",
            waBizCloneLabel = "Work WhatsApp Business",
            keypadIdFormat = { d -> "com.android.systemui:id/key$d" }
        ),
        IndianDeviceProfile(
            deviceName = "Lava Agni 3 / Stock India 5G (Dual / 2nd Instance)",
            manufacturer = "LAVA",
            brand = "LAVA",
            expectedOem = OemFamily.OTHER,
            chooserPackage = "android",
            waPrimaryLabel = "WhatsApp",
            waCloneLabel = "WhatsApp 2nd",
            waBizPrimaryLabel = "WhatsApp Business",
            waBizCloneLabel = "WhatsApp Business 2nd",
            keypadIdFormat = { d -> "com.android.systemui:id/key$d" }
        ),
        IndianDeviceProfile(
            deviceName = "Tecno Camon 30 / Infinix Note 40 / Itel S24 (HiOS/XOS App Twin)",
            manufacturer = "TECNO",
            brand = "TECNO",
            expectedOem = OemFamily.TRANSSION,
            chooserPackage = "com.transsion.phonemaster",
            waPrimaryLabel = "WhatsApp",
            waCloneLabel = "WhatsApp Twin",
            waBizPrimaryLabel = "WhatsApp Business",
            waBizCloneLabel = "WhatsApp Business Twin",
            keypadIdFormat = { d -> "com.android.systemui:id/key$d" }
        )
    )

    @Before
    fun setup() {
        BroadcastExecutionCoordinator.resetForTest()
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BroadcastRepository(context, db)
    }

    @After
    fun teardown() {
        BroadcastExecutionCoordinator.resetForTest()
        db.close()
    }

    @Test
    fun `STRESS 1 - all Indian devices with 4 WhatsApp accounts select exact chooser tile and keypad`() {
        val ownPkg = "com.aistudio.masswabroadcast.mwbxkp"
        val activeScope = WhatsAppAutomationController.resolveAllowedPackages(
            AccessibilityScopeMode.SCOPED_WHATSAPP,
            ownPkg
        )

        for (device in indianDeviceMatrix) {
            // 1. OEM Detection & Survival Recipes
            val oem = OemDetector.detectOem(device.manufacturer, device.brand)
            assertEquals("OEM mismatch on ${device.deviceName}", device.expectedOem, oem)
            val recipes = OemDetector.getOemSurvivalRecipes(context, oem)
            assertTrue("Missing OEM recipes for ${device.deviceName}", recipes.isNotEmpty())

            // 2. Chooser package must be permitted in active dispatch scope
            assertTrue(
                "Chooser package ${device.chooserPackage} missing in SCOPED_WHATSAPP for ${device.deviceName}",
                device.chooserPackage in activeScope
            )

            // 3. Chooser window recognition
            val sheetSummary = "complete action using ${device.waPrimaryLabel} ${device.waCloneLabel}".lowercase()
            assertTrue(
                "Chooser not detected on ${device.deviceName}",
                WhatsAppChooserAndScreenMatcher.looksLikeChooserWindow(device.chooserPackage, sheetSummary)
            )

            // 4. 2-tile WhatsApp chooser (Primary vs 2nd Clone)
            val waTwoTiles = listOfNotNull(
                WhatsAppChooserAndScreenMatcher.buildCandidate(device.waPrimaryLabel),
                WhatsAppChooserAndScreenMatcher.buildCandidate(device.waCloneLabel)
            )
            assertEquals("Expected 2 WA tiles on ${device.deviceName}", 2, waTwoTiles.size)
            assertEquals(
                "Primary WA selection failed on ${device.deviceName}",
                0,
                WhatsAppChooserAndScreenMatcher.selectChooserCandidateIndex(waTwoTiles, isBusinessTarget = false, isCloneTarget = false)
            )
            assertEquals(
                "Clone WA selection failed on ${device.deviceName}",
                1,
                WhatsAppChooserAndScreenMatcher.selectChooserCandidateIndex(waTwoTiles, isBusinessTarget = false, isCloneTarget = true)
            )

            // 5. Full 4-tile chooser (WA Primary, WA Clone, WA Business Primary, WA Business Clone)
            val allFourTiles = listOfNotNull(
                WhatsAppChooserAndScreenMatcher.buildCandidate(device.waPrimaryLabel),
                WhatsAppChooserAndScreenMatcher.buildCandidate(device.waCloneLabel),
                WhatsAppChooserAndScreenMatcher.buildCandidate(device.waBizPrimaryLabel),
                WhatsAppChooserAndScreenMatcher.buildCandidate(device.waBizCloneLabel)
            )
            assertEquals("Expected 4 tiles on ${device.deviceName}", 4, allFourTiles.size)
            assertEquals(0, WhatsAppChooserAndScreenMatcher.selectChooserCandidateIndex(allFourTiles, isBusinessTarget = false, isCloneTarget = false))
            assertEquals(1, WhatsAppChooserAndScreenMatcher.selectChooserCandidateIndex(allFourTiles, isBusinessTarget = false, isCloneTarget = true))
            assertEquals(2, WhatsAppChooserAndScreenMatcher.selectChooserCandidateIndex(allFourTiles, isBusinessTarget = true, isCloneTarget = false))
            assertEquals(3, WhatsAppChooserAndScreenMatcher.selectChooserCandidateIndex(allFourTiles, isBusinessTarget = true, isCloneTarget = true))

            // 6. Lockscreen PIN keypad matching across 0..9
            for (d in '0'..'9') {
                assertTrue(
                    "Keypad digit '$d' failed on ${device.deviceName}",
                    WhatsAppAutoSendAccessibilityService.matchesKeypadDigit(device.keypadIdFormat(d), "", "", d)
                )
            }
        }
    }

    @Test
    fun `STRESS 2 - multi-campaign FIFO execution across 6 campaigns and 2400 recipients targeting all 4 WhatsApp accounts`() = runTest {
        val groupRetail = repository.createGroup("India Retail Buyers", "Tier 1-3 retail")
        val groupVip = repository.createGroup("VIP Distributors", "Pan-India distributors")
        val groupWa = repository.createGroup("WhatsApp Groups", "Community groups")

        // Seed 2,400 contacts + 20 WhatsApp Groups (exceeds SQLite 999 bind limit by 2.4x)
        val retailContacts = (1..1200).map { i ->
            Contact(
                groupId = groupRetail,
                name = "Retailer $i",
                phoneNumber = "+91 98100 ${String.format("%05d", i)}",
                company = "Bharat Mart $i",
                customTag = "RETAIL"
            )
        }
        val vipContacts = (1..1200).map { i ->
            Contact(
                groupId = groupVip,
                name = "Distributor $i",
                phoneNumber = "098200${String.format("%05d", i)}",
                company = "Desh Exports $i",
                customTag = "VIP"
            )
        }
        val waGroups = (1..20).map { i ->
            Contact(
                groupId = groupWa,
                name = "India Dealers Group $i",
                phoneNumber = "GROUP_$i",
                isGroup = true
            )
        }

        assertEquals(1200, repository.importContactsList(retailContacts))
        assertEquals(1200, repository.importContactsList(vipContacts))
        assertEquals(20, repository.importContactsList(waGroups))

        // Duplicate import must deduplicate to 0 new rows
        assertEquals(0, repository.importContactsList(retailContacts.take(200)))

        // 6 campaigns across all 4 WhatsApp accounts (Primary, 2nd Clone, Business Primary, Business 2nd Clone)
        val targets = listOf(
            Triple(WhatsAppInstanceDiscovery.PKG_WHATSAPP, 0L, false),
            Triple(WhatsAppInstanceDiscovery.PKG_WHATSAPP, 999L, true),
            Triple(WhatsAppInstanceDiscovery.PKG_WHATSAPP_BUSINESS, 0L, false),
            Triple(WhatsAppInstanceDiscovery.PKG_WHATSAPP_BUSINESS, 999L, true),
            Triple(WhatsAppInstanceDiscovery.PKG_WHATSAPP, 0L, false),
            Triple(WhatsAppInstanceDiscovery.PKG_WHATSAPP_BUSINESS, 999L, true)
        )

        val allRetail = repository.getContactsByGroupIdsChunked(listOf(groupRetail))
        val allVip = repository.getContactsByGroupIdsChunked(listOf(groupVip))
        val allGroups = repository.getContactsByGroupIdsChunked(listOf(groupWa))
        assertEquals(1200, allRetail.size)
        assertEquals(1200, allVip.size)
        assertEquals(20, allGroups.size)

        val campaignIds = mutableListOf<Long>()
        val slices = listOf(
            allRetail.subList(0, 400),
            allRetail.subList(400, 800),
            allRetail.subList(800, 1200),
            allVip.subList(0, 400),
            allVip.subList(400, 800),
            allVip.subList(800, 1200) + allGroups
        )

        slices.forEachIndexed { idx, recipients ->
            val (pkg, serial, isClone) = targets[idx]
            val id = repository.createCampaignWithLogs(
                title = "Stress Campaign #${idx + 1}",
                messageTemplate = "Namaste {{first_name}} from {{company}}! Offer {{custom_tag}} on {{date}}.",
                targetGroupIds = emptyList(),
                explicitContacts = recipients,
                scheduledTimeEpoch = 0L,
                delaySeconds = 2,
                mediaUri = if (idx % 2 == 0) "/tmp/promo_${idx}.jpg" else null,
                targetPackage = pkg,
                targetUserSerial = serial,
                isCloneTarget = isClone,
                initialStatus = CampaignStatus.QUEUED
            )
            campaignIds.add(id)
        }

        // Launch campaign 1 and enqueue campaigns 2..6 into FIFO queue concurrently
        for (i in 1 until campaignIds.size) {
            repository.enqueueScheduledCampaign(campaignIds[i])
        }
        repository.executeBroadcast(campaignIds[0], dryRunForTest = true)

        // Verify ALL 6 campaigns drained via FIFO and ALL 2,420 messages completed with zero drops
        val aggregate = repository.dashboardAggregateFlow.first()
        assertEquals(6, aggregate.totalCampaigns)
        assertEquals(2420, aggregate.totalSent)
        assertEquals(0, aggregate.totalFailed)

        val allCampaigns = repository.campaignsFlow.first()
        assertEquals(6, allCampaigns.size)
        assertTrue(allCampaigns.all { it.status == CampaignStatus.COMPLETED })
    }

    @Test
    fun `STRESS 3 - 120 scheduled campaigns obey 48h rolling horizon 80-alarm OEM cap and daily pacing`() = runTest {
        val now = System.currentTimeMillis()
        val contact = Contact(name = "Test User", phoneNumber = "9876543210")
        repository.addContact(contact)
        val saved = repository.contactsFlow.first()

        // Schedule 100 campaigns inside 48h window + 20 campaigns beyond 48h window
        for (i in 1..100) {
            repository.createCampaignWithLogs(
                title = "Within Horizon #$i",
                messageTemplate = "Hi {{name}}",
                targetGroupIds = emptyList(),
                explicitContacts = saved,
                scheduledTimeEpoch = now + (i * 15L * 60L * 1000L), // every 15 min -> 25 hours total
                delaySeconds = 4,
                mediaUri = null,
                targetPackage = WhatsAppInstanceDiscovery.PKG_WHATSAPP,
                targetUserSerial = 0L,
                isCloneTarget = false,
                initialStatus = CampaignStatus.SCHEDULED
            )
        }
        for (i in 1..20) {
            repository.createCampaignWithLogs(
                title = "Beyond Horizon #$i",
                messageTemplate = "Hi {{name}}",
                targetGroupIds = emptyList(),
                explicitContacts = saved,
                scheduledTimeEpoch = now + ((50L + i) * 60L * 60L * 1000L), // 51h..70h
                delaySeconds = 4,
                mediaUri = null,
                targetPackage = WhatsAppInstanceDiscovery.PKG_WHATSAPP_BUSINESS,
                targetUserSerial = 999L,
                isCloneTarget = true,
                initialStatus = CampaignStatus.SCHEDULED
            )
        }

        val armedCount = CampaignScheduler.syncRollingHorizon(context, db.campaignDao(), now)
        assertEquals(
            "Rolling horizon MUST cap armed alarms at MAX_CONCURRENT_OEM_ALARMS (80) for MIUI/ColorOS safety",
            CampaignScheduler.MAX_CONCURRENT_OEM_ALARMS,
            armedCount
        )

        // Daily pacing detector flags heavy load (>18h) and over-capacity (>24h)
        val heavyEstimate = repository.computeDailyPacingEstimate(
            proposedRecipients = 6000,
            proposedDelaySeconds = 12,
            targetEpochMs = now + 3_600_000L
        )
        assertTrue("Expected heavy load flag", heavyEstimate.isHeavyLoad)
        assertTrue("Expected over capacity flag (>24h)", heavyEstimate.isOverCapacity)
    }

    @Test
    fun `STRESS 4 - AES-256-GCM vault 50 rapid encrypt-decrypt-wipe cycles and banking-safe isolation`() = runTest {
        val vault = VaultPreferences(context)
        val testSecrets = listOf(
            LockType.PIN to "258096",
            LockType.PATTERN to "0,1,2,4,6,7,8",
            LockType.PASSWORD to "Bharat@2026#Secure",
            LockType.SLIDE to ""
        )

        repeat(50) { cycle ->
            val (type, secret) = testSecrets[cycle % testSecrets.size]
            val chars = secret.takeIf { it.isNotEmpty() }?.toCharArray()
            vault.saveDeviceLock(type, chars)
            vault.saveWhatsAppLock(type, secret.takeIf { it.isNotEmpty() }?.toCharArray())

            val decrypted = vault.decryptForDispatch()
            val devRef = decrypted.deviceSecret
            decrypted.use { creds ->
                assertEquals(type, creds.deviceLockType)
                if (secret.isNotEmpty()) {
                    assertEquals(secret, creds.deviceSecret?.concatToString())
                }
            }
            if (devRef != null) {
                assertTrue("Secret buffer not zero-filled on cycle $cycle", devRef.all { it == '\u0000' })
            }
        }

        // Verify Indian banking/UPI apps are NEVER in any Accessibility scope
        val indianBankingApps = listOf(
            "com.google.android.apps.nbu.paisa.user", // GPay
            "com.phonepe.app",                        // PhonePe
            "net.one97.paytm",                        // Paytm
            "in.org.npci.upiapp",                     // BHIM
            "com.sbi.lotusintouch",                   // YONO SBI
            "com.snapwork.hdfc",                      // HDFC
            "com.csam.icici.bank.imobile"             // iMobile ICICI
        )
        for (mode in AccessibilityScopeMode.entries) {
            val allowed = WhatsAppAutomationController.resolveAllowedPackages(mode, "com.aistudio.masswabroadcast.mwbxkp")
            for (bankPkg in indianBankingApps) {
                assertFalse("Banking app $bankPkg leaked into $mode!", bankPkg in allowed)
            }
        }
    }

    @Test
    fun `STRESS 5 - verifies all 7 v1-4 production hardening fixes across India OEMs`() = runTest {
        // Fix #3: Transsion (Tecno/Infinix/Itel) App Twin & Secondary clone labels + Oppo no-space "(Clone)"
        assertTrue(
            "Tecno/Infinix 'WhatsApp Twin' must be recognized as 2nd clone",
            WhatsAppChooserAndScreenMatcher.isCloneAppLabel("WhatsApp Twin")
        )
        assertTrue(
            "Oppo/Realme no-space 'WhatsApp(Clone)' must be recognized as 2nd clone",
            WhatsAppChooserAndScreenMatcher.isCloneAppLabel("WhatsApp(Clone)")
        )
        assertTrue(
            "'WhatsApp Secondary' must be recognized as 2nd clone",
            WhatsAppChooserAndScreenMatcher.isCloneAppLabel("WhatsApp Secondary")
        )
        assertEquals(OemFamily.TRANSSION, OemDetector.detectOem("INFINIX", "Infinix"))
        assertEquals(OemFamily.TRANSSION, OemDetector.detectOem("itel", "itel"))

        // Fix #4: MRU-reordered chooser tiles (Clone at index 0, Primary at index 1) select exact target via container badge
        val mruReorderedTiles = listOfNotNull(
            WhatsAppChooserAndScreenMatcher.buildCandidate("WhatsApp", "2nd badge"),
            WhatsAppChooserAndScreenMatcher.buildCandidate("WhatsApp", "Primary app")
        )
        assertEquals(2, mruReorderedTiles.size)
        assertEquals(
            "Primary target MUST pick index 1 when OEM reorders Clone to index 0",
            1,
            WhatsAppChooserAndScreenMatcher.selectChooserCandidateIndex(mruReorderedTiles, isBusinessTarget = false, isCloneTarget = false)
        )
        assertEquals(
            "Clone target MUST pick index 0 when OEM reorders Clone to index 0",
            0,
            WhatsAppChooserAndScreenMatcher.selectChooserCandidateIndex(mruReorderedTiles, isBusinessTarget = false, isCloneTarget = true)
        )

        // Fix #5: MAIN_CHATS cold-start classification recovers cleanly via ContactPicker search
        assertEquals(
            WhatsAppScreenKind.MAIN_CHATS,
            WhatsAppChooserAndScreenMatcher.classifyWhatsAppScreen(
                hasConversationHeader = false,
                hasEntryBox = false,
                hasCaptionBox = false,
                hasMediaPreviewControls = false,
                hasContactPickerSignals = false,
                hasMediaTask = false,
                hasMainChatsSignals = true
            )
        )

        // Multi-instance discovery always exposes all 4 accounts when both WA + WA Business exist
        val fourInstances = WhatsAppInstanceDiscovery.ensurePrimaryAndCloneOptions(
            listOf(
                WhatsAppInstance(WhatsAppInstanceDiscovery.PKG_WHATSAPP, "WhatsApp (Primary)", 0L, false, false),
                WhatsAppInstance(WhatsAppInstanceDiscovery.PKG_WHATSAPP_BUSINESS, "WhatsApp Business (Primary)", 0L, true, false)
            )
        )
        assertEquals(4, fourInstances.size)

        // Fix #1: Idempotent enqueue & duplicate launch guard never clobber an active RUNNING campaign
        val c = Contact(name = "Rohan", phoneNumber = "9876543210")
        val id = repository.createCampaignWithLogs(
            title = "Concurrent Launch Guard",
            messageTemplate = "Hello {{name}}",
            targetGroupIds = emptyList(),
            explicitContacts = listOf(c),
            scheduledTimeEpoch = 0L,
            delaySeconds = 2,
            mediaUri = null,
            targetPackage = WhatsAppInstanceDiscovery.PKG_WHATSAPP,
            targetUserSerial = 0L,
            isCloneTarget = false,
            initialStatus = CampaignStatus.RUNNING
        )

        assertTrue(BroadcastExecutionCoordinator.tryBeginExecution(id, "Concurrent Launch Guard", 1))
        assertTrue(BroadcastExecutionCoordinator.isCampaignActiveOrQueued(id))
        assertFalse("Second concurrent begin MUST be rejected", BroadcastExecutionCoordinator.tryBeginExecution(id, "Concurrent Launch Guard", 1))
        // Calling enqueueScheduledCampaign on an already-active campaign must be a strict no-op (stays RUNNING, not QUEUED)
        repository.enqueueScheduledCampaign(id)
        val stillRunning = db.campaignDao().getCampaignByIdDirect(id)
        assertEquals(CampaignStatus.RUNNING, stillRunning?.status)
        assertEquals(0, BroadcastExecutionCoordinator.state.value.queuedCampaignIds.size)
        BroadcastExecutionCoordinator.finishExecution(clearQueue = true)

        // Fix #6: Orphaned QUEUED and RUNNING campaigns recover cleanly to PAUSED after process death
        val queuedOrphanId = repository.createCampaignWithLogs(
            title = "Orphaned Queued Campaign",
            messageTemplate = "Hi {{name}}",
            targetGroupIds = emptyList(),
            explicitContacts = listOf(c),
            scheduledTimeEpoch = 0L,
            delaySeconds = 2,
            mediaUri = null,
            targetPackage = WhatsAppInstanceDiscovery.PKG_WHATSAPP,
            targetUserSerial = 0L,
            isCloneTarget = false,
            initialStatus = CampaignStatus.QUEUED
        )
        repository.recoverInterruptedCampaignsAndRepairMetrics()
        assertEquals(CampaignStatus.PAUSED, db.campaignDao().getCampaignByIdDirect(id)?.status)
        assertEquals(CampaignStatus.PAUSED, db.campaignDao().getCampaignByIdDirect(queuedOrphanId)?.status)

        // Fix #6b: Live coordinator syncs resume counts accurately
        BroadcastExecutionCoordinator.tryBeginExecution(id, "Resume Test", 10)
        BroadcastExecutionCoordinator.syncInitialCounts(sentCount = 6, failedCount = 1)
        assertEquals(6, BroadcastExecutionCoordinator.state.value.sentCount)
        assertEquals(1, BroadcastExecutionCoordinator.state.value.failedCount)
        BroadcastExecutionCoordinator.finishExecution(clearQueue = true)

        // Fix #7: Devanagari numerals & WhatsApp Bidi-wrapped phone normalization (digits-only E.164 for wa.me)
        assertEquals("919876543210", MessageVariableParser.normalizeIndianOrNriPhone("९८७६५४३२१०"))
        assertEquals("919876543210", MessageVariableParser.normalizeIndianOrNriPhone("\u200E+91 98765 43210\u200F"))
    }
}
