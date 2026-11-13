package com.openclaude.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens for the conversation surface.
 *
 * These were taken from the reference recording rather than invented: a true black
 * canvas, one saturated blue that appears exactly once per screen (the composer
 * button), and a navy wash that only exists behind an empty conversation. Colour
 * is used as position, not decoration: blue means "act", grey means "text".
 *
 * Everything numeric lives here so a screen file never contains a magic dp.
 */
object Tokens {

    // canvas
    val Black = Color(0xFF000000)
    val Canvas = Color(0xFF0A0A0A)
    val WashTop = Color(0xFF000000)
    val WashMid = Color(0xFF04102E)
    val WashBottom = Color(0xFF0B1B4D)

    // surfaces
    val BubbleUser = Color(0xFF2A2A2A)
    val ComposerFill = Color(0xFF1B1B1B)
    val ComposerStroke = Color(0xFF2E2E2E)
    val SheetFill = Color(0xFF141414)
    val Divider = Color(0xFF232323)

    // text
    val TextPrimary = Color(0xFFE8EAED)
    val TextSecondary = Color(0xFF9AA0A6)
    val TextTertiary = Color(0xFF6B7076)

    // accent, used once per screen
    val Accent = Color(0xFF3B6DF6)
    val AccentDeep = Color(0xFF1B4BD8)
    val AccentSoft = Color(0x333B6DF6)

    // semantic
    val Danger = Color(0xFFE0605C)
    val Ok = Color(0xFF5BC48A)
    val Warn = Color(0xFFE8A33D)

    // shape
    val ComposerShape = RoundedCornerShape(28.dp)
    val BubbleShape = RoundedCornerShape(24.dp)
    val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val ChipShape = RoundedCornerShape(50)

    // spacing scale, 4dp base
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp
    val s10 = 40.dp

    // sizes
    val composerHeight = 56.dp
    val composerButton = 40.dp
    val touchTarget = 44.dp
    val avatar = 28.dp
    val avatarSmall = 22.dp

    // type
    val bodySize = 16.sp
    val bodyLine = 25.sp
    val suggestionSize = 15.sp
    val disclaimerSize = 12.sp
}

/**
 * Vertical rhythm for the empty state. Suggestions sit low, not centred: the eye
 * should land on the composer first and drift up into the prompts.
 */
object EmptyStateLayout {
    val suggestionGap = 22.dp
    val suggestionIconSize = 20.dp
    val suggestionIndent = 26.dp
    val bottomFadeHeight = 220.dp
}
