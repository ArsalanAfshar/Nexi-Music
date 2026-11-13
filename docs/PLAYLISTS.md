# On-device playlists

Playlists are private local data stored in app preferences. Each playlist has a trimmed, case-insensitively unique name (1–80 printable characters) and an ordered list of positive MediaStore track IDs. The playlist may contain the same track more than once. Tracks that are missing from the current device are skipped when playback starts; the playlist itself is preserved.

## JSON backup format, version 1

Use the Collections screen to export or import a JSON file. Import merges by playlist name (case-insensitive): matching playlists are replaced, unrelated playlists remain. The importer rejects unknown versions, malformed values, duplicate names, invalid IDs, oversized documents, and excessive playlist/track counts before saving.

```json
{
  "format": "nexi-playlists",
  "version": 1,
  "playlists": [
    { "name": "Evening walk", "tracks": [103, 221, 103] }
  ]
}
```

An export is a backup, not a portable audio copy: track IDs are specific to one device's MediaStore catalog. Rebuild or retarget playlists after moving music to another device.
