package com.openclaude.mobile.data.agent

import com.openclaude.mobile.data.secure.Vault
import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists provider profiles as plain JSON in filesDir (they contain no secrets)
 * and keeps API keys in the encrypted [Vault] under the profile's alias.
 */
class ProviderStore(private val vault: Vault) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file: File get() = File(vault.baseDir(), "providers.json")

    private val _profiles = MutableStateFlow<List<ProviderProfile>>(emptyList())
    val profiles: StateFlow<List<ProviderProfile>> = _profiles

    private val _active = MutableStateFlow<String>("openrouter")
    val activeId: StateFlow<String> = _active

    fun load() {
        val list = if (file.exists()) {
            runCatching {
                json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ProviderProfile.serializer()), file.readText())
            }.getOrElse {
                Logs.w("providers", "unreadable provider file, reseeding")
                ProviderProfile.defaults()
            }
        } else {
            ProviderProfile.defaults().also { save(it) }
        }
        _profiles.value = list
        _active.value = vault.prefs.getString("active.provider", null) ?: list.firstOrNull()?.id ?: "openrouter"
    }

    fun save(list: List<ProviderProfile>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ProviderProfile.serializer()), list))
        _profiles.value = list
    }

    fun setActive(id: String) {
        _active.value = id
        vault.prefs.edit().putString("active.provider", id).apply()
    }

    fun active(): ProviderProfile? = _profiles.value.firstOrNull { it.id == _active.value }

    fun putKey(profile: ProviderProfile, key: String) {
        val alias = profile.keyAlias ?: return
        vault.putSecret(alias, key)
    }

    fun keyFor(profile: ProviderProfile): String? =
        profile.keyAlias?.let { vault.getSecret(it) }

    fun envForActive(): Map<String, String> {
        val profile = active() ?: return emptyMap()
        return profile.envFor(keyFor(profile))
    }
}
