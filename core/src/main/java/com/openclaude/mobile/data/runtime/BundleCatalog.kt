package com.openclaude.mobile.data.runtime

import com.openclaude.mobile.data.local.DeviceProfile

/**
 * Everything the first boot can install, in one flat list.
 *
 * Two sources feed it: the runtime manifest (binaries the agent needs) and the
 * model catalogue (weights the user might want). Merging them here means the
 * download screen has one queue, one progress bar and one retry, instead of a
 * separate "models" downloader that duplicates all of that.
 */
object BundleCatalog {

    data class Entry(
        val id: String,
        val label: String,
        val detail: String,
        val url: String,
        val sha256: String,
        val bytes: Long,
        val extractTo: String,
        val required: Boolean,
        val stage: Int,
        val kind: Kind,
        val bundled: Boolean = false,
        /** Exact name in app/src/main/assets/runtime — not derivable from the id. */
        val fileName: String = ""
    ) {
        val downloadable: Boolean get() = url.isNotBlank() || !bundled

        enum class Kind { RUNTIME, MODEL, TOOL }
    }

    fun fromManifest(manifest: BootstrapManifest): List<Entry> =
        manifest.components.map { spec ->
            Entry(
                id = spec.id,
                label = spec.label,
                detail = "${spec.bytes / 1_048_576} MB · runtime",
                url = spec.resolvedUrl(manifest.baseUrl),
                sha256 = spec.sha256,
                bytes = spec.bytes,
                extractTo = spec.extractTo,
                required = spec.required,
                stage = spec.stage,
                kind = Entry.Kind.RUNTIME,
                bundled = spec.bundled,
                fileName = spec.file
            )
        }

    /**
     * Model suggestions sized for this device.
     *
     * The rule is the same one [DeviceProfile.canRun] enforces, applied early so a
     * user never downloads 3 GB of weights the phone cannot hold.
     */
    fun modelsFor(device: DeviceProfile): List<Entry> {
        val budget = device.snapshot.modelBudgetBytes
        val candidates = listOf(
            Triple(
                "model-qwen3-1.7b-q4",
                "Qwen3 1.7B · Q4_K_M",
                1_100_000_000L
            ),
            Triple(
                "model-llama-3.2-3b-q4",
                "Llama 3.2 3B · Q4_K_M",
                2_000_000_000L
            ),
            Triple(
                "model-gemma-3-4b-q4",
                "Gemma 3 4B · Q4_K_M",
                2_500_000_000L
            )
        )

        return candidates
            .filter { it.third <= budget }
            .map { (id, label, bytes) ->
                Entry(
                    id = id,
                    label = label,
                    detail = "%.1f GB · runs on this phone".format(bytes / 1_073_741_824.0),
                    url = if (MODEL_BASE.isBlank()) "" else MODEL_BASE + "$id.gguf",
                    sha256 = "",
                    bytes = bytes,
                    extractTo = "models",
                    required = false,
                    stage = 8,
                    kind = Entry.Kind.MODEL
                )
            }
    }

    // No public model host is configured, so these entries carry an empty URL and the
    // installer marks them skipped instead of failing with a 404 nobody can act on.
    private const val MODEL_BASE = ""
}
