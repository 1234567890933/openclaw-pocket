package com.openclaude.mobile.ui.bots

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaude.mobile.bots.Bot
import com.openclaude.mobile.ui.theme.Tokens

/**
 * Bot picker.
 *
 * A single horizontal row of chips, directly under the title bar. It has to be
 * visible without a tap — picking who answers is a per-message decision, not a
 * setting — and it has to stay one row, because a grid of personas is a menu and a
 * menu is where conversation goes to die.
 */
@Composable
fun BotStrip(
    bots: List<Bot>,
    selectedId: String,
    onSelect: (Bot) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Tokens.s2),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Tokens.s4)
    ) {
        items(bots, key = { it.id }) { bot ->
            val selected = bot.id == selectedId
            Row(
                modifier = Modifier
                    .clip(Tokens.ChipShape)
                    .background(if (selected) Tokens.AccentSoft else Color(0x0FFFFFFF))
                    .border(
                        width = 1.dp,
                        color = if (selected) Tokens.Accent else Color(0x1AFFFFFF),
                        shape = Tokens.ChipShape
                    )
                    .clickable { onSelect(bot) }
                    .padding(horizontal = Tokens.s3, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = bot.glyph, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = bot.name,
                    color = if (selected) Tokens.Accent else Tokens.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
