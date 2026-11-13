package com.openclaude.mobile

import com.openclaude.mobile.data.runtime.BundleCatalog
import com.openclaude.mobile.data.runtime.DownloadQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Progress maths is what the user stares at for ten minutes, so it is worth three
 * tests. Every one of these has shipped broken in some installer, somewhere.
 */
class DownloadQueueTest {

    private fun entry(bytes: Long) = BundleCatalog.Entry(
        id = "x",
        label = "x",
        detail = "",
        url = "https://example.test/x.tar.gz",
        sha256 = "",
        bytes = bytes,
        extractTo = "runtime/x",
        required = true,
        stage = 1,
        kind = BundleCatalog.Entry.Kind.RUNTIME
    )

    @Test
    fun `pending item has zero progress`() {
        val item = DownloadQueue.Item(entry(1000))
        assertEquals(0f, item.fraction, 0.0001f)
    }

    @Test
    fun `running item reports a fraction of its own size`() {
        val item = DownloadQueue.Item(
            entry(1000),
            state = DownloadQueue.State.RUNNING,
            bytesRead = 250
        )
        assertEquals(0.25f, item.fraction, 0.0001f)
    }

    @Test
    fun `fraction is clamped when the server sends more than expected`() {
        val item = DownloadQueue.Item(
            entry(1000),
            state = DownloadQueue.State.RUNNING,
            bytesRead = 4000
        )
        assertEquals(1f, item.fraction, 0.0001f)
    }

    @Test
    fun `done and skipped are always full`() {
        assertEquals(1f, DownloadQueue.Item(entry(10), DownloadQueue.State.DONE).fraction, 0.0001f)
        assertEquals(1f, DownloadQueue.Item(entry(10), DownloadQueue.State.SKIPPED).fraction, 0.0001f)
    }

    @Test
    fun `zero byte entry does not divide by zero`() {
        val item = DownloadQueue.Item(entry(0), state = DownloadQueue.State.RUNNING, bytesRead = 5)
        assertTrue(item.fraction.isFinite())
    }

    @Test
    fun `snapshot fraction aggregates by bytes not by count`() {
        val snapshot = DownloadQueue.Snapshot(
            items = listOf(
                DownloadQueue.Item(entry(900), DownloadQueue.State.DONE, 900),
                DownloadQueue.Item(entry(100), DownloadQueue.State.PENDING, 0)
            ),
            totalBytes = 1000,
            doneBytes = 900
        )
        assertEquals(0.9f, snapshot.fraction, 0.0001f)
    }
}
