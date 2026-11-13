# Product roadmap

This is an honest, staged plan for the vision; the current code is an MVP, not yet parity with every established player.

## Shipped foundations

- MediaStore local-library scan, Media3 playback service/session, basic queue playback, mini-player and search.
- Compose appearance controls with persistent accent, dark mode, density, shape, wallpaper, brightness and blur.
- Modular appearance/settings `.nexi` archives, optional bundled wallpaper and opt-in dependency changes.
- ABI-split APK build and signed tag-release workflow.

## Next: library and playback completeness

- Full queue editor, repeat/shuffle, seek/progress, playback speed, sleep timer, resume position, gapless and crossfade controls.
- Playlist CRUD, smart playlists, favorites, folder browser, sorting/grouping, multi-select, recently played/added and robust empty/error states.
- ID3 metadata editing, embedded artwork, artwork cache, album/artist/genre views and duplicate detection.
- Media scan refresh, SAF folder access, exclusions, Android Auto, Bluetooth/headset actions, audio focus, notification permissions and lifecycle tests.
- Equalizer and device-supported audio effects, replay gain, output route/device diagnostics and explicit quality controls (never claim unsupported formats/effects).

## Next: customization depth

- Screen/component arrangement editor, component visibility/order, list/grid modes, type scale/font choices, icon packs, navigation placement, player layouts, gesture mapping and per-screen options.
- Preset schema migration, preview/diff before import, selective module imports, validation, wallpaper format/dimension limits, and golden compatibility tests.
- Accessibility contrast/large type, landscape/tablet/foldable layouts and UI screenshot tests.

## Release readiness

- Run device/emulator tests across API 23–current, ABI install checks, accessibility review, license/dependency audit and reproducible signed-release verification.
- Publish signed APK assets and release notes from annotated version tags; document checksums and supported Android versions.
