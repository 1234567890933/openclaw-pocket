package com.openclaude.mobile.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.openclaude.mobile.di.ServiceLocator
import com.openclaude.mobile.ui.auth.AuthScreen
import com.openclaude.mobile.ui.download.DownloadAllScreen
import com.openclaude.mobile.ui.gemini.GeminiHome
import com.openclaude.mobile.ui.splash.IntroSplash

/**
 * Four steps, walked in order: intro, install, sign in, talk.
 *
 * The step is derived from persisted state on every cold start, so killing the app
 * mid-install resumes at the installer with the queue intact rather than starting
 * over. There is no deep link into a later step: an agent without a runtime is not
 * a useful screen to land on.
 */
private enum class Step { ROOTED, BLOCKED, INTRO, INSTALL, AUTH, CHAT }

@Composable
fun RootScreen() {
    var step by remember { mutableStateOf(Step.INTRO) }
    var signedIn by remember { mutableStateOf(false) }

    val vault = ServiceLocator.vault
    val queue = ServiceLocator.downloadQueue
    val queueState by queue.state.collectAsState()

    LaunchedEffect(Unit) {
        ServiceLocator.providerStore.load()
        signedIn = !vault.signedInEmail.isNullOrBlank()
        // Hardware gate first: below 4 GB of RAM or 64 GB of storage the agent cannot
        // start, and failing here is kinder than failing mid-download.
        if (ServiceLocator.rootGuard.isRooted()) step = Step.ROOTED
        else if (ServiceLocator.deviceGate.check().blocked) step = Step.BLOCKED
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "root"
    ) { current ->
        when (current) {
            Step.ROOTED -> com.openclaude.mobile.ui.gate.RootBlockedScreen()

            Step.BLOCKED -> com.openclaude.mobile.ui.gate.DeviceGateScreen(
                onRetry = {
                    if (!ServiceLocator.deviceGate.check().blocked) step = Step.INTRO
                }
            )

            Step.INTRO -> IntroSplash(onFinished = {
                val installed = queueState.items.isNotEmpty() && queueState.items.all {
                    it.state == com.openclaude.mobile.data.runtime.DownloadQueue.State.DONE ||
                        it.state == com.openclaude.mobile.data.runtime.DownloadQueue.State.SKIPPED
                }
                step = when {
                    !installed -> Step.INSTALL
                    signedIn -> Step.CHAT
                    else -> Step.AUTH
                }
            })

            Step.INSTALL -> DownloadAllScreen(
                onComplete = {
                    vault.runtimeInstalled = true
                    step = if (signedIn) Step.CHAT else Step.AUTH
                }
            )

            Step.AUTH -> AuthScreen(
                onSignedIn = { email ->
                    vault.signedInEmail = email
                    signedIn = true
                    step = Step.CHAT
                },
                onSkip = { step = Step.CHAT }
            )

            Step.CHAT -> GeminiHome()
        }
    }
}
