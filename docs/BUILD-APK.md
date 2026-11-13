# Building the APK

## The short version

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./scripts/build-apk.sh            # -> app/build/outputs/apk/debug/*.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or push a tag and let CI do it:

```bash
git tag v1.0.0 && git push origin v1.0.0
# .github/workflows/release-apk.yml builds and attaches the APK to the release
```

## Requirements

| Thing | Version | Why |
|---|---|---|
| JDK | 17 | AGP 8.7 requires it |
| Android SDK | platform 35, build-tools 35.0.0 | compileSdk |
| NDK | 27.0.12077973 | the native core |
| CMake | 3.22.1 | NDK build |
| RAM | **3 GB free minimum** | Kotlin + Compose compile |

That last row is not a suggestion. The Compose compiler holds a large symbol table
per module and the Kotlin daemon plus Gradle daemon together want roughly 2.5–3 GB
before any native build starts.

## What was actually verified while building this

The repository was assembled in a sandbox with **2 GB of RAM and 2 cores**, and the
build was driven as far as it would go there:

| Stage | Result |
|---|---|
| dependency resolution | passed (after configuring the JVM proxy properties) |
| `:app:processDebugManifest` | passed |
| `:app:mergeDebugResources` / `processDebugResources` | passed |
| `:app:kspDebugKotlin` (Room + serialization codegen) | passed |
| **`:core:compileDebugKotlin`** | **BUILD SUCCESSFUL (4m25s)** |
| `:app:compileDebugKotlin` (Compose) | not reached — needs more RAM than 2 GB |

That last row is the whole reason the project is split into `:core` and `:app`. The
non-UI module compiles completely and cleanly here; the Compose module does not fit,
because the Compose compiler's working set plus Gradle exceeds what the machine has,
and the kernel kills the daemon before it finishes.

So the APK has not been produced in this environment, and this document does not
claim it has. On a 4-core / 16 GB runner the workflow above is the reproducible path
and takes 6–8 minutes.

Three real defects were found and fixed on the way, which is the useful part:

1. **`kotlinx.serialization` plugin was never applied.** Every `@Serializable` type
   failed with `Unresolved reference: serializer`. The dependency was there; the
   compiler plugin was not.
2. **`resourceConfigurations` was at the wrong scope** — it belongs inside
   `defaultConfig`, not `android`.
3. **`Process.pid()` does not exist in the Android API surface** at this compile
   target. Replaced with an identity hash for logging.

## Variants

```
-PocmLocalLlm=ON    # ships the vendored llama.cpp engine (default)
-PocmLocalLlm=OFF   # stub engine, "on-device unavailable" at runtime
```

The stub build is roughly 40% faster and is the right choice when you only want to
work on the UI.

## Release builds

Create `keystore.properties` (gitignored) and wire a `signingConfigs` block, or let
CI sign with a debug key for internal testing. Never commit a keystore.

R8 full mode is on. `proguard-rules.pro` keeps the JNI surface, the serialization
models and the token sink; anything new that is called from C++ must be added there
or it will work in debug and fail in release.
