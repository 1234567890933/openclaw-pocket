package com.openclaude.mobile.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.IntOffset

/**
 * Motion, in one place.
 *
 * Two things make app motion feel cheap: everything moving at the same speed, and
 * everything moving at a speed the device cannot sustain. So durations live here,
 * scaled by the device tier, and each transition picks the curve that suits it:
 *
 *   - **enter** decelerates (fast start, soft landing) — the thing arriving
 *   - **exit** accelerates (slow start, quick out) — the thing leaving
 *   - **emphasised** is the long one, used once per screen at most
 *
 * The scale is 0.6 on a low-end phone, which is the difference between "smooth" and
 * "waiting for the animation".
 */
object Motion {

    val enterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val exitEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val standardEasing = FastOutSlowInEasing

    fun enter(scale: Float) = (260 * scale).toInt()
    fun exit(scale: Float) = (180 * scale).toInt()
    fun emphasised(scale: Float) = (420 * scale).toInt()
    fun micro(scale: Float) = (120 * scale).toInt()

    fun springSettle() = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun springPress() = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )
}

/** Provided once at the root so no screen has to look up the device tier. */
val LocalMotionScale = compositionLocalOf { 1f }

@Composable
fun motionScale(): Float = LocalMotionScale.current
