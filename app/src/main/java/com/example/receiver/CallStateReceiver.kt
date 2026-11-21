package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.example.util.AppSettingsPreferences
import com.example.util.CallDetectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val stateExtra = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = AppSettingsPreferences(context).getCurrentSettings()
                CallDetectionManager.onCallStateChanged(stateExtra, settings.pauseOnIncomingCall)
            } finally {
                pending.finish()
            }
        }
    }
}
