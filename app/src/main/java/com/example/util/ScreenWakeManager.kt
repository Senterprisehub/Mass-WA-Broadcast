package com.example.util

import android.content.Context
import android.os.PowerManager

object ScreenWakeManager {

    private const val WAKE_TAG = "MassWaBroadcast:ScheduleWakeLock"

    @Volatile
    private var activeWakeLock: PowerManager.WakeLock? = null

    fun acquireTemporaryWakeLock(context: Context, timeoutMs: Long = 30_000L) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        runCatching {
            releaseWakeLock()
            @Suppress("DEPRECATION")
            val flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE
            val lock = pm.newWakeLock(flags, WAKE_TAG)
            lock.setReferenceCounted(false)
            lock.acquire(timeoutMs)
            activeWakeLock = lock
        }
    }

    fun releaseWakeLock() {
        runCatching {
            activeWakeLock?.let { lock ->
                if (lock.isHeld) lock.release()
            }
            activeWakeLock = null
        }
    }
}
