package com.openclaude.mobile.data.auth

import android.content.Context
import com.openclaude.mobile.util.Logs

/**
 * Account handling without Google Play Services.
 *
 * The app used Credential Manager for sign-in. It worked, and it cost a Play Services
 * dependency that pulled thousands of classes into the APK, required GMS on the
 * device, and bought nothing the app actually needs: the agent runs locally and the
 * provider key is the user's own.
 *
 * So sign-in is now a local profile. It identifies the person to this installation
 * only, which is all the retention policy and the provider keys ever used it for.
 */
class GoogleAuthClient(private val context: Context) {

    data class SignedIn(val email: String, val displayName: String?, val idToken: String? = null)

    /** Creates a device-local profile. No network, no account, no tracking. */
    suspend fun signInLocal(displayName: String): Result<SignedIn> = runCatching {
        val name = displayName.trim().ifBlank { "local" }
        Logs.i("auth", "local profile created")
        SignedIn(email = "$name@device", displayName = name)
    }

    suspend fun signOut(): Unit = Unit
}
