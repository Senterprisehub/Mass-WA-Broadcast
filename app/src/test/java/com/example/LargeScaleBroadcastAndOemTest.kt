package com.example

import android.content.Context
import android.telephony.TelephonyManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.CampaignStatus
import com.example.data.model.Contact
import com.example.data.model.OemFamily
import com.example.data.repository.BroadcastRepository
import com.example.util.BroadcastExecutionCoordinator
import com.example.util.CallDetectionManager
import com.example.util.NotificationHelper
import com.example.util.OemDetector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LargeScaleBroadcastAndOemTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: BroadcastRepository

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
    fun `handles 1200+ contacts exceeding SQLite 999 bind limit and computes O1 SQL aggregates`() = runTest {
        val groupId = repository.createGroup("Pan-India Scale Group", "Stress test")
        val contacts = (1..1250).map { i ->
            Contact(
                groupId = groupId,
                name = "Customer $i",
                phoneNumber = "98765${String.format("%05d", i)}"
            )
        }
        val imported = repository.importContactsList(contacts)
        assertEquals(1250, imported)

        val fetchedByGroup = repository.getContactsByGroupIdsChunked(listOf(groupId))
        assertEquals(1250, fetchedByGroup.size)

        val fetchedByIds = repository.getContactsByIdsChunked(fetchedByGroup.map { it.id })
        assertEquals(1250, fetchedByIds.size)

        // Create and execute a small campaign in dryRunForTest mode to verify FIFO + SQL aggregate
        val campaignId = repository.createCampaignWithLogs(
            title = "Dry Run Campaign",
            messageTemplate = "Hi {{first_name}}!",
            targetGroupIds = emptyList(),
            explicitContacts = fetchedByIds.take(15),
            scheduledTimeEpoch = 0L,
            delaySeconds = 2,
            mediaUri = null,
            targetPackage = "com.whatsapp",
            targetUserSerial = 0L,
            isCloneTarget = false,
            initialStatus = CampaignStatus.QUEUED
        )

        repository.executeBroadcast(campaignId, dryRunForTest = true)

        val aggregate = repository.dashboardAggregateFlow.first()
        assertEquals(1, aggregate.totalCampaigns)
        assertEquals(15, aggregate.totalSent)
        assertEquals(0, aggregate.totalFailed)
    }

    @Test
    fun `detects all major Indian OEM families and pauses active broadcasts on incoming call`() {
        assertEquals(OemFamily.XIAOMI, OemDetector.detectOem("Xiaomi", "Redmi"))
        assertEquals(OemFamily.XIAOMI, OemDetector.detectOem("Xiaomi", "POCO"))
        assertEquals(OemFamily.OPPO_REALME_ONEPLUS, OemDetector.detectOem("realme", "realme"))
        assertEquals(OemFamily.OPPO_REALME_ONEPLUS, OemDetector.detectOem("OnePlus", "OnePlus"))
        assertEquals(OemFamily.VIVO_IQOO, OemDetector.detectOem("vivo", "iQOO"))
        assertEquals(OemFamily.SAMSUNG, OemDetector.detectOem("samsung", "samsung"))
        assertEquals(OemFamily.OTHER, OemDetector.detectOem("Google", "Pixel"))

        // Incoming phone call auto-pause test
        assertTrue(BroadcastExecutionCoordinator.tryBeginExecution(101L, "Test", 10))
        assertFalse(BroadcastExecutionCoordinator.state.value.isPaused)
        CallDetectionManager.onCallStateChanged(TelephonyManager.EXTRA_STATE_RINGING, pauseEnabled = true)
        assertTrue(BroadcastExecutionCoordinator.state.value.isPaused)
        BroadcastExecutionCoordinator.finishExecution(clearQueue = true)
    }

    @Test
    fun `notification IDs are strictly collision-free across lifecycle states`() {
        val ids = setOf(
            NotificationHelper.campaignSuccessNotificationId(42L),
            NotificationHelper.campaignFailedNotificationId(42L),
            NotificationHelper.campaignQueuedNotificationId(42L),
            NotificationHelper.scheduledReadyNotificationId(42L),
            NotificationHelper.missedNotificationId(42L, 0),
            NotificationHelper.missedNotificationId(42L, 1)
        )
        assertEquals(6, ids.size)
    }
}
