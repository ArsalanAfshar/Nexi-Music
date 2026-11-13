package com.nexi.music.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.net.Uri

data class Track(val id: Long, val title: String, val artist: String, val album: String, val duration: Long) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}

object MusicLibrary {
    fun scan(context: Context): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        return runCatching {
            context.contentResolver.query(collection, projection, selection, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                buildList { while (cursor.moveToNext()) add(Track(cursor.getLong(id), cursor.getString(title) ?: "Unknown title", cursor.getString(artist) ?: "Unknown artist", cursor.getString(album) ?: "Unknown album", cursor.getLong(duration))) }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
