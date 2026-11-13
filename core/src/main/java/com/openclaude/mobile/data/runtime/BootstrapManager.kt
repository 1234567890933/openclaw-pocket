package com.openclaude.mobile.data.runtime

import android.content.Context
import com.openclaude.mobile.core.R
import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Drives the first-boot install: read the manifest, fetch each component in
 * stage order, verify, extract, and record what is installed.
 *
 * The state machine is intentionally linear and restartable. If the user kills
 * the app mid-download, the `.part` files are reused on the next attempt.
 */
class BootstrapManager(
    private val context: Context,
    private val paths: RuntimePaths,
    private val downloader: Downloader = Downloader(),
    private val extractor: TarGzExtractor = TarGzExtractor()
) {

    sealed interface Status {
        data object Idle : Status
        data class Running(
            val componentId: String,
            val label: String,
            val bytesRead: Long,
            val bytesTotal: Long,
            val overallRead: Long,
            val overallTotal: Long
        ) : Status
        data class Verifying(val componentId: String) : Status
        data class Failed(val componentId: String, val message: String) : Status
        data object Done : Status
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _installed = MutableStateFlow<Set<String>>(emptySet())
    val installed: StateFlow<Set<String>> = _installed.asStateFlow()

    fun loadManifest(): BootstrapManifest {
        val text = context.resources.openRawResource(R.raw.bootstrap_manifest)
            .bufferedReader().use { it.readText() }
        return json.decodeFromString(BootstrapManifest.serializer(), text)
    }

    /** Components already on disk, judged by their extraction target. */
    fun scanInstalled(manifest: BootstrapManifest): Set<String> {
        val done = manifest.components.filter { spec ->
            val dir = File(context.filesDir, spec.extractTo)
            dir.exists() && (dir.list()?.isNotEmpty() == true)
        }.map { it.id }.toSet()
        _installed.value = done
        return done
    }

    suspend fun installAll(manifest: BootstrapManifest) = withContext(Dispatchers.IO) {
        paths.ensureAll()
        val already = scanInstalled(manifest)
        val total = manifest.requiredBytes()
        var completed = manifest.components.filter { it.id in already }.sumOf { it.bytes }

        for (spec in manifest.ordered()) {
            if (spec.id in already) continue
            _status.value = Status.Running(spec.id, spec.label, 0, spec.bytes, completed, total)
            val archive = File(paths.downloads, spec.file)
            try {
                if (!archive.exists() || archive.length() != spec.bytes) {
                    downloader.fetch(manifest.baseUrl + spec.file, archive, spec.sha256) { p ->
                        _status.value = Status.Running(
                            spec.id, spec.label, p.bytesRead, p.totalBytes,
                            completed + p.bytesRead, total
                        )
                    }
                } else {
                    _status.value = Status.Running(spec.id, spec.label, spec.bytes, spec.bytes, completed + spec.bytes, total)
                }

                _status.value = Status.Verifying(spec.id)
                val dest = File(context.filesDir, spec.extractTo)
                extractor.extract(archive, dest)
                completed += spec.bytes
                _installed.value = _installed.value + spec.id
                Logs.i("boot", "installed ${spec.id}")
            } catch (t: Throwable) {
                Logs.e("boot", "install failed for ${spec.id}", t)
                _status.value = Status.Failed(spec.id, t.message ?: t.javaClass.simpleName)
                return@withContext
            }
        }

        // Mark the runtime executable bit on proot, which tar sometimes drops.
        paths.prootBinary.takeIf { it.exists() }?.setExecutable(true, true)
        _status.value = Status.Done
        Logs.i("boot", "runtime install complete")
    }

    suspend fun retry(spec: ComponentSpec, manifest: BootstrapManifest) = withContext(Dispatchers.IO) {
        val archive = File(paths.downloads, spec.file)
        if (archive.exists()) archive.delete()
        installAll(manifest)
    }

    fun diskUsageBytes(): Long = paths.root.walkTopDown()
        .filter { it.isFile }
        .sumOf { it.length() }
}
