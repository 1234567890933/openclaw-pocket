# Building

## Requirements

* Android Studio Ladybug or newer, or a command line SDK with platform 35
* JDK 17
* Android NDK 27.0.12077973 (for the native core)
* A physical arm64 device for anything past the UI — the emulator cannot run
  proot reliably

`local.properties` needs `sdk.dir`. There is an example file at the repo root.

## Google sign-in

1. In Google Cloud Console create an OAuth client of type **Web application**.
2. Add your debug SHA-1 as an authorised origin for the Android client if you
   also create one; Credential Manager only needs the web client id.
3. Put the client id in `app/src/main/res/values/strings.xml`:

```xml
<string name="default_web_client_id">1234567890-abc.apps.googleusercontent.com</string>
```

If you skip this the sign-in button reports a failure and the "Use without an
account" path still works. Nothing else in the app depends on Google.

## Runtime bundle

The app expects a release asset per component, named exactly as in
`app/src/main/res/raw/bootstrap_manifest.json`. `scripts/build-runtime-bundle.sh`
builds them and prints the SHA-256 values to paste into the manifest.

Host them anywhere that serves plain HTTPS with `Range` support — GitHub
Releases, S3, Cloudflare R2. Then set `baseUrl` in the manifest.

Until the digests in the manifest are replaced, `Downloader` logs a warning and
skips verification. That is a developer convenience and it is loud on purpose;
never ship it.

## Native build

```bash
./gradlew :app:externalNativeBuildDebug
```

The module compiles as `libocmcore.so` for `arm64-v8a` only. Adding x86_64 would
mean shipping a second userland; not worth it for an emulator-only convenience.

## Release build

1. Create a keystore and a `keystore.properties` (not committed).
2. Wire a `signingConfigs` block in `app/build.gradle.kts`.
3. `./gradlew :app:bundleRelease`

R8 full mode is on. `proguard-rules.pro` keeps the JNI surface and the
serialization models; if you add a `@Serializable` type outside
`data.agent`, extend those rules or it will fail at runtime, not at build time.

## Checks before you tag

```bash
./scripts/verify.sh
```

It runs the lint task, checks that no secret-looking string is committed, and
confirms the manifest digests are not placeholders.
