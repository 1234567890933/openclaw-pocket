package com.openclaude.mobile.ui.gemini

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.openclaude.mobile.ui.icons.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.openclaude.mobile.data.router.ModelMode
import com.openclaude.mobile.ui.theme.Tokens

/**
 * Model picker.
 *
 * Three rows, no nesting, no "advanced". Each row says what it costs in the
 * subtitle -- "no data leaves the phone" or "needs a connection" -- because that
 * is the only distinction a user actually cares about here.
 */
@Composable
fun ModelSheet(
    current: ModelMode,
    localModelLabel: String?,
    cloudModelLabel: String,
    onSelect: (ModelMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.s4, vertical = Tokens.s3),
        verticalArrangement = Arrangement.spacedBy(Tokens.s2)
    ) {
        Text(
            text = "Choose a model",
            color = Tokens.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Tokens.s2)
        )

        ModelRow(
            icon = AppIcons.Bolt,
            title = "Auto",
            subtitle = "Picks on device or cloud per message",
            selected = current == ModelMode.AUTO
        ) { onSelect(ModelMode.AUTO) }

        ModelRow(
            icon = AppIcons.Phone,
            title = "On device",
            subtitle = localModelLabel ?: "No model installed yet — download one first",
            selected = current == ModelMode.LOCAL,
            enabled = localModelLabel != null
        ) { onSelect(ModelMode.LOCAL) }

        ModelRow(
            icon = AppIcons.Cloud,
            title = "Cloud",
            subtitle = "$cloudModelLabel — needs a connection",
            selected = current == ModelMode.CLOUD
        ) { onSelect(ModelMode.CLOUD) }

        Spacer(Modifier.height(Tokens.s4))
    }
}

@Composable
private fun ModelRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(if (selected) Tokens.AccentSoft else Color.Transparent)
            .border(
                1.dp,
                if (selected) Tokens.Accent else Tokens.Divider,
                androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(Tokens.s3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (enabled) Tokens.ComposerFill else Color(0xFF111111)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) Tokens.TextPrimary else Tokens.TextTertiary,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(Tokens.s3))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) Tokens.TextPrimary else Tokens.TextTertiary,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                color = Tokens.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Tokens.Accent)
            )
        }
    }
}
