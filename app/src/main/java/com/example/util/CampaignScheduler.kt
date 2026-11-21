package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.local.CampaignDao
import com.example.data.model.BootScheduleDecision
import com.example.data.model.Campaign
import com.example.receiver.ScheduledCampaignReceiver

object CampaignScheduler {

    const val ACTION_EXECUTE_SCHEDULED_CAMPAIGN = "com.example.ACTION_EXECUTE_SCHEDULED_CAMPAIGN"
    const val ACTION_REPLENISH_ALARM_HORIZON = "com.example.ACTION_REPLENISH_ALARM_HORIZON"
    const val EXTRA_CAMPAIGN_ID = "extra_scheduled_campaign_id"

    const val ROLLING_ALARM_HORIZON_MS = 48L * 60L * 60L * 1000L // 48 hours
    const val HORIZON_REPLENISH_INTERVAL_MS = 36L * 60L * 60L * 1000L // 36 hours
    const val BOOT_GRACE_WINDOW_MS = 15L * 60L * 1000L // 15 minutes
    const val HORIZON_ALARM_REQUEST_CODE = 99_001
    const val MAX_CONCURRENT_OEM_ALARMS = 80 // Safely below MIUI ~100 & ColorOS ~120 caps

    fun classifyBootSchedule(scheduledEpoch: Long, nowEpoch: Long): BootScheduleDecision {
        if (scheduledEpoch <= 0L) return BootScheduleDecision.MARK_FAILED_MISSED_WINDOW
        if (scheduledEpoch > nowEpoch) return BootScheduleDecision.REARM_FUTURE
        val overdueMs = nowEpoch - scheduledEpoch
        return if (overdueMs <= BOOT_GRACE_WINDOW_MS) {
            BootScheduleDecision.FIRE_IMMEDIATELY_WITHIN_GRACE
        } else {
            BootScheduleDecision.MARK_FAILED_MISSED_WINDOW
        }
    }

    /**
     * Arms only campaigns within the rolling 48-hour horizon (capped at MAX_CONCURRENT_OEM_ALARMS)
     * and schedules a single horizon replenisher alarm for campaigns beyond 48 hours.
     */
    suspend fun syncRollingHorizon(
        context: Context,
        campaignDao: CampaignDao,
        nowEpoch: Long = System.currentTimeMillis()
    ): Int {
        val horizonCutoff = nowEpoch + ROLLING_ALARM_HORIZON_MS
        val withinHorizon = campaignDao.getScheduledWithinHorizonDirect(horizonCutoff)
            .filter { it.scheduledTimeEpoch > nowEpoch }
            .take(MAX_CONCURRENT_OEM_ALARMS)

        withinHorizon.forEach { campaign ->
            armExactCampaignAlarm(context, campaign.id, campaign.scheduledTimeEpoch)
        }

        val nextBeyond = campaignDao.getNextScheduledBeyondHorizonDirect(horizonCutoff)
        if (nextBeyond != null) {
            val halfway = nowEpoch + ((nextBeyond.scheduledTimeEpoch - nowEpoch) / 2L)
            val replenishAt = minOf(nowEpoch + HORIZON_REPLENISH_INTERVAL_MS, halfway)
                .coerceAtLeast(nowEpoch + 60_000L)
            armHorizonReplenishAlarm(context, replenishAt)
        } else {
            cancelHorizonReplenishAlarm(context)
        }

        return withinHorizon.size
    }

    fun armExactCampaignAlarm(context: Context, campaignId: Long, triggerAtEpoch: Long): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        val pi = buildCampaignPendingIntent(context, campaignId)
        return scheduleExactSafe(am, triggerAtEpoch, pi)
    }

    fun cancelCampaignAlarm(context: Context, campaignId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = buildCampaignPendingIntent(context, campaignId)
        runCatching { am.cancel(pi) }
        pi.cancel()
    }

    fun armHorizonReplenishAlarm(context: Context, triggerAtEpoch: Long): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        val pi = buildHorizonReplenishPendingIntent(context)
        return scheduleExactSafe(am, triggerAtEpoch, pi)
    }

    fun cancelHorizonReplenishAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = buildHorizonReplenishPendingIntent(context)
        runCatching { am.cancel(pi) }
        pi.cancel()
    }

    private fun scheduleExactSafe(am: AlarmManager, triggerAtEpoch: Long, pi: PendingIntent): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpoch, pi)
            } else {
                val clockInfo = AlarmManager.AlarmClockInfo(triggerAtEpoch, pi)
                am.setAlarmClock(clockInfo, pi)
            }
            true
        } catch (_: SecurityException) {
            runCatching {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpoch, pi)
                true
            }.getOrDefault(false)
        } catch (_: Throwable) {
            false
        }
    }

    private fun buildCampaignPendingIntent(context: Context, campaignId: Long): PendingIntent {
        val intent = Intent(context, ScheduledCampaignReceiver::class.java).apply {
            action = ACTION_EXECUTE_SCHEDULED_CAMPAIGN
            putExtra(EXTRA_CAMPAIGN_ID, campaignId)
        }
        val reqCode = (20_000 + (campaignId % 70_000L)).toInt()
        return PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildHorizonReplenishPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScheduledCampaignReceiver::class.java).apply {
            action = ACTION_REPLENISH_ALARM_HORIZON
        }
        return PendingIntent.getBroadcast(
            context,
            HORIZON_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
