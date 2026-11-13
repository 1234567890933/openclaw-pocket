package com.openclaude.mobile.tools

import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A loopback HTTP server the gateway calls to reach the phone's tools.
 *
 * Why a server rather than a pipe: the gateway is a long-running Node process in a
 * different mount namespace. Handing it a URL it can POST to is the least invasive
 * way to extend it, and it keeps the tool code inside the app where the Android
 * permission model still applies.
 *
 * Security:
 *  - bound to 127.0.0.1, so nothing off-device can reach it
 *  - every request needs `Authorization: Bearer <gateway token>`, compared in
 *    constant time
 *  - request bodies are capped, so a malformed Content-Length cannot exhaust memory
 *  - one request per connection, closed immediately: no keep-alive to reason about
 *
 * This is deliberately a small HTTP/1.1 implementation, not a framework. It handles
 * exactly one shape of request and refuses everything else.
 */
class ToolBridgeServer(
    private val registry: ToolRegistry,
    private val token: String,
    private val port: Int
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private var server: ServerSocket? = null
    private var acceptJob: Job? = null

    fun start() {
        if (running.getAndSet(true)) return
        acceptJob = scope.launch {
            val socket = runCatching {
                ServerSocket(port, 16, InetAddress.getByName("127.0.0.1"))
            }.getOrElse {
                Logs.e("bridge", "cannot bind $port", it)
                running.set(false)
                return@launch
            }
            server = socket
            Logs.i("bridge", "tool bridge listening on 127.0.0.1:$port")

            while (running.get()) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch { handle(client) }
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { server?.close() }
        acceptJob?.cancel()
        server = null
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 30_000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            val request = runCatching { readRequest(input) }.getOrNull()
            if (request == null) {
                respond(output, 400, """{"error":"malformed request"}""")
                return
            }

            if (!authorized(request)) {
                Logs.w("bridge", "unauthorized ${request.method} ${request.path}")
                respond(output, 401, """{"error":"unauthorized"}""")
                return
            }

            when {
                request.method == "GET" && request.path == "/health" ->
                    respond(output, 200, """{"ok":true,"tools":${registry.specs().size}}""")

                request.method == "GET" && request.path == "/tools" ->
                    respond(output, 200, registry.catalogueJson())

                request.method == "POST" && request.path.startsWith("/tool/") -> {
                    val name = request.path.removePrefix("/tool/").substringBefore('?')
                    val body = runCatching {
                        Json.parseToJsonElement(request.body).jsonObject
                    }.getOrElse { JsonObject(emptyMap()) }

                    val result = runBlocking { registry.call(name, body) }
                    respond(
                        output,
                        200,
                        """{"ok":${result.ok},"output":${quote(result.output)},""" +
                            """"ms":${result.ms},"truncated":${result.truncated},""" +
                            """"error":${if (result.error == null) "null" else quote(result.error)}}"""
                    )
                }

                else -> respond(output, 404, """{"error":"not found"}""")
            }
        }
    }

    private data class Request(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String
    )

    private fun readRequest(input: InputStream): Request {
        val line = readLine(input) ?: error("empty request")
        val parts = line.split(' ')
        if (parts.size < 2) error("bad request line")

        val headers = mutableMapOf<String, String>()
        while (true) {
            val header = readLine(input) ?: break
            if (header.isEmpty()) break
            val index = header.indexOf(':')
            if (index > 0) {
                headers[header.substring(0, index).trim().lowercase()] =
                    header.substring(index + 1).trim()
            }
            if (headers.size > 64) error("too many headers")
        }

        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length > MAX_BODY) error("body too large")
        val body = if (length > 0) {
            val buffer = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(buffer, read, length - read)
                if (n < 0) break
                read += n
            }
            String(buffer, 0, read, Charsets.UTF_8)
        } else ""

        return Request(parts[0].uppercase(), parts[1], headers, body)
    }

    private fun authorized(request: Request): Boolean {
        val header = request.headers["authorization"] ?: return false
        val presented = header.removePrefix("Bearer ").trim()
        return constantTimeEquals(presented, token)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val left = a.toByteArray()
        val right = b.toByteArray()
        var diff = left.size xor right.size
        val n = minOf(left.size, right.size)
        for (i in 0 until n) diff = diff or (left[i].toInt() xor right[i].toInt())
        return diff == 0
    }

    private fun readLine(input: InputStream): String? {
        val buffer = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (buffer.isEmpty()) null else buffer.toString()
            if (c == '\n'.code) break
            if (c != '\r'.code) buffer.append(c.toChar())
            if (buffer.length > 8192) error("header line too long")
        }
        return buffer.toString()
    }

    private fun respond(output: OutputStream, status: Int, body: String) {
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Error"
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        runCatching {
            output.write(head.toByteArray())
            output.write(bytes)
            output.flush()
        }
    }

    private fun quote(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    private companion object {
        const val MAX_BODY = 512 * 1024
    }
}
