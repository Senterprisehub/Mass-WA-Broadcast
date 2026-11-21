package com.example.ui.activity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.service.ScheduledBroadcastService
import com.example.ui.theme.MassWaBroadcastTheme
import com.example.util.NotificationAlertManager
import kotlinx.coroutines.delay

class ScheduleAlertOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val campaignId = intent.getLongExtra(ScheduledBroadcastService.EXTRA_CAMPAIGN_ID, -1L)

        setContent {
            var countdown by remember { mutableIntStateOf(6) }

            LaunchedEffect(Unit) {
                while (countdown > 0) {
                    delay(1_000L)
                    countdown--
                }
                acknowledgeAndStart(campaignId)
            }

            MassWaBroadcastTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AlarmOn,
                                contentDescription = "Scheduled broadcast alarm",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "Scheduled Broadcast Ready",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = "Starting automatically in ${countdown}s. Ensure device stays awake during dispatch.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        NotificationAlertManager.stopAlert(applicationContext)
                                        val stopIntent = Intent(this@ScheduleAlertOverlayActivity, ScheduledBroadcastService::class.java).apply {
                                            action = ScheduledBroadcastService.ACTION_STOP
                                        }
                                        startService(stopIntent)
                                        finish()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("overlay_cancel_button")
                                ) {
                                    Text("Dismiss")
                                }
                                Button(
                                    onClick = { acknowledgeAndStart(campaignId) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("overlay_start_now_button")
                                ) {
                                    Text("Start Now")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun acknowledgeAndStart(campaignId: Long) {
        NotificationAlertManager.stopAlert(applicationContext)
        val svcIntent = Intent(this, ScheduledBroadcastService::class.java).apply {
            action = ScheduledBroadcastService.ACTION_OVERLAY_ACKNOWLEDGED
            putExtra(ScheduledBroadcastService.EXTRA_CAMPAIGN_ID, campaignId)
        }
        ContextCompat.startForegroundService(this, svcIntent)
        finish()
    }
}
