package com.openclaude.mobile.data.router

import com.openclaude.mobile.data.local.DeviceProfile
import com.openclaude.mobile.data.local.LocalModelManager
import com.openclaude.mobile.util.Logs

/**
 * Decides local vs cloud for each turn.
 *
 * The policy is deliberately boring and explainable, because a router that
 * surprises you is worse than no router:
 *
 *  1. An explicit user choice always wins.
 *  2. A turn too large for the local context goes to the cloud.
 *  3. A turn that needs tools or a shell goes to the cloud: local models on a
 *     phone are unreliable at tool calling, and a wrong shell command is not a
 *     cosmetic failure.
 *  4. Otherwise local, if a model is installed and the device can hold it.
 *
 * Every decision is logged with its reason so the UI can show "why".
 */
class ModelRouter(
    private val models: LocalModelManager,
    private val device: DeviceProfile
) {

    sealed interface Route {
        val reason: String

        data class Local(val modelId: String, override val reason: String) : Route
        data class Cloud(val providerId: String, override val reason: String) : Route
    }

    fun decide(prompt: String, mode: ModelMode, needsTools: Boolean = false): Route {
        val installed = models.installedModel()

        return when {
            mode == ModelMode.LOCAL && installed != null ->
                Route.Local(installed.id, "you chose on device")

            mode == ModelMode.LOCAL && installed == null ->
                Route.Cloud(
                    "cloud",
                    "on-device requested but no model is installed"
                )

            mode == ModelMode.CLOUD ->
                Route.Cloud("cloud", "you chose cloud")

            needsTools ->
                Route.Cloud("cloud", "this task needs tools, which run in the cloud path")

            prompt.length > LOCAL_CONTEXT_CHARS && installed != null ->
                Route.Cloud("cloud", "prompt is larger than the on-device context")

            installed == null ->
                Route.Cloud("cloud", "no local model installed")

            !device.canRun(installed.sizeBytes) ->
                Route.Cloud("cloud", "device memory is too small for ${installed.id}")

            else ->
                Route.Local(installed.id, "fits on device, no tools needed")
        }.also { Logs.d("router", "route=${it::class.simpleName} :: ${it.reason}") }
    }

    companion object {
        /** Roughly 4000 characters of prompt fits a 4k-token window with headroom. */
        const val LOCAL_CONTEXT_CHARS = 4000
    }
}
