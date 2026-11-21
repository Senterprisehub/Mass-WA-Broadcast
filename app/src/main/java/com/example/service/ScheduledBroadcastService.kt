package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.repository.BroadcastRepository
import com.example.ui.activity.ScheduleAlertOverlayActivity
import com.example.util.AppSettingsPreferences
import com.example.util.BroadcastExecutionCoordinator
import com.example.util.BroadcastExecutionState
import com.example.util.NotificationAlertManager
import com.example.util.NotificationHelper
import com.example.util.ScreenWakeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class ScheduledBroadcastService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var stateObserverJob: Job? = null
    private val pendingFallbackJobs = ConcurrentHashMap<Long, Job>()

    private lateinit var repository: BroadcastRepository
    private lateinit var settingsPreferences: AppSettingsPreferences

    override fun onCreate() {
        super.onCreate()
        repository = BroadcastRepository(applicationContext)
        settingsPreferences = AppSettingsPreferences(applicationContext)
        NotificationHelper.ensureChannelsCreated(applicationContext)

        stateObserverJob = serviceScope.launch {
            BroadcastExecutionCoordinator.state.collectLatest { execState ->
                if (execState.isRunning) {
                    updateForegroundNotification(execState)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_EXECUTE_NOW
        val campaignId = intent?.getLongExtra(EXTRA_CAMPAIGN_ID, -1L) ?: -1L

        startForegroundCompat(buildNotification(BroadcastExecutionCoordinator.state.value))

        when (action) {
            ACTION_PAUSE -> {
                BroadcastExecutionCoordinator.pause("Paused from notification")
            }
            ACTION_RESUME -> {
                BroadcastExecutionCoordinator.resume()
            }
            ACTION_STOP -> {
                pendingFallbackJobs.values.forEach { it.cancel() }
                pendingFallbackJobs.clear()
                NotificationAlertManager.stopAlert(applicationContext)
                if (BroadcastExecutionCoordinator.isActivelyExecuting) {
                    // Let executeBroadcast()'s finally block flush Room counts & reset Accessibility scope before stopping
                    BroadcastExecutionCoordinator.requestStop()
                    serviceScope.launch {
                        delay(3_500L)
                        if (!BroadcastExecutionCoordinator.isActivelyExecuting) {
                            ScreenWakeManager.releaseWakeLock()
                            stopSelf()
                        }
                    }
                } else {
                    ScreenWakeManager.releaseWakeLock()
                    BroadcastExecutionCoordinator.requestStop()
                    stopSelf()
                }
            }
            ACTION_OVERLAY_ACKNOWLEDGED -> {
                if (campaignId > 0L) {
                    pendingFallbackJobs.remove(campaignId)?.cancel()
                    NotificationAlertManager.stopAlert(applicationContext)
                    launchExecution(campaignId)
                }
            }
            ACTION_WAKE_AND_ALERT -> {
                if (campaignId > 0L) {
                    handleWakeAndAlert(campaignId)
                }
            }
            ACTION_EXECUTE_NOW -> {
                if (campaignId > 0L) {
                    pendingFallbackJobs.remove(campaignId)?.cancel()
                    launchExecution(campaignId)
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun handleWakeAndAlert(campaignId: Long) {
        // Idempotency guard: never re-alert or hijack WhatsApp if this campaign is already running/queued
        if (BroadcastExecutionCoordinator.isCampaignActiveOrQueued(campaignId)) {
            return
        }

        // CRITICAL: If another broadcast is currently sending in WhatsApp, silently enqueue into FIFO
        // so we NEVER launch the overlay or ring an alarm on top of an active WhatsApp automation session!
        if (BroadcastExecutionCoordinator.isActivelyExecuting) {
            serviceScope.launch {
                repository.enqueueScheduledCampaign(campaignId)
            }
            return
        }

        serviceScope.launch {
            val settings = settingsPreferences.getCurrentSettings()
            ScreenWakeManager.acquireTemporaryWakeLock(applicationContext, 30_000L)
            NotificationAlertManager.startScheduledAlert(
                applicationContext,
                soundEnabled = settings.alertSoundEnabled,
                vibrateEnabled = settings.alertVibrateEnabled
            )

            // Launch lock-screen wake overlay activity
            val overlayIntent = Intent(applicationContext, ScheduleAlertOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_CAMPAIGN_ID, campaignId)
            }
            runCatching { startActivity(overlayIntent) }

            // Per-campaign 6-second OEM headless fallback watchdog (never cancels another campaign's fallback!)
            pendingFallbackJobs.remove(campaignId)?.cancel()
            val fallbackJob = serviceScope.launch {
                delay(OEM_HEADLESS_FALLBACK_DELAY_MS)
                pendingFallbackJobs.remove(campaignId)
                NotificationAlertManager.stopAlert(applicationContext)
                if (!BroadcastExecutionCoordinator.isCampaignActiveOrQueued(campaignId)) {
                    launchExecution(campaignId)
                }
            }
            pendingFallbackJobs[campaignId] = fallbackJob
        }
    }

    private fun launchExecution(campaignId: Long) {
        if (BroadcastExecutionCoordinator.isCampaignActiveOrQueued(campaignId)) {
            return
        }
        serviceScope.launch {
            try {
                repository.executeBroadcast(campaignId)
            } finally {
                pendingFallbackJobs.remove(campaignId)?.cancel()
                ScreenWakeManager.releaseWakeLock()
                NotificationAlertManager.stopAlert(applicationContext)
                if (!BroadcastExecutionCoordinator.isActivelyExecuting && pendingFallbackJobs.isEmpty()) {
                    stopSelf()
                }
            }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.FOREGROUND_SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationHelper.FOREGROUND_SERVICE_NOTIFICATION_ID, notification)
        }
    }

    private fun updateForegroundNotification(execState: BroadcastExecutionState) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
        runCatching {
            nm.notify(NotificationHelper.FOREGROUND_SERVICE_NOTIFICATION_ID, buildNotification(execState))
        }
    }

    private fun buildNotification(execState: BroadcastExecutionState): Notification {
        val title = execState.activeCampaignTitle.ifBlank { "Mass WA Broadcast Engine" }
        val progressText = if (execState.totalRecipients > 0) {
            "${execState.currentRecipientIndex}/${execState.totalRecipients} • ${execState.lastStatusNote}"
        } else {
            "Preparing broadcast session..."
        }

        val toggleAction = if (execState.isPaused) {
            NotificationCompat.Action(
                0,
                "Resume",
                serviceActionPendingIntent(ACTION_RESUME, 301)
            )
        } else {
            NotificationCompat.Action(
                0,
                "Pause",
                serviceActionPendingIntent(ACTION_PAUSE, 302)
            )
        }

        val stopAction = NotificationCompat.Action(
            0,
            "Stop",
            serviceActionPendingIntent(ACTION_STOP, 303)
        )

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_BROADCAST_SERVICE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(progressText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(execState.totalRecipients, execState.currentRecipientIndex, execState.totalRecipients == 0)
            .addAction(toggleAction)
            .addAction(stopAction)
            .build()
    }

    private fun serviceActionPendingIntent(action: String, reqCode: Int): PendingIntent {
        val intent = Intent(this, ScheduledBroadcastService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pendingFallbackJobs.values.forEach { it.cancel() }
        pendingFallbackJobs.clear()
        stateObserverJob?.cancel()
        serviceScope.cancel()
        ScreenWakeManager.releaseWakeLock()
        NotificationAlertManager.stopAlert(applicationContext)
        super.onDestroy()
    }

    companion object {
        const val ACTION_EXECUTE_NOW = "com.example.service.ACTION_EXECUTE_NOW"
        const val ACTION_WAKE_AND_ALERT = "com.example.service.ACTION_WAKE_AND_ALERT"
        const val ACTION_OVERLAY_ACKNOWLEDGED = "com.example.service.ACTION_OVERLAY_ACKNOWLEDGED"
        const val ACTION_PAUSE = "com.example.service.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.service.ACTION_RESUME"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val EXTRA_CAMPAIGN_ID = "extra_service_campaign_id"
        const val OEM_HEADLESS_FALLBACK_DELAY_MS = 6_000L

        fun startExecuteNow(context: Context, campaignId: Long) {
            val intent = Intent(context, ScheduledBroadcastService::class.java).apply {
                action = ACTION_EXECUTE_NOW
                putExtra(EXTRA_CAMPAIGN_ID, campaignId)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
