package com.openclaude.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colour grading, second pass.
 *
 * The rule the first pass got wrong: glass only works on a dark, slightly blue
 * base. On white it turns grey and dirty, because there is nothing behind it to
 * catch light. So the palette is built dark-first and the light theme does not use
 * glass at all — it uses flat surfaces with a hairline border, which is what
 * actually reads as "clean" on a bright screen.
 *
 * Every surface colour is a step on one ramp (0 → 5). Screens pick a step, never an
 * ad-hoc hex value, which is what keeps eleven screens looking like one app.
 */
object Palette {

    // Base ramp: near-black with a blue bias so amber and blue both sit on it
    // without vibrating.
    val step0 = Color(0xFF07080B)   // app background
    val step1 = Color(0xFF0C0E13)   // raised background
    val step2 = Color(0xFF13161D)   // cards
    val step3 = Color(0xFF1A1E27)   // pressed / selected
    val step4 = Color(0xFF232834)   // borders on strong surfaces
    val step5 = Color(0xFF2E3543)   // dividers on glass

    // Text ramp
    val textHigh = Color(0xFFF2F4F8)
    val textMid = Color(0xFFA8AFBD)
    val textLow = Color(0xFF6C7484)
    val textGhost = Color(0xFF474D5A)

    // Accents. Blue is action, amber is the agent's own voice, green is success.
    val blue = Color(0xFF4C7DFF)
    val blueDeep = Color(0xFF2A56D6)
    val blueDim = Color(0x334C7DFF)
    val amber = Color(0xFFE9A94B)
    val amberDeep = Color(0xFFB87A22)
    val green = Color(0xFF57C98C)
    val red = Color(0xFFE2685F)
    val violet = Color(0xFF9B84FF)

    // Glass, tuned for step0 behind it. Alpha values were picked by looking at the
    // result over the darkest and lightest content the app has, not by taste.
    val glassTop = Color(0x1FFFFFFF)
    val glassMid = Color(0x14FFFFFF)
    val glassBottom = Color(0x0AFFFFFF)
    val glassEdgeBright = Color(0x59FFFFFF)
    val glassEdgeDim = Color(0x0DFFFFFF)
    val glassShadow = Color(0xCC000000)

    // Light theme: flat, no glass.
    val lightBg = Color(0xFFF7F8FA)
    val lightSurface = Color(0xFFFFFFFF)
    val lightBorder = Color(0xFFE3E6EC)
    val lightText = Color(0xFF111318)
    val lightTextMid = Color(0xFF5A616F)
}
