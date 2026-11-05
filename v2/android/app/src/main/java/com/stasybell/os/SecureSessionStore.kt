package com.stasybell.os

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores ONLY the opaque refresh token, encrypted with an Android Keystore AES-GCM key.
 * Neither the password nor short-lived access JWT is persisted.
 * Biometrics are an optional app unlock gate; they do not replace server authorization.
 */
class SecureSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("owner_session_v1", Context.MODE_PRIVATE)
    private val alias = "stasy_bell_owner_refresh_v1"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    @Synchronized
    fun save(refreshToken: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(refreshToken.toByteArray(StandardCharsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        check(prefs.edit().putString("refresh", encoded).commit()) { "Cannot store owner session" }
    }

    @Synchronized
    fun load(): String? {
        val stored = prefs.getString("refresh", null) ?: return null
        return try {
            val fields = stored.split(":", limit = 2)
            if (fields.size != 2) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(),
                GCMParameterSpec(128, Base64.decode(fields[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(fields[1], Base64.NO_WRAP)), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            // The keystore key may have been invalidated (e.g. device reset).
            clear()
            null
        }
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove("refresh").apply()
    }

    fun hasSession(): Boolean = prefs.contains("refresh")

    var biometricUnlockEnabled: Boolean
        get() = prefs.getBoolean("biometric_unlock", false)
        set(value) { prefs.edit().putBoolean("biometric_unlock", value).apply() }
}
