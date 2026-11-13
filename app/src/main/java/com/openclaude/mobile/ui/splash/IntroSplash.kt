package com.openclaude.mobile.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openclaude.mobile.ui.theme.Amber
import com.openclaude.mobile.ui.theme.Ink
import com.openclaude.mobile.ui.theme.MistDim
import kotlinx.coroutines.delay

/**
 * Brand moment: the claw mark draws itself, then a slow amber arc sweeps behind it.
 * Short on purpose (about 1.4s) because a splash the user cannot skip is a tax.
 */
@Composable
fun IntroSplash(onFinished: () -> Unit) {
    val drawProgress = remember { Animatable(0f) }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    LaunchedEffect(Unit) {
        drawProgress.animateTo(1f, tween(900, easing = LinearEasing))
        delay(420)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF141821), Ink))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Canvas(modifier = Modifier.size(132.dp)) {
                val w = size.width
                val h = size.height

                // ambient halo
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Amber.copy(alpha = 0.18f * glow), Color.Transparent),
                        center = Offset(w * 0.5f, h * 0.5f),
                        radius = w * 0.62f
                    ),
                    radius = w * 0.62f
                )

                val stroke = Stroke(width = w * 0.055f, cap = StrokeCap.Round)

                // terminal chevron
                drawLine(
                    color = Color(0xFFB7C0D0),
                    start = Offset(w * 0.30f, h * 0.42f),
                    end = Offset(w * 0.44f, h * 0.52f),
                    strokeWidth = stroke.width, cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFB7C0D0),
                    start = Offset(w * 0.44f, h * 0.52f),
                    end = Offset(w * 0.30f, h * 0.62f),
                    strokeWidth = stroke.width, cap = StrokeCap.Round
                )

                // claw strokes, revealed by drawProgress
                val claws = listOf(
                    Triple(Offset(w * 0.60f, h * 0.24f), Offset(w * 0.60f, h * 0.48f), Amber),
                    Triple(Offset(w * 0.72f, h * 0.20f), Offset(w * 0.72f, h * 0.54f), Amber),
                    Triple(Offset(w * 0.84f, h * 0.28f), Offset(w * 0.84f, h * 0.50f), Color(0xFFC77B1F))
                )
                claws.forEachIndexed { index, (start, end, color) ->
                    val local = ((drawProgress.value - index * 0.18f) / 0.64f).coerceIn(0f, 1f)
                    if (local > 0f) {
                        drawLine(
                            color = color,
                            start = start,
                            end = Offset(start.x, start.y + (end.y - start.y) * local),
                            strokeWidth = stroke.width, cap = StrokeCap.Round
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            Text(
                text = "OPENCLAUDE",
                style = MaterialTheme.typography.labelSmall,
                color = Amber,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "agent runtime, pocket sized",
                style = MaterialTheme.typography.bodyMedium,
                color = MistDim,
                textAlign = TextAlign.Center
            )
        }
    }
}
