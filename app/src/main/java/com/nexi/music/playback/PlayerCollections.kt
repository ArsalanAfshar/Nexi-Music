package com.nexi.music.playback

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_PLAYLISTS = 500
private const val MAX_TRACKS_PER_PLAYLIST = 50_000
private const val MAX_PLAYLIST_NAME_LENGTH = 80

/** A named, ordered local playlist; IDs point to MediaStore tracks on this device. */
data class SavedPlaylist(val name: String, val trackIds: List<Long>)

/** Strict, versioned JSON codec used by local persistence and explicit playlist import/export. */
object PlaylistCodec {
    fun encode(playlists: List<SavedPlaylist>): String {
        require(playlists.size <= MAX_PLAYLISTS) { "Too many playlists" }
        val normalized = playlists.map { it.copy(name = validateName(it.name)) }
        require(normalized.map { it.name.lowercase() }.distinct().size == normalized.size) { "Playlist names must be unique" }
        normalized.forEach { require(it.trackIds.size <= MAX_TRACKS_PER_PLAYLIST && it.trackIds.all { id -> id > 0 }) { "Playlist contains invalid tracks" } }
        val array = JSONArray()
        normalized.forEach { playlist ->
            array.put(JSONObject().put("name", playlist.name).put("tracks", JSONArray().apply { playlist.trackIds.forEach(::put) }))
        }
        return JSONObject().put("format", "nexi-playlists").put("version", 1).put("playlists", array).toString()
    }

    fun decode(json: String): List<SavedPlaylist> {
        require(json.toByteArray(Charsets.UTF_8).size <= 8 * 1024 * 1024) { "Playlist file is too large" }
        val root = try { JSONObject(json) } catch (error: Exception) { throw IllegalArgumentException("Playlist file is not valid JSON", error) }
        require(root.opt("format") == "nexi-playlists" && root.opt("version") == 1) { "Unsupported playlist file format" }
        val array = root.optJSONArray("playlists") ?: throw IllegalArgumentException("Playlist list is missing")
        require(array.length() <= MAX_PLAYLISTS) { "Playlist file contains too many playlists" }
        val playlists = (0 until array.length()).map { index ->
            val item = array.optJSONObject(index) ?: throw IllegalArgumentException("Playlist entry is malformed")
            val rawName = item.opt("name")
            require(rawName is String) { "Playlist name is missing" }
            val tracks = item.optJSONArray("tracks") ?: throw IllegalArgumentException("Playlist tracks are missing")
            require(tracks.length() <= MAX_TRACKS_PER_PLAYLIST) { "Playlist contains too many tracks" }
            val ids = (0 until tracks.length()).map { trackIndex ->
                val value = tracks.opt(trackIndex)
                require(value is Number && value.toDouble().isFinite() && value.toDouble() % 1.0 == 0.0 && value.toLong() > 0) {
                    "Playlist contains an invalid track ID"
                }
                value.toLong()
            }
            SavedPlaylist(validateName(rawName), ids)
        }
        require(playlists.map { it.name.lowercase() }.distinct().size == playlists.size) { "Playlist names must be unique" }
        return playlists
    }

    fun validateName(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= MAX_PLAYLIST_NAME_LENGTH && trimmed.none(Char::isISOControl)) {
            "Playlist name must be 1–80 printable characters"
        }
        return trimmed
    }
}

/** SharedPreferences-backed playlist catalog. Updates are persisted synchronously before being reported. */
class PlaylistStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("nexi_playlists", Context.MODE_PRIVATE)

    @Synchronized fun all(): List<SavedPlaylist> = preferences.getString(KEY, null)?.let(PlaylistCodec::decode).orEmpty()

    @Synchronized fun create(name: String, trackIds: List<Long> = emptyList()): List<SavedPlaylist> {
        val playlist = SavedPlaylist(PlaylistCodec.validateName(name), trackIds)
        val current = all()
        require(current.none { it.name.equals(playlist.name, ignoreCase = true) }) { "A playlist with that name already exists" }
        return persist(current + playlist)
    }

    @Synchronized fun rename(oldName: String, newName: String): List<SavedPlaylist> {
        val name = PlaylistCodec.validateName(newName)
        val current = all()
        require(current.any { it.name == oldName }) { "Playlist no longer exists" }
        require(current.none { it.name != oldName && it.name.equals(name, ignoreCase = true) }) { "A playlist with that name already exists" }
        return persist(current.map { if (it.name == oldName) it.copy(name = name) else it })
    }

    @Synchronized fun delete(name: String): List<SavedPlaylist> = persist(all().filterNot { it.name == name })

    @Synchronized fun replaceTracks(name: String, trackIds: List<Long>): List<SavedPlaylist> {
        require(trackIds.size <= MAX_TRACKS_PER_PLAYLIST && trackIds.all { it > 0 }) { "Playlist contains invalid tracks" }
        val current = all()
        require(current.any { it.name == name }) { "Playlist no longer exists" }
        return persist(current.map { if (it.name == name) it.copy(trackIds = trackIds.toList()) else it })
    }

    @Synchronized fun import(json: String): List<SavedPlaylist> {
        val incoming = PlaylistCodec.decode(json)
        val current = all().associateBy { it.name.lowercase() }.toMutableMap()
        incoming.forEach { current[it.name.lowercase()] = it }
        require(current.size <= MAX_PLAYLISTS) { "Import would exceed the playlist limit" }
        return persist(current.values.toList())
    }

    fun export(): String = PlaylistCodec.encode(all())

    private fun persist(playlists: List<SavedPlaylist>): List<SavedPlaylist> {
        val encoded = PlaylistCodec.encode(playlists)
        check(preferences.edit().putString(KEY, encoded).commit()) { "Playlists could not be saved" }
        return playlists
    }

    private companion object { const val KEY = "playlists_v1" }
}

/** Deterministic queue edits shared by the UI and JVM tests. */
object QueueOperations {
    fun addNext(queue: List<Long>, ids: List<Long>, currentIndex: Int): List<Long> {
        if (ids.isEmpty()) return queue.toList()
        val result = queue.toMutableList()
        val insertion = (currentIndex + 1).coerceIn(0, result.size)
        result.addAll(insertion, ids)
        return result
    }

    fun move(queue: List<Long>, from: Int, to: Int): List<Long> {
        require(from in queue.indices && to in queue.indices) { "Queue position is out of range" }
        val result = queue.toMutableList()
        val item = result.removeAt(from)
        result.add(to, item)
        return result
    }

    fun remove(queue: List<Long>, index: Int): List<Long> {
        require(index in queue.indices) { "Queue position is out of range" }
        return queue.toMutableList().also { it.removeAt(index) }
    }
}
