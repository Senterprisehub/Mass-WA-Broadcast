package com.example.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.LockType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "keystore_vault_prefs"
)

data class VaultStatus(
    val deviceLockType: LockType = LockType.NONE,
    val hasDeviceSecret: Boolean = false,
    val whatsAppLockType: LockType = LockType.NONE,
    val hasWhatsAppSecret: Boolean = false,
    val autoUnlockEnabled: Boolean = false
) {
    val isAnyVaultConfigured: Boolean
        get() = (deviceLockType == LockType.SLIDE || hasDeviceSecret) ||
            (whatsAppLockType == LockType.SLIDE || hasWhatsAppSecret)
}

class DecryptedDispatchCredentials(
    val deviceLockType: LockType,
    val deviceSecret: CharArray?,
    val whatsAppLockType: LockType,
    val whatsAppSecret: CharArray?,
    val autoUnlockEnabled: Boolean
) : AutoCloseable {
    var isClosed: Boolean = false
        private set

    override fun close() {
        if (!isClosed) {
            KeystoreCryptoManager.zeroFill(deviceSecret)
            KeystoreCryptoManager.zeroFill(whatsAppSecret)
            isClosed = true
        }
    }
}

class VaultPreferences(private val context: Context) {

    companion object {
        private val KEY_DEVICE_LOCK_TYPE = stringPreferencesKey("enc_device_lock_type")
        private val KEY_DEVICE_SECRET_ENC = stringPreferencesKey("enc_device_secret")
        private val KEY_WA_LOCK_TYPE = stringPreferencesKey("enc_wa_lock_type")
        private val KEY_WA_SECRET_ENC = stringPreferencesKey("enc_wa_secret")
        private val KEY_AUTO_UNLOCK = booleanPreferencesKey("vault_auto_unlock_enabled")

        private const val LEGACY_PREFS_NAME = "legacy_lock_prefs"
        private const val LEGACY_DEVICE_PIN = "device_pin_plain"
        private const val LEGACY_WA_PIN = "wa_pin_plain"
    }

    val vaultStatusFlow: Flow<VaultStatus> = context.vaultDataStore.data.map { prefs ->
        val devType = decodeEncryptedLockType(prefs[KEY_DEVICE_LOCK_TYPE])
        val waType = decodeEncryptedLockType(prefs[KEY_WA_LOCK_TYPE])
        VaultStatus(
            deviceLockType = devType,
            hasDeviceSecret = !prefs[KEY_DEVICE_SECRET_ENC].isNullOrBlank(),
            whatsAppLockType = waType,
            hasWhatsAppSecret = !prefs[KEY_WA_SECRET_ENC].isNullOrBlank(),
            autoUnlockEnabled = prefs[KEY_AUTO_UNLOCK] ?: false
        )
    }

    private fun decodeEncryptedLockType(encryptedType: String?): LockType {
        if (encryptedType.isNullOrBlank()) return LockType.NONE
        val decrypted = KeystoreCryptoManager.decryptToChars(encryptedType) ?: return LockType.NONE
        return try {
            runCatching { LockType.valueOf(String(decrypted)) }.getOrDefault(LockType.NONE)
        } finally {
            KeystoreCryptoManager.zeroFill(decrypted)
        }
    }

    suspend fun saveDeviceLock(lockType: LockType, secret: CharArray?) {
        val encryptedType = KeystoreCryptoManager.encrypt(lockType.name)
        val encryptedSecret = when (lockType) {
            LockType.NONE -> null
            LockType.SLIDE -> KeystoreCryptoManager.encrypt("SLIDE_SWIPE_UNLOCK_V1")
            LockType.PIN, LockType.PATTERN, LockType.PASSWORD -> {
                if (secret != null && secret.isNotEmpty()) {
                    KeystoreCryptoManager.encrypt(secret)
                } else {
                    null
                }
            }
        }
        context.vaultDataStore.edit { prefs ->
            prefs[KEY_DEVICE_LOCK_TYPE] = encryptedType
            if (encryptedSecret != null) {
                prefs[KEY_DEVICE_SECRET_ENC] = encryptedSecret
            } else {
                prefs.remove(KEY_DEVICE_SECRET_ENC)
            }
            if (lockType != LockType.NONE) {
                prefs[KEY_AUTO_UNLOCK] = true
            }
        }
    }

