# `.nexi` preset format (v1)

A `.nexi` file is a ZIP archive with UTF-8 `preset.json` and an optional bundled `wallpaper.jpg` or `wallpaper.png`. The file is data only; it contains no executable code. The importer accepts only the manifest and one wallpaper asset, caps the compressed archive at 24 MiB, the manifest at 64 KiB, and the image at 20 MiB, and rejects duplicate, unexpected, or path-like entries. Images must decode within bounded dimensions and are re-encoded before being stored. Invalid archives are rejected before preferences are changed.

## Appearance example

```json
{
  "format": "nexi-preset",
  "version": 1,
  "kind": "appearance",
  "appearance": {
    "accent": -4357018,
    "dark": false,
    "corner": 24.0,
    "density": 1.0,
    "wallpaperBrightness": 0.18,
    "wallpaperBlur": 0.0,
    "wallpaper": "wallpaper.jpg"
  },
  "requires": [],
  "requiredSettings": {}
}
```

Appearance imports never apply settings unless the archive declares their keys in `requires`, provides their values in `requiredSettings`, and the user approves the dependency prompt. Wallpaper bytes are stored in the archive, not an external path. An appearance archive without wallpaper doesn't overwrite an unrelated wallpaper asset.

## Settings example

```json
{
  "format": "nexi-preset",
  "version": 1,
  "kind": "settings",
  "settings": { "crossfadeSeconds": 0, "replayGain": false, "gapless": true }
}
```

Settings imports never change appearance and only update keys actually present in the archive. Appearance imports update only visual fields; optional setting dependencies must exactly match `requires` and are applied only after explicit approval. Unknown settings, unknown format versions/kinds, invalid ranges, mismatched wallpaper declarations, malformed images, and unrecognized ZIP entries are rejected before applying changes. Current validated ranges are corner radius 8–36 dp, density 0.85–1.15, wallpaper dim 0–0.6, blur 0–24 dp, and crossfade 0–12 seconds. Future incompatible formats must increment `version` and provide a migration rather than silently reinterpreting data.
