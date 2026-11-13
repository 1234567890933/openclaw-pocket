package com.openclaude.mobile.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.openclaude.mobile.ui.icons.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.openclaude.mobile.data.chat.SessionEntity
import com.openclaude.mobile.ui.theme.Amber
import com.openclaude.mobile.ui.theme.Danger
import com.openclaude.mobile.ui.theme.MistDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Chat list, capped at four sessions by policy.
 *
 * The cap is not a storage limit; it is a UX one. People do not return to old
 * agent transcripts, they return to the last thing they were doing.
 */
@Composable
fun HistorySheet(
    sessions: List<SessionEntity>,
    activeId: String,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val fmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Recent chats",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFEDF1F7),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClearAll) {
                Text("Clear all", color = Danger, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            text = "Only the four most recent chats are kept.",
            style = MaterialTheme.typography.labelSmall,
            color = MistDim
        )
        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sessions, key = { it.id }) { session ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (session.id == activeId) Color(0x1AE8A33D) else Color(0x0FFFFFFF)
                        )
                        .clickable { onSelect(session.id) }
                        .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (session.id == activeId) Amber else Color(0xFFEDF1F7),
                            maxLines = 1
                        )
                        Text(
                            text = fmt.format(Date(session.updatedAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MistDim
                        )
                    }
                    IconButton(onClick = { onDelete(session.id) }) {
                        Icon(
                            AppIcons.Delete,
                            contentDescription = "Delete chat",
                            tint = MistDim
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
    }
}
