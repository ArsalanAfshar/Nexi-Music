# `.nexi` preset format (v1)

A `.nexi` file is a ZIP archive with UTF-8 `preset.json` and an optional bundled `wallpaper.jpg` or `wallpaper.png`. The file is data only; it contains no executable code.

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

Settings imports never change appearance. Unknown format versions and unknown kinds are rejected. Keep archives small; the importer limits uncompressed entries to 20 MiB. Future incompatible formats must increment `version` and provide a migration rather than silently reinterpreting data.
