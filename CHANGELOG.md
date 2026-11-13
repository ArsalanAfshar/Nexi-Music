# Changelog

## 0.2.0 — Stabilization pre-release

- Run MediaStore scans and preset/wallpaper file work off the UI thread; surface permission and library-query failures with retry actions.
- Synchronize current track/play state with Media3 and correctly disconnect the controller future with the Activity lifecycle; configure audio focus and noisy-route pause behavior.
- Validate preset schema, types, ranges, dependencies, ZIP paths, duplicate entries, sizes and bundled image data before any settings are committed.
- Save preset changes transactionally, preserve unrelated settings, and process wallpapers through bounded image decoding and atomic app-private files.
- Add a bounded local crash log and in-app viewer/copy/clear controls, plus JVM tests for preset validation and log bounds.
- Commit the exact v0.1.0 public test keystore in `signing/`, reuse it across tagged pre-releases, and verify release backups match it; label test key material and update compatibility warnings.

**Test-key notice:** this is a pre-release test build signed with the intentionally public v0.1.0 key. Replacing it with a private production key will require uninstalling these test builds first.

## 0.1.0 — Initial MVP

- Initial Compose Android player shell and local MediaStore library discovery.
- Media3 background playback session, mini-player, search and collections entry points.
- User-controlled accent, light/dark appearance, shape, density and wallpaper adjustments.
- Separate appearance and settings preset export/import with bundled wallpaper and explicit import consent.
- GitHub Actions workflows for Android validation and signed multi-ABI releases.

**Pre-release signing warning:** v0.1.0 APKs use an intentionally public test signing key. The keystore and its passwords are attached to the release for backup, and are not secure. These APKs are test builds, not production software. APKs signed with a replacement private key cannot update this installation; uninstall the test build before installing a differently signed release.

This is an early preview. See `docs/ROADMAP.md` for known gaps.
