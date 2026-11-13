package com.openclaude.mobile.ui.gemini

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import com.openclaude.mobile.ui.icons.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaude.mobile.ui.glass.GlassSurface
import com.openclaude.mobile.ui.theme.Tokens

/**
 * The chat composer — the only glass in the app.
 *
 * Same three controls as the flat version, on a frosted pane: attach, text, and one
 * button that is voice, then send, then stop. The glass is the whole visual idea, so
 * nothing else on the screen competes for it: no second translucent card, no gradient
 * behind the text.
 */
@Composable
fun GlassComposer(
    streaming: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask OpenClaw"
) {
    var value by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val hasText = value.isNotBlank()

    val buttonColor by animateColorAsState(
        targetValue = if (streaming) Color(0xFFEDEFF2) else Tokens.Accent,
        label = "glassButton"
    )
    val iconTint by animateColorAsState(
        targetValue = if (streaming) Tokens.Black else Color.White,
        label = "glassIcon"
    )

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = Tokens.ComposerShape,
        tint = Color(0xFF9DBBFF)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Tokens.composerHeight)
                .padding(start = Tokens.s4, end = Tokens.s2, top = Tokens.s2, bottom = Tokens.s2),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .size(Tokens.touchTarget)
                    .clip(CircleShape)
                    .clickable { onAttach() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    AppIcons.Add,
                    contentDescription = "Attach",
                    tint = Tokens.TextPrimary.copy(alpha = 0.9f),
                    modifier = Modifier.size(24.dp)
                )
            }

            BasicTextField(
                value = value,
                onValueChange = { value = it.take(8000) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Tokens.composerHeight - Tokens.s4)
                    .padding(horizontal = Tokens.s3, vertical = 14.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = Tokens.TextPrimary,
                    fontSize = Tokens.bodySize
                ),
                cursorBrush = SolidColor(Tokens.Accent),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Tokens.TextPrimary.copy(alpha = 0.45f),
                            fontSize = Tokens.bodySize
                        )
                    }
                    inner()
                }
            )

            if (!hasText && !streaming) {
                Box(
                    modifier = Modifier
                        .size(Tokens.touchTarget)
                        .clip(CircleShape)
                        .clickable { onVoice() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        AppIcons.Mic,
                        contentDescription = "Voice input",
                        tint = Tokens.TextPrimary.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(Tokens.s1))
            }

            Box(
                modifier = Modifier
                    .size(Tokens.composerButton)
                    .clip(CircleShape)
                    .background(buttonColor)
                    .clickable {
                        when {
                            streaming -> onStop()
                            hasText -> {
                                onSend(value)
                                value = ""
                                keyboard?.hide()
                            }
                            else -> onVoice()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    streaming -> Icon(
                        AppIcons.Stop,
                        contentDescription = "Stop",
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                    hasText -> Icon(
                        AppIcons.Send,
                        contentDescription = "Send",
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                    else -> Icon(
                        AppIcons.Mic,
                        contentDescription = "Voice input",
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
