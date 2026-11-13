# Repository setup

Name: **openclaw-pocket**

```bash
# 1. create the repo (needs the GitHub CLI, or use the web UI)
gh repo create openclaw-pocket --public --description "OpenClaw gateway, hosted on the phone"

# 2. push
git init
git add .
git commit -m "OpenClaw Pocket 1.0.0 — gateway on the phone, tool belt, bots, TTS"
git branch -M main
git remote add origin git@github.com:<you>/openclaw-pocket.git
git push -u origin main

# 3. tag it — this is what builds the APK
git tag v1.0.0
git push origin v1.0.0
```

The `release-apk` workflow runs on the tag, builds on a 4-core / 16 GB runner and
attaches `openclaw-pocket-v1.0.0-app-debug.apk` to the release. Download it from the
release page, or from the run's Artifacts section.

## Local alternative

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./scripts/build-apk.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
