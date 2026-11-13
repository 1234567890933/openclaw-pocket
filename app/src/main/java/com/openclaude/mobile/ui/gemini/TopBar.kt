package com.openclaude.mobile.ui.gemini

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.openclaude.mobile.ui.icons.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaude.mobile.ui.theme.Tokens

/**
 * Top bar.
 *
 * Order is deliberate and matches what the reference does: navigation, then the
 * thing you are talking to, then the actions that create something new. The model
 * name is the only text with weight, because "which model answered this" is the
 * question a user asks most often on a device that can run two of them.
 */
@Composable
fun GeminiTopBar(
    modelLabel: String,
    modeLabel: String?,
    onMenu: () -> Unit,
    onModelClick: () -> Unit,
    onNewChat: () -> Unit,
    avatarInitial: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.s2, vertical = Tokens.s2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenu, modifier = Modifier.size(Tokens.touchTarget)) {
            Icon(AppIcons.Menu, contentDescription = "Chats", tint = Tokens.TextPrimary)
        }

        Row(
            modifier = Modifier
                .clip(Tokens.ChipShape)
                .clickable { onModelClick() }
                .padding(horizontal = Tokens.s2, vertical = Tokens.s1),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = modelLabel,
                color = Tokens.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(148.dp)
            )
            Icon(
                AppIcons.KeyboardArrowDown,
                contentDescription = "Change model",
                tint = Tokens.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        if (modeLabel != null) {
            Text(
                text = modeLabel,
                color = Tokens.TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.width(Tokens.s2))
        }

        IconButton(onClick = onNewChat, modifier = Modifier.size(Tokens.touchTarget)) {
            Icon(AppIcons.Edit, contentDescription = "New chat", tint = Tokens.TextPrimary)
        }

        Box(
            modifier = Modifier
                .size(Tokens.avatar)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(Tokens.Accent, Tokens.AccentDeep))
                ),
            contentAlignment = Alignment.Center
        ) {
            if (avatarInitial.isNullOrBlank()) {
                Icon(
                    AppIcons.Spark,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            } else {
                Text(
                    text = avatarInitial.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
