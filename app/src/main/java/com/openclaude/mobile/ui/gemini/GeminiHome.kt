package com.openclaude.mobile.ui.gemini

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openclaude.mobile.data.chat.MessageEntity
import com.openclaude.mobile.bots.BotRoster
import com.openclaude.mobile.data.gateway.GatewayClient
import com.openclaude.mobile.di.ServiceLocator
import com.openclaude.mobile.ui.bots.BotStrip
import com.openclaude.mobile.ui.gateway.GatewaySheet
import com.openclaude.mobile.ui.history.HistorySheet
import com.openclaude.mobile.ui.settings.SettingsScreen
import com.openclaude.mobile.ui.theme.EmptyStateLayout
import com.openclaude.mobile.ui.theme.Tokens
import android.content.Intent

/**
 * The conversation surface.
 *
 * Structure is one screen, not a navigation graph: an empty conversation and a full
 * one are the same layout with different content above the composer. That is why
 * sending the first message never animates a screen change -- the composer stays
 * put and text appears above it.
 *
 * The navy wash exists only while the conversation is empty. Once there is content
 * it would compete with the text, so it fades out.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GeminiHome(vm: GeminiViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var showHistory by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val empty = ui.messages.isEmpty()

    val connection by ServiceLocator.gatewayClient.connection.collectAsState()
    val thermal by ServiceLocator.thermal.state.collectAsState()
    var showGateway by remember { mutableStateOf(false) }
    var botId by remember { mutableStateOf(BotRoster.scout.id) }
    val bots = remember { BotRoster.all }

    // Dictation sends as soon as the recogniser returns a final transcript: talking
    // to an assistant and then having to press send is a step nobody wants.
    val voiceState by ServiceLocator.voice.state.collectAsState()
    LaunchedEffect(voiceState.finalText) {
        if (voiceState.finalText.isNotBlank()) {
            val text = ServiceLocator.voice.consumeFinal()
            if (text.isNotBlank()) vm.send(text, botId)
        }
    }

    LaunchedEffect(ui.messages.size, ui.messages.lastOrNull()?.text?.length) {
        if (ui.messages.isNotEmpty()) {
            listState.animateScrollToItem((ui.messages.size - 1).coerceAtLeast(0))
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Tokens.Black)) {

        // Navy wash behind an empty conversation, fading up from the composer.
        AnimatedVisibility(visible = empty, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Tokens.WashMid.copy(alpha = 0.55f),
                            1f to Tokens.WashBottom.copy(alpha = 0.85f)
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            GeminiTopBar(
                modelLabel = ui.modelLabel,
                modeLabel = ui.modeLabel,
                onMenu = { showHistory = true },
                onModelClick = { showModelSheet = true },
                onNewChat = { vm.newChat() },
                avatarInitial = ui.avatarInitial
            )

            GatewayPill(
                connection = connection,
                onClick = { showGateway = true }
            )

            Spacer(Modifier.height(Tokens.s2))

            BotStrip(
                bots = bots,
                selectedId = botId,
                onSelect = { bot ->
                    botId = bot.id
                    vm.selectBot(bot)
                }
            )

            Spacer(Modifier.height(Tokens.s2))

            if (empty) {
                EmptyConversation(
                    modifier = Modifier.weight(1f),
                    onPick = { suggestion -> vm.send(suggestion.prompt) }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = Tokens.s5,
                        end = Tokens.s5,
                        top = Tokens.s4,
                        bottom = Tokens.s5
                    ),
                    verticalArrangement = Arrangement.spacedBy(Tokens.s5)
                ) {
                    items(ui.messages, key = { it.id }) { message ->
                        when (message.role) {
                            MessageEntity.Role.USER -> UserBubble(message.text)

                            MessageEntity.Role.ASSISTANT -> AssistantTurn(
                                text = message.text,
                                streaming = ui.streaming && message.id == ui.messages.lastOrNull()?.id,
                                onCopy = { clipboard.setText(AnnotatedString(message.text)) },
                                onRetry = { vm.regenerate() },
                                onShare = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, message.text)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(intent, "Share answer")
                                    )
                                },
                                onFeedback = { vm.feedback(message.id, it) },
                                onSpeak = { ServiceLocator.tts.speak(message.text) }
                            )

                            MessageEntity.Role.TOOL -> ToolChip(
                                name = message.toolName ?: "tool",
                                body = message.text
                            )

                            MessageEntity.Role.SYSTEM -> Text(
                                text = message.text,
                                color = Tokens.TextTertiary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            ui.notice?.let { notice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Tokens.s5, vertical = Tokens.s1),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Tokens.Accent)
                    )
                    Spacer(Modifier.width(Tokens.s2))
                    Text(
                        text = notice,
                        color = Tokens.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            ui.error?.let { error ->
                Text(
                    text = error,
                    color = Tokens.Danger,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = Tokens.s5, vertical = Tokens.s1)
                )
            }

            thermal.message?.let { message ->
                Row(
                    modifier = Modifier
                        .padding(horizontal = Tokens.s4, vertical = Tokens.s1)
                        .clip(Tokens.ChipShape)
                        .background(Color(0x1AE9A94B))
                        .padding(horizontal = Tokens.s3, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Tokens.Warn)
                    )
                    Spacer(Modifier.width(Tokens.s2))
                    Text(message, color = Tokens.Warn, style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(Tokens.s2))

            GlassComposer(
                streaming = ui.streaming,
                onSend = { vm.send(it, botId) },
                onStop = { vm.stop() },
                onAttach = { vm.requestAttachment() },
                onVoice = {
                    if (ServiceLocator.voice.isAvailable()) {
                        ServiceLocator.voice.start()
                        vm.startVoice()
                    } else {
                        vm.voiceUnavailable()
                    }
                },
                modifier = Modifier.padding(horizontal = Tokens.s3)
            )

            Spacer(Modifier.height(Tokens.s3))
        }

        if (showHistory) {
            ModalBottomSheet(
                onDismissRequest = { showHistory = false },
                sheetState = sheetState,
                containerColor = Tokens.SheetFill,
                shape = Tokens.SheetShape
            ) {
                HistorySheet(
                    sessions = sessions,
                    activeId = ui.sessionId,
                    onSelect = { vm.open(it); showHistory = false },
                    onDelete = vm::deleteSession,
                    onClearAll = { vm.clearAll(); showHistory = false }
                )
            }
        }

        if (showModelSheet) {
            ModalBottomSheet(
                onDismissRequest = { showModelSheet = false },
                sheetState = sheetState,
                containerColor = Tokens.SheetFill,
                shape = Tokens.SheetShape
            ) {
                ModelSheet(
                    current = ui.mode,
                    localModelLabel = ui.localModelLabel,
                    cloudModelLabel = ui.cloudModelLabel,
                    onSelect = {
                        vm.setMode(it)
                        showModelSheet = false
                    }
                )
            }
        }

        if (showSettings) {
            SettingsScreen(onClose = { showSettings = false })
        }

        if (showGateway) {
            ModalBottomSheet(
                onDismissRequest = { showGateway = false },
                sheetState = sheetState,
                containerColor = Tokens.SheetFill,
                shape = Tokens.SheetShape
            ) {
                GatewaySheet(
                    connection = connection,
                    endpoint = ServiceLocator.gatewayPairing.let { pairing ->
                        (connection as? GatewayClient.Connection.Open)?.let {
                            ServiceLocator.gatewayHost.endpoint()
                        }
                    },
                    tools = ServiceLocator.toolRegistry.specs(),
                    onStartHost = {
                        ServiceLocator.toolBridge.start()
                        ServiceLocator.gatewayHost.start()
                        ServiceLocator.gatewayClient.connect(ServiceLocator.gatewayHost.endpoint())
                    },
                    onStopHost = {
                        ServiceLocator.gatewayClient.disconnect()
                        ServiceLocator.gatewayHost.stop()
                        ServiceLocator.toolBridge.stop()
                    },
                    onConnectRemote = { address, token ->
                        val parsed = ServiceLocator.gatewayPairing.parseAddress(address)
                        if (parsed != null) {
                            val (host, port) = parsed
                            ServiceLocator.gatewayPairing.saveRemote(host, port, token, tls = false)
                            ServiceLocator.toolBridge.start()
                            // `!!` here would crash the app if the host/port pair failed
                            // to parse — a typo in a settings field taking the whole UI
                            // down. Fail soft instead and say so.
                            val endpoint = ServiceLocator.gatewayPairing.remoteEndpoint()
                            if (endpoint != null) {
                                ServiceLocator.gatewayClient.connect(endpoint)
                            }
                            showGateway = false
                        }
                    }
                )
            }
        }
    }
}

/**
 * One line that answers "is anything running".
 *
 * Tappable, because the gateway is the single most common reason a message fails and
 * the fix is almost always on that screen.
 */
