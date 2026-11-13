package com.openclaude.mobile.ui.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import com.openclaude.mobile.ui.icons.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import com.openclaude.mobile.di.ServiceLocator
import com.openclaude.mobile.ui.theme.Palette
import com.openclaude.mobile.util.Logs

/**
 * Developer settings, reachable only through the code gate.
 *
 * Everything here is a control a power user legitimately wants and a normal user
 * should never see: the system prompt, sampling temperature, the raw protocol log,
 * the tool policy and the engine diagnostics.
 *
 * The system prompt field is deliberately blank by default. An empty override means
 * "use the bot's persona"; a filled one replaces it for every bot. That is a real
 * feature — teams tune their agent's voice — and it is also the only honest way to
 * offer this: you can change what the assistant is told, you cannot make a model
 * provider's own policy stop applying.
 */
@Composable
fun DevPanel(onClose: () -> Unit) {
    val vault = ServiceLocator.vault
    var promptOverride by remember { mutableStateOf(vault.prefs.getString("dev.prompt", "").orEmpty()) }
    var showProtocolLog by remember { mutableStateOf(false) }
    var temperature by remember { mutableStateOf(vault.prefs.getFloat("dev.temp", 0.4f)) }
    var verboseLogs by remember { mutableStateOf(Logs.verbose) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.step0)
    ) {
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
                Text("Developer", color = Palette.textHigh, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "These settings change how every bot behaves. They are not a safety switch: " +
                    "a model provider's own policy still applies to every request.",
                color = Palette.textLow,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(20.dp))

            Label("SYSTEM PROMPT OVERRIDE")
            BasicTextField(
                value = promptOverride,
                onValueChange = {
                    promptOverride = it.take(4000)
                    vault.prefs.edit().putString("dev.prompt", promptOverride).apply()
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.textHigh),
                cursorBrush = SolidColor(Palette.blue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.step2)
                    .border(1.dp, Palette.step4, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (promptOverride.isEmpty()) {
                        Text(
                            "empty = use each bot's own persona",
                            color = Palette.textGhost,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    inner()
                }
            )

            Spacer(Modifier.height(20.dp))
            Label("SAMPLING")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("temperature %.2f".format(temperature), color = Palette.textMid,
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                TextButtonish("−") {
                    temperature = (temperature - 0.1f).coerceIn(0f, 1.5f)
                    vault.prefs.edit().putFloat("dev.temp", temperature).apply()
                }
                TextButtonish("+") {
                    temperature = (temperature + 0.1f).coerceIn(0f, 1.5f)
                    vault.prefs.edit().putFloat("dev.temp", temperature).apply()
                }
            }

            Spacer(Modifier.height(16.dp))
            Toggle("Verbose logs", verboseLogs) {
                verboseLogs = it
                Logs.verbose = it
            }
            Toggle("Show raw protocol frames", showProtocolLog) { showProtocolLog = it }

            Spacer(Modifier.height(20.dp))
            Label("DIAGNOSTICS")
            Diagnostic("Device", ServiceLocator.performance.describe())
            Diagnostic("Engine", ServiceLocator.localEngine.describe())
            Diagnostic("Native core", runCatching {
                com.openclaude.mobile.data.secure.NativeHasher.buildFingerprint()
            }.getOrDefault("unavailable"))
            Diagnostic("Tools", ServiceLocator.toolRegistry.specs().joinToString { it.name })

            if (showProtocolLog) {
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Palette.step1)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Logs.snapshot().takeLast(40).forEach { line ->
                        Text(line, color = Palette.textLow, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Palette.red.copy(alpha = 0.12f))
                    .border(1.dp, Palette.red.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    .clickable {
                        ServiceLocator.devGate.relock()
                        onClose()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Lock developer mode", color = Palette.red, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        color = Palette.textLow,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Palette.textHigh, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.step0,
                checkedTrackColor = Palette.blue,
                uncheckedTrackColor = Palette.step3
            )
        )
    }
}

@Composable
private fun Diagnostic(name: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(name, color = Palette.textLow, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(96.dp))
        Text(value, color = Palette.textMid, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TextButtonish(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.step3)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(label, color = Palette.textHigh, style = MaterialTheme.typography.bodyMedium)
    }
}
