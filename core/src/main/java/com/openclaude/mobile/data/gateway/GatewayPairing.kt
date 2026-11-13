package com.openclaude.mobile.data.gateway

import com.openclaude.mobile.data.secure.Vault
import com.openclaude.mobile.util.Logs
import java.security.SecureRandom
import java.util.Base64

/**
 * How this phone is allowed to talk to a gateway.
 *
 * Two modes, one credential format:
 *
 *  - **Host** — the gateway runs inside this app. The token is generated here, kept
 *    in the vault and handed to the child process through its environment. Nothing
 *    leaves the device; the socket is bound to 127.0.0.1.
 *  - **Remote** — the gateway runs on a laptop. OpenClaw pairs a client out of band
 *    (its `pairing approve` flow), and the phone stores the resulting token.
 *
 * Tokens are 32 bytes of CSPRNG output, base64url. They are never logged and never
 * written to the plain prefs file.
 */
class GatewayPairing(private val vault: Vault) {

    enum class Mode { HOST, REMOTE }

    data class Endpoint(
        val mode: Mode,
        val host: String,
        val port: Int,
        val token: String,
        val tls: Boolean = false
    ) {
        val baseUrl: String get() = (if (tls) "https" else "http") + "://$host:$port"
        val streamUrl: String get() = (if (tls) "wss" else "ws") + "://$host:$port" + GatewayProtocol.PATH_STREAM
    }

    fun generateToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** Token for the in-app gateway, created once and reused. */
    fun hostToken(): String {
        val existing = vault.getSecret(KEY_HOST_TOKEN)
        if (!existing.isNullOrBlank()) return existing
        val fresh = generateToken()
        vault.putSecret(KEY_HOST_TOKEN, fresh)
        Logs.i("gateway", "generated host token (${fresh.length} chars)")
        return fresh
    }

    fun rotateHostToken(): String {
        vault.removeSecret(KEY_HOST_TOKEN)
        return hostToken()
    }

    fun hostEndpoint(port: Int): Endpoint =
        Endpoint(Mode.HOST, "127.0.0.1", port, hostToken())

    fun saveRemote(host: String, port: Int, token: String, tls: Boolean) {
        vault.prefs.edit()
            .putString(KEY_REMOTE_HOST, host)
            .putInt(KEY_REMOTE_PORT, port)
            .putBoolean(KEY_REMOTE_TLS, tls)
            .apply()
        vault.putSecret(KEY_REMOTE_TOKEN, token)
    }

    fun remoteEndpoint(): Endpoint? {
        val host = vault.prefs.getString(KEY_REMOTE_HOST, null) ?: return null
        val port = vault.prefs.getInt(KEY_REMOTE_PORT, 0)
        val token = vault.getSecret(KEY_REMOTE_TOKEN) ?: return null
        if (host.isBlank() || port <= 0) return null
        return Endpoint(
            Mode.REMOTE,
            host,
            port,
            token,
            vault.prefs.getBoolean(KEY_REMOTE_TLS, false)
        )
    }

    fun clearRemote() {
        vault.prefs.edit()
            .remove(KEY_REMOTE_HOST)
            .remove(KEY_REMOTE_PORT)
            .remove(KEY_REMOTE_TLS)
            .apply()
        vault.removeSecret(KEY_REMOTE_TOKEN)
    }

    /** Accepts `host:port` or `openclaw://host:port` and normalises it. */
    fun parseAddress(raw: String): Pair<String, Int>? {
        val cleaned = raw.trim()
            .removePrefix("openclaw://")
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split(':')
        val host = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 18789
        if (port !in 1..65535) return null
        return host to port
    }

    private companion object {
        const val KEY_HOST_TOKEN = "gateway.host.token"
        const val KEY_REMOTE_HOST = "gateway.remote.host"
        const val KEY_REMOTE_PORT = "gateway.remote.port"
        const val KEY_REMOTE_TLS = "gateway.remote.tls"
        const val KEY_REMOTE_TOKEN = "gateway.remote.token"
    }
}
