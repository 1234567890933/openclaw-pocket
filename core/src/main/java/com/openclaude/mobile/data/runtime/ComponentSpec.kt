package com.openclaude.mobile.data.runtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One downloadable slice of the runtime. The manifest is versioned separately
 * from the APK so the heavy payload can move without a store release.
 */
@Serializable
data class ComponentSpec(
    val id: String,
    val label: String,
    val file: String,
    val bytes: Long,
    val sha256: String,
    @SerialName("extractTo") val extractTo: String,
    val required: Boolean = true,
    val stage: Int = 1,
    /** Absolute URL. Empty means "resolve against baseUrl", which may also be empty. */
    val url: String = "",
    /**
     * True when the file ships inside the APK's assets. Bundled components cannot
     * 404, cannot time out, and do not need a network at first boot — which is the
     * whole reason Node and proot are in there.
     */
    val bundled: Boolean = false
) {
    /**
     * Absolute URL for this component, or empty when there is none.
     *
     * The empty case used to return just the file name, which OkHttp rejects with
     * "Expected URL scheme 'http' or 'https'" — an error the user cannot act on for a
     * component that was never meant to be downloaded at all.
     */
    fun resolvedUrl(baseUrl: String): String = when {
        url.isNotBlank() -> url
        baseUrl.isBlank() -> ""
        else -> baseUrl + file
    }
}

@Serializable
data class BootstrapManifest(
    val schemaVersion: Int = 1,
    val channel: String = "stable",
    val baseUrl: String,
    val totalBytes: Long = 0,
    val components: List<ComponentSpec> = emptyList()
) {
    fun ordered(): List<ComponentSpec> = components.sortedWith(
        compareBy<ComponentSpec> { it.stage }.thenBy { it.id }
    )

    fun requiredBytes(): Long = components.filter { it.required }.sumOf { it.bytes }
}
