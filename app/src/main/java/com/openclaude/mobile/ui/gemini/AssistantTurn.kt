package com.openclaude.mobile.ui.gemini

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.openclaude.mobile.ui.icons.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaude.mobile.ui.theme.Tokens

/**
 * One assistant answer.
 *
 * No bubble. Long technical answers in a bubble look like a text message and read
 * badly; the reference puts the text straight on the canvas, and that is the right
 * call for an agent that writes paragraphs and code blocks.
 *
 * The action row only appears once the answer is complete. Offering "copy" while
 * text is still streaming invites a user to copy a half sentence.
 */
@Composable
fun AssistantTurn(
    text: String,
    streaming: Boolean,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    onShare: () -> Unit,
    onFeedback: (Boolean) -> Unit,
    onSpeak: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            AssistantAvatar(streaming)
            Spacer(Modifier.width(Tokens.s3))
            Box(modifier = Modifier.weight(1f)) {
                if (text.isBlank() && streaming) {
                    ShimmerLines()
                } else {
                    RichText(text)
                }
            }
        }

        AnimatedVisibility(visible = !streaming && text.isNotBlank(), enter = fadeIn()) {
            Column {
                Spacer(Modifier.height(Tokens.s3))
                ActionRow(
                    onCopy = onCopy,
                    onRetry = onRetry,
                    onShare = onShare,
                    onFeedback = onFeedback,
                    onSpeak = onSpeak
                )
                Spacer(Modifier.height(Tokens.s2))
                Text(
                    text = "OpenClaude can make mistakes. Check important results.",
                    color = Tokens.TextTertiary,
                    fontSize = Tokens.disclaimerSize
                )
            }
        }
    }
}

@Composable
private fun AssistantAvatar(streaming: Boolean) {
    val transition = rememberInfiniteTransition(label = "avatar")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .size(Tokens.avatarSmall)
            .alpha(if (streaming) pulse else 1f)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(listOf(Tokens.Accent, Tokens.AccentDeep))
            )
    )
}

/**
 * Renders fenced code blocks in monospace and everything else as prose.
 *
 * A full markdown engine would pull in a parser and a layout pass per block; for
 * an agent that mostly emits prose plus fenced code, splitting on the fence is
 * enough and cannot mis-render nested lists.
 */
@Composable
private fun RichText(text: String) {
    val segments = rememberSegments(text)
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.s2)) {
        segments.forEach { segment ->
            when (segment) {
                is Segment.Prose -> Text(
                    text = segment.text,
                    color = Tokens.TextPrimary,
                    fontSize = Tokens.bodySize,
                    lineHeight = Tokens.bodyLine
                )
                is Segment.Code -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(Color(0xFF141414))
                        .padding(Tokens.s3)
                ) {
                    Text(
                        text = segment.text,
                        color = Color(0xFFD7DBE0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

private sealed interface Segment {
    data class Prose(val text: String) : Segment
    data class Code(val text: String) : Segment
}

private fun splitSegments(text: String): List<Segment> {
    if (!text.contains("```")) return listOf(Segment.Prose(text))
    val out = mutableListOf<Segment>()
    var cursor = 0
    while (cursor < text.length) {
        val start = text.indexOf("```", cursor)
        if (start < 0) {
            out += Segment.Prose(text.substring(cursor))
            break
        }
        if (start > cursor) out += Segment.Prose(text.substring(cursor, start))
        val end = text.indexOf("```", start + 3)
        if (end < 0) {
            // unterminated fence while streaming: show the rest as code
            out += Segment.Code(text.substring(start + 3).trimStart('\n'))
            break
        }
        out += Segment.Code(text.substring(start + 3, end).trimStart('\n'))
        cursor = end + 3
    }
    return out.filterNot {
        (it is Segment.Prose && it.text.isBlank()) || (it is Segment.Code && it.text.isBlank())
    }
}

@Composable
private fun rememberSegments(text: String): List<Segment> =
    androidx.compose.runtime.remember(text) { splitSegments(text) }

@Composable
private fun ShimmerLines() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "phase"
    )
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.s2)) {
        listOf(0.92f, 0.74f, 0.48f).forEachIndexed { index, width ->
            val local = ((phase + index * 0.2f) % 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(width)
                    .height(12.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF1E1E1E),
                                Color(0xFF2C2C2C),
                                Color(0xFF1E1E1E)
                            ),
                            startX = -300f + local * 900f,
                            endX = 300f + local * 900f
                        )
                    )
            )
        }
    }
}

@Composable
private fun ActionRow(
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    onShare: () -> Unit,
    onFeedback: (Boolean) -> Unit,
    onSpeak: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.s5)
    ) {
        ActionIcon(AppIcons.ThumbUp, "Good answer") { onFeedback(true) }
        ActionIcon(AppIcons.ThumbDown, "Bad answer") { onFeedback(false) }
        ActionIcon(AppIcons.Refresh, "Regenerate", onRetry)
        ActionIcon(AppIcons.Share, "Share", onShare)
        ActionIcon(AppIcons.Copy, "Copy", onCopy)
        ActionIcon(AppIcons.MoreVert, "More") { }
        ActionIcon(AppIcons.Volume, "Read aloud", onSpeak)
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Tokens.TextSecondary,
            modifier = Modifier.size(19.dp)
        )
    }
}
