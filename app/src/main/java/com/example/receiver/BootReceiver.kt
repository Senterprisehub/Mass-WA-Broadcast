package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.data.local.AppDatabase
import com.example.data.model.BootScheduleDecision
import com.example.data.model.CampaignStatus
import com.example.service.ScheduledBroadcastService
import com.example.util.CampaignScheduler
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val supported = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
        if (action !in supported) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val campaignDao = db.campaignDao()
                val scheduled = campaignDao.getScheduledCampaignsDirect()
                val now = System.currentTimeMillis()

                for (campaign in scheduled) {
                    when (CampaignScheduler.classifyBootSchedule(campaign.scheduledTimeEpoch, now)) {
                        BootScheduleDecision.REARM_FUTURE -> {
                            // Handled cleanly via syncRollingHorizon below
                        }
                        BootScheduleDecision.FIRE_IMMEDIATELY_WITHIN_GRACE -> {
                            val svc = Intent(context, ScheduledBroadcastService::class.java).apply {
                                this.action = ScheduledBroadcastService.ACTION_WAKE_AND_ALERT
                                putExtra(ScheduledBroadcastService.EXTRA_CAMPAIGN_ID, campaign.id)
                            }
                            runCatching { ContextCompat.startForegroundService(context, svc) }
                        }
                        BootScheduleDecision.MARK_FAILED_MISSED_WINDOW -> {
                            campaignDao.updateStatus(
                                id = campaign.id,
                                status = CampaignStatus.FAILED,
                                diagnostic = "Missed scheduled window while device was powered off (>15 min grace)."
                            )
                            NotificationHelper.notifyCampaignStatus(
                                context = context,
                                notificationId = NotificationHelper.missedNotificationId(campaign.id),
                                channelId = NotificationHelper.CHANNEL_CAMPAIGN_ALERTS,
                                title = "Missed Scheduled Broadcast",
                                message = "${campaign.title} missed its scheduled window. Tap to review and relaunch.",
                                campaignId = campaign.id
                            )
                        }
                    }
                }

                CampaignScheduler.syncRollingHorizon(context, campaignDao, now)
            } finally {
                pending.finish()
            }
        }
    }
}
