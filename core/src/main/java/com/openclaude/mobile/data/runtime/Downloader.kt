package com.openclaude.mobile.data.runtime

import com.openclaude.mobile.util.Constants
import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Resumable HTTP downloader with SHA-256 verification.
 *
 * Design notes:
 *  - Partial files live at `<name>.part`; a resumed run sends `Range` and appends.
 *  - Verification happens on the finished file, before it is renamed into place,
 *    so a corrupt download can never be mistaken for an installed component.
 *  - Progress is reported as bytes rather than percent so the UI can smooth it.
 */
class Downloader(
    private val client: OkHttpClient = defaultClient()
) {

    data class Progress(val bytesRead: Long, val totalBytes: Long, val done: Boolean)

    suspend fun fetch(
        url: String,
        target: File,
        expectedSha256: String,
        onProgress: (Progress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        var existing = if (part.exists()) part.length() else 0L

        val builder = Request.Builder().url(url).header("Accept-Encoding", "identity")
        if (existing > 0) builder.header("Range", "bytes=$existing-")

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                // 416 means our partial file is already the whole thing.
                if (response.code == 416 && part.exists()) {
                    verifyOrThrow(part, expectedSha256)
                    part.renameTo(target)
                    onProgress(Progress(part.length(), part.length(), true))
                    return@withContext target
                }
                throw IllegalStateException("HTTP ${response.code} for $url")
            }

            val body = response.body ?: throw IllegalStateException("empty body for $url")
            val total = existing + body.contentLength()
            var written = existing

            FileOutputStream(part, existing > 0).use { out ->
                val buf = ByteArray(Constants.IO_BUFFER_BYTES)
                body.byteStream().use { input ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        written += n
                        onProgress(Progress(written, total.coerceAtLeast(written), false))
                    }
                    out.flush()
                    out.fd.sync()
                }
            }
            Logs.d("dl", "${target.name} wrote $written bytes")
        }

        verifyOrThrow(part, expectedSha256)
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) throw IllegalStateException("rename failed for ${target.name}")
        onProgress(Progress(target.length(), target.length(), true))
        target
    }

    private fun verifyOrThrow(file: File, expected: String) {
        if (expected.isBlank() || expected.all { it == '0' }) {
            // Placeholder digest in the sample manifest: skip, but say so loudly.
            Logs.w("dl", "no real digest for ${file.name}; skipping verification")
            return
        }
        val actual = sha256(file)
        if (!actual.equals(expected, ignoreCase = true)) {
            file.delete()
            throw SecurityException("checksum mismatch for ${file.name}: $actual != $expected")
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()

        fun sha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
