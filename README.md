# Nexi Music

**An offline-first Android music player built around visual ownership.** Nexi is open source and early in development; this repository contains the first native Android MVP and its release pipeline.

> Status: prototype/MVP — not yet a complete “everything player.” Playback and visual preset foundations are in place; the roadmap documents the remaining work. Contributions and testing on real devices are welcome.

## What works today

- Android 6.0+ (API 23+) project built with Kotlin, Jetpack Compose and Media3.
- Scans the device's music library using Android MediaStore after an explicit audio permission prompt.
- Plays local tracks through a MediaSessionService, with lock-screen/system media controls, mini-player, searchable library and basic collections surfaces.
- Live appearance controls: accent palette, light/dark mode, interface density, corner scale, custom image wallpaper, wallpaper blur and brightness.
- **Separate `.nexi` ZIP presets**: export/import appearance (including optional bundled wallpaper) independently from settings. Every import asks for confirmation. The archive format can declare setting dependencies; the app shows those dependencies and asks before applying them.
- Local-only by default. No account, analytics SDK, or network music service.

The collection, audio-quality and metadata-management screens are intentionally a foundation, not a claim that every planned feature is already implemented. See [the roadmap](docs/ROADMAP.md).

## Build

Requirements: Android Studio (recommended) or Gradle 8.9, JDK 17, Android SDK Platform 35 and Build Tools installed.

```sh
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Open this repository in Android Studio for an emulator or device install. Grant the requested music/audio permission to populate the library. Android 6–12 uses the legacy read permission; Android 13+ uses `READ_MEDIA_AUDIO`.

## Install a release APK

GitHub Releases provide one **universal** APK and smaller architecture-specific APKs when a signed release has been published:

- `Nexi-Music-<version>-universal.apk` — choose this if unsure; works across supported ABIs.
- `Nexi-Music-<version>-arm64-v8a.apk` — most current Android phones/tablets.
- `Nexi-Music-<version>-armeabi-v7a.apk` — older 32-bit ARM devices.
- `Nexi-Music-<version>-x86_64.apk` / `...-x86.apk` — compatible Intel devices and some emulators.

Check **Settings → About phone → Android version / device specifications** or use the universal APK. APKs are signed with the project's release key and can be updated in place only when signed by the same key. Don't uninstall an existing install to switch architecture unless you have backed up its data.

## Release maintainers

Tag a version (`v0.1.0`, for example) to run `.github/workflows/release.yml`. Before the first release, add repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded release keystore (`base64 -w0 release.jks` on Linux).
- `ANDROID_KEY_ALIAS`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD`.

Keep the keystore and passwords offline/backed up. **Never commit them.** The workflow fails if signing credentials are missing; unsigned APKs are not published. The signing key must be retained permanently for upgrade compatibility.

## Preset format

A `.nexi` file is a ZIP containing `preset.json`, and optionally `wallpaper.jpg` or `wallpaper.png`. `kind` is either `appearance` or `settings`. Appearance imports only update visual fields. Settings imports only update settings. Optional `requires` names correspond to values in `requiredSettings`; they are displayed for explicit approval before any such values are applied. See [preset format](docs/PRESETS.md).

## Project

- [Roadmap](docs/ROADMAP.md)
- [Contributing](CONTRIBUTING.md)
- [Changelog](CHANGELOG.md)
- [Security policy](SECURITY.md)
- [License](LICENSE)

Nexi Music is not affiliated with any commercial streaming service. Music remains on your device.
