package com.nexi.music.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.nexi.music.diagnostics.CrashLogStore

class MusicPlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

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
        session = MediaSession.Builder(this, exoPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
