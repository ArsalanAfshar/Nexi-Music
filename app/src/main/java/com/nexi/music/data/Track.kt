package com.nexi.music.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}

/** MediaStore access is deliberately moved off the main thread; permission/query errors reach the UI. */
object MusicLibrary {
    suspend fun scan(context: Context): List<Track> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val tracks = mutableListOf<Track>()
        val cursor = context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        ) ?: error("Android returned no music library cursor")
        cursor.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val duration = cursor.getLong(durationColumn).coerceAtLeast(0L)
                if (duration == 0L) continue
                tracks += Track(
                    id = cursor.getLong(idColumn),
                    title = cursor.getString(titleColumn)?.takeIf(String::isNotBlank) ?: "Unknown title",
                    artist = cursor.getString(artistColumn)?.takeIf(String::isNotBlank) ?: "Unknown artist",
                    album = cursor.getString(albumColumn)?.takeIf(String::isNotBlank) ?: "Unknown album",
                    duration = duration,
                )
            }
        }
        tracks
    }
}
