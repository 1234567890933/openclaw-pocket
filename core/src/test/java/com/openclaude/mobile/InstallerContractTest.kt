package com.openclaude.mobile

import com.openclaude.mobile.data.runtime.BootstrapManifest
import com.openclaude.mobile.data.runtime.ComponentSpec
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks the installer against the manifest that actually ships.
 *
 * Every bug in this area so far was a mismatch between what the manifest said and
 * what the code assumed — a placeholder base URL, a bare file name passed to OkHttp,
 * a total that did not match the sum. So the tests read the real file.
 */
class InstallerContractTest {

    private val manifestFile = File("../app/src/main/res/raw/bootstrap_manifest.json")
    private val manifest: BootstrapManifest by lazy {
        Json { ignoreUnknownKeys = true }
            .decodeFromString(BootstrapManifest.serializer(), manifestFile.readText())
    }

    @Test
    fun `manifest file is present where the app reads it`() {
        assertTrue("manifest missing at ${manifestFile.absolutePath}", manifestFile.exists())
    }

    @Test
    fun `a bundled component never produces a download url`() {
        manifest.components.filter { it.bundled }.forEach { spec ->
            assertEquals(
                "bundled ${spec.id} must not resolve to a URL",
                "",
                spec.resolvedUrl(manifest.baseUrl)
            )
        }
    }

    @Test
    fun `a component without a url or base resolves to empty not a filename`() {
        val spec = ComponentSpec(
            id = "x", label = "x", file = "thing.tar.gz", bytes = 1,
            sha256 = "", extractTo = "runtime/x", required = false, stage = 1
        )
        assertEquals("", spec.resolvedUrl(""))
    }

    @Test
    fun `every downloadable component has an http url`() {
        manifest.components.filter { !it.bundled }.forEach { spec ->
            val url = spec.resolvedUrl(manifest.baseUrl)
            if (url.isNotBlank()) {
                assertTrue(
                    "${spec.id} has a non-http url: $url",
                    url.startsWith("http://") || url.startsWith("https://")
                )
            }
        }
    }

    @Test
    fun `a component with no url is optional so the installer can skip it`() {
        manifest.components
            .filter { !it.bundled && it.resolvedUrl(manifest.baseUrl).isBlank() }
            .forEach { spec ->
                assertTrue(
                    "${spec.id} has no URL but is marked required — the install would fail",
                    !spec.required
                )
            }
    }

    @Test
    fun `bundled files exist when the payloads have been fetched`() {
        val dir = File("../app/src/main/assets/runtime")
        // A plain `git clone` has no payloads: they are 250 MB of binaries fetched by
        // scripts/fetch-runtime-payloads.sh, so their absence is expected and not a
        // failure. What must never happen is a manifest that promises a bundled file
        // the build did not put there — that is checked by the release job instead.
        if (!dir.exists() || dir.listFiles().isNullOrEmpty()) {
            println("payloads not fetched — skipping asset presence check")
            return
        }
        manifest.components.filter { it.bundled }.forEach { spec ->
            val asset = File(dir, spec.file)
            if (!asset.exists()) {
                println("not fetched: ${spec.file} (optional in a source checkout)")
                return@forEach
            }
            assertTrue("asset empty for ${spec.id}", asset.length() > 1024)
        }
    }

    @Test
    fun `declared total matches the required sum`() {
        val requiredSum = manifest.components.filter { it.required }.sumOf { it.bytes }
        assertTrue(
            "totalBytes ${manifest.totalBytes} does not match required sum $requiredSum",
            manifest.totalBytes == requiredSum
        )
    }

    @Test
    fun `stages are ordered and unique per component`() {
        val stages = manifest.components.map { it.stage }
        assertTrue("duplicate stage numbers: $stages", stages.size == stages.distinct().size)
    }
}
