package com.openclaude.mobile.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaude.mobile.data.runtime.BundleCatalog
import com.openclaude.mobile.data.runtime.DownloadQueue
import com.openclaude.mobile.runtime.StorageProfile
import com.openclaude.mobile.di.ServiceLocator
import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DownloadAllViewModel(
    private val queue: DownloadQueue = ServiceLocator.downloadQueue
) : ViewModel() {

    val state: StateFlow<DownloadQueue.Snapshot> = queue.state

    private val _deviceLine = MutableStateFlow("")
    val deviceLine: StateFlow<String> = _deviceLine.asStateFlow()

    init {
        viewModelScope.launch {
            val manifest = runCatching {
                ServiceLocator.bootstrapManager.loadManifest()
            }.getOrNull()

            val profile = ServiceLocator.storageProfile()
            val all = buildList {
                if (manifest != null) addAll(BundleCatalog.fromManifest(manifest))
                addAll(BundleCatalog.modelsFor(ServiceLocator.deviceProfile))
            }
            // Lite keeps proot, the userland, Node and the agent — the four things
            // without which nothing runs. Everything else is optional and downloads
            // later if the user wants it.
            val entries = when (profile) {
                StorageProfile.LITE -> all.filter { it.required || it.stage <= 4 }
                StorageProfile.STANDARD -> all.filter { it.stage <= 5 }
                StorageProfile.FULL -> all
            }
            Logs.i("download", "profile=${profile.name} entries=${entries.size}")

            if (entries.isEmpty()) {
                Logs.e("download", "no installable entries; manifest unreadable")
            }
            queue.load(entries)
            _deviceLine.value = ServiceLocator.deviceProfile.describe()
        }
    }

    fun startAll() = queue.startAll()
    fun pause() = queue.pauseAll()
    fun retryFailed() = queue.retryFailed()
    fun reset() = queue.cancel()

    fun summaryLine(state: DownloadQueue.Snapshot): String {
        val done = state.items.count { it.state == DownloadQueue.State.DONE }
        val total = state.items.size
        val mb = "%.0f MB".format(state.totalBytes / 1_048_576.0)
        return when {
            state.running -> "$done of $total · $mb"
            state.hasFailure -> "Some components failed"
            state.fraction >= 1f -> "Everything is installed"
            else -> "$total components · $mb"
        }
    }
}
