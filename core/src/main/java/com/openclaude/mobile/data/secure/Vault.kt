package com.openclaude.mobile.data.secure

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.openclaude.mobile.util.Constants
import com.openclaude.mobile.util.Logs
import java.io.File

/**
 * Encrypted key/value store for API keys, the unlock flag and the device id.
 *
 * Backed by EncryptedSharedPreferences, whose master key lives in the Android
 * Keystore (hardware backed where available). Values are AES-256-GCM, keys are
 * AES-256-SIV. Nothing here is ever written to logcat.
 */
class Vault(private val context: Context) {

    val prefs: SharedPreferences by lazy { open() }

    private fun open(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setUserAuthenticationRequired(false)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            Constants.PREFS_SECURE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun putSecret(alias: String, value: String) {
        prefs.edit().putString(alias, value).apply()
        Logs.d("vault", "stored secret $alias (${value.length} chars)")
    }

    fun getSecret(alias: String): String? = prefs.getString(alias, null)

    fun removeSecret(alias: String) {
        prefs.edit().remove(alias).apply()
    }

    fun hasSecret(alias: String): Boolean = !getSecret(alias).isNullOrBlank()

    /** A random, stable per-install identifier. Not an advertising id. */
    fun deviceId(): String {
        val existing = prefs.getString("device.id", null)
        if (existing != null) return existing
        val bytes = ByteArray(18).also { java.security.SecureRandom().nextBytes(it) }
        val id = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
        prefs.edit().putString("device.id", id).apply()
        return id
    }

    var developerUnlocked: Boolean
        get() = prefs.getBoolean("dev.unlocked", false)
        set(value) = prefs.edit().putBoolean("dev.unlocked", value).apply()

    var signedInEmail: String?
        get() = prefs.getString("auth.email", null)
        set(value) = prefs.edit().putString("auth.email", value).apply()

    var runtimeInstalled: Boolean
        get() = prefs.getBoolean("runtime.installed", false)
        set(value) = prefs.edit().putBoolean("runtime.installed", value).apply()

    fun baseDir(): File = File(context.filesDir, "vault").apply { mkdirs() }

    /** Wipes every secret but keeps the keystore entry. Used by "reset app". */
    fun wipeSecrets() {
        prefs.edit().clear().apply()
        Logs.w("vault", "all secrets wiped")
    }
}
