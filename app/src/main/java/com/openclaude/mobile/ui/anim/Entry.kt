package com.openclaude.mobile.ui.anim

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import com.openclaude.mobile.ui.motion.Motion
import com.openclaude.mobile.ui.motion.motionScale

/**
 * Staggered entry, used on every list and every screen.
 *
 * One rule: content rises 12dp and fades in, staggered by index, and nothing bounces.
 * Bounce on entry reads as a toy; a short decelerating rise reads as "this arrived",
 * which is the only thing the animation has to say.
 *
 * The delay is capped, because a list of forty items must not take four seconds to
 * appear — items past the eighth all start together.
 */
@Composable
fun Modifier.entryAnimation(index: Int = 0, maxStagger: Int = 8): Modifier {
    val scale = motionScale()
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val stagger = (index.coerceIn(0, maxStagger) * (28 * scale)).toInt()
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(
            durationMillis = Motion.enter(scale) + stagger,
            easing = Motion.enterEasing
        ),
        label = "entry"
    )

    return this
        .alpha(progress)
        .graphicsLayer {
            translationY = (1f - progress) * 12f.dpToPx()
            scaleX = 0.985f + 0.015f * progress
            scaleY = 0.985f + 0.015f * progress
        }
}

private fun Float.dpToPx(): Float = this * 2.75f
