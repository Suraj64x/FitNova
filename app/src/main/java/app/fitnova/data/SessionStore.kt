package app.fitnova.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The signed-in account. Tokens are encrypted with an Android Keystore key that never leaves the device. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    // Decrypt once; Keystore calls are slow on low-end phones.
    private var cachedAccess: String? = null
    private var cachedRefresh: String? = null

    val userId: String? get() = prefs.getString("user_id", null)
    val username: String? get() = prefs.getString("username", null)
    val signedIn: Boolean get() = userId != null

    val accessToken: String?
        get() = cachedAccess ?: prefs.getString("access", null)?.let(::decrypt).also { cachedAccess = it }
    val refreshToken: String?
        get() = cachedRefresh ?: prefs.getString("refresh", null)?.let(::decrypt).also { cachedRefresh = it }

    fun save(userId: String, username: String, access: String, refresh: String) {
        cachedAccess = access
        cachedRefresh = refresh
        prefs.edit()
            .putString("user_id", userId)
            .putString("username", username)
            .putString("access", encrypt(access))
            .putString("refresh", encrypt(refresh))
            .apply()
    }

    fun updateTokens(access: String, refresh: String) {
        cachedAccess = access
        cachedRefresh = refresh
        prefs.edit().putString("access", encrypt(access)).putString("refresh", encrypt(refresh)).apply()
    }

    fun clear() {
        cachedAccess = null
        cachedRefresh = null
        prefs.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return b64(cipher.iv) + ":" + b64(cipher.doFinal(plain.toByteArray()))
    }

    private fun decrypt(stored: String): String? = runCatching {
        val (iv, data) = stored.split(":")
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)))
    }.getOrNull()

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private companion object {
        const val ALIAS = "fitnova_session"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
