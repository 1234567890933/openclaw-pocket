package com.openclaude.mobile.ui.gate

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.openclaude.mobile.di.ServiceLocator
import com.openclaude.mobile.ui.icons.AppIcons
import com.openclaude.mobile.ui.theme.Palette

/**
 * The hardware gate.
 *
 * Shown instead of the app when the device cannot host the agent: under 4 GB of RAM,
 * under 64 GB of free storage, or not arm64. It says exactly which requirement
 * failed and by how much, because "not supported" with no number is the most
 * frustrating screen an app can show.
 */
@Composable
fun DeviceGateScreen(onRetry: () -> Unit) {
    val report = remember { ServiceLocator.deviceGate.check() }
    val description = remember { ServiceLocator.deviceGate.describe(report) }

    val pulse = rememberInfiniteTransition(label = "gate")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF12151C), Palette.step0)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .alpha(glow)
                    .clip(CircleShape)
                    .background(Palette.amber.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = AppIcons.Phone,
                    contentDescription = null,
                    tint = Palette.amber,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(26.dp))

            Text(
                text = "This phone is below the minimum",
                color = Palette.textHigh,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "OpenClaw Pocket runs a Linux userland and an agent on the device. " +
                    "This one is below the absolute floor — it would be killed during " +
                    "startup, so it stops here instead of failing halfway through a download.",
                color = Palette.textMid,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Palette.step2)
                    .border(1.dp, Palette.step4, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Requirement("RAM", "%.1f GB".format(report.ramBytes / 1_073_741_824.0), report.ramOk)
                Requirement("Free storage", "%.0f GB".format(report.storageBytes / 1_073_741_824.0), report.storageOk)
                Requirement("CPU", report.abi, report.abiOk)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = description,
                color = Palette.textGhost,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(28.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(Palette.blue)
                    .clickable(onClick = onRetry),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Check again", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Free up storage and try again, or use a phone with 3 GB of usable RAM or more.",
                color = Palette.textLow,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun Requirement(label: String, value: String, ok: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (ok) Palette.green else Palette.red)
        )
        Spacer(Modifier.width(10.dp))
        Text(label, color = Palette.textMid, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        Text(value, color = if (ok) Palette.textHigh else Palette.red,
            style = MaterialTheme.typography.bodySmall)
    }
}
