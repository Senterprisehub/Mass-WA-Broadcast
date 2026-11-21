package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.BroadcastLogEntry
import com.example.data.model.DeliveryStatus
import kotlinx.coroutines.flow.Flow

data class CampaignLogCountSummary(
    val sentCount: Int = 0,
    val deliveredCount: Int = 0,
    val readCount: Int = 0,
    val failedCount: Int = 0,
    val pendingCount: Int = 0
)

@Dao
interface BroadcastLogDao {
    @Query("SELECT * FROM broadcast_logs WHERE campaignId = :campaignId ORDER BY id ASC")
    fun observeLogsForCampaign(campaignId: Long): Flow<List<BroadcastLogEntry>>

    @Query("SELECT * FROM broadcast_logs WHERE campaignId = :campaignId ORDER BY id ASC")
    suspend fun getLogsForCampaignDirect(campaignId: Long): List<BroadcastLogEntry>

    @Query("SELECT * FROM broadcast_logs WHERE campaignId = :campaignId AND status = 'PENDING' ORDER BY id ASC")
    suspend fun getPendingLogsForCampaignDirect(campaignId: Long): List<BroadcastLogEntry>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN status IN ('SENT', 'DELIVERED', 'READ') THEN 1 ELSE 0 END), 0) AS sentCount,
            COALESCE(SUM(CASE WHEN status IN ('DELIVERED', 'READ') THEN 1 ELSE 0 END), 0) AS deliveredCount,
            COALESCE(SUM(CASE WHEN status = 'READ' THEN 1 ELSE 0 END), 0) AS readCount,
            COALESCE(SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END), 0) AS failedCount,
            COALESCE(SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pendingCount
        FROM broadcast_logs
        WHERE campaignId = :campaignId
        """
    )
    suspend fun getCountSummaryForCampaign(campaignId: Long): CampaignLogCountSummary

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogsBatch(logs: List<BroadcastLogEntry>)

    @Query(
        """
        UPDATE broadcast_logs
        SET status = :status,
            timestamp = :timestamp,
            errorMessage = :errorMessage
        WHERE id = :logId
        """
    )
    suspend fun updateLogStatus(
        logId: Long,
        status: DeliveryStatus,
        timestamp: Long = System.currentTimeMillis(),
        errorMessage: String? = null
    )

    @Query("DELETE FROM broadcast_logs WHERE campaignId = :campaignId")
    suspend fun deleteLogsForCampaign(campaignId: Long)

    @Query("DELETE FROM broadcast_logs WHERE timestamp < :cutoffEpoch AND status != 'PENDING'")
    suspend fun pruneLogsOlderThan(cutoffEpoch: Long): Int

    @Query("SELECT COUNT(*) FROM broadcast_logs")
    fun observeTotalLogCount(): Flow<Int>
}
