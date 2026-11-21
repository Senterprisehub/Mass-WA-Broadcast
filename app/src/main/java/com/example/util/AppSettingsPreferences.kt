package com.example.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mass_wa_settings_prefs"
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultWhatsAppPackage: String = "com.whatsapp",
    val defaultWhatsAppUserSerial: Long = 0L,
    val defaultWhatsAppLabel: String = "WhatsApp",
    val defaultIsClone: Boolean = false,
    val defaultDelaySeconds: Int = 6,
    val autoDisableAccessibilityAfterBroadcast: Boolean = false,
    val pauseOnIncomingCall: Boolean = true,
    val logRetentionDays: Int = 90, // 30, 90, 180, or 0 (Keep All)
    val alertSoundEnabled: Boolean = true,
    val alertVibrateEnabled: Boolean = true,
    val autoExitWhatsAppAfterBroadcast: Boolean = true,
    val autoLockDeviceIfStartedLocked: Boolean = true
)

class AppSettingsPreferences(private val context: Context) {

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_WA_PACKAGE = stringPreferencesKey("wa_target_package")
        private val KEY_WA_USER_SERIAL = longPreferencesKey("wa_target_user_serial")
        private val KEY_WA_LABEL = stringPreferencesKey("wa_target_label")
        private val KEY_WA_IS_CLONE = booleanPreferencesKey("wa_target_is_clone")
        private val KEY_DELAY_SECONDS = intPreferencesKey("default_delay_seconds")
        private val KEY_AUTO_DISABLE_A11Y = booleanPreferencesKey("auto_disable_a11y")
        private val KEY_PAUSE_ON_CALL = booleanPreferencesKey("pause_on_incoming_call")
        private val KEY_LOG_RETENTION_DAYS = intPreferencesKey("log_retention_days")
        private val KEY_ALERT_SOUND = booleanPreferencesKey("alert_sound_enabled")
        private val KEY_ALERT_VIBRATE = booleanPreferencesKey("alert_vibrate_enabled")
        private val KEY_AUTO_EXIT_WA = booleanPreferencesKey("auto_exit_whatsapp_after_broadcast")
        private val KEY_AUTO_LOCK_IF_LOCKED = booleanPreferencesKey("auto_lock_device_if_started_locked")
    }

    val settingsFlow: Flow<AppSettings> = context.appSettingsDataStore.data.map { prefs ->
        val theme = prefs[KEY_THEME_MODE]?.let {
            runCatching { ThemeMode.valueOf(it) }.getOrDefault(ThemeMode.SYSTEM)
        } ?: ThemeMode.SYSTEM

        AppSettings(
            themeMode = theme,
            defaultWhatsAppPackage = prefs[KEY_WA_PACKAGE] ?: "com.whatsapp",
            defaultWhatsAppUserSerial = prefs[KEY_WA_USER_SERIAL] ?: 0L,
            defaultWhatsAppLabel = prefs[KEY_WA_LABEL] ?: "WhatsApp",
            defaultIsClone = prefs[KEY_WA_IS_CLONE] ?: false,
            defaultDelaySeconds = prefs[KEY_DELAY_SECONDS] ?: 6,
            autoDisableAccessibilityAfterBroadcast = prefs[KEY_AUTO_DISABLE_A11Y] ?: false,
            pauseOnIncomingCall = prefs[KEY_PAUSE_ON_CALL] ?: true,
            logRetentionDays = prefs[KEY_LOG_RETENTION_DAYS] ?: 90,
            alertSoundEnabled = prefs[KEY_ALERT_SOUND] ?: true,
            alertVibrateEnabled = prefs[KEY_ALERT_VIBRATE] ?: true,
            autoExitWhatsAppAfterBroadcast = prefs[KEY_AUTO_EXIT_WA] ?: true,
            autoLockDeviceIfStartedLocked = prefs[KEY_AUTO_LOCK_IF_LOCKED] ?: true
        )
    }

    suspend fun getCurrentSettings(): AppSettings = settingsFlow.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appSettingsDataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setDefaultWhatsAppInstance(
        packageName: String,
        userSerial: Long,
        label: String,
        isClone: Boolean
    ) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[KEY_WA_PACKAGE] = packageName
            prefs[KEY_WA_USER_SERIAL] = userSerial
            prefs[KEY_WA_LABEL] = label
            prefs[KEY_WA_IS_CLONE] = isClone
        }
    }

    suspend fun setDefaultDelaySeconds(seconds: Int) {
        context.appSettingsDataStore.edit { it[KEY_DELAY_SECONDS] = seconds.coerceIn(2, 60) }
    }

    suspend fun setAutoDisableAccessibility(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[KEY_AUTO_DISABLE_A11Y] = enabled }
    }

    suspend fun setPauseOnIncomingCall(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[KEY_PAUSE_ON_CALL] = enabled }
    }

    suspend fun setLogRetentionDays(days: Int) {
        context.appSettingsDataStore.edit { it[KEY_LOG_RETENTION_DAYS] = days }
    }

    suspend fun setAlertPreferences(sound: Boolean, vibrate: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[KEY_ALERT_SOUND] = sound
            prefs[KEY_ALERT_VIBRATE] = vibrate
        }
    }

    suspend fun setAutoExitWhatsAppAfterBroadcast(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[KEY_AUTO_EXIT_WA] = enabled }
    }

    suspend fun setAutoLockDeviceIfStartedLocked(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[KEY_AUTO_LOCK_IF_LOCKED] = enabled }
    }
}
