package com.nexi.music.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.nexi.music.data.MusicLibrary
import com.nexi.music.data.Track
import com.nexi.music.diagnostics.CrashLogStore
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Background playback session and browsable local catalog for Android Auto. */
@androidx.media3.common.util.UnstableApi
class MusicPlaybackService : MediaLibraryService() {
    private var player: ExoPlayer? = null
    private var session: MediaLibrarySession? = null
    private val libraryExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    @Volatile private var cachedTracks: List<Track> = emptyList()

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .apply {
                setAudioAttributes(AudioAttributes.DEFAULT, true) // Media3 manages audio focus.
                setHandleAudioBecomingNoisy(true) // Pause when headphones/Bluetooth disconnect.
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        CrashLogStore.record(this@MusicPlaybackService, "Background player error ${error.errorCodeName}", error)
                    }
                })
            }
        player = exoPlayer
        session = MediaLibrarySession.Builder(this, exoPlayer, LibraryCallback()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        player?.release()
        player = null
        libraryExecutor.shutdownNow()
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(rootItem(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (parentId != ROOT_ID) return Futures.immediateFuture(LibraryResult.ofItemList(emptyList(), params))
            val result = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            libraryExecutor.execute {
                try {
                    val tracks = runBlocking { MusicLibrary.scan(this@MusicPlaybackService) }
                    cachedTracks = tracks
                    val first = (page.toLong() * pageSize).coerceAtMost(tracks.size.toLong()).toInt()
                    val children = tracks.drop(first).take(pageSize).map(::libraryItem)
                    result.set(LibraryResult.ofItemList(children, params))
                } catch (error: Exception) {
                    CrashLogStore.record(this@MusicPlaybackService, "Android Auto library query failed", error)
                    result.set(LibraryResult.ofItemList(emptyList(), params))
                }
            }
            return result
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (mediaId == ROOT_ID) return Futures.immediateFuture(LibraryResult.ofItem(rootItem(), null))
            val trackId = mediaId.toLongOrNull()
            val track = trackId?.let { id -> cachedTracks.firstOrNull { it.id == id } }
            return if (track != null) Futures.immediateFuture(LibraryResult.ofItem(libraryItem(track), null))
            else Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        }
    }

    private fun rootItem(): MediaItem = MediaItem.Builder()
        .setMediaId(ROOT_ID)
        .setMediaMetadata(MediaMetadata.Builder().setTitle("Nexi Music").setIsBrowsable(true).setIsPlayable(false).build())
        .build()

    private fun libraryItem(track: Track): MediaItem = MediaItem.Builder()
        .setMediaId(track.id.toString())
        .setUri(track.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build(),
        )
        .build()

    private companion object { const val ROOT_ID = "nexi-root" }
}
