package com.openclaude.mobile.ui.dev

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import com.openclaude.mobile.ui.icons.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaude.mobile.di.ServiceLocator
import com.openclaude.mobile.ui.theme.Palette

/**
 * Twelve boxes, one code.
 *
 * A single text field would be simpler to build and worse to use: on a numeric
 * keypad, twelve unbroken digits are impossible to check by eye. Twelve boxes with
 * a filled/unfilled state make a typo obvious before submit.
 *
 * The gate is rate limited in [com.openclaude.mobile.security.DevGate] — five wrong
 * codes and it locks until the process restarts — so this screen never needs a
 * timer or a lockout message of its own.
 */
@Composable
fun DevCodeScreen(
    onUnlocked: () -> Unit,
    onClose: () -> Unit
) {
    val gate = ServiceLocator.devGate
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableStateOf(0) }

    val shake by animateFloatAsState(
        targetValue = if (error != null) 1f else 0f,
        label = "shake"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.step0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 22.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose) {
                    Icon(AppIcons.Close, contentDescription = "Close", tint = Palette.textLow)
                }
            }

            Spacer(Modifier.height(40.dp))

            Text(
                "Developer access",
                color = Palette.textHigh,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Enter the twelve digit code. Five wrong attempts locks this screen until the app restarts.",
                color = Palette.textLow,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(28.dp))

            // Hidden field that drives the twelve boxes.
            BasicTextField(
                value = code,
                onValueChange = {
                    code = it.filter { c -> c.isDigit() }.take(12)
                    error = null
                    if (code.length == 12) {
                        gate.verify(code).fold(
                            onSuccess = { onUnlocked() },
                            onFailure = { failure ->
                                attempts++
                                error = failure.message ?: "rejected"
                                code = ""
                            }
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                cursorBrush = SolidColor(Color.Transparent),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Transparent),
                modifier = Modifier.fillMaxWidth()
            ) { inner ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        repeat(12) { index ->
                            val filled = index < code.length
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (filled) Palette.blueDim else Palette.step2)
                                    .border(
                                        1.dp,
                                        if (filled) Palette.blue else Palette.step4,
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (filled) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Palette.blue)
                                    )
                                }
                            }
                        }
                    }
                    // The invisible field still needs a cursor target.
                    Box(modifier = Modifier.height(1.dp)) { inner() }
                }
            }

            Spacer(Modifier.height(14.dp))

            error?.let {
                Text(
                    text = "$it  ·  ${gate.remainingAttempts()} attempts left",
                    color = Palette.red,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (attempts == 0) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "This unlocks the system prompt, sampling parameters and the protocol log. " +
                        "It does not change what a model provider will accept.",
                    color = Palette.textGhost,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}
