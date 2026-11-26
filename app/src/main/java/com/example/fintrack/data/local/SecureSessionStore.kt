package com.example.fintrack.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.fintrack.data.model.UserSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
private data class StoredSession(
    val email: String,
    val refreshToken: String,
    val supabaseUrl: String,
    val supabaseKey: String
)

/**
 * Keeps the Supabase refresh token so the user stays signed in after an app restart.
 *
 * The file is encrypted with an AES-256-GCM key that lives in the Android Keystore (the key can
 * not be extracted from the device) and is written outside the `ledger` folder, so it is never
 * included in Auto Backup / device transfer. If the file cannot be decrypted (new device,
 * wiped keystore) it is deleted and the user simply signs in again.
 *
 * Only the long-lived refresh token is stored; access tokens stay in memory.
 */
class SecureSessionStore(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun save(session: UserSession) {
        val refresh = session.refreshToken ?: return
        val email = session.email ?: return
        try {
            val payload = json.encodeToString(
                StoredSession.serializer(),
                StoredSession(email, refresh, session.supabaseUrl, session.supabaseKey)
            ).toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            file.parentFile?.mkdirs()
            file.writeBytes(cipher.iv + cipher.doFinal(payload))
        } catch (e: Exception) {
            // Persisting the session is a convenience only; never crash the app because of it.
        }
    }

    /** Returns a session holding just the refresh token (isAuthenticated = false) or null. */
    @Synchronized
    fun load(): UserSession? {
        if (!file.exists()) return null
        return try {
            val bytes = file.readBytes()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_SIZE))
            val plain = cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE)
            val stored = json.decodeFromString(StoredSession.serializer(), String(plain, Charsets.UTF_8))
            UserSession(
                email = stored.email,
                refreshToken = stored.refreshToken,
                supabaseUrl = stored.supabaseUrl,
                supabaseKey = stored.supabaseKey,
                isAuthenticated = false
            )
        } catch (e: Exception) {
            clear()
            null
        }
    }

    @Synchronized
    fun clear() {
        runCatching { file.delete() }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "fintrack_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val IV_SIZE = 12
    }
}
