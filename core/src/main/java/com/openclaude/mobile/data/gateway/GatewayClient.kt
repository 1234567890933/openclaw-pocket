package com.openclaude.mobile.data.gateway

import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebSocket client for the gateway.
 *
 * The gateway is a control plane, so the socket carries both directions at once:
 * requests out, events in, on one connection. Three behaviours make it survive a
 * phone rather than a desktop:
 *
 *  - **Reconnect with backoff**, capped at 20s, with jitter so a wake-from-doze
 *    does not produce a thundering herd against a local socket.
 *  - **Outbox.** A message typed while the socket is down is kept and sent on the
 *    next `ready`, not dropped. Losing a prompt because the app was backgrounded is
 *    the single most annoying bug a chat client can have.
 *  - **Heartbeat.** A ping every 25s so a dead socket is detected in under a
 *    minute rather than at the next send.
 */
class GatewayClient {

    sealed interface Connection {
        data object Idle : Connection
        data object Connecting : Connection
        data class Open(val gatewayVersion: String) : Connection
        data class Lost(val reason: String) : Connection
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _connection = MutableStateFlow<Connection>(Connection.Idle)
    val connection: StateFlow<Connection> = _connection.asStateFlow()

    private val _events = MutableSharedFlow<Any>(
        replay = 0,
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<Any> = _events.asSharedFlow()

    private var socket: WebSocket? = null
    private var endpoint: GatewayPairing.Endpoint? = null
    private val outbox = ArrayDeque<String>()
    private val closed = AtomicBoolean(false)
    private var attempt = 0
    private var heartbeat: kotlinx.coroutines.Job? = null

    fun connect(endpoint: GatewayPairing.Endpoint) {
        this.endpoint = endpoint
        closed.set(false)
        attempt = 0
        open()
    }

    private fun open() {
        val ep = endpoint ?: return
        _connection.value = Connection.Connecting

        val request = Request.Builder()
            .url(ep.streamUrl)
            .header("Authorization", "Bearer ${ep.token}")
            .header("X-Client", "openclaude-mobile")
            .build()

        socket = http.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Logs.i("gateway.ws", "socket open")
                attempt = 0
                val hello = GatewayProtocol.Hello(
                    token = ep.token,
                    deviceName = android.os.Build.MODEL ?: "android"
                )
                webSocket.send(GatewayProtocol.json.encodeToString(GatewayProtocol.Hello.serializer(), hello))
                startHeartbeat(webSocket)
                flushOutbox(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                when (val parsed = GatewayProtocol.parse(text)) {
                    null -> Logs.d("gateway.ws", "dropped frame: ${text.take(120)}")
                    is GatewayProtocol.Ready -> {
                        _connection.value = Connection.Open(parsed.gatewayVersion)
                        scope.launch { _events.emit(parsed) }
                    }
                    is String -> Unit
                    else -> scope.launch { _events.emit(parsed) }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Logs.w("gateway.ws", "failure: ${t.message}")
                _connection.value = Connection.Lost(t.message ?: "socket failure")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Logs.i("gateway.ws", "closed $code $reason")
                if (!closed.get()) {
                    _connection.value = Connection.Lost(reason.ifBlank { "closed" })
                    scheduleReconnect()
                }
            }
        })
    }

    private fun scheduleReconnect() {
        if (closed.get()) return
        attempt++
        if (attempt > 8) {
            _connection.value = Connection.Lost("gave up after $attempt attempts")
            return
        }
        val base = minOf(20_000L, 500L * (1L shl (attempt - 1)))
        val jitter = (0..400).random()
        scope.launch {
            delay(base + jitter)
            if (!closed.get()) open()
        }
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeat?.cancel()
        heartbeat = scope.launch {
            while (true) {
                delay(25_000)
                webSocket.send("""{"type":"ping","id":"hb"}""")
            }
        }
    }

    private fun flushOutbox(webSocket: WebSocket) {
        synchronized(outbox) {
            while (outbox.isNotEmpty()) {
                webSocket.send(outbox.removeFirst())
            }
        }
    }

    fun send(frame: String): Boolean {
        val ws = socket
        if (ws != null && _connection.value is Connection.Open) {
            return ws.send(frame)
        }
        synchronized(outbox) {
            if (outbox.size > 32) outbox.removeFirst()
            outbox.addLast(frame)
        }
        Logs.d("gateway.ws", "queued frame (offline)")
        return false
    }

    fun sendUserMessage(sessionId: String, text: String, botId: String? = null) {
        val message = GatewayProtocol.UserMessage(
            id = java.util.UUID.randomUUID().toString(),
            sessionId = sessionId,
            text = text,
            botId = botId
        )
        send(GatewayProtocol.json.encodeToString(GatewayProtocol.UserMessage.serializer(), message))
    }

    fun interrupt(sessionId: String) {
        val message = GatewayProtocol.Interrupt(
            id = java.util.UUID.randomUUID().toString(),
            sessionId = sessionId
        )
        send(GatewayProtocol.json.encodeToString(GatewayProtocol.Interrupt.serializer(), message))
    }

    fun replyApproval(requestId: String, approved: Boolean, remember: Boolean) {
        val reply = GatewayProtocol.ApprovalReply(
            id = java.util.UUID.randomUUID().toString(),
            requestId = requestId,
            approved = approved,
            remember = remember
        )
        send(GatewayProtocol.json.encodeToString(GatewayProtocol.ApprovalReply.serializer(), reply))
    }

    fun disconnect() {
        closed.set(true)
        heartbeat?.cancel()
        socket?.close(1000, "client shutdown")
        socket = null
        _connection.value = Connection.Idle
    }
}
