package com.openclaude.mobile.ui.gemini

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaude.mobile.ui.theme.Tokens

/**
 * The composer.
 *
 * One pill, three controls, no chrome: attach on the left, the text in the middle,
 * then a single button on the right whose meaning changes with state. Voice when
 * the field is empty, send when it has text, stop while a turn is running. That
 * collapse is the whole point of the shape -- there is never more than one thing
 * to press.
 *
 * Height animates between 56dp and 140dp rather than jumping, because a text field
 * that resizes instantly makes the list above it look like it stuttered.
 */
@Composable
fun GeminiComposer(
    streaming: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask OpenClaude"
) {
    var value by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    val hasText = value.isNotBlank()
    val showStop = streaming
    val showSend = !streaming && hasText

    val buttonColor by animateColorAsState(
        targetValue = when {
            showStop -> Tokens.TextPrimary
            showSend -> Tokens.Accent
            else -> Tokens.Accent
        },
        label = "buttonColor"
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            showStop -> Tokens.Black
            showSend -> Color.White
            else -> Color.White
        },
        label = "iconTint"
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (hasText || streaming) 1f else 1f,
        label = "scale"
    )
    val minHeight by animateDpAsState(
        targetValue = if (value.contains('\n')) 84.dp else Tokens.composerHeight,
        label = "height"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(Tokens.ComposerShape)
            .background(Tokens.ComposerFill)
            .border(1.dp, Tokens.ComposerStroke, Tokens.ComposerShape)
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
                tint = Tokens.TextPrimary,
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
                        color = Tokens.TextTertiary,
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
                    tint = Tokens.TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(Tokens.s1))
        }

        Box(
            modifier = Modifier
                .size(Tokens.composerButton)
                .scale(buttonScale)
                .clip(CircleShape)
                .background(buttonColor)
                .clickable {
                    when {
                        showStop -> onStop()
                        showSend -> {
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
                showStop -> Icon(
                    AppIcons.Stop,
                    contentDescription = "Stop",
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                showSend -> Icon(
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
