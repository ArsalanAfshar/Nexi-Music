package com.nexi.music.presets

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Versioned, modular preset format. Theme bundles contain appearance only; wallpaper is an optional asset. */
object PresetArchive {
    data class Appearance(val accent: Int, val dark: Boolean, val corner: Float, val density: Float, val wallpaperBrightness: Float, val wallpaperBlur: Float, val wallpaperPath: String?)
    data class Imported(val kind: String, val appearance: Appearance?, val settings: JSONObject?, val requiredSettings: List<String>, val wallpaperBytes: ByteArray?)

    fun exportAppearance(context: Context, uri: Uri, appearance: Appearance, wallpaper: ByteArray?, requiredSettings: JSONObject = JSONObject()) {
        val json = JSONObject().apply {
            put("format", "nexi-preset"); put("version", 1); put("kind", "appearance")
            put("appearance", JSONObject().apply {
                put("accent", appearance.accent); put("dark", appearance.dark); put("corner", appearance.corner); put("density", appearance.density)
                put("wallpaperBrightness", appearance.wallpaperBrightness); put("wallpaperBlur", appearance.wallpaperBlur)
                if (wallpaper != null) put("wallpaper", "wallpaper.jpg")
            })
            put("requires", JSONArray().apply { requiredSettings.keys().forEach { put(it) } }); put("requiredSettings", requiredSettings)
        }
        context.contentResolver.openOutputStream(uri)?.use { output -> ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("preset.json")); zip.write(json.toString(2).toByteArray()); zip.closeEntry()
            if (wallpaper != null) { zip.putNextEntry(ZipEntry("wallpaper.jpg")); zip.write(wallpaper); zip.closeEntry() }
        } } ?: error("Cannot write preset file")
    }

    fun exportSettings(context: Context, uri: Uri, values: JSONObject) {
        val root = JSONObject().put("format", "nexi-preset").put("version", 1).put("kind", "settings").put("settings", values)
        context.contentResolver.openOutputStream(uri)?.use { output -> ZipOutputStream(output).use { zip -> zip.putNextEntry(ZipEntry("preset.json")); zip.write(root.toString(2).toByteArray()); zip.closeEntry() } } ?: error("Cannot write settings preset")
    }

    fun read(context: Context, uri: Uri): Imported {
        var manifest: JSONObject? = null; var wallpaper: ByteArray? = null
        context.contentResolver.openInputStream(uri)?.use { input -> ZipInputStream(input).use { zip ->
            var archiveBytes = 0
            var entries = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                entries += 1
                require(entries <= 16) { "Preset has too many files" }
                // Bound individual archive entries to prevent accidental or malicious huge imports.
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = zip.read(chunk)
                    if (count < 0) break
                    total += count
                    archiveBytes += count
                    require(total <= 20 * 1024 * 1024 && archiveBytes <= 24 * 1024 * 1024) { "Preset asset is too large" }
                    buffer.write(chunk, 0, count)
                }
                val bytes = buffer.toByteArray()
                when (entry.name) { "preset.json" -> manifest = JSONObject(bytes.toString(Charsets.UTF_8)); "wallpaper.jpg", "wallpaper.png" -> wallpaper = bytes }
            }
        } } ?: error("Cannot read preset file")
        val root = manifest ?: error("This file is not a Nexi preset")
        require(root.optString("format") == "nexi-preset" && root.optInt("version") == 1) { "Unsupported preset format" }
        val required = root.optJSONArray("requires")?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: emptyList()
        val kind = root.getString("kind")
        require(kind == "appearance" || kind == "settings") { "Unknown preset type" }
        val appearance = if (kind == "appearance") root.getJSONObject("appearance").let {
            Appearance(it.optInt("accent", 0xffc28d70.toInt()), it.optBoolean("dark", false), it.optDouble("corner", 24.0).toFloat(), it.optDouble("density", 1.0).toFloat(), it.optDouble("wallpaperBrightness", .18).toFloat(), it.optDouble("wallpaperBlur", 0.0).toFloat(), it.optString("wallpaper").takeIf(String::isNotBlank))
        } else null
        return Imported(kind, appearance, if (kind == "appearance") root.optJSONObject("requiredSettings") else root.optJSONObject("settings"), required, wallpaper)
    }
}
