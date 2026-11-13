package com.openclaude.mobile

import com.openclaude.mobile.data.agent.ProviderProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The env map is the contract with the guest CLI. A typo here produces a model that
 * silently falls back to the wrong provider, which is very hard to notice.
 */
class ProviderProfileTest {

    private fun profile(kind: ProviderProfile.Kind) = ProviderProfile(
        id = "test",
        label = "Test",
        kind = kind,
        baseUrl = "https://example.test/v1",
        model = "some-model",
        keyAlias = "key.test"
    )

    @Test
    fun `openai compatible sets the openai flags`() {
        val env = profile(ProviderProfile.Kind.OPENAI_COMPATIBLE).envFor("sk-test")
        assertEquals("1", env["CLAUDE_CODE_USE_OPENAI"])
        assertEquals("https://example.test/v1", env["OPENAI_BASE_URL"])
        assertEquals("some-model", env["OPENAI_MODEL"])
        assertEquals("sk-test", env["OPENAI_API_KEY"])
    }

    @Test
    fun `gemini sets the gemini flags and no openai ones`() {
        val env = profile(ProviderProfile.Kind.GEMINI).envFor("AIza-test")
        assertEquals("1", env["CLAUDE_CODE_USE_GEMINI"])
        assertEquals("AIza-test", env["GEMINI_API_KEY"])
        assertNull(env["OPENAI_API_KEY"])
    }

    @Test
    fun `ollama works without a key`() {
        val env = profile(ProviderProfile.Kind.OLLAMA).envFor(null)
        assertEquals("ollama", env["OPENAI_API_KEY"])
        assertTrue(
            requireNotNull(env["OPENAI_BASE_URL"]) { "OPENAI_BASE_URL missing" }
                .contains("example.test")
        )
    }

    @Test
    fun `a null key never becomes the string null`() {
        val env = profile(ProviderProfile.Kind.ANTHROPIC).envFor(null)
        assertNull(env["ANTHROPIC_API_KEY"])
    }

    @Test
    fun `defaults cover more than one provider kind`() {
        val kinds = ProviderProfile.defaults().map { it.kind }.toSet()
        assertTrue(kinds.size >= 2)
    }

    @Test
    fun `default profiles declare a key alias except local`() {
        ProviderProfile.defaults()
            .filter { it.kind != ProviderProfile.Kind.OLLAMA }
            .forEach { assertTrue("${it.id} needs an alias", it.keyAlias != null) }
    }
}