    suspend fun saveWhatsAppLock(lockType: LockType, secret: CharArray?) {
        val encryptedType = KeystoreCryptoManager.encrypt(lockType.name)
        val encryptedSecret = when (lockType) {
            LockType.NONE -> null
            LockType.SLIDE -> KeystoreCryptoManager.encrypt("SLIDE_SWIPE_UNLOCK_V1")
            LockType.PIN, LockType.PATTERN, LockType.PASSWORD -> {
                if (secret != null && secret.isNotEmpty()) {
                    KeystoreCryptoManager.encrypt(secret)
                } else {
                    null
                }
            }
        }
        context.vaultDataStore.edit { prefs ->
            prefs[KEY_WA_LOCK_TYPE] = encryptedType
            if (encryptedSecret != null) {
                prefs[KEY_WA_SECRET_ENC] = encryptedSecret
            } else {
                prefs.remove(KEY_WA_SECRET_ENC)
            }
            if (lockType != LockType.NONE) {
                prefs[KEY_AUTO_UNLOCK] = true
            }
        }
    }

    suspend fun setAutoUnlockEnabled(enabled: Boolean) {
        context.vaultDataStore.edit { prefs ->
            prefs[KEY_AUTO_UNLOCK] = enabled
        }
    }

    suspend fun wipeAllVaultCredentials() {
        context.vaultDataStore.edit { prefs ->
            prefs.remove(KEY_DEVICE_LOCK_TYPE)
            prefs.remove(KEY_DEVICE_SECRET_ENC)
            prefs.remove(KEY_WA_LOCK_TYPE)
            prefs.remove(KEY_WA_SECRET_ENC)
            prefs[KEY_AUTO_UNLOCK] = false
        }
    }

    suspend fun migrateLegacyPlaintextIfNeeded() {
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val legacyDevicePin = legacy.getString(LEGACY_DEVICE_PIN, null)
        val legacyWaPin = legacy.getString(LEGACY_WA_PIN, null)
        if (!legacyDevicePin.isNullOrBlank()) {
            val chars = legacyDevicePin.toCharArray()
            try {
                saveDeviceLock(LockType.PIN, chars)
            } finally {
                KeystoreCryptoManager.zeroFill(chars)
            }
        }
        if (!legacyWaPin.isNullOrBlank()) {
            val chars = legacyWaPin.toCharArray()
            try {
                saveWhatsAppLock(LockType.PIN, chars)
            } finally {
                KeystoreCryptoManager.zeroFill(chars)
            }
        }
        if (!legacyDevicePin.isNullOrBlank() || !legacyWaPin.isNullOrBlank()) {
            legacy.edit().remove(LEGACY_DEVICE_PIN).remove(LEGACY_WA_PIN).apply()
        }
    }

    suspend fun decryptForDispatch(): DecryptedDispatchCredentials {
        val prefs = context.vaultDataStore.data.first()
        val devType = decodeEncryptedLockType(prefs[KEY_DEVICE_LOCK_TYPE])
        val waType = decodeEncryptedLockType(prefs[KEY_WA_LOCK_TYPE])
        val devSecret = prefs[KEY_DEVICE_SECRET_ENC]?.let { KeystoreCryptoManager.decryptToChars(it) }
        val waSecret = prefs[KEY_WA_SECRET_ENC]?.let { KeystoreCryptoManager.decryptToChars(it) }
        val autoUnlock = prefs[KEY_AUTO_UNLOCK] ?: false
        return DecryptedDispatchCredentials(
            deviceLockType = devType,
            deviceSecret = devSecret,
            whatsAppLockType = waType,
            whatsAppSecret = waSecret,
            autoUnlockEnabled = autoUnlock
        )
    }

    fun decryptForDispatchBlocking(): DecryptedDispatchCredentials = runBlocking {
        decryptForDispatch()
    }
}
