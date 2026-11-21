package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Campaign
import com.example.data.model.CampaignDashboardAggregate
import com.example.data.model.CampaignStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CampaignDao {
    @Query("SELECT * FROM campaigns ORDER BY createdAt DESC")
    fun observeCampaigns(): Flow<List<Campaign>>

    @Query("SELECT * FROM campaigns WHERE id = :id")
    fun observeCampaignById(id: Long): Flow<Campaign?>

    @Query("SELECT * FROM campaigns WHERE id = :id")
    suspend fun getCampaignByIdDirect(id: Long): Campaign?

    @Query(
        """
        SELECT
            COUNT(*) AS totalCampaigns,
            COALESCE(SUM(sentCount), 0) AS totalSent,
            COALESCE(SUM(deliveredCount), 0) AS totalDelivered,
            COALESCE(SUM(readCount), 0) AS totalRead,
            COALESCE(SUM(failedCount), 0) AS totalFailed,
            COALESCE(SUM(CASE WHEN status = 'SCHEDULED' THEN 1 ELSE 0 END), 0) AS scheduledCount,
            COALESCE(SUM(CASE WHEN status = 'QUEUED' THEN 1 ELSE 0 END), 0) AS queuedCount,
            COALESCE(SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END), 0) AS runningCount
        FROM campaigns
        """
    )
    fun observeDashboardAggregate(): Flow<CampaignDashboardAggregate>

    @Query("SELECT * FROM campaigns WHERE status = 'SCHEDULED' ORDER BY scheduledTimeEpoch ASC")
    suspend fun getScheduledCampaignsDirect(): List<Campaign>

    @Query(
        """
        SELECT * FROM campaigns
        WHERE status = 'SCHEDULED'
          AND scheduledTimeEpoch > 0
          AND scheduledTimeEpoch <= :horizonCutoffEpoch
        ORDER BY scheduledTimeEpoch ASC
        """
    )
    suspend fun getScheduledWithinHorizonDirect(horizonCutoffEpoch: Long): List<Campaign>

    @Query(
        """
        SELECT * FROM campaigns
        WHERE status = 'SCHEDULED'
          AND scheduledTimeEpoch > :horizonCutoffEpoch
        ORDER BY scheduledTimeEpoch ASC
        LIMIT 1
        """
    )
    suspend fun getNextScheduledBeyondHorizonDirect(horizonCutoffEpoch: Long): Campaign?

    @Query("SELECT * FROM campaigns WHERE status = 'QUEUED' ORDER BY scheduledTimeEpoch ASC, createdAt ASC")
    suspend fun getQueuedCampaignsDirect(): List<Campaign>

    @Query(
        """
        SELECT * FROM campaigns
        WHERE status IN ('RUNNING', 'PAUSED', 'QUEUED')
           OR (status = 'COMPLETED' AND (sentCount + failedCount) < totalRecipients)
        """
    )
    suspend fun getCampaignsNeedingMetricRepairDirect(): List<Campaign>

    @Query(
        """
        SELECT * FROM campaigns
        WHERE status IN ('SCHEDULED', 'QUEUED', 'RUNNING')
          AND scheduledTimeEpoch BETWEEN :dayStartEpoch AND :dayEndEpoch
        ORDER BY scheduledTimeEpoch ASC
        """
    )
    suspend fun getCampaignsForDayDirect(dayStartEpoch: Long, dayEndEpoch: Long): List<Campaign>

    @Query("SELECT mediaUri FROM campaigns WHERE mediaUri IS NOT NULL AND mediaUri != ''")
    suspend fun getAllReferencedMediaUrisDirect(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCampaign(campaign: Campaign): Long

    @Update
    suspend fun updateCampaign(campaign: Campaign)

    @Query("UPDATE campaigns SET status = :status, failureDiagnostic = :diagnostic WHERE id = :id")
    suspend fun updateStatus(id: Long, status: CampaignStatus, diagnostic: String? = null)

    @Query(
        """
        UPDATE campaigns
        SET sentCount = :sentCount,
            deliveredCount = :deliveredCount,
            readCount = :readCount,
            failedCount = :failedCount,
            status = :status,
            completedAt = :completedAt
        WHERE id = :id
        """
    )
    suspend fun updateCountsAndStatus(
        id: Long,
        sentCount: Int,
        deliveredCount: Int,
        readCount: Int,
        failedCount: Int,
        status: CampaignStatus,
        completedAt: Long?
    )

    @Delete
    suspend fun deleteCampaign(campaign: Campaign)
}
