package com.openclaude.mobile.ui.safety

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.openclaude.mobile.di.ServiceLocator
import com.openclaude.mobile.ui.icons.AppIcons
import com.openclaude.mobile.ui.theme.Palette

/**
 * The protection layer, in one screen.
 *
 * Reachable only from the developer section, which is itself behind the twelve digit
 * code. Nothing here is mentioned in the chat surface, the model never sees it, and
 * there is no tool that can reach it.
 *
 * The maintenance field is intentionally the only control on the page. Turning the
 * guard *on* is a button; turning it *off* needs a passphrase that is not printed
 * anywhere — not in this UI, not in the repository, not in the build.
 */
@Composable
fun SafetyScreen(onClose: () -> Unit) {
    val guard = ServiceLocator.safetyGuard
    val enabled by guard.enabled.collectAsState()
    val blocked by guard.blockedCount.collectAsState()
    val lastReason by guard.lastReason.collectAsState()

    var passphrase by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Palette.step0)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(AppIcons.ArrowBack, contentDescription = "Back", tint = Palette.textMid)
                }
                Text("Protection", color = Palette.textHigh,
                    style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(16.dp))

            // status card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (enabled) Color(0x1A57C98C) else Color(0x1AE2685F))
                    .border(1.dp,
                        if (enabled) Palette.green.copy(alpha = 0.5f) else Palette.red.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (enabled) Palette.green else Palette.red)
                )
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        if (enabled) "Protection is on" else "Protection is off",
                        color = Palette.textHigh,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (enabled) "Prompt injection and jailbreak attempts are blocked before they reach a model."
                        else "Only an authorised passphrase can turn this off.",
                        color = Palette.textMid,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Label("WHAT IT DOES")
            Bullet("Screens every message for instruction-override, fake system tags, hidden characters and encoded payloads.")
            Bullet("Separates your text from the assistant's instructions with a per-session marker the text cannot forge.")
            Bullet("Refuses a request outright when it asks for weapons, drugs, malware or harm — whatever the framing.")
            Bullet("Redacts credentials from answers before they are shown or read aloud.")
            Bullet("Fails closed: tampering with its settings turns it back on.")

            Spacer(Modifier.height(18.dp))
            Label("ATTEMPTS BLOCKED")
            Text("$blocked this session", color = Palette.textHigh,
                style = MaterialTheme.typography.bodyMedium)
            lastReason?.let {
                Text("last: $it", color = Palette.textLow,
                    style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(22.dp))
            Label("MAINTENANCE")

            if (enabled) {
                Text(
                    "Turning protection off requires a passphrase that is not stored in this " +
                        "app and cannot be recovered. If you do not have it, the guard stays on — " +
                        "which is the intended behaviour.",
                    color = Palette.textLow,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                BasicTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it.take(64); message = null },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.textHigh),
                    cursorBrush = SolidColor(Palette.blue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Palette.step2)
                        .border(1.dp, Palette.step4, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    decorationBox = { inner ->
                        if (passphrase.isEmpty()) {
                            Text("maintenance passphrase", color = Palette.textGhost,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        inner()
                    }
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(Palette.step3)
                        .border(1.dp, Palette.step4, RoundedCornerShape(23.dp))
                        .clickable {
                            message = if (guard.disable(passphrase)) {
                                passphrase = ""
                                "Protection disabled."
                            } else {
                                "Passphrase rejected."
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Disable protection", color = Palette.red,
                        style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(Palette.blue)
                        .clickable { guard.enable() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Turn protection back on", color = Color.White,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }

            message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Palette.textMid, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Palette.textLow, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text("·", color = Palette.blue, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(8.dp))
        Text(text, color = Palette.textMid, style = MaterialTheme.typography.bodySmall)
    }
}
