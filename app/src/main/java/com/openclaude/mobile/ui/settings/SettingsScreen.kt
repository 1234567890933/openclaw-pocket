package com.openclaude.mobile.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.openclaude.mobile.ui.icons.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.openclaude.mobile.BuildConfig
import com.openclaude.mobile.di.ServiceLocator
import com.openclaude.mobile.runtime.StorageProfile
import com.openclaude.mobile.ui.theme.Amber
import com.openclaude.mobile.ui.theme.Ink
import com.openclaude.mobile.ui.theme.MistDim
import com.openclaude.mobile.ui.vault.DevUnlockScreen

/**
 * Settings.
 *
 * Advanced options are not merely hidden behind a disabled toggle; the entry point
 * does not exist until the vault gate has been passed. Tapping the version string
 * seven times reveals the gate. That is discoverable for an owner and invisible to
 * anyone poking at the UI, and it cannot be reached from a prompt.
 */
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    var tapCount by remember { mutableIntStateOf(0) }
    var showGate by remember { mutableStateOf(false) }
    var showDevGate by remember { mutableStateOf(false) }
    var showDevPanel by remember { mutableStateOf(ServiceLocator.vault.developerUnlocked) }

    var showSafety by remember { mutableStateOf(false) }

    if (showSafety) {
        com.openclaude.mobile.ui.safety.SafetyScreen(onClose = { showSafety = false })
        return
    }

    if (showDevPanel) {
        com.openclaude.mobile.ui.dev.DevPanel(onClose = {
            showDevPanel = false
            if (!ServiceLocator.vault.developerUnlocked) showDevGate = false
        })
        return
    }

    if (showDevGate) {
        com.openclaude.mobile.ui.dev.DevCodeScreen(
            onUnlocked = { showDevGate = false; showDevPanel = true },
            onClose = { showDevGate = false }
        )
        return
    }
    val unlocked = remember { mutableStateOf(ServiceLocator.vault.developerUnlocked) }
    val profiles by ServiceLocator.providerStore.profiles.collectAsState()
    val active by ServiceLocator.providerStore.activeId.collectAsState()

    if (showGate) {
        DevUnlockScreen(
            onUnlocked = {
                unlocked.value = true
                showGate = false
            },
            onClose = { showGate = false }
        )
        return
    }

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
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(AppIcons.ArrowBack, contentDescription = "Back", tint = MistDim)
                }
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFEDF1F7)
                )
            }

            Section("Model providers") {
                profiles.forEach { profile ->
                    SettingRow(
                        title = profile.label,
                        subtitle = if (profile.id == active) "${profile.model}  ·  active" else profile.model,
                        highlight = profile.id == active
                    ) { ServiceLocator.providerStore.setActive(profile.id) }
                }
                SettingRow(
                    title = "Add a provider",
                    subtitle = "OpenAI-compatible, Gemini, Ollama, GitHub Models",
                    highlight = false
                ) { }
            }

            Section("Storage") {
                SettingRow(
                    title = "Install profile",
                    subtitle = ServiceLocator.storageProfile().let { "${it.label} · ~${it.approxBytes / 1_048_576} MB" },
                    highlight = true
                ) {
                    val next = when (ServiceLocator.storageProfile()) {
                        StorageProfile.LITE -> StorageProfile.STANDARD
                        StorageProfile.STANDARD -> StorageProfile.FULL
                        StorageProfile.FULL -> StorageProfile.LITE
                    }
                    ServiceLocator.setStorageProfile(next)
                }
                SettingRow(
                    title = "Runtime size",
                    subtitle = "%.1f MB on disk".format(ServiceLocator.bootstrapManager.diskUsageBytes() / 1_048_576.0),
                    highlight = false
                ) { }
                SettingRow(
                    title = "Clear downloads",
                    subtitle = "Removes cached archives, keeps the installed runtime",
                    highlight = false
                ) { }
            }

            Section("Agent behaviour") {
                SettingRow("Sub-agents", "Up to 3 parallel workers", false) { }
                SettingRow("Tool permissions", "Ask before shell commands", false) { }
                SettingRow("Turn timeout", "10 minutes", false) { }
            }

            if (unlocked.value) {
                Section("Developer") {
                    SettingRow(
                        "Protection",
                        if (ServiceLocator.safetyGuard.enabled.value)
                            "on · prompt injection and jailbreak blocked"
                        else "off · authorised",
                        true
                    ) { showSafety = true }
                    SettingRow("Native core", "hash and stream diagnostics", true) { }
                    SettingRow("Protocol log", "raw JSON stream from the guest CLI", true) { }
                    SettingRow("Reset vault", "wipes API keys, keeps the runtime", false) { }
                }
            }

            Section("About") {
                SettingRow(
                    title = "OpenClaude Mobile",
                    subtitle = "built on the open-source OpenClaude agent (MIT)",
                    highlight = false
                ) {
                    tapCount++
                    if (tapCount >= 7 && !unlocked.value) {
                        tapCount = 0
                        showGate = true
                    }
                }
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MistDim,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .clickable {
                            tapCount++
                            if (tapCount >= 7) {
                                tapCount = 0
                                showDevGate = true
                            }
                        }
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Amber,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    highlight: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlight) Color(0x14E8A33D) else Color(0x0AFFFFFF))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (highlight) Amber else Color(0xFFEDF1F7)
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MistDim)
        }
    }
}
