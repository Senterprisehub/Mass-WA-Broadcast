package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.data.local.AppDatabase
import com.example.service.ScheduledBroadcastService
import com.example.util.CampaignScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduledCampaignReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CampaignScheduler.ACTION_REPLENISH_ALARM_HORIZON -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getInstance(context)
                        CampaignScheduler.syncRollingHorizon(context, db.campaignDao())
                    } finally {
                        pending.finish()
                    }
                }
            }

            CampaignScheduler.ACTION_EXECUTE_SCHEDULED_CAMPAIGN -> {
                val campaignId = intent.getLongExtra(CampaignScheduler.EXTRA_CAMPAIGN_ID, -1L)
                if (campaignId <= 0L) return
                val serviceIntent = Intent(context, ScheduledBroadcastService::class.java).apply {
                    action = ScheduledBroadcastService.ACTION_WAKE_AND_ALERT
                    putExtra(ScheduledBroadcastService.EXTRA_CAMPAIGN_ID, campaignId)
                }
                runCatching {
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
            }
        }
    }
}
