package com.openclaude.mobile.ui.download

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openclaude.mobile.data.runtime.DownloadQueue
import com.openclaude.mobile.ui.theme.Tokens

/**
 * The one-tap installer.
 *
 * One button installs everything. The list underneath is not decoration: a user
 * who waits for 2 GB deserves to know which piece is slow, and a failed mirror
 * should cost one retry, not the whole download.
 */
@Composable
fun DownloadAllScreen(
    onComplete: () -> Unit,
    vm: DownloadAllViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val deviceLine by vm.deviceLine.collectAsState()
    val progress by animateFloatAsState(state.fraction, label = "total")

    LaunchedEffect(state.finished) {
        if (state.finished && !state.hasFailure) onComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Tokens.s5)
    ) {
        Spacer(Modifier.height(Tokens.s8))

        Text(
            text = "Set up OpenClaw Pocket",
            color = Tokens.TextPrimary,
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(Modifier.height(Tokens.s2))
        Text(
            text = "Node and proot are already inside the app. This installs the Linux userland " +
                "and the agent around them. Nothing is sent anywhere while this runs.",
            color = Tokens.TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(Tokens.s6))

        // total progress
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1E1E))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(Tokens.Accent, Tokens.AccentDeep)))
            )
        }
        Spacer(Modifier.height(Tokens.s2))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = vm.summaryLine(state),
                color = Tokens.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(state.fraction * 100).toInt()}%",
                color = Tokens.TextPrimary,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(Modifier.height(Tokens.s4))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Tokens.s2)
        ) {
            items(state.items, key = { it.entry.id }) { item ->
                QueueRow(item)
            }
        }

        Spacer(Modifier.height(Tokens.s3))

        Text(
            text = deviceLine,
            color = Tokens.TextTertiary,
            style = MaterialTheme.typography.labelSmall
        )

        Spacer(Modifier.height(Tokens.s2))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    when {
                        state.hasFailure -> vm.retryFailed()
                        state.running -> vm.pause()
                        else -> vm.startAll()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Tokens.Accent,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = when {
                        state.hasFailure -> "Retry failed"
                        state.running -> "Pause"
                        state.fraction >= 1f -> "Continue"
                        else -> "Download all"
                    },
                    fontWeight = FontWeight.Medium
                )
            }
            if (state.fraction > 0f && state.fraction < 1f && !state.running) {
                Spacer(Modifier.width(Tokens.s2))
                TextButton(onClick = { vm.reset() }) {
                    Text("Reset", color = Tokens.TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(Tokens.s5))
    }
}

@Composable
private fun QueueRow(item: DownloadQueue.Item) {
    val tint = when (item.state) {
        DownloadQueue.State.DONE -> Tokens.Ok
        DownloadQueue.State.FAILED -> Tokens.Danger
        DownloadQueue.State.RUNNING, DownloadQueue.State.VERIFYING -> Tokens.Accent
        else -> Tokens.TextTertiary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F0F0F))
            .border(1.dp, Color(0xFF1C1C1C), RoundedCornerShape(14.dp))
            .padding(horizontal = Tokens.s3, vertical = Tokens.s3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(tint)
        )
        Spacer(Modifier.width(Tokens.s3))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.entry.label,
                color = Tokens.TextPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = item.error ?: item.entry.detail,
                color = if (item.error != null) Tokens.Danger else Tokens.TextTertiary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
            if (item.state == DownloadQueue.State.RUNNING) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E1E))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(item.fraction)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Tokens.Accent)
                    )
                }
            }
        }
        Spacer(Modifier.width(Tokens.s2))
        when (item.state) {
            DownloadQueue.State.RUNNING, DownloadQueue.State.VERIFYING ->
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Tokens.Accent
                )
            DownloadQueue.State.DONE -> Text("done", color = Tokens.Ok, style = MaterialTheme.typography.labelSmall)
            DownloadQueue.State.FAILED -> Text("failed", color = Tokens.Danger, style = MaterialTheme.typography.labelSmall)
            DownloadQueue.State.SKIPPED -> Text("skipped", color = Tokens.TextTertiary, style = MaterialTheme.typography.labelSmall)
            else -> Text("queued", color = Tokens.TextTertiary, style = MaterialTheme.typography.labelSmall)
        }
    }
}
