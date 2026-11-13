# Build state — how the APK was produced

Read this before rebuilding. It records the exact configuration that works on a
small machine, so a restart does not have to rediscover it.

## Result

| Artefact | Size | Notes |
|---|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 13 MB | arm64-v8a, minSdk 26, targetSdk 35 |
| `lib/arm64-v8a/libocmcore.so` | 41 KB | SHA-256, constant-time compare, gate; stub inference |

## The machine that built it

2 GB RAM, 2 cores. That is below what a Compose build normally needs, so the build
was arranged around it:

1. **Two modules.** `:core` (no Compose) and `:app` (UI). Each compiles in under
   4m30s on its own.
2. **In-process Kotlin, 760 MB heap.**
   `org.gradle.jvmargs=-Xmx760m -XX:MaxMetaspaceSize=320m -XX:+UseSerialGC`
   plus `kotlin.compiler.execution.strategy=in-process`.
   A separate Kotlin daemon cannot start here; in-process fits.
3. **Sequential stages.** `:core:compileDebugKotlin`, then `:app:compileDebugKotlin`,
   then `:app:assembleDebug`. Gradle caches completed tasks, so a killed run resumes.
4. **No `material-icons-extended`.** A 30 MB jar of 5,000 generated classes was the
   single biggest cost; it is replaced by `AppIcons.kt`.
5. **Local engine off** for this build (`-PocmLocalLlm=OFF`), because the NDK build of
   ggml does not fit in 2 GB. Turn it on with `OCM_LOCAL_LLM=ON` on a real machine.

## Rebuild

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./scripts/resume-build.sh          # retries each stage, survives a restart
```

or, on a normal machine:

```bash
./gradlew :app:assembleDebug -PocmLocalLlm=ON
```

## If it fails again

The failure signature tells you which knob to turn:

* `Gradle build daemon disappeared` — out of RAM. Lower `org.gradle.jvmargs` by 100m.
* `Map failed` — out of address space. Lower `-XX:MaxMetaspaceSize`.
* `Could not connect to Kotlin compile daemon` — harmless; Kotlin falls back to
  in-process, which is what we want anyway.
* `No cached version available for offline mode` — drop `--offline` once.
