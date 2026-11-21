package com.example.util

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.data.model.OemFamily

data class SystemHealthReport(
    val accessibilityEnabled: Boolean = false,
    val exactAlarmsAllowed: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val fullScreenIntentAllowed: Boolean = true,
    val batteryOptimizationIgnored: Boolean = false,
    val overlayAllowed: Boolean = false,
    val contactsPermissionGranted: Boolean = false,
    val phoneStatePermissionGranted: Boolean = false,
    val deviceKeyguardSecure: Boolean = false,
    val vaultConfigured: Boolean = false,
    val oemFamily: OemFamily = OemFamily.OTHER
) {
    val criticalGatesPassed: Int
        get() = listOf(
            accessibilityEnabled,
            exactAlarmsAllowed,
            notificationsEnabled,
            fullScreenIntentAllowed,
            batteryOptimizationIgnored
        ).count { it }

    val totalCriticalGates: Int = 5

    val allCriticalReady: Boolean
        get() = criticalGatesPassed == totalCriticalGates

    val scorePercent: Int
        get() = (criticalGatesPassed * 100) / totalCriticalGates
}

object PermissionHealthManager {

    fun evaluate(context: Context, vaultStatus: VaultStatus = VaultStatus()): SystemHealthReport {
        val accessibility = isAccessibilityServiceEnabled(context)
        val exactAlarms = isExactAlarmAllowed(context)
        val notifications = isNotificationsAllowed(context)
        val fullScreenIntent = isFullScreenIntentAllowed(context)
        val batteryIgnored = isBatteryOptimizationIgnored(context)
        val overlay = Settings.canDrawOverlays(context)
        val contacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        val phoneState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val keyguardSecure = km?.isDeviceSecure == true

        return SystemHealthReport(
            accessibilityEnabled = accessibility,
            exactAlarmsAllowed = exactAlarms,
            notificationsEnabled = notifications,
            fullScreenIntentAllowed = fullScreenIntent,
            batteryOptimizationIgnored = batteryIgnored,
            overlayAllowed = overlay,
            contactsPermissionGranted = contacts,
            phoneStatePermissionGranted = phoneState,
            deviceKeyguardSecure = keyguardSecure,
            vaultConfigured = vaultStatus.isAnyVaultConfigured,
            oemFamily = OemDetector.detectOem()
        )
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val expectedPkg = context.packageName
        if (enabled.any { it.resolveInfo?.serviceInfo?.packageName == expectedPkg }) {
            return true
        }
        val flatSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return flatSetting.contains(expectedPkg) &&
            flatSetting.contains("WhatsAppAutoSendAccessibilityService")
    }

    fun isExactAlarmAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return am.canScheduleExactAlarms()
    }

    fun isNotificationsAllowed(context: Context): Boolean {
        val runtimeOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return runtimeOk && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun isFullScreenIntentAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return true
        return nm.canUseFullScreenIntent()
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // One-Tap System Settings Intents
    fun openAccessibilitySettings(context: Context) {
        safeStartActivity(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            safeStartActivity(
                context,
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
        } else {
            openAppDetailsSettings(context)
        }
    }

    fun openNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
        safeStartActivity(context, intent)
    }

    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            safeStartActivity(
                context,
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
        } else {
            openNotificationSettings(context)
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        if (!safeStartActivity(context, requestIntent)) {
            safeStartActivity(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    fun openOverlaySettings(context: Context) {
        safeStartActivity(
            context,
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
    }

    fun openAppDetailsSettings(context: Context) {
        safeStartActivity(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        )
    }

    private fun safeStartActivity(context: Context, intent: Intent): Boolean {
        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrElse {
            runCatching {
                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                true
            }.getOrDefault(false)
        }
    }
}
