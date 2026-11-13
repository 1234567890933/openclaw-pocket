package com.openclaude.mobile.ui.gateway

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import com.openclaude.mobile.data.gateway.GatewayClient
import com.openclaude.mobile.data.gateway.GatewayPairing
import com.openclaude.mobile.tools.ToolSpec
import com.openclaude.mobile.ui.theme.Tokens

/**
 * Where the gateway lives, and what it is allowed to touch.
 *
 * Two modes on one screen because they are the same decision: *this* phone runs the
 * gateway, or *another* device does. The tool list underneath is the part people
 * never get to see in other apps, and it is the part that determines what the
 * assistant can actually do — so it is here, with risk badges, rather than buried.
 */
@Composable
fun GatewaySheet(
    connection: GatewayClient.Connection,
    endpoint: GatewayPairing.Endpoint?,
    tools: List<ToolSpec>,
    onStartHost: () -> Unit,
    onStopHost: () -> Unit,
    onConnectRemote: (String, String) -> Unit
) {
    var address by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.s4, vertical = Tokens.s3),
        verticalArrangement = Arrangement.spacedBy(Tokens.s3)
    ) {
        Text(
            text = "Gateway",
            color = Tokens.TextPrimary,
            style = MaterialTheme.typography.titleMedium
        )

        // status line
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (tint, label) = when (connection) {
                is GatewayClient.Connection.Open ->
                    Tokens.Ok to "connected · v${connection.gatewayVersion.ifBlank { "?" }}"
                is GatewayClient.Connection.Connecting -> Tokens.Warn to "connecting…"
                is GatewayClient.Connection.Lost -> Tokens.Danger to connection.reason
                GatewayClient.Connection.Idle -> Tokens.TextTertiary to "not connected"
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(tint)
            )
            Spacer(Modifier.width(Tokens.s2))
            Text(label, color = Tokens.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            endpoint?.let {
                Text(
                    text = "${it.mode.name.lowercase()} :${it.port}",
                    color = Tokens.TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Text(
            text = "Run the gateway on this phone, or point at one already running on a " +
                "computer on your network. Both use the same token.",
            color = Tokens.TextTertiary,
            style = MaterialTheme.typography.bodySmall
        )

        Button(
            onClick = { if (connection is GatewayClient.Connection.Idle) onStartHost() else onStopHost() },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Tokens.Accent,
                contentColor = Color.White
            )
        ) {
            Text(if (connection is GatewayClient.Connection.Idle) "Start on this phone" else "Stop gateway")
        }

        Text(
            text = "OR CONNECT TO A COMPUTER",
            color = Tokens.TextTertiary,
            style = MaterialTheme.typography.labelSmall
        )

        Field(value = address, placeholder = "192.168.1.20:18789", onChange = { address = it })
        Field(value = token, placeholder = "pairing token", onChange = { token = it }, secret = true)

        Button(
            onClick = { onConnectRemote(address, token) },
            enabled = address.isNotBlank() && token.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1B1B1B),
                contentColor = Tokens.TextPrimary
            )
        ) {
            Text("Connect")
        }

        Spacer(Modifier.height(Tokens.s2))

        Text(
            text = "TOOLS ON THIS PHONE",
            color = Tokens.TextTertiary,
            style = MaterialTheme.typography.labelSmall
        )

        tools.forEach { tool ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F0F0F))
                    .padding(horizontal = Tokens.s3, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tool.title, color = Tokens.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text(tool.name, color = Tokens.TextTertiary, style = MaterialTheme.typography.labelSmall)
                }
                RiskBadge(tool.risk)
            }
        }

        Spacer(Modifier.height(Tokens.s5))
    }
}

@Composable
private fun RiskBadge(risk: ToolSpec.Risk) {
    val (label, tint) = when (risk) {
        ToolSpec.Risk.READ -> "read" to Tokens.Ok
        ToolSpec.Risk.WRITE -> "write" to Tokens.Warn
        ToolSpec.Risk.NETWORK -> "net" to Tokens.Accent
        ToolSpec.Risk.EXEC -> "exec" to Tokens.Danger
    }
    Text(
        text = label,
        color = tint,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, tint.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun Field(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    secret: Boolean = false
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Tokens.TextPrimary),
        cursorBrush = SolidColor(Tokens.Accent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF242424), RoundedCornerShape(12.dp))
            .padding(horizontal = Tokens.s3, vertical = 12.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    text = if (secret) placeholder else placeholder,
                    color = Tokens.TextTertiary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            inner()
        }
    )
}
