package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

object CallDetectionManager {

    fun isCellularCallActive(context: Context): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return false
        @Suppress("DEPRECATION")
        val state = runCatching { tm.callState }.getOrDefault(TelephonyManager.CALL_STATE_IDLE)
        return state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK
    }

    fun onCallStateChanged(stateExtra: String?, pauseEnabled: Boolean) {
        if (!pauseEnabled) return
        when (stateExtra) {
            TelephonyManager.EXTRA_STATE_RINGING,
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (BroadcastExecutionCoordinator.isActivelyExecuting) {
                    BroadcastExecutionCoordinator.pause("Auto-paused for incoming/active phone call")
                }
            }
        }
    }
}
