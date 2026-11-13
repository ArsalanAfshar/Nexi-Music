package com.nexi.music.presets

import android.content.Context
import android.net.Uri
import com.nexi.music.data.WallpaperStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Versioned modular archive; parsing validates all data before any app setting is changed. */
object PresetArchive {
    private const val MAX_ARCHIVE_BYTES = 24 * 1024 * 1024
    private const val MAX_MANIFEST_BYTES = 64 * 1024
    private const val MAX_ENTRIES = 2

    data class Appearance(
        val accent: Int,
        val dark: Boolean,
        val corner: Float,
        val density: Float,
        val wallpaperBrightness: Float,
        val wallpaperBlur: Float,
        val wallpaperPath: String?,
    )

    data class Imported(
        val kind: String,
        val appearance: Appearance?,
        val settings: JSONObject?,
        val requiredSettings: List<String>,
        val wallpaperBytes: ByteArray?,
    )

    fun exportAppearance(context: Context, uri: Uri, appearance: Appearance, wallpaper: ByteArray?, requiredSettings: JSONObject = JSONObject()) {
        val normalizedWallpaper = wallpaper?.let(WallpaperStore::normalizeJpeg)
        val manifest = JSONObject().apply {
            put("format", "nexi-preset")
            put("version", 1)
            put("kind", "appearance")
            put("appearance", JSONObject().apply {
                put("accent", appearance.accent)
                put("dark", appearance.dark)
                put("corner", appearance.corner)
                put("density", appearance.density)
                put("wallpaperBrightness", appearance.wallpaperBrightness)
                put("wallpaperBlur", appearance.wallpaperBlur)
                if (normalizedWallpaper != null) put("wallpaper", "wallpaper.jpg")
            })
            put("requires", JSONArray().apply { requiredSettings.keys().forEach { key -> put(key) } })
            put("requiredSettings", requiredSettings)
        }
        PresetManifestParser.parse(manifest.toString())
        writeArchive(context, uri) { zip ->
            zip.putNextEntry(ZipEntry("preset.json"))
            zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            if (normalizedWallpaper != null) {
                zip.putNextEntry(ZipEntry("wallpaper.jpg"))
                zip.write(normalizedWallpaper)
                zip.closeEntry()
            }
        }
    }

    fun exportSettings(context: Context, uri: Uri, values: JSONObject) {
        val root = JSONObject()
            .put("format", "nexi-preset")
            .put("version", 1)
            .put("kind", "settings")
            .put("settings", values)
        PresetManifestParser.parse(root.toString())
        writeArchive(context, uri) { zip ->
            zip.putNextEntry(ZipEntry("preset.json"))
            zip.write(root.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    fun read(context: Context, uri: Uri): Imported {
        val archiveBytes = context.contentResolver.openInputStream(uri)?.use { readBounded(it, MAX_ARCHIVE_BYTES) }
            ?: error("Android could not open the selected preset")
        val contents = linkedMapOf<String, ByteArray>()
        ZipInputStream(archiveBytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory) { "Preset contains an unexpected folder" }
                require(entry.name in setOf("preset.json", "wallpaper.jpg", "wallpaper.png")) {
                    "Preset contains an unexpected file"
                }
                require(!contents.containsKey(entry.name)) { "Preset contains duplicate files" }
                require(contents.size < MAX_ENTRIES) { "Preset contains too many files" }
                val maxEntryBytes = if (entry.name == "preset.json") MAX_MANIFEST_BYTES else WallpaperStore.MAX_SOURCE_BYTES
                contents[entry.name] = readBounded(zip, maxEntryBytes)
            }
        }
        val manifest = contents["preset.json"] ?: error("Preset manifest is missing")
        val parsed = PresetManifestParser.parse(manifest.toString(Charsets.UTF_8))
        val bundledWallpaperNames = contents.keys.filter { it.startsWith("wallpaper.") }.toSet()
        val expectedWallpaperNames = parsed.wallpaperName?.let { setOf(it) } ?: emptySet()
        require(bundledWallpaperNames == expectedWallpaperNames) {
            "Preset wallpaper configuration and bundled image do not match"
        }
        val wallpaper = parsed.wallpaperName?.let(contents::get)
        wallpaper?.let(WallpaperStore::normalizeJpeg)
        return Imported(parsed.kind, parsed.appearance, parsed.settings, parsed.requiredSettings, wallpaper)
    }

    private fun writeArchive(context: Context, uri: Uri, write: (ZipOutputStream) -> Unit) {
        val output = context.contentResolver.openOutputStream(uri) ?: error("Android could not create the preset file")
        ZipOutputStream(output).use(write)
    }

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                val single = input.read()
                if (single < 0) break
                total += 1
                require(total <= maxBytes) { "Preset file or image exceeds the size limit" }
                output.write(single)
            } else {
                total += count
                require(total <= maxBytes) { "Preset file or image exceeds the size limit" }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    /** Decode at a bounded sample size so malformed dimensions cannot allocate an unbounded bitmap. */
    fun validateWallpaper(bytes: ByteArray) {
        WallpaperStore.normalizeJpeg(bytes)
    }
}
