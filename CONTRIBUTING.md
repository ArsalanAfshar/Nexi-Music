# Contributing

Thanks for helping make music listening more personal. Please open an issue before large changes and include the device/API version for Android-specific reports.

## Development

- Use JDK 17, Android SDK 35 and Gradle 8.9.
- Keep playback and library data local unless a feature explicitly needs network access and documents the privacy implications.
- Keep appearance and settings preset modules independent; importing must preview its scope and must never silently modify a dependency.
- Add tests for preset compatibility and playback/library behavior. Verify permission denial and empty-library states.
- Run `gradle testDebugUnitTest assembleDebug` before opening a pull request. CI builds the debug APK.
- Do not commit signing keys, secrets, private music, generated APKs, or local SDK configuration.

Use conventional commit-style summaries where practical. By contributing, you agree your contribution is offered under the repository's Apache-2.0 license.
