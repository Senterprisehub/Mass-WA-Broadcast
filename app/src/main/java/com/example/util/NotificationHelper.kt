package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {

    const val CHANNEL_BROADCAST_SERVICE = "mass_wa_broadcast_service"
    const val CHANNEL_CAMPAIGN_ALERTS = "mass_wa_campaign_alerts"
    const val CHANNEL_MESSAGE_STATUS = "mass_wa_message_status"
    const val CHANNEL_SCREEN_WAKE = "mass_wa_screen_wake"

    const val FOREGROUND_SERVICE_NOTIFICATION_ID = 1001
    const val EXTRA_CAMPAIGN_ID = "extra_campaign_id"

    // Non-overlapping stride-based IDs per campaign lifecycle
    fun campaignSuccessNotificationId(campaignId: Long): Int =
        10_000 + ((campaignId % 10_000L).toInt() * 5)

    fun campaignFailedNotificationId(campaignId: Long): Int =
        10_000 + ((campaignId % 10_000L).toInt() * 5) + 1

    fun campaignQueuedNotificationId(campaignId: Long): Int =
        10_000 + ((campaignId % 10_000L).toInt() * 5) + 2

    fun scheduledReadyNotificationId(campaignId: Long): Int =
        10_000 + ((campaignId % 10_000L).toInt() * 5) + 3

    fun missedNotificationId(campaignId: Long, attempt: Int = 0): Int =
        100_000 + ((campaignId % 10_000L).toInt() * 10) + (attempt.coerceIn(0, 9))

    fun ensureChannelsCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_BROADCAST_SERVICE,
                "Active Broadcast Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent live progress and Pause/Resume/Stop controls during dispatch"
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_CAMPAIGN_ALERTS,
                "Scheduled Campaign Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alerts when scheduled campaigns are queued, completed, or missed"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_MESSAGE_STATUS,
                "Delivery Summaries",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Campaign completion summaries and recipient status notifications"
            },
            NotificationChannel(
                CHANNEL_SCREEN_WAKE,
                "Scheduled Lockscreen Wake",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Full-screen alarm notifications for scheduled unattended broadcasts"
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
        )
        channels.forEach { nm.createNotificationChannel(it) }
    }

    fun buildOpenCampaignPendingIntent(context: Context, campaignId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CAMPAIGN_ID, campaignId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun notifyCampaignStatus(
        context: Context,
        notificationId: Int,
        channelId: String,
        title: String,
        message: String,
        campaignId: Long
    ) {
        ensureChannelsCreated(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(buildOpenCampaignPendingIntent(context, campaignId, notificationId))
            .build()
        runCatching { nm.notify(notificationId, notification) }
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { nm.cancel(notificationId) }
    }
}
