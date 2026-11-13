# Build notes — installer fix, root guard, frame cap, PiP

## The error in the screenshot

`Expected URL scheme 'http' or 'https' but no` on Python, Offline toolset, Model index
and the model row.

Cause: `ComponentSpec.resolvedUrl("")` returned just the **file name** when the base
URL was empty, so the downloader was handed `python312-arm64-bundle.tar.gz` as a URL.
OkHttp rejected it — correctly. The model row was worse: it built `"" + ".gguf"`.

Fixed:
* `resolvedUrl` returns an empty string when there is no base URL, instead of a bare
  file name.
* The model catalogue only builds a URL when a model host is configured.
* The queue treats a blank URL as **SKIPPED**, not failed. A red row for a component
  nobody asked for is noise.

## Root guard

`RootGuard` looks for su binaries, test-key builds, a read-write `/system` and Magisk
artefacts, and refuses to run the agent on a rooted device. This is not moralising:
proot rewrites syscalls through ptrace, and on a rooted device with Magisk or a
modified kernel that rewriting can corrupt the guest filesystem instead of failing
cleanly. The screen names the signal it found rather than saying "unsupported".

## 24 fps cap

`FpsLock` calls `Window.setFrameRate(24, FIXED_SOURCE)` reflectively on API 30+. Nothing
in this app is a game; rendering at 90 fps to animate three dots is battery and heat for
no visible benefit. Below API 30 the app leans on shorter animations instead.

## Floating window

Picture-in-picture on user-leave: the conversation stays visible while the user is in
another app. This is the sanctioned form of a floating window — no overlay permission,
no draw-over-other-apps, and the system can still reclaim it.

## Size: still 55 MB, not 150

Reaching 100–150 MB needs another 45–95 MB of *real* payload. The two candidates are
the Debian userland (~102 MB) and a Python runtime (~60 MB). Both are downloaded on
the phone from real URLs, but I could not fetch them into this build: the GitHub API is
rate-limited from here and the proot-distro asset URLs I tried all return 404. Padding
the APK to hit the number is the one thing I will not do.

## Not done this round

Icon redesign, the full animation pass, and the download booster/VPN. The composer
glass, gateway chip and installer copy from the previous build are unchanged.
