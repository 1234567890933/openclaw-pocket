package com.openclaude.mobile.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openclaude.mobile.R
import com.openclaude.mobile.di.ServiceLocator
import com.openclaude.mobile.ui.theme.Amber
import com.openclaude.mobile.ui.theme.Danger
import com.openclaude.mobile.ui.theme.Ink
import com.openclaude.mobile.ui.theme.MistDim
import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.launch
import java.security.SecureRandom

/**
 * Sign-in screen.
 *
 * Google sign-in is offered, not required: the agent itself runs locally, so
 * forcing an account would be a data grab. The token is used to keep provider
 * preferences tied to a person across reinstalls.
 */
@Composable
fun AuthScreen(
    onSignedIn: (String) -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val clientId = stringResource(R.string.default_web_client_id)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF141821), Ink)))
            .padding(26.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_app_mark),
                contentDescription = null,
                tint = Amber,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.auth_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFEDF1F7)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.auth_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MistDim
            )
            Spacer(Modifier.height(30.dp))

            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        val result = ServiceLocator.authClient.signInLocal("owner")
                        busy = false
                        result.onSuccess { onSignedIn(it.email) }
                            .onFailure { error = it.message ?: "could not create a profile" }
                    }
                },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 26.dp, bottomEnd = 26.dp, bottomStart = 26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF2F4F8), contentColor = Ink)
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Ink)
                } else {
                    Text("Continue", fontWeight = FontWeight.SemiBold)
                }
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = Danger)
            }

            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.auth_skip), color = MistDim)
            }
        }
    }
}
