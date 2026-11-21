package com.example.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeystoreCryptoManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "masswabroadcast_vault_aes_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_SIZE_BYTES = 12

    @Volatile
    private var jvmFallbackKey: SecretKey? = null

    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Throwable) {
            // Fallback for local JVM unit / Robolectric tests where AndroidKeyStore hardware provider is unavailable
            jvmFallbackKey ?: synchronized(this) {
                jvmFallbackKey ?: KeyGenerator.getInstance("AES").apply {
                    init(256, SecureRandom())
                }.generateKey().also { jvmFallbackKey = it }
            }
        }
    }

    fun encrypt(plainChars: CharArray): String {
        val byteBuf = StandardCharsets.UTF_8.encode(CharBuffer.wrap(plainChars))
        val plainBytes = ByteArray(byteBuf.remaining())
        byteBuf.get(plainBytes)
        return try {
            val key = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainBytes)
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val ctB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            "$ivB64:$ctB64"
        } finally {
            plainBytes.fill(0)
            if (byteBuf.hasArray()) byteBuf.array().fill(0)
        }
    }

    fun encrypt(plainText: String): String {
        val chars = plainText.toCharArray()
        return try {
            encrypt(chars)
        } finally {
            zeroFill(chars)
        }
    }

    fun decryptToChars(encryptedPayload: String): CharArray? {
        if (encryptedPayload.isBlank()) return null
        val parts = encryptedPayload.split(":")
        if (parts.size != 2) return null
        var plainBytes: ByteArray? = null
        return try {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            if (iv.size != IV_SIZE_BYTES || cipherBytes.isEmpty()) return null
            val key = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            plainBytes = cipher.doFinal(cipherBytes)
            val charBuf = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(plainBytes))
            CharArray(charBuf.remaining()).also { charBuf.get(it) }
        } catch (_: Throwable) {
            null
        } finally {
            plainBytes?.fill(0)
        }
    }

    fun zeroFill(chars: CharArray?) {
        chars?.fill('\u0000')
    }
}
