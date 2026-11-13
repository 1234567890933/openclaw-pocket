package com.openclaude.mobile.ui.gate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.openclaude.mobile.di.ServiceLocator
import com.openclaude.mobile.ui.icons.AppIcons
import com.openclaude.mobile.ui.theme.Palette

/**
 * Shown on a rooted device.
 *
 * The copy names the reason rather than saying "unsupported": a user who rooted
 * their phone deserves to know it is the root, not the phone, and what would change
 * the outcome.
 */
@Composable
fun RootBlockedScreen() {
    val verdict = remember { ServiceLocator.rootGuard.inspect() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1210), Palette.step0)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Palette.red.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Lock, contentDescription = null, tint = Palette.red,
                    modifier = Modifier.size(28.dp))
            }

            Spacer(Modifier.height(24.dp))
            Text("This device is rooted", color = Palette.textHigh,
                style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                "OpenClaw Pocket runs a Linux userland through proot, which rewrites " +
                    "syscalls. On a rooted device with Magisk or a modified kernel that " +
                    "rewriting can corrupt the guest filesystem instead of failing cleanly — " +
                    "so the app stops here rather than damaging the install.",
                color = Palette.textMid,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.step2)
                    .border(1.dp, Palette.step4, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("DETECTED", color = Palette.textLow,
                    style = MaterialTheme.typography.labelSmall)
                verdict.reasons.forEach { reason ->
                    Text("· $reason", color = Palette.textMid,
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Unroot the device, or hide root from this app with a Magisk deny-list " +
                    "entry, then reopen.",
                color = Palette.textGhost,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
