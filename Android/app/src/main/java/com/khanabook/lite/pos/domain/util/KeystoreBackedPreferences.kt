package com.khanabook.lite.pos.domain.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEY_STORE = "AndroidKeyStore"
private const val KEY_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
private const val TRANSFORMATION = "$KEY_ALGORITHM/$BLOCK_MODE/$PADDING"
private const val GCM_TAG_LENGTH_BITS = 128
private const val IV_LENGTH_BYTES = 12

class KeystoreBackedPreferences(
    context: Context,
    prefsName: String,
    private val keyAlias: String = "com.khanabook.lite.pos.secure.$prefsName"
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun getString(key: String, defaultValue: String? = null): String? {
        val encoded = prefs.getString(key, null) ?: return defaultValue
        return runCatching { decrypt(encoded) }
            .onFailure { android.util.Log.e("KeystorePrefs", "Decryption failed for key: $key", it) }
            .getOrNull() ?: defaultValue
    }

    fun putString(key: String, value: String) {
        runCatching { encrypt(value) }
            .onSuccess { encrypted -> prefs.edit().putString(key, encrypted).apply() }
            .onFailure { android.util.Log.e("KeystorePrefs", "Encryption failed for key: $key", it) }
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun contains(key: String): Boolean = prefs.contains(key)

    private fun encrypt(value: String): String {
        val secretKey = getOrCreateSecretKey()
            ?: throw IllegalStateException("Cannot access Android KeyStore")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        require(iv.size == IV_LENGTH_BYTES) { "Unexpected IV length: ${iv.size}" }
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

        val payload = ByteBuffer.allocate(4 + iv.size + ciphertext.size)
            .putInt(iv.size)
            .put(iv)
            .put(ciphertext)
            .array()

        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(payload)
        val ivLength = buffer.int
        require(ivLength in 1..32) { "Invalid IV length: $ivLength" }

        val iv = ByteArray(ivLength)
        buffer.get(iv)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        val secretKey = getOrCreateSecretKey()
            ?: throw IllegalStateException("Cannot access Android KeyStore")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey) ?: run {
            val keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM, ANDROID_KEY_STORE)
            val builder = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(BLOCK_MODE)
                .setEncryptionPaddings(PADDING)
                .setRandomizedEncryptionRequired(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setUnlockedDeviceRequired(false)
            }

            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
        }
    }.onFailure {
        android.util.Log.e("KeystorePrefs", "Keystore operation failed", it)
    }.getOrNull()
}
