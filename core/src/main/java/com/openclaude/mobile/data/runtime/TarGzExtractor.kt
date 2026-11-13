package com.openclaude.mobile.data.runtime

import com.openclaude.mobile.util.Logs
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Minimal tar.gz extractor.
 *
 * Android has no tar in the platform, and shelling out to one would mean shipping
 * a second binary before the runtime exists. The tar format is simple enough to
 * read directly: 512-byte headers, octal size field at offset 124, ustar magic at
 * 257. We support regular files, directories, GNU long names ('L') and PAX
 * extended headers ('x') because Debian's minirootfs uses them.
 *
 * Deliberately unsupported: hard links, device nodes, setuid bits. Extraction is
 * confined to [destination] and any entry that escapes it is refused.
 */
class TarGzExtractor {

    fun extract(archive: File, destination: File) {
        destination.mkdirs()
        val destCanonical = destination.canonicalPath
        var longName: String? = null
        var paxPath: String? = null

        // Sniff the magic bytes rather than trusting the extension: Android's asset
        // packaging rewrites some extensions, and a .tar that is really a .tar.gz (or
        // the reverse) should not be a runtime failure.
        val raw = BufferedInputStream(archive.inputStream(), 64 * 1024)
        raw.mark(4)
        val b0 = raw.read()
        val b1 = raw.read()
        raw.reset()
        val stream: InputStream = if (b0 == 0x1f && b1 == 0x8b) GZIPInputStream(raw) else raw

        stream.use { gz ->
            val header = ByteArray(512)
            while (true) {
                if (!readFully(gz, header, 512)) break
                if (header.all { it.toInt() == 0 }) break

                val type = header[156].toInt().toChar()
                val size = parseOctal(header, 124, 12)
                val rawName = readString(header, 0, 100)
                val prefix = readString(header, 345, 155)
                val name = longName ?: paxPath ?: if (prefix.isNotEmpty()) "$prefix/$rawName" else rawName

                when (type) {
                    'L' -> {
                        longName = String(readPayload(gz, size), Charsets.UTF_8).trimEnd('\u0000')
                        continue
                    }
                    'x' -> {
                        paxPath = parsePax(readPayload(gz, size)) ?: paxPath
                        continue
                    }
                    '5' -> {
                        safeResolve(destCanonical, name)?.mkdirs()
                        skip(gz, size)
                    }
                    '0', '\u0000', '7' -> {
                        val target = safeResolve(destCanonical, name)
                        if (target != null) {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { out ->
                                copyExact(gz, out, size)
                                out.fd.sync()
                            }
                            val mode = parseOctal(header, 100, 8)
                            if (mode and 0b001_000_000L != 0L) target.setExecutable(true, true)
                        } else {
                            Logs.w("tar", "refused path outside root: $name")
                            skip(gz, size)
                        }
                    }
                    else -> skip(gz, size)
                }

                longName = null
                paxPath = null
                // Entries are padded to a 512-byte boundary.
                val pad = (512 - (size % 512)) % 512
                if (pad > 0) skip(gz, pad)
            }
        }
        Logs.i("tar", "extracted ${archive.name} -> ${destination.name}")
    }

    private fun safeResolve(root: String, name: String): File? {
        val cleaned = name.removePrefix("./").removePrefix("/")
        if (cleaned.contains("..")) return null
        val candidate = File(root, cleaned)
        val canonical = candidate.canonicalPath
        if (canonical != root && !canonical.startsWith(root + File.separator)) return null
        return candidate
    }

    private fun parsePax(payload: ByteArray): String? {
        val text = String(payload, Charsets.UTF_8)
        var cursor = 0
        while (cursor < text.length) {
            val space = text.indexOf(' ', cursor)
            if (space < 0) break
            val len = text.substring(cursor, space).toIntOrNull() ?: break
            if (len <= 0 || cursor + len > text.length) break
            val record = text.substring(space + 1, cursor + len - 1)
            if (record.startsWith("path=")) return record.removePrefix("path=")
            cursor += len
        }
        return null
    }

    private fun parseOctal(h: ByteArray, off: Int, len: Int): Long {
        var value = 0L
        for (i in off until off + len) {
            val c = h[i].toInt()
            if (c == 0 || c == ' '.code) continue
            if (c < '0'.code || c > '7'.code) break
            value = value * 8 + (c - '0'.code)
        }
        return value
    }

    private fun readString(h: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && h[end].toInt() != 0) end++
        return String(h, off, end - off, Charsets.UTF_8)
    }

    private fun readFully(input: InputStream, buf: ByteArray, len: Int): Boolean {
        var read = 0
        while (read < len) {
            val n = input.read(buf, read, len - read)
            if (n < 0) return read > 0
            read += n
        }
        return true
    }

    private fun readPayload(input: InputStream, size: Long): ByteArray {
        if (size > 1_048_576) throw IllegalStateException("pax header too large: $size")
        val out = ByteArray(size.toInt())
        var read = 0
        while (read < out.size) {
            val n = input.read(out, read, out.size - read)
            if (n < 0) break
            read += n
        }
        return out
    }

    private fun copyExact(input: InputStream, out: FileOutputStream, size: Long) {
        var remaining = size
        val buf = ByteArray(64 * 1024)
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (n < 0) break
            out.write(buf, 0, n)
            remaining -= n
        }
    }

    private fun skip(input: InputStream, size: Long) {
        var remaining = size
        while (remaining > 0) {
            val n = input.skip(remaining)
            if (n <= 0) {
                // skip() may legitimately return 0; read one byte to make progress.
                if (input.read() < 0) return
                remaining -= 1
            } else {
                remaining -= n
            }
        }
    }
}
