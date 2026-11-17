package dev.toastlabs.toastlift.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores only ciphertext in app-private preferences; the encryption key remains in Android Keystore. */
internal class GeminiSearchCredentials(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("gemini_search", Context.MODE_PRIVATE)
    private val alias = "toastlift.gemini.search.v1"

    private fun secretKey(create: Boolean): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        if (!create) return null
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        }
    }

    fun read(): String? = runCatching {
        val encoded = prefs.getString("ciphertext", null) ?: return null
        val iv = Base64.decode(prefs.getString("iv", null) ?: return null, Base64.NO_WRAP)
        val key = secretKey(false) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        String(cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    fun save(value: String) {
        require(value.isNotBlank())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, requireNotNull(secretKey(true)))
        val encrypted = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }

    fun clear() {
        check(prefs.edit().clear().commit())
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }
}
