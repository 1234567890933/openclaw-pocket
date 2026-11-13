package com.openclaude.mobile.data.agent

import kotlinx.serialization.Serializable

/**
 * One model backend. Keys are never stored in this object; [keyAlias] points into
 * the encrypted vault, so a serialized profile is safe to show in a settings dump.
 */
@Serializable
data class ProviderProfile(
    val id: String,
    val label: String,
    val kind: Kind,
    val baseUrl: String,
    val model: String,
    val keyAlias: String? = null,
    val maxTokens: Int = 4096,
    val temperature: Double = 0.2,
    val contextWindow: Int = 200_000,
    val supportsTools: Boolean = true,
    val supportsStreaming: Boolean = true
) {
    enum class Kind { ANTHROPIC, OPENAI_COMPATIBLE, GEMINI, OLLAMA, GITHUB_MODELS, CUSTOM }

    /** Environment variables the guest CLI reads for this provider. */
    fun envFor(key: String?): Map<String, String> = when (kind) {
        Kind.ANTHROPIC -> buildMap {
            put("CLAUDE_CODE_USE_ANTHROPIC", "1")
            put("ANTHROPIC_BASE_URL", baseUrl)
            put("ANTHROPIC_MODEL", model)
            key?.let { put("ANTHROPIC_API_KEY", it) }
        }
        Kind.OPENAI_COMPATIBLE -> buildMap {
            put("CLAUDE_CODE_USE_OPENAI", "1")
            put("OPENAI_BASE_URL", baseUrl)
            put("OPENAI_MODEL", model)
            key?.let { put("OPENAI_API_KEY", it) }
        }
        Kind.GEMINI -> buildMap {
            put("CLAUDE_CODE_USE_GEMINI", "1")
            put("GEMINI_MODEL", model)
            key?.let { put("GEMINI_API_KEY", it) }
        }
        Kind.OLLAMA -> buildMap {
            put("CLAUDE_CODE_USE_OPENAI", "1")
            put("OPENAI_BASE_URL", baseUrl.ifBlank { "http://127.0.0.1:11434/v1" })
            put("OPENAI_MODEL", model)
            put("OPENAI_API_KEY", "ollama")
        }
        Kind.GITHUB_MODELS -> buildMap {
            put("CLAUDE_CODE_USE_OPENAI", "1")
            put("OPENAI_BASE_URL", "https://models.github.ai/inference")
            put("OPENAI_MODEL", model)
            key?.let { put("OPENAI_API_KEY", it) }
        }
        Kind.CUSTOM -> buildMap {
            put("CLAUDE_CODE_USE_OPENAI", "1")
            put("OPENAI_BASE_URL", baseUrl)
            put("OPENAI_MODEL", model)
            key?.let { put("OPENAI_API_KEY", it) }
        }
    }

    companion object {
        /** Seeded on first run so the app is usable without a settings tour. */
        fun defaults(): List<ProviderProfile> = listOf(
            ProviderProfile(
                id = "openrouter",
                label = "OpenRouter",
                kind = Kind.OPENAI_COMPATIBLE,
                baseUrl = "https://openrouter.ai/api/v1",
                model = "anthropic/claude-sonnet-4",
                keyAlias = "key.openrouter"
            ),
            ProviderProfile(
                id = "gemini",
                label = "Google Gemini",
                kind = Kind.GEMINI,
                baseUrl = "https://generativelanguage.googleapis.com",
                model = "gemini-2.5-pro",
                keyAlias = "key.gemini"
            ),
            ProviderProfile(
                id = "local",
                label = "Local (Ollama on LAN)",
                kind = Kind.OLLAMA,
                baseUrl = "http://192.168.1.10:11434/v1",
                model = "qwen2.5-coder:7b",
                contextWindow = 32_000
            )
        )
    }
}
