package com.openclaude.mobile

import com.openclaude.mobile.core.LineFramer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Framing is the boundary between a byte stream and the JSON parser, so it gets
 * tested against the cases that actually break in production: a line split across
 * reads, CRLF, and a multi-byte character cut in half.
 */
class LineFramerTest {

    private fun feed(framer: LineFramer, text: String): List<String> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        framer.append(bytes, 0, bytes.size)
        val out = mutableListOf<String>()
        while (true) {
            val line = framer.nextLine() ?: break
            out += line
        }
        return out
    }

    @Test
    fun `returns complete lines only`() {
        val framer = LineFramer(256)
        assertEquals(listOf("alpha"), feed(framer, "alpha\n"))
        assertEquals(0, framer.pendingBytes())
    }

    @Test
    fun `holds a partial line until its newline arrives`() {
        val framer = LineFramer(256)
        assertEquals(emptyList<String>(), feed(framer, "{\"type\":\"a\""))
        assertEquals(5, framer.pendingBytes())
        assertEquals(listOf("{\"type\":\"a\"}"), feed(framer, "}\n"))
    }

    @Test
    fun `strips carriage returns`() {
        val framer = LineFramer(256)
        assertEquals(listOf("one", "two"), feed(framer, "one\r\ntwo\r\n"))
    }

    @Test
    fun `keeps a multi byte character intact across reads`() {
        val framer = LineFramer(256)
        // "ह" is three bytes in UTF-8; split it down the middle.
        val bytes = "ह\n".toByteArray(Charsets.UTF_8)
        framer.append(bytes, 0, 1)
        assertNull(framer.nextLine())
        framer.append(bytes, 1, bytes.size - 1)
        assertEquals("ह", framer.nextLine())
    }

    @Test
    fun `empty line is returned as empty string`() {
        val framer = LineFramer(256)
        assertEquals(listOf(""), feed(framer, "\n"))
    }

    @Test
    fun `overflow is reported and does not throw`() {
        val framer = LineFramer(64)
        val big = "x".repeat(200)
        framer.append(big.toByteArray(), 0, big.length)
        assertTrue(framer.overflowed())
        framer.clearOverflow()
        assertTrue(!framer.overflowed())
    }

    @Test
    fun `reset clears buffered bytes`() {
        val framer = LineFramer(256)
        framer.append("partial".toByteArray(), 0, 7)
        assertEquals(7, framer.pendingBytes())
        framer.reset()
        assertEquals(0, framer.pendingBytes())
    }
}
