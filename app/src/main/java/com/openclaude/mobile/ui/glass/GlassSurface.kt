package com.openclaude.mobile.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.openclaude.mobile.ui.theme.Tokens

/**
 * Frosted glass, used in exactly one place: the chat composer.
 *
 * Compose cannot sample what is behind a composable, so a true backdrop blur is not
 * available without a render-effect hack that breaks on older devices. What reads as
 * glass on a phone is not the blur anyway — it is four cheap layers:
 *
 *  1. a translucent white gradient, brighter at the top, so the surface catches light
 *     the way a real pane does
 *  2. a hairline border that is brighter at the top-left and fades to the bottom-right,
 *     which is the specular edge
 *  3. an inner highlight along the top edge
 *  4. a soft drop shadow, so the pane sits above the content rather than in it
 *
 * The rule that keeps it tasteful: one glass surface per screen. Two and the screen
 * looks like a 2010 dashboard.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = Tokens.ComposerShape,
    tint: Color = Color.White,
    elevation: Int = 18,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = elevation.dp,
                    shape = shape,
                    ambientColor = Color(0x66000000),
                    spotColor = Color(0x99000000)
                )
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        0f to tint.copy(alpha = 0.16f),
                        0.45f to tint.copy(alpha = 0.09f),
                        1f to tint.copy(alpha = 0.05f)
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        0f to tint.copy(alpha = 0.42f),
                        0.5f to tint.copy(alpha = 0.14f),
                        1f to tint.copy(alpha = 0.06f)
                    ),
                    shape = shape
                )
        )

        // Specular highlight: a thin bright line hugging the top edge.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        0f to tint.copy(alpha = 0.22f),
                        0.10f to Color.Transparent,
                        1f to Color.Transparent
                    )
                )
        )

        content()
    }
}
