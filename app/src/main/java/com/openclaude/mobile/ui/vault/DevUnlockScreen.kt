package com.openclaude.mobile.ui.vault

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import com.openclaude.mobile.ui.icons.AppIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.openclaude.mobile.di.ServiceLocator
import com.openclaude.mobile.ui.theme.Amber
import com.openclaude.mobile.ui.theme.Danger
import com.openclaude.mobile.ui.theme.Ink
import com.openclaude.mobile.ui.theme.Mist
import com.openclaude.mobile.ui.theme.MistDim

/**
 * The hidden gate.
 *
 * Five algebra answers, each zero padded to nine digits, then a five digit
 * checksum of all forty-five digits. Fifty characters exactly. The phrase is
 * checked in native code against a stored digest; nothing is compared in Kotlin
 * and the expected value is never materialised as a string.
 */
@Composable
fun DevUnlockScreen(
    onUnlocked: () -> Unit,
    onClose: () -> Unit
) {
    val gate = ServiceLocator.gate
    val problem = remember { gate.problem() }
    var phrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF101319), Ink)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onClose) {
                    Icon(AppIcons.Close, contentDescription = "Close", tint = MistDim)
                }
            }

            Text(
                "Developer access",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFEDF1F7)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Advanced settings stay locked. There is no prompt based shortcut.",
                style = MaterialTheme.typography.bodyMedium,
                color = MistDim
            )

            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x0FFFFFFF))
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                problem.lines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = Mist
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = gate.hint(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MistDim
                )
            }

            Spacer(Modifier.height(18.dp))

            BasicTextField(
                value = phrase,
                onValueChange = { phrase = it.filter { c -> c.isDigit() }.take(50) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = Color(0xFFEDF1F7),
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
                ),
                cursorBrush = SolidColor(Amber),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                    .background(Color(0x14FFFFFF))
                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(topStart = 0.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                    .padding(16.dp),
                decorationBox = { inner ->
                    if (phrase.isEmpty()) {
                        Text(
                            "50-character access phrase",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF6E7889)
                        )
                    }
                    inner()
                }
            )

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${phrase.length} / 50",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (phrase.length == 50) Amber else MistDim
                )
                Spacer(Modifier.height(0.dp))
                error?.let {
                    Spacer(Modifier.height(0.dp))
                    Text(
                        text = "   $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = Danger
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    attempts++
                    if (gate.verify(phrase)) {
                        ServiceLocator.vault.developerUnlocked = true
                        onUnlocked()
                    } else {
                        error = "Phrase rejected."
                        phrase = ""
                    }
                },
                enabled = phrase.length == 50,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink)
            ) {
                Text("Unlock")
            }

            if (attempts >= 3) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Failed attempts are logged. Five failures in a session locks the gate until restart.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MistDim
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
