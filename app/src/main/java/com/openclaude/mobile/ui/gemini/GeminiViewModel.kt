package com.openclaude.mobile.ui.gemini

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaude.mobile.bots.Bot
import com.openclaude.mobile.bots.BotRoster
import com.openclaude.mobile.data.agent.AgentEvent
import com.openclaude.mobile.data.agent.AgentRepository
import com.openclaude.mobile.data.agent.ProviderStore
import com.openclaude.mobile.data.chat.ChatRepository
import com.openclaude.mobile.data.chat.MessageEntity
import com.openclaude.mobile.data.chat.SessionEntity
import com.openclaude.mobile.data.gateway.GatewayClient
import com.openclaude.mobile.data.gateway.GatewayProtocol
import com.openclaude.mobile.data.provider.DirectModelClient.Message as DirectMessage
import com.openclaude.mobile.data.local.LocalEngine
import com.openclaude.mobile.data.local.LocalModelManager
import com.openclaude.mobile.data.router.ModelMode
import com.openclaude.mobile.data.router.ModelRouter
import com.openclaude.mobile.di.ServiceLocator
import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class GeminiUi(
    val sessionId: String = "",
    val messages: List<MessageEntity> = emptyList(),
    val streaming: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val mode: ModelMode = ModelMode.AUTO,
    val modelLabel: String = "OpenClaude",
    val modeLabel: String? = null,
    val localModelLabel: String? = null,
    val cloudModelLabel: String = "cloud",
    val avatarInitial: String? = null
)

/**
 * Conversation state for the Gemini-style surface.
 *
 * Two engines feed one message list. That is the whole design: whether a turn ran
 * on the phone's CPU or on a cloud provider is an implementation detail to the
 * transcript, and the only visible trace is the small mode label in the top bar.
 *
 * Streaming writes are throttled. Room will happily take one insert per token, and
 * that is a few hundred disk writes per answer on a device that is already warm
 * from inference.
 */
