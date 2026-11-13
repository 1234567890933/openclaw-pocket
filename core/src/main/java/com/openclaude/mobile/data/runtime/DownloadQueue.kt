package com.openclaude.mobile.data.runtime

import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * The one-tap installer.
 *
 * One button, one queue, bounded parallelism. Two downloads at a time is the
 * sweet spot on mobile: more saturates the radio and makes per-item progress jump
 * around, fewer wastes the pipe.
 *
 * Failure is per item. A dead mirror on the offline toolset must not stop the
 * agent bundle from installing, which is what a single sequential loop with a
 * thrown exception would do.
 */
class DownloadQueue(
    private val context: android.content.Context,
    private val paths: RuntimePaths,
    private val downloader: Downloader = Downloader(),
    private val extractor: TarGzExtractor = TarGzExtractor(),
    private val modelDir: File
) {

    enum class State { PENDING, RUNNING, VERIFYING, DONE, FAILED, SKIPPED }

    data class Item(
        val entry: BundleCatalog.Entry,
        val state: State = State.PENDING,
        val bytesRead: Long = 0L,
        val error: String? = null
    ) {
        val fraction: Float
            get() = when {
                state == State.DONE || state == State.SKIPPED -> 1f
                entry.bytes <= 0 -> 0f
                else -> (bytesRead.toFloat() / entry.bytes).coerceIn(0f, 1f)
            }
    }

    data class Snapshot(
        val items: List<Item> = emptyList(),
        val running: Boolean = false,
        val paused: Boolean = false,
        val totalBytes: Long = 0,
        val doneBytes: Long = 0,
        val activeCount: Int = 0
    ) {
        val fraction: Float
            get() = if (totalBytes <= 0) 0f else (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)

        val finished: Boolean
            get() = items.isNotEmpty() && items.all {
                it.state == State.DONE || it.state == State.SKIPPED || it.state == State.FAILED
            }

        val hasFailure: Boolean get() = items.any { it.state == State.FAILED }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var paused = false

    fun load(entries: List<BundleCatalog.Entry>) {
        paths.ensureAll()
        val items = entries.map { entry ->
            val target = destinationFor(entry)
            val alreadyThere = when (entry.kind) {
                BundleCatalog.Entry.Kind.MODEL ->
                    File(modelDir, entry.id + ".gguf").exists()
                else -> {
                    val dir = File(paths.root.parentFile ?: paths.root, entry.extractTo)
                    dir.exists() && (dir.list()?.isNotEmpty() == true)
                }
            }
            Item(entry, if (alreadyThere) State.DONE else State.PENDING)
        }
        _state.value = Snapshot(
            items = items,
            totalBytes = items.filter { it.state != State.DONE }.sumOf { it.entry.bytes },
            doneBytes = items.filter { it.state == State.DONE }.sumOf { it.entry.bytes }
        )
    }

    fun startAll(maxParallel: Int = 2) {
        if (job?.isActive == true) return
        paused = false
        _state.value = _state.value.copy(running = true, paused = false)

        job = scope.launch {
            val semaphore = Semaphore(maxParallel)
            val pending = _state.value.items.filter { it.state == State.PENDING }
            val doneCounter = AtomicLong(_state.value.items.filter { it.state == State.DONE }.sumOf { it.entry.bytes })

            val workers = pending.map { item ->
                launch {
                    semaphore.withPermit {
                        if (paused) {
                            update(item.entry.id) { it.copy(state = State.PENDING) }
                            return@withPermit
                        }
                        runOne(item, doneCounter)
                    }
                }
            }
            workers.forEach { it.join() }

            _state.value = _state.value.copy(running = false)
            Logs.i("queue", "queue finished, ${_state.value.doneBytes} bytes")
        }
    }

    private suspend fun runOne(item: Item, doneCounter: AtomicLong) {
        val entry = item.entry
        update(entry.id) { it.copy(state = State.RUNNING, error = null) }

        try {
            val target = destinationFor(entry)

            if (entry.bundled) {
                // Ships in the APK. Nothing here can 404, time out or be rate limited.
                installBundled(entry, target) { read -> update(entry.id) { it.copy(bytesRead = read) } }
            } else if (entry.url.isBlank()) {
                // Optional component with no server configured. Skip rather than fail:
                // a red row for something the user never asked for is just noise.
                update(entry.id) { it.copy(state = State.SKIPPED, error = null) }
                return
            } else if (entry.kind == BundleCatalog.Entry.Kind.MODEL) {
                val file = File(modelDir, entry.id + ".gguf")
                file.parentFile?.mkdirs()
                downloader.fetch(entry.url, file, entry.sha256) { p ->
                    update(entry.id) { it.copy(bytesRead = p.bytesRead) }
                }
            } else {
                val archive = File(paths.downloads, entry.url.substringAfterLast('/'))
                downloader.fetch(entry.url, archive, entry.sha256) { p ->
                    update(entry.id) { it.copy(bytesRead = p.bytesRead) }
                }
                update(entry.id) { it.copy(state = State.VERIFYING) }
                extractor.extract(archive, target)
            }

            doneCounter.addAndGet(entry.bytes)
            update(entry.id) {
                it.copy(state = State.DONE, bytesRead = entry.bytes)
            }
            _state.value = _state.value.copy(doneBytes = doneCounter.get())
        } catch (t: Throwable) {
            Logs.e("queue", "failed ${entry.id}", t)
            update(entry.id) {
                it.copy(state = State.FAILED, error = t.message ?: t.javaClass.simpleName)
            }
        }
    }

    fun pauseAll() {
        paused = true
        job?.cancel()
        _state.value = _state.value.copy(running = false, paused = true)
        Logs.i("queue", "paused by user")
    }

    fun retry(id: String) {
        update(id) { it.copy(state = State.PENDING, error = null, bytesRead = 0) }
        val remaining = _state.value.items.filter { it.state == State.PENDING }
        if (remaining.isNotEmpty() && job?.isActive != true) startAll()
    }

    fun retryFailed() {
        _state.value.items.filter { it.state == State.FAILED }.forEach { item ->
            update(item.entry.id) { it.copy(state = State.PENDING, error = null, bytesRead = 0) }
        }
        startAll()
    }

    fun cancel() {
        job?.cancel()
        _state.value = _state.value.copy(running = false)
    }


    /**
     * Copies a component out of the APK's assets and unpacks it.
     *
     * Two file shapes: a tar.gz to extract, and a bare binary to drop in place with
     * the execute bit set (proot). The distinction is by extension because the
     * manifest already describes the file, and inventing another field for it would
     * be one more thing to get wrong.
     */
    private suspend fun installBundled(
        entry: BundleCatalog.Entry,
        target: File,
        onProgress: (Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val name = entry.fileName.ifBlank {
            entry.url.substringAfterLast('/').ifBlank { entry.id + ".tar.gz" }
        }
        val assetPath = "runtime/$name"
        val staging = File(paths.downloads, name)
        staging.parentFile?.mkdirs()

        context.assets.open(assetPath).use { input ->
            staging.outputStream().use { out ->
                val buffer = ByteArray(256 * 1024)
                var written = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    written += read
                    if (written % (4L * 1024 * 1024) < buffer.size) onProgress(written)
                }
                out.flush()
                onProgress(written)
            }
        }

        target.parentFile?.mkdirs()
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            extractor.extract(staging, target)
        } else {
            if (target.exists()) target.delete()
            staging.copyTo(target, overwrite = true)
            target.setExecutable(true, false)
        }
        staging.delete()
        Logs.i("queue", "installed bundled ${entry.id} -> ${target.name}")
    }

    private fun destinationFor(entry: BundleCatalog.Entry): File =
        when (entry.kind) {
            BundleCatalog.Entry.Kind.MODEL -> File(modelDir, entry.id + ".gguf")
            else -> File(paths.root.parentFile ?: paths.root, entry.extractTo)
        }

    private fun update(id: String, transform: (Item) -> Item) {
        _state.value = _state.value.copy(
            items = _state.value.items.map { if (it.entry.id == id) transform(it) else it }
        )
    }
}
