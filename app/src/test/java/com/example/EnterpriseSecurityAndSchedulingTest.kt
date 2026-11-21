package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.BootScheduleDecision
import com.example.data.model.LockType
import com.example.util.CampaignScheduler
import com.example.util.KeystoreCryptoManager
import com.example.util.MediaAttachmentHelper
import com.example.util.VaultPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EnterpriseSecurityAndSchedulingTest {

    @Test
    fun `AES-256-GCM generates randomized IVs and rejects tampered ciphertext`() {
        val enc1 = KeystoreCryptoManager.encrypt("482910")
        val enc2 = KeystoreCryptoManager.encrypt("482910")
        assertNotEquals("Randomized IV must produce distinct ciphertexts", enc1, enc2)

        val decrypted = KeystoreCryptoManager.decryptToChars(enc1)
        assertNotNull(decrypted)
        assertEquals("482910", decrypted!!.concatToString())
        KeystoreCryptoManager.zeroFill(decrypted)
        assertTrue(decrypted.all { it == '\u0000' })

        // Tamper ciphertext byte
        val parts = enc1.split(":")
        val tampered = "${parts[0]}:AAAA${parts[1].drop(4)}"
        assertNull(KeystoreCryptoManager.decryptToChars(tampered))
    }

    @Test
    fun `VaultPreferences encrypts PIN, Pattern, Password, Slide and zero-fills on close`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = VaultPreferences(context)

        // 1. PIN & 3x3 Pattern
        vault.saveDeviceLock(LockType.PIN, "2580".toCharArray())
        vault.saveWhatsAppLock(LockType.PATTERN, "0,1,2,4,6,7,8".toCharArray())

        val creds = vault.decryptForDispatch()
        val devChars = creds.deviceSecret
        val waChars = creds.whatsAppSecret
        creds.use {
            assertEquals(LockType.PIN, it.deviceLockType)
            assertEquals("2580", it.deviceSecret?.concatToString())
            assertEquals(LockType.PATTERN, it.whatsAppLockType)
            assertEquals("0,1,2,4,6,7,8", it.whatsAppSecret?.concatToString())
            assertTrue(it.autoUnlockEnabled)
        }
        assertTrue(creds.isClosed)
        assertTrue(devChars!!.all { it == '\u0000' })
        assertTrue(waChars!!.all { it == '\u0000' })

        // 2. Password & Slide Lock
        vault.saveDeviceLock(LockType.SLIDE, null)
        vault.saveWhatsAppLock(LockType.PASSWORD, "S3cure#India!".toCharArray())
        vault.decryptForDispatch().use {
            assertEquals(LockType.SLIDE, it.deviceLockType)
            assertEquals(LockType.PASSWORD, it.whatsAppLockType)
            assertEquals("S3cure#India!", it.whatsAppSecret?.concatToString())
        }

        // 3. Wipe
        vault.wipeAllVaultCredentials()
        vault.decryptForDispatch().use {
            assertEquals(LockType.NONE, it.deviceLockType)
            assertEquals(LockType.NONE, it.whatsAppLockType)
            assertNull(it.deviceSecret)
            assertNull(it.whatsAppSecret)
            assertFalse(it.autoUnlockEnabled)
        }
    }

    @Test
    fun `CampaignScheduler 15 minute boot grace classifier handles all windows`() {
        val now = 1_700_000_000_000L
        assertEquals(
            BootScheduleDecision.REARM_FUTURE,
            CampaignScheduler.classifyBootSchedule(now + 60_000L, now)
        )
        assertEquals(
            BootScheduleDecision.FIRE_IMMEDIATELY_WITHIN_GRACE,
            CampaignScheduler.classifyBootSchedule(now - 5L * 60L * 1000L, now)
        )
        assertEquals(
            BootScheduleDecision.FIRE_IMMEDIATELY_WITHIN_GRACE,
            CampaignScheduler.classifyBootSchedule(now - CampaignScheduler.BOOT_GRACE_WINDOW_MS, now)
        )
        assertEquals(
            BootScheduleDecision.MARK_FAILED_MISSED_WINDOW,
            CampaignScheduler.classifyBootSchedule(now - CampaignScheduler.BOOT_GRACE_WINDOW_MS - 1_000L, now)
        )
        assertEquals(
            BootScheduleDecision.MARK_FAILED_MISSED_WINDOW,
            CampaignScheduler.classifyBootSchedule(0L, now)
        )
    }

    @Test
    fun `MediaAttachmentHelper collects orphaned files while keeping referenced files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = MediaAttachmentHelper.getMediaDirectory(context)
        val kept = File(dir, "kept_banner.jpg").apply { writeText("img1") }
        val orphan = File(dir, "orphan_banner.jpg").apply { writeText("img2") }

        val removed = MediaAttachmentHelper.collectOrphanedMedia(context, setOf(kept.absolutePath))
        assertEquals(1, removed)
        assertTrue(kept.exists())
        assertFalse(orphan.exists())
        kept.delete()
    }

    @Test
    fun `ScheduleDateTimeHelper preserves exact IST calendar day and time without timezone drift`() {
        val ist = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        val initialCal = java.util.Calendar.getInstance(ist).apply {
            set(2026, java.util.Calendar.NOVEMBER, 20, 9, 30, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val utcMidnight = com.example.ui.components.ScheduleDateTimeHelper.localEpochToUtcDateMidnight(
            initialCal.timeInMillis,
            ist
        )
        // Pick 25 Nov 2026 in UTC midnight
        val pickedUtc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, java.util.Calendar.NOVEMBER, 25, 0, 0, 0)
        }.timeInMillis

        val afterDate = com.example.ui.components.ScheduleDateTimeHelper.applyUtcDateToLocalEpoch(
            initialCal.timeInMillis,
            pickedUtc,
            ist
        )
        val afterTime = com.example.ui.components.ScheduleDateTimeHelper.applyLocalTimeToEpoch(
            afterDate,
            18,
            45,
            ist
        )

        val resultCal = java.util.Calendar.getInstance(ist).apply { timeInMillis = afterTime }
        assertTrue(utcMidnight > 0L)
        assertEquals(2026, resultCal.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.NOVEMBER, resultCal.get(java.util.Calendar.MONTH))
        assertEquals(25, resultCal.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(18, resultCal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(45, resultCal.get(java.util.Calendar.MINUTE))
    }

    @Test
    fun `conditional auto-lock triggers only when started while locked and undisturbed`() {
        assertTrue(
            com.example.util.BroadcastExecutionCoordinator.shouldAutoLockAfterCompletion(
                startedWhileLocked = true,
                autoLockEnabled = true,
                stoppedByUser = false,
                pausedForCall = false
            )
        )
        assertFalse(
            com.example.util.BroadcastExecutionCoordinator.shouldAutoLockAfterCompletion(
                startedWhileLocked = false,
                autoLockEnabled = true,
                stoppedByUser = false,
                pausedForCall = false
            )
        )
        assertFalse(
            com.example.util.BroadcastExecutionCoordinator.shouldAutoLockAfterCompletion(
                startedWhileLocked = true,
                autoLockEnabled = false,
                stoppedByUser = false,
                pausedForCall = false
            )
        )
        assertFalse(
            com.example.util.BroadcastExecutionCoordinator.shouldAutoLockAfterCompletion(
                startedWhileLocked = true,
                autoLockEnabled = true,
                stoppedByUser = true,
                pausedForCall = false
            )
        )
        assertFalse(
            com.example.util.BroadcastExecutionCoordinator.shouldAutoLockAfterCompletion(
                startedWhileLocked = true,
                autoLockEnabled = true,
                stoppedByUser = false,
                pausedForCall = true
            )
        )

        com.example.util.BroadcastExecutionCoordinator.resetForTest()
        val begun = com.example.util.BroadcastExecutionCoordinator.tryBeginExecution(
            campaignId = 42L,
            title = "Night Scheduled Run",
            totalRecipients = 10,
            startedWhileLocked = true
        )
        assertTrue(begun)
        assertTrue(com.example.util.BroadcastExecutionCoordinator.state.value.startedWhileLocked)
        assertEquals(
            com.example.data.model.AutomationStep.SWIPING_LOCKSCREEN,
            com.example.util.BroadcastExecutionCoordinator.state.value.currentStep
        )
        com.example.util.BroadcastExecutionCoordinator.resetForTest()
    }
}
