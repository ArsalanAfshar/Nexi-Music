package com.nexi.music.presets

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PresetManifestParserTest {
    @Test fun acceptsAppearanceOnlyWithoutTouchingSettings() {
        val parsed = PresetManifestParser.parse(
            """{"format":"nexi-preset","version":1,"kind":"appearance","appearance":{"accent":-4362138,"dark":true,"corner":20,"density":1.1,"wallpaperBrightness":0.3,"wallpaperBlur":4}}""",
        )
        assertEquals("appearance", parsed.kind)
        assertEquals(20f, parsed.appearance?.corner)
        assertNull(parsed.settings)
        assertEquals(emptyList<String>(), parsed.requiredSettings)
    }

    @Test fun acceptsOnlyExplicitlyDeclaredDependencies() {
        val parsed = PresetManifestParser.parse(
            """{"format":"nexi-preset","version":1,"kind":"appearance","appearance":{"accent":-1},"requires":["gapless"],"requiredSettings":{"gapless":false}}""",
        )
        assertEquals(listOf("gapless"), parsed.requiredSettings)
        assertEquals(false, parsed.settings?.getBoolean("gapless"))
    }

    @Test fun rejectsPathTraversalWallpaperNames() {
        val json = """{"format":"nexi-preset","version":1,"kind":"appearance","appearance":{"accent":-1,"wallpaper":"../wallpaper.jpg"}}"""
        assertThrows(IllegalArgumentException::class.java) { PresetManifestParser.parse(json) }
    }

    @Test fun rejectsUnsupportedVersionAndMalformedJson() {
        assertThrows(IllegalArgumentException::class.java) {
            PresetManifestParser.parse("""{"format":"nexi-preset","version":2,"kind":"settings","settings":{}}""")
        }
        assertThrows(IllegalArgumentException::class.java) { PresetManifestParser.parse("not json") }
    }

    @Test fun rejectsOutOfRangeAppearanceAndUnknownSettings() {
        val badAppearance = """{"format":"nexi-preset","version":1,"kind":"appearance","appearance":{"accent":-1,"density":8}}"""
        assertThrows(IllegalArgumentException::class.java) { PresetManifestParser.parse(badAppearance) }
        val badSettings = JSONObject()
            .put("format", "nexi-preset")
            .put("version", 1)
            .put("kind", "settings")
            .put("settings", JSONObject().put("wipeEverything", true))
        assertThrows(IllegalArgumentException::class.java) { PresetManifestParser.parse(badSettings.toString()) }
    }

    @Test fun validatesPartialSettingsWithoutInventingMissingValues() {
        val parsed = PresetManifestParser.parse(
            """{"format":"nexi-preset","version":1,"kind":"settings","settings":{"crossfadeSeconds":6}}""",
        )
        assertEquals(6, parsed.settings?.getInt("crossfadeSeconds"))
        assertNull(parsed.appearance)
    }
}
