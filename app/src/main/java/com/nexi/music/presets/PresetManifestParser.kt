package com.nexi.music.presets

import org.json.JSONArray
import org.json.JSONObject

/** Pure, strict schema validation shared by imports and local JVM tests. */
object PresetManifestParser {
    private val settingKeys = setOf("crossfadeSeconds", "replayGain", "gapless")
    private val wallpaperNames = setOf("wallpaper.jpg", "wallpaper.png")

    data class Parsed(
        val kind: String,
        val appearance: PresetArchive.Appearance?,
        val settings: JSONObject?,
        val requiredSettings: List<String>,
        val wallpaperName: String?,
    )

    fun parse(json: String): Parsed {
        require(json.toByteArray(Charsets.UTF_8).size <= 64 * 1024) { "Preset manifest is too large" }
        val root = try {
            JSONObject(json)
        } catch (error: Exception) {
            throw IllegalArgumentException("Preset manifest is not valid JSON", error)
        }
        require(root.opt("format") == "nexi-preset") { "This is not a Nexi preset" }
        val version = root.opt("version")
        require(version is Number && version.toDouble() == 1.0) { "Unsupported Nexi preset version" }
        val kindValue = root.opt("kind")
        require(kindValue is String) { "Preset type is missing" }
        val kind = kindValue
        require(kind == "appearance" || kind == "settings") { "Unknown preset type" }

        val requires = parseRequires(root.opt("requires"))
        return if (kind == "appearance") {
            require(!root.has("settings")) { "Appearance presets cannot contain a settings preset payload" }
            val appearanceJson = root.optJSONObject("appearance") ?: error("Appearance settings are missing")
            val appearance = parseAppearance(appearanceJson)
            require(!root.has("requiredSettings") || root.optJSONObject("requiredSettings") != null) {
                "Appearance dependencies must be a settings object"
            }
            val dependencies = root.optJSONObject("requiredSettings") ?: JSONObject()
            validateSettings(dependencies)
            val dependencyKeys = dependencies.keys().asSequence().toList()
            require(dependencyKeys.toSet() == requires.toSet()) {
                "Preset dependency names must exactly match the supplied settings"
            }
            val wallpaperName = if (appearanceJson.has("wallpaper")) {
                val name = appearanceJson.optString("wallpaper")
                require(name in wallpaperNames) { "Preset wallpaper reference is not allowed" }
                name
            } else null
            Parsed(kind, appearance, dependencies, requires, wallpaperName)
        } else {
            require(!root.has("appearance") && !root.has("requiredSettings")) {
                "Settings presets cannot contain appearance data or appearance dependencies"
            }
            require(requires.isEmpty()) { "Settings presets cannot declare appearance dependencies" }
            val settings = root.optJSONObject("settings") ?: error("Settings payload is missing")
            validateSettings(settings)
            Parsed(kind, null, settings, emptyList(), null)
        }
    }

    private fun parseRequires(raw: Any?): List<String> {
        if (raw == null) return emptyList()
        require(raw is JSONArray) { "Preset dependencies must be an array" }
        require(raw.length() <= settingKeys.size) { "Preset declares too many setting dependencies" }
        val values = (0 until raw.length()).map { index ->
            val value = raw.opt(index)
            require(value is String && value in settingKeys) { "Preset declares an unsupported setting dependency" }
            value
        }
        require(values.distinct().size == values.size) { "Preset has duplicate setting dependencies" }
        return values
    }

    private fun parseAppearance(json: JSONObject): PresetArchive.Appearance {
        val accentValue = json.opt("accent")
        require(accentValue is Number) { "Appearance accent color is missing or invalid" }
        val accent = accentValue.toLong()
        require(accentValue.toDouble().isFinite() && accentValue.toDouble() % 1.0 == 0.0) { "Appearance accent color must be a whole number" }
        require(accent in Int.MIN_VALUE.toLong()..0xffffffffL) { "Appearance accent color is out of range" }
        return PresetArchive.Appearance(
            accent = accent.toInt(),
            dark = optionalBoolean(json, "dark", false),
            corner = optionalNumber(json, "corner", 24f, 8f..36f),
            density = optionalNumber(json, "density", 1f, .85f..1.15f),
            wallpaperBrightness = optionalNumber(json, "wallpaperBrightness", .18f, 0f..0.6f),
            wallpaperBlur = optionalNumber(json, "wallpaperBlur", 0f, 0f..24f),
            wallpaperPath = null,
        )
    }

    private fun optionalBoolean(json: JSONObject, key: String, default: Boolean): Boolean {
        if (!json.has(key)) return default
        val value = json.opt(key)
        require(value is Boolean) { "Appearance option '$key' must be true or false" }
        return value
    }

    private fun optionalNumber(json: JSONObject, key: String, default: Float, range: ClosedFloatingPointRange<Float>): Float {
        if (!json.has(key)) return default
        val raw = json.opt(key)
        require(raw is Number) { "Appearance option '$key' must be a number" }
        val value = raw.toDouble()
        require(value.isFinite() && value >= range.start && value <= range.endInclusive) {
            "Appearance option '$key' is outside its supported range"
        }
        return value.toFloat()
    }

    private fun validateSettings(settings: JSONObject) {
        val keys = settings.keys().asSequence().toList()
        require(keys.all { it in settingKeys }) { "Preset contains an unsupported setting" }
        if (settings.has("crossfadeSeconds")) {
            val raw = settings.opt("crossfadeSeconds")
            require(raw is Number && raw.toDouble().isFinite() && raw.toDouble() % 1.0 == 0.0) {
                "crossfadeSeconds must be a whole number"
            }
            require(raw.toInt() in 0..12) { "crossfadeSeconds must be between 0 and 12" }
        }
        listOf("replayGain", "gapless").forEach { key ->
            if (settings.has(key)) require(settings.opt(key) is Boolean) { "$key must be true or false" }
        }
    }
}