class GeminiViewModel(
    private val chat: ChatRepository = ServiceLocator.chatRepository,
    private val agent: AgentRepository = ServiceLocator.agentRepository,
    private val router: ModelRouter = ServiceLocator.modelRouter,
    private val local: LocalEngine = ServiceLocator.localEngine,
    private val models: LocalModelManager = ServiceLocator.localModelManager,
    private val providers: ProviderStore = ServiceLocator.providerStore,
    private val gateway: GatewayClient = ServiceLocator.gatewayClient,
    private val guard: com.openclaude.mobile.security.SafetyGuard = ServiceLocator.safetyGuard,
    private val direct: com.openclaude.mobile.data.provider.DirectModelClient = ServiceLocator.directClient
) : ViewModel() {

    private val _ui = MutableStateFlow(GeminiUi())
    val ui: StateFlow<GeminiUi> = _ui

    private val _sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val sessions: StateFlow<List<SessionEntity>> = _sessions

    private var streamJob: Job? = null
    private var pendingId: Long = -1
    private val buffer = StringBuilder()
    private var lastFlush = 0L
    private var lastUserPrompt: String = ""
    private var currentBot: Bot = BotRoster.scout

    init {
        refreshLabels()
        viewModelScope.launch {
            chat.sessions().collect { list ->
                _sessions.value = list
                if (_ui.value.sessionId.isBlank()) {
                    val target = list.firstOrNull() ?: chat.createSession()
                    open(target.id)
                }
            }
        }
        viewModelScope.launch {
            agent.events.collect { handleAgentEvent(it) }
        }
        viewModelScope.launch {
            models.installed.collect { refreshLabels() }
        }
        // The gateway is one of three transports; its events land in the same buffer
        // as the other two so the transcript does not care where an answer came from.
        viewModelScope.launch {
            gateway.events.collect { event -> handleGatewayEvent(event) }
        }
    }

    private fun refreshLabels() {
        val localModel = models.installedModel()
        val cloud = providers.active()
        _ui.value = _ui.value.copy(
            localModelLabel = localModel?.let { "${it.label} · ${it.quant}" },
            cloudModelLabel = cloud?.model ?: "cloud",
            modelLabel = when (_ui.value.mode) {
                ModelMode.LOCAL -> localModel?.label ?: "OpenClaude"
                ModelMode.CLOUD -> cloud?.label ?: "OpenClaude"
                ModelMode.AUTO -> "OpenClaude"
            },
            modeLabel = when (_ui.value.mode) {
                ModelMode.AUTO -> null
                ModelMode.LOCAL -> "on device"
                ModelMode.CLOUD -> "cloud"
            }
        )
    }

    fun setMode(mode: ModelMode) {
        _ui.value = _ui.value.copy(mode = mode)
        refreshLabels()
        Logs.i("ui", "mode -> $mode")
    }

    fun open(sessionId: String) {
        streamJob?.cancel()
        _ui.value = _ui.value.copy(sessionId = sessionId, messages = emptyList(), error = null, notice = null)
        viewModelScope.launch {
            chat.messages(sessionId).collect { msgs ->
                _ui.value = _ui.value.copy(messages = msgs)
            }
        }
    }

    fun newChat() {
        viewModelScope.launch {
            val session = chat.createSession()
            open(session.id)
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            chat.deleteSession(id)
            if (id == _ui.value.sessionId) open(chat.createSession().id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            chat.clearAll()
            open(chat.createSession().id)
        }
    }

    fun selectBot(bot: Bot) {
        currentBot = bot
        _ui.value = _ui.value.copy(notice = "${bot.name} · ${bot.tagline}")
    }

    fun send(text: String, botId: String? = null) {
        val clean = text.trim()
        if (clean.isEmpty() || _ui.value.streaming) return
        if (botId != null) currentBot = BotRoster.byId(botId)

        // The guard runs before anything is stored, sent or routed. A blocked prompt
        // never reaches the transcript, so it cannot be replayed or quoted later.
        val screening = guard.screenPrompt(clean)
        if (screening.blocked) {
            _ui.value = _ui.value.copy(
                error = "Blocked by the safety guard: " +
                    (screening.reasons.firstOrNull() ?: "instruction override attempt"),
                notice = "nothing was sent"
            )
            return
        }

        val sessionId = _ui.value.sessionId.ifBlank { UUID.randomUUID().toString() }
        lastUserPrompt = clean
        buffer.setLength(0)
        lastFlush = 0L
        pendingId = -1

        val route = router.decide(
            prompt = clean,
            mode = _ui.value.mode,
            needsTools = currentBot.tools.isNotEmpty()
        )
        _ui.value = _ui.value.copy(
            streaming = true,
            error = null,
            notice = route.reason
        )

        streamJob = viewModelScope.launch {
            try {
                chat.appendUser(sessionId, clean)
                pendingId = chat.appendAssistantPlaceholder(sessionId)

                when (route) {
                    is ModelRouter.Route.Local -> runLocal(sessionId, clean)
                    is ModelRouter.Route.Cloud -> {
                        when {
                            gateway.connection.value is GatewayClient.Connection.Open ->
                                runGateway(sessionId, clean)
                            // No gateway, no userland, no Node: go straight to the provider
                            // over HTTPS. This is the path that always works.
                            else -> runDirect(sessionId, clean)
                        }
                    }
                }
            } catch (t: Throwable) {
                Logs.e("gemini", "turn failed", t)
                finishTurn(error = t.message ?: "turn failed")
            }
        }
    }

    /**
     * On-device path. The prompt is rebuilt from the transcript each turn because
     * the engine keeps no conversation state of its own; the KV cache covers one
     * generation, not one chat.
     */
    private suspend fun runLocal(sessionId: String, prompt: String) {
        val history = chat.historyFor(sessionId)
            .filter { it.role == MessageEntity.Role.USER || it.role == MessageEntity.Role.ASSISTANT }
            .filter { it.text.isNotBlank() }
            .takeLast(8)
            .map { message ->
                val role = if (message.role == MessageEntity.Role.USER) "user" else "assistant"
                role to message.text
            }

        val system = guard.hardenSystem(currentBot.systemPrompt())
        val fullPrompt = local.buildPrompt(system, history)

        val result = local.generate(fullPrompt, maxTokens = 512) { piece ->
            buffer.append(piece)
            val now = System.currentTimeMillis()
            if (now - lastFlush > 40L) {
                lastFlush = now
                flush(streaming = true)
            }
        }

        result.onFailure { finishTurn(error = it.message ?: "on-device generation failed") }
        result.onSuccess { stats ->
            _ui.value = _ui.value.copy(
                notice = "on device · %.1f tok/s".format(stats.tokensPerSecond)
            )
            finishTurn(error = null)
        }
    }

    /**
     * Direct provider turn: the transport that needs nothing installed.
     */
    private suspend fun runDirect(sessionId: String, prompt: String) {
        val profile = providers.active()
        if (profile == null) {
            finishTurn(error = "no model provider configured")
            return
        }
        val key = providers.keyFor(profile)
        if (key.isNullOrBlank() && profile.kind != com.openclaude.mobile.data.agent.ProviderProfile.Kind.OLLAMA) {
            finishTurn(error = "no API key for ${profile.label} — add one in Settings")
            return
        }

        val history = chat.historyFor(sessionId)
            .filter { it.role == MessageEntity.Role.USER || it.role == MessageEntity.Role.ASSISTANT }
            .filter { it.text.isNotBlank() }
            .takeLast(12)
            .map {
                DirectMessage(
                    role = if (it.role == MessageEntity.Role.USER) "user" else "assistant",
                    content = it.text
                )
            }

        direct.stream(
            profile = profile,
            apiKey = key,
            system = guard.hardenSystem(currentBot.systemPrompt()),
            messages = history
        ).collect { chunk ->
            buffer.append(chunk)
            val now = System.currentTimeMillis()
            if (now - lastFlush > 40L) {
                lastFlush = now
                flush(streaming = true)
            }
        }
        finishTurn(error = null)
    }

    private suspend fun runCloud(sessionId: String, prompt: String) {
        agent.ensureStarted(sessionId)
        agent.send(guard.wrapUser(prompt), sessionId)
    }

    /**
     * Preferred cloud transport: the gateway owns the session, the tool loop and the
     * bot routing, so the app only renders. The reply arrives as events on the socket
     * and is handled by [handleGatewayEvent].
     */
    private fun runGateway(sessionId: String, prompt: String) {
        gateway.sendUserMessage(sessionId, guard.wrapUser(prompt), currentBot.id)
        _ui.value = _ui.value.copy(notice = "${currentBot.name} · via gateway")
    }

    private fun handleGatewayEvent(event: Any) {
        when (event) {
            is GatewayProtocol.Ready -> _ui.value =
                _ui.value.copy(notice = "gateway ${event.gatewayVersion} · ${event.sessions} sessions")

            is GatewayProtocol.Delta -> {
                buffer.append(event.text)
                val now = System.currentTimeMillis()
                if (now - lastFlush > 40L) {
                    lastFlush = now
                    flush(streaming = true)
                }
            }

            is GatewayProtocol.ToolCall -> {
                flush(streaming = true)
                viewModelScope.launch {
                    chat.appendTool(_ui.value.sessionId, event.tool, "running ${event.tool}")
                }
            }

            is GatewayProtocol.ToolResult -> viewModelScope.launch {
                chat.appendTool(
                    _ui.value.sessionId,
                    "result",
                    if (event.ok) event.output else "failed: ${event.output}"
                )
            }

            is GatewayProtocol.Done -> finishTurn(error = null)
            is GatewayProtocol.Failure -> finishTurn(error = event.message)
            else -> Unit
        }
    }

    fun stop() {
        val sessionId = _ui.value.sessionId
        local.cancel()
        viewModelScope.launch {
            runCatching { agent.interrupt(sessionId) }
            finishTurn(error = null)
        }
    }

    fun regenerate() {
        val prompt = lastUserPrompt
        if (prompt.isBlank() || _ui.value.streaming) return
        send(prompt)
    }

    fun feedback(messageId: Long, positive: Boolean) {
        // Local only: no telemetry endpoint, and inventing one would be worse than
        // not having feedback at all.
        Logs.i("feedback", "message=$messageId positive=$positive")
        _ui.value = _ui.value.copy(
            notice = if (positive) "Thanks — noted on this device" else "Noted on this device"
        )
    }

    fun requestAttachment() {
        _ui.value = _ui.value.copy(notice = "attachments land in the next step")
    }

    fun startVoice() {
        _ui.value = _ui.value.copy(notice = "listening…")
    }

    fun voiceUnavailable() {
        _ui.value = _ui.value.copy(notice = "no speech service on this device")
    }

    private fun handleAgentEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.AssistantDelta -> {
                buffer.append(event.text)
                val now = System.currentTimeMillis()
                if (now - lastFlush > 40L) {
                    lastFlush = now
                    flush(streaming = true)
                }
            }

            is AgentEvent.ToolCall -> {
                flush(streaming = true)
                viewModelScope.launch {
                    chat.appendTool(_ui.value.sessionId, event.name, "running ${event.name}")
                }
            }

            is AgentEvent.ToolResult -> viewModelScope.launch {
                chat.appendTool(
                    _ui.value.sessionId,
                    "result",
                    if (event.ok) event.output else "failed: ${event.output}"
                )
            }

            is AgentEvent.Failure -> finishTurn(error = event.message)
            is AgentEvent.TurnDone -> finishTurn(error = null)
            is AgentEvent.AgentSpawn, is AgentEvent.AgentDone, is AgentEvent.Usage -> Unit
        }
    }

    private fun flush(streaming: Boolean) {
        val id = pendingId
        if (id < 0) return
        val snapshot = if (streaming) buffer.toString() else guard.screenOutput(buffer.toString()).text
        viewModelScope.launch { chat.patchAssistant(id, snapshot, streaming) }
    }

    private fun finishTurn(error: String?) {
        flush(streaming = false)
        _ui.value = _ui.value.copy(streaming = false, error = error)
        pendingId = -1
    }

    override fun onCleared() {
        streamJob?.cancel()
        super.onCleared()
    }
}