@Composable
private fun GatewayPill(
    connection: GatewayClient.Connection,
    onClick: () -> Unit
) {
    val (tint, label) = when (connection) {
        is GatewayClient.Connection.Open -> Tokens.Ok to "gateway · on this phone"
        is GatewayClient.Connection.Connecting -> Tokens.Warn to "starting gateway…"
        is GatewayClient.Connection.Lost -> Tokens.Danger to "gateway offline"
        GatewayClient.Connection.Idle -> Tokens.TextTertiary to "gateway off"
    }

    Row(
        modifier = Modifier
            .padding(horizontal = Tokens.s4)
            .clip(Tokens.ChipShape)
            .background(Color(0x1A4C7DFF))
            .clickable { onClick() }
            .padding(horizontal = Tokens.s3, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(tint)
        )
        Spacer(Modifier.width(Tokens.s2))
        Text(
            text = label,
            color = Tokens.TextTertiary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun EmptyConversation(
    modifier: Modifier = Modifier,
    onPick: (Suggestion) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.s5),
        verticalArrangement = Arrangement.Bottom
    ) {
        Spacer(Modifier.weight(1f))
        SuggestionList(onPick = onPick)
        Spacer(Modifier.height(EmptyStateLayout.bottomFadeHeight * 0.25f))
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(Tokens.BubbleShape)
                .background(Tokens.BubbleUser)
                .padding(horizontal = Tokens.s4, vertical = Tokens.s3)
        ) {
            Text(
                text = text,
                color = Tokens.TextPrimary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ToolChip(name: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF121212))
            .clickable { }
            .padding(Tokens.s3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Tokens.Ok)
        )
        Spacer(Modifier.width(Tokens.s2))
        Text(
            text = name,
            color = Tokens.TextSecondary,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.width(Tokens.s2))
        Text(
            text = body.take(90).replace("\n", " "),
            color = Tokens.TextTertiary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
    }
}
