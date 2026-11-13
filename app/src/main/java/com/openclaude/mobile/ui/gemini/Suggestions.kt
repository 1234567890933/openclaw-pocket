package com.openclaude.mobile.ui.gemini

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.openclaude.mobile.ui.icons.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.openclaude.mobile.ui.theme.EmptyStateLayout
import com.openclaude.mobile.ui.theme.Tokens

/**
 * Suggestion rows for an empty conversation.
 *
 * They are prompts, not marketing copy: each one is a real task the agent can run
 * on a phone. That constraint keeps the list honest -- no "plan my wedding" rows
 * that would fail on a 7B local model.
 */
data class Suggestion(val icon: ImageVector, val text: String, val prompt: String)

val defaultSuggestions: List<Suggestion> = listOf(
    Suggestion(
        AppIcons.Terminal,
        "Explain what changed in my last commit",
        "Look at the git history in my workspace and explain what changed in the most recent commit."
    ),
    Suggestion(
        AppIcons.Code,
        "Write a script to rename these files",
        "Write a small script that renames every file in the workspace to a zero-padded sequence, and show it to me before running it."
    ),
    Suggestion(
        AppIcons.History,
        "Summarise the notes in this folder",
        "Read the notes in my workspace and give me a short summary with the three most important points."
    ),
    Suggestion(
        AppIcons.Camera,
        "Describe an image and extract its text",
        "I am attaching an image. Describe it, then extract any text you can read from it."
    ),
    Suggestion(
        AppIcons.Bulb,
        "Run this locally, no data leaves the phone",
        "Switch to the local model and answer without using the network."
    )
)

@Composable
fun SuggestionList(
    suggestions: List<Suggestion> = defaultSuggestions,
    onPick: (Suggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EmptyStateLayout.suggestionGap)
    ) {
        suggestions.forEach { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(suggestion) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = suggestion.icon,
                    contentDescription = null,
                    tint = Tokens.TextSecondary,
                    modifier = Modifier.size(EmptyStateLayout.suggestionIconSize)
                )
                Spacer(Modifier.width(EmptyStateLayout.suggestionIndent))
                Text(
                    text = suggestion.text,
                    color = Tokens.TextPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
