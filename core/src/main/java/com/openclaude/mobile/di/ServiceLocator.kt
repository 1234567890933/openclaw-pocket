package com.openclaude.mobile.di

import android.content.Context
import androidx.room.Room
import com.openclaude.mobile.data.agent.AgentRepository
import com.openclaude.mobile.data.agent.ProviderStore
import com.openclaude.mobile.data.auth.GoogleAuthClient
import com.openclaude.mobile.data.chat.ChatDatabase
import com.openclaude.mobile.bots.BotSupervisor
import com.openclaude.mobile.perf.DeviceGate
import com.openclaude.mobile.perf.PerformanceProfile
import com.openclaude.mobile.perf.ThermalGovernor
import com.openclaude.mobile.runtime.StorageProfile
import com.openclaude.mobile.security.SafetyGuard
import com.openclaude.mobile.security.DevGate
import com.openclaude.mobile.speech.TtsEngine
import com.openclaude.mobile.speech.VoiceInput
import com.openclaude.mobile.bots.Orchestrator
import com.openclaude.mobile.data.chat.ChatRepository
import com.openclaude.mobile.data.gateway.GatewayClient
import com.openclaude.mobile.data.gateway.GatewayHost
import com.openclaude.mobile.data.gateway.GatewayPairing
import com.openclaude.mobile.tools.ToolBridgeServer
import com.openclaude.mobile.tools.ToolRegistry
import com.openclaude.mobile.data.local.DeviceProfile
import com.openclaude.mobile.data.local.LocalEngine
import com.openclaude.mobile.data.local.LocalModelManager
import com.openclaude.mobile.data.router.ModelRouter
import com.openclaude.mobile.data.runtime.BootstrapManager
import com.openclaude.mobile.data.runtime.DownloadQueue
import com.openclaude.mobile.data.runtime.RuntimePaths
import com.openclaude.mobile.data.secure.PassphraseGate
import com.openclaude.mobile.data.secure.Vault
import com.openclaude.mobile.util.Constants
import com.openclaude.mobile.util.Logs
import java.io.File

/**
 * Hand-rolled service graph.
 *
 * Order matters and is enforced by `by lazy`: the vault must exist before provider
 * keys, the model manager before the router, and the paths before anything that
 * writes to disk. Construction is lazy so a cold start only builds the branch the
 * first screen actually needs.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    val paths: RuntimePaths by lazy { RuntimePaths(appContext) }
    val vault: Vault by lazy { Vault(appContext) }
    val providerStore: ProviderStore by lazy { ProviderStore(vault) }
    val directClient: com.openclaude.mobile.data.provider.DirectModelClient by lazy {
        com.openclaude.mobile.data.provider.DirectModelClient()
    }
    val gate: PassphraseGate by lazy { PassphraseGate() }

    val deviceProfile: DeviceProfile by lazy { DeviceProfile(appContext) }
    val localModelManager: LocalModelManager by lazy { LocalModelManager(appContext) }
    val localEngine: LocalEngine by lazy { LocalEngine(localModelManager, deviceProfile) }
    val modelRouter: ModelRouter by lazy { ModelRouter(localModelManager, deviceProfile) }

    val database: ChatDatabase by lazy {
        Room.databaseBuilder(appContext, ChatDatabase::class.java, "ocm-chat.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(database.chatDao(), database.sessionDao())
    }

    val bootstrapManager: BootstrapManager by lazy {
        BootstrapManager(appContext, paths)
    }

    val downloadQueue: DownloadQueue by lazy {
        DownloadQueue(appContext, paths, modelDir = localModelManager.modelsDir())
    }

    val agentRepository: AgentRepository by lazy {
        AgentRepository(paths, providerStore)
    }

    val authClient: GoogleAuthClient by lazy {
        GoogleAuthClient(appContext)
    }

    // ---------------------------------------------------------------- gateway

    val gatewayPairing: GatewayPairing by lazy { GatewayPairing(vault) }
    val gatewayHost: GatewayHost by lazy { GatewayHost(paths, gatewayPairing) }
    val gatewayClient: GatewayClient by lazy { GatewayClient() }

    // ---------------------------------------------------------------- tools

    val toolRegistry: ToolRegistry by lazy {
        ToolRegistry(File(appContext.filesDir, Constants.DIR_WORK), appContext)
    }

    val toolBridge: ToolBridgeServer by lazy {
        ToolBridgeServer(
            registry = toolRegistry,
            token = gatewayPairing.hostToken(),
            port = Constants.TOOL_BRIDGE_PORT
        )
    }

    // ---------------------------------------------------------------- bots

    val botSupervisor: BotSupervisor by lazy {
        BotSupervisor(maxParallel = performance.parallelBots())
    }

    // ---------------------------------------------------------------- platform

    val performance: PerformanceProfile by lazy { PerformanceProfile(appContext) }
    val deviceGate: DeviceGate by lazy { DeviceGate(appContext) }
    val rootGuard: com.openclaude.mobile.security.RootGuard by lazy { com.openclaude.mobile.security.RootGuard() }
    val soundBank: com.openclaude.mobile.sound.SoundBank by lazy { com.openclaude.mobile.sound.SoundBank(appContext) }
    val thermal: ThermalGovernor by lazy { ThermalGovernor(appContext) }

    /** Chosen once from free space, then editable in Settings. */
    fun storageProfile(): StorageProfile {
        val stored = vault.prefs.getString("storage.profile", null)
        if (stored != null) return StorageProfile.fromName(stored)
        val recommended = StorageProfile.recommend(deviceGate.check())
        vault.prefs.edit().putString("storage.profile", recommended.name).apply()
        return recommended
    }

    fun setStorageProfile(profile: StorageProfile) {
        vault.prefs.edit().putString("storage.profile", profile.name).apply()
        Logs.i("storage", "profile -> ${profile.name}")
    }
    val safetyGuard: SafetyGuard by lazy { SafetyGuard(vault) }
    val devGate: DevGate by lazy { DevGate(vault) }
    val tts: TtsEngine by lazy { TtsEngine(appContext) }
    val voice: VoiceInput by lazy { VoiceInput(appContext) }
    val orchestrator: Orchestrator by lazy { Orchestrator(botSupervisor) }

    fun init(context: Context) {
        appContext = context.applicationContext
        Logs.verbose = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Logs.i("boot", "OpenClaude Mobile starting")
        deviceProfile.log()
        performance.describe().let { Logs.i("perf", it) }
        tts.init()
        thermal.start()
        soundBank.init()
        safetyGuard.load()
        safetyGuard.verifyIntegrity()
        localModelManager.load()
        Logs.i("local", "engine compiled=${localEngine.isCompiled}")
    }

    fun onTrimMemory(level: Int) {
        Logs.d("mem", "onTrimMemory($level)")
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            localEngine.release()
        }
    }
}
