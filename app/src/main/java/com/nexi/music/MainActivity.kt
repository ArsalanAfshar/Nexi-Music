package com.nexi.music

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nexi.music.data.MusicLibrary
import com.nexi.music.data.Track
import com.nexi.music.data.WallpaperStore
import com.nexi.music.diagnostics.CrashLogStore
import com.nexi.music.playback.MusicPlaybackService
import com.nexi.music.playback.PlaylistStore
import com.nexi.music.playback.SavedPlaylist
import com.nexi.music.playback.PlayerScreen
import com.nexi.music.playback.QueueOperations
import com.nexi.music.presets.PresetArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private val Ink = Color(0xff282724)
private val Muted = Color(0xff77746e)
private val Paper = Color(0xfff7f5f1)
private val Clay = Color(0xffbd8066)
private val LocalCornerScale = staticCompositionLocalOf { 1f }
@Composable private fun rounded(size: Dp) = RoundedCornerShape(size * LocalCornerScale.current)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NexiApp(this) }
    }
}

private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).build())
    .build()

@Composable private fun NexiApp(activity: MainActivity) {
    val prefs = remember { activity.getSharedPreferences("nexi", Context.MODE_PRIVATE) }
    var accent by remember { mutableIntStateOf(prefs.getInt("accent", 0xffbd8066.toInt())) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var corner by remember { mutableFloatStateOf(prefs.getFloat("corner", 24f).takeIf { it.isFinite() && it in 8f..36f } ?: 24f) }
    var density by remember { mutableFloatStateOf(prefs.getFloat("density", 1f).takeIf { it.isFinite() && it in .85f..1.15f } ?: 1f) }
    var wallpaperBrightness by remember { mutableFloatStateOf(prefs.getFloat("wallpaperBrightness", .18f).takeIf { it.isFinite() && it in 0f..0.6f } ?: .18f) }
    var wallpaperBlur by remember { mutableFloatStateOf(prefs.getFloat("wallpaperBlur", 0f).takeIf { it.isFinite() && it in 0f..24f } ?: 0f) }
    var wallpaperPath by remember { mutableStateOf(prefs.getString("wallpaperPath", null)) }
    var tab by remember { mutableStateOf("Listen") }
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var playing by remember { mutableStateOf<Track?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var hasAudioAccess by remember { mutableStateOf(ContextCompat.checkSelfPermission(activity, audioPermission()) == PackageManager.PERMISSION_GRANTED) }
    var isScanning by remember { mutableStateOf(false) }
    var libraryMessage by remember { mutableStateOf<String?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    val playlistStore = remember { PlaylistStore(activity) }
    var playlists by remember { mutableStateOf(runCatching { playlistStore.all() }.getOrDefault(emptyList())) }
    var queueTracks by remember { mutableStateOf(emptyList<Track>()) }
    var playerPosition by remember { mutableLongStateOf(0L) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(prefs.getFloat("playbackSpeed", 1f).takeIf { it.isFinite() && it in .5f..2f } ?: 1f) }
    var playbackPitch by remember { mutableFloatStateOf(prefs.getFloat("playbackPitch", 1f).takeIf { it.isFinite() && it in .5f..2f } ?: 1f) }
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var currentBookmark by remember { mutableStateOf<Long?>(null) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var saveQueueInDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var collectionMessage by remember { mutableStateOf<String?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importCandidate by remember { mutableStateOf<PresetArchive.Imported?>(null) }
    var showDependencyApproval by remember { mutableStateOf(false) }
    var crashLog by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val scanLibrary: () -> Unit = {
        scope.launch {
            if (!hasAudioAccess || isScanning) return@launch
            isScanning = true
            libraryMessage = null
            runCatching { MusicLibrary.scan(activity) }
                .onSuccess { tracks = it }
                .onFailure {
                    libraryMessage = it.message ?: "The music library could not be scanned."
                    CrashLogStore.record(activity, "MediaStore library scan failed", it)
                }
            isScanning = false
        }
        Unit
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioAccess = granted
        if (granted) {
            libraryMessage = null
            scanLibrary()
        } else {
            libraryMessage = "Audio access was denied. You can allow it later to show local music."
        }
    }
    val requestAudioAccess: () -> Unit = {
        if (ContextCompat.checkSelfPermission(activity, audioPermission()) == PackageManager.PERMISSION_GRANTED) {
            hasAudioAccess = true
            scanLibrary()
        } else permissionLauncher.launch(audioPermission())
    }

    val appearanceExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val wallpaper = wallpaperPath?.let { path -> withContext(Dispatchers.IO) { WallpaperStore.readFile(File(path)) } }
                withContext(Dispatchers.IO) {
                    PresetArchive.exportAppearance(activity, uri, PresetArchive.Appearance(accent, dark, corner, density, wallpaperBrightness, wallpaperBlur, wallpaperPath), wallpaper)
                }
            }.onFailure { importError = it.message ?: "Appearance preset could not be exported." }
        }
    }
    val settingsExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val values = JSONObject()
                .put("crossfadeSeconds", prefs.getInt("crossfade", 0))
                .put("replayGain", prefs.getBoolean("replayGain", false))
                .put("gapless", prefs.getBoolean("gapless", true))
                .put("playbackSpeed", prefs.getFloat("playbackSpeed", 1f))
                .put("playbackPitch", prefs.getFloat("playbackPitch", 1f))
                .put("repeatMode", prefs.getInt("repeatMode", Player.REPEAT_MODE_OFF))
                .put("shuffle", prefs.getBoolean("shuffle", false))
            runCatching { withContext(Dispatchers.IO) { PresetArchive.exportSettings(activity, uri, values) } }
                .onFailure { importError = it.message ?: "Settings preset could not be exported." }
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { PresetArchive.read(activity, uri) } }
                .onSuccess { imported -> importCandidate = imported; showDependencyApproval = imported.requiredSettings.isNotEmpty() }
                .onFailure { importError = it.message ?: "Preset could not be opened. Nothing was changed." }
        }
    }
    val playlistExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    activity.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(playlistStore.export()) }
                        ?: error("Android could not create the playlist export")
                }
            }.onFailure { collectionMessage = it.message ?: "Playlists could not be exported." }
        }
    }
    val playlistImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    activity.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader -> reader.readText().take(8 * 1024 * 1024 + 1) }
                        ?: error("Android could not open the playlist file")
                }
                require(text.toByteArray(Charsets.UTF_8).size <= 8 * 1024 * 1024) { "Playlist file is too large" }
                playlistStore.import(text)
            }.onSuccess { playlists = it; collectionMessage = "Playlists imported. Matching names were updated; other playlists were kept." }
                .onFailure { collectionMessage = it.message ?: "Playlists could not be imported." }
        }
    }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val previousPath = wallpaperPath
            runCatching {
                val file = withContext(Dispatchers.IO) { WallpaperStore.save(activity, WallpaperStore.readUri(activity, uri)) }
                if (!prefs.edit().putString("wallpaperPath", file.absolutePath).commit()) {
                    val rollback = prefs.edit()
                    if (previousPath == null) rollback.remove("wallpaperPath") else rollback.putString("wallpaperPath", previousPath)
                    rollback.commit()
                    file.delete()
                    error("Wallpaper was processed but settings could not be saved; the prior wallpaper is unchanged.")
                }
                wallpaperPath = file.absolutePath
                previousPath?.let(::File)?.takeIf { it.parentFile == File(activity.filesDir, "wallpapers") }?.delete()
            }.onFailure { importError = it.message ?: "Wallpaper could not be loaded." }
        }
    }

    val controllerFuture = remember(activity) {
        MediaController.Builder(activity, SessionToken(activity, ComponentName(activity, MusicPlaybackService::class.java))).buildAsync()
    }
    DisposableEffect(controllerFuture) {
        var disposed = false
        controllerFuture.addListener({
            try {
                val connected = controllerFuture.get()
                if (disposed) connected.release() else controller = connected
            } catch (error: Exception) {
                if (!disposed) {
                    playbackError = "The playback service could not start. Please try again."
                    CrashLogStore.record(activity, "MediaController connection failed", error)
                }
            }
        }, ContextCompat.getMainExecutor(activity))
        onDispose {
            disposed = true
            MediaController.releaseFuture(controllerFuture)
        }
    }
    LaunchedEffect(Unit) {
        if (hasAudioAccess) scanLibrary() else permissionLauncher.launch(audioPermission())
    }

    val latestTracks by rememberUpdatedState(tracks)
    var sleepTimerJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun syncPlayerState() {
        val active = controller ?: return
        queueTracks = (0 until active.mediaItemCount).mapNotNull { index ->
            active.getMediaItemAt(index).mediaId.toLongOrNull()?.let { id -> latestTracks.firstOrNull { it.id == id } }
        }
        val currentId = active.currentMediaItem?.mediaId?.toLongOrNull()
        playing = currentId?.let { id -> latestTracks.firstOrNull { it.id == id } }
        playerPosition = active.currentPosition.coerceAtLeast(0L)
        prefs.edit().putString("queueIds", queueTracks.joinToString(",") { it.id.toString() }).apply()
        if (currentId != null) prefs.edit().putLong("currentTrackId", currentId).apply()
        repeatMode = active.repeatMode
        shuffleEnabled = active.shuffleModeEnabled
        playbackSpeed = active.playbackParameters.speed
        playbackPitch = active.playbackParameters.pitch
        currentBookmark = currentId?.let { prefs.getLong("bookmark_$it", -1L).takeIf { mark -> mark >= 0L } }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerMinutes = minutes.coerceIn(0, 180)
        if (sleepTimerMinutes > 0) sleepTimerJob = scope.launch {
            kotlinx.coroutines.delay(sleepTimerMinutes * 60_000L)
            controller?.pause()
            sleepTimerMinutes = 0
            sleepTimerJob = null
        }
    }

    LaunchedEffect(tracks, controller) {
        val active = controller ?: return@LaunchedEffect
        if (tracks.isEmpty() || active.mediaItemCount > 0) return@LaunchedEffect
        active.repeatMode = prefs.getInt("repeatMode", Player.REPEAT_MODE_OFF).coerceIn(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL)
        active.shuffleModeEnabled = prefs.getBoolean("shuffle", false)
        active.playbackParameters = androidx.media3.common.PlaybackParameters(playbackSpeed, playbackPitch)
        val savedIds = prefs.getString("queueIds", null).orEmpty().split(',').mapNotNull(String::toLongOrNull)
        val restored = savedIds.mapNotNull { id -> tracks.firstOrNull { it.id == id } }
        if (restored.isNotEmpty()) {
            val currentId = prefs.getLong("currentTrackId", restored.first().id)
            val index = restored.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
            val position = prefs.getLong("resume_$currentId", 0L).coerceIn(0L, restored[index].duration)
            active.setMediaItems(restored.map(Track::toMediaItem), index, position)
            syncPlayerState()
        }
    }

    DisposableEffect(controller) {
        val activeController = controller
        if (activeController == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playingNow: Boolean) {
                isPlaying = playingNow
                val id = activeController.currentMediaItem?.mediaId?.toLongOrNull()
                if (id != null) prefs.edit().putLong("resume_$id", activeController.currentPosition.coerceAtLeast(0L)).apply()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncPlayerState()
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) { syncPlayerState() }
            override fun onRepeatModeChanged(repeatModeNow: Int) { repeatMode = repeatModeNow; prefs.edit().putInt("repeatMode", repeatModeNow).apply() }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { shuffleEnabled = shuffleModeEnabled; prefs.edit().putBoolean("shuffle", shuffleModeEnabled).apply() }
            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                playbackSpeed = playbackParameters.speed
                playbackPitch = playbackParameters.pitch
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                playerPosition = newPosition.positionMs.coerceAtLeast(0L)
                newPosition.mediaItem?.mediaId?.toLongOrNull()?.let { prefs.edit().putLong("resume_$it", playerPosition).apply() }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackError = error.message ?: "Playback failed. Check that the file is still available."
                CrashLogStore.record(activity, "Media3 player error ${error.errorCodeName}", error)
            }
        }
        activeController.addListener(listener)
        isPlaying = activeController.isPlaying
        syncPlayerState()
        onDispose {
            activeController.removeListener(listener)
            activeController.release()
        }
    }

    LaunchedEffect(controller, isPlaying) {
        val active = controller ?: return@LaunchedEffect
        while (isPlaying) {
            playerPosition = active.currentPosition.coerceAtLeast(0L)
            active.currentMediaItem?.mediaId?.toLongOrNull()?.let { id -> prefs.edit().putLong("resume_$id", playerPosition).apply() }
            kotlinx.coroutines.delay(1_000L)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(activity, audioPermission()) == PackageManager.PERMISSION_GRANTED
                hasAudioAccess = granted
                if (granted) scanLibrary() else {
                    tracks = emptyList()
                    isScanning = false
                    libraryMessage = "Audio access is off. Allow it again to view your local music."
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun startPlayback(track: Track) {
        val player = controller
        if (player == null) {
            playbackError = "The playback service is still connecting. Try again in a moment."
            return
        }
        val queue = tracks.map(Track::toMediaItem)
        val startIndex = queue.indexOfFirst { it.mediaId == track.id.toString() }
        if (startIndex < 0) {
            playbackError = "That track is no longer in the scanned library. Refresh and try again."
            scanLibrary()
            return
        }
        val savedPosition = prefs.getLong("resume_${track.id}", 0L).coerceIn(0L, track.duration.coerceAtLeast(1L))
        val resumePosition = savedPosition.takeIf { it >= 10_000L && it < track.duration - 10_000L } ?: 0L
        player.setMediaItems(queue, startIndex, resumePosition)
        player.prepare()
        player.play()
        syncPlayerState()
    }

    fun togglePlayback() {
        val player = controller
        if (player == null) playbackError = "The playback service is still connecting. Try again in a moment."
        else if (player.isPlaying) player.pause()
        else if (player.mediaItemCount > 0) player.play()
        else tracks.firstOrNull()?.let(::startPlayback)
    }

    fun addTrackNext(track: Track) {
        val player = controller ?: run { playbackError = "The player is still connecting."; return }
        if (player.mediaItemCount == 0) {
            startPlayback(track)
            return
        }
        val insertion = (player.currentMediaItemIndex + 1).coerceIn(0, player.mediaItemCount)
        player.addMediaItem(insertion, track.toMediaItem())
        syncPlayerState()
    }

    fun playPlaylist(playlist: SavedPlaylist) {
        val entries = playlist.trackIds.mapNotNull { id -> tracks.firstOrNull { it.id == id } }
        if (entries.isEmpty()) { collectionMessage = "This playlist has no tracks available on this device."; return }
        if (entries.size != playlist.trackIds.size) collectionMessage = "Some playlist tracks are unavailable and were skipped."
        val player = controller ?: run { playbackError = "The player is still connecting."; return }
        player.setMediaItems(entries.map(Track::toMediaItem))
        player.prepare()
        player.play()
        syncPlayerState()
        tab = "Player"
    }

    fun createPlaylist(name: String, ids: List<Long>) {
        runCatching { playlistStore.create(name, ids) }
            .onSuccess { playlists = it; collectionMessage = "Playlist saved." }
            .onFailure { collectionMessage = it.message ?: "Playlist could not be saved." }
    }

    fun setPlaybackRate(speed: Float = playbackSpeed, pitch: Float = playbackPitch) {
        val safeSpeed = speed.coerceIn(.5f, 2f)
        val safePitch = pitch.coerceIn(.5f, 2f)
        controller?.playbackParameters = androidx.media3.common.PlaybackParameters(safeSpeed, safePitch)
        playbackSpeed = safeSpeed
        playbackPitch = safePitch
        prefs.edit().putFloat("playbackSpeed", safeSpeed).putFloat("playbackPitch", safePitch).apply()
    }

    fun applyPreset(approveDependencies: Boolean) {
        val candidate = importCandidate ?: return
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { applyImported(activity, candidate, prefs, approveDependencies) } }
                .onSuccess { result ->
                    result.appearance?.let { a ->
                        accent = a.accent; dark = a.dark; corner = a.corner; density = a.density
                        wallpaperBrightness = a.wallpaperBrightness; wallpaperBlur = a.wallpaperBlur
                    }
                    result.wallpaperPath?.let { wallpaperPath = it }
                    playbackSpeed = prefs.getFloat("playbackSpeed", 1f).coerceIn(.5f, 2f)
                    playbackPitch = prefs.getFloat("playbackPitch", 1f).coerceIn(.5f, 2f)
                    repeatMode = prefs.getInt("repeatMode", Player.REPEAT_MODE_OFF).coerceIn(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL)
                    shuffleEnabled = prefs.getBoolean("shuffle", false)
                    controller?.repeatMode = repeatMode
                    controller?.shuffleModeEnabled = shuffleEnabled
                    controller?.playbackParameters = androidx.media3.common.PlaybackParameters(playbackSpeed, playbackPitch)
                    importCandidate = null
                    showDependencyApproval = false
                }
                .onFailure { importError = it.message ?: "Preset was not applied; your current settings are unchanged." }
        }
    }

    val colorScheme = if (dark) darkColorScheme(primary = Color(accent), background = Color(0xff1f201f), surface = Color(0xff292a28), onBackground = Color(0xfff2f0ec), onSurface = Color(0xfff2f0ec))
        else lightColorScheme(primary = Color(accent), background = Paper, surface = Color.White, onBackground = Ink, onSurface = Ink)
    SideEffect {
        activity.window.statusBarColor = (if (dark) android.graphics.Color.rgb(31, 32, 31) else android.graphics.Color.rgb(247, 245, 241))
        activity.window.navigationBarColor = (if (dark) android.graphics.Color.rgb(31, 32, 31) else android.graphics.Color.rgb(247, 245, 241))
        activity.window.decorView.systemUiVisibility = if (dark) 0 else android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }
    MaterialTheme(colorScheme = colorScheme) {
        val baseDensity = LocalDensity.current
        CompositionLocalProvider(LocalCornerScale provides corner / 24f, LocalDensity provides Density(baseDensity.density * density, baseDensity.fontScale)) {
            Surface(Modifier.fillMaxSize(), color = colorScheme.background) {
                Box(Modifier.fillMaxSize()) {
                    val wallpaper = remember(wallpaperPath) { wallpaperPath?.let(::loadWallpaper)?.asImageBitmap() }
                    if (wallpaper != null) {
                        Image(wallpaper, null, modifier = Modifier.fillMaxSize().blur(wallpaperBlur.dp), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background((if (dark) Color.Black else colorScheme.background).copy(alpha = (0.75f * (1f - wallpaperBrightness / 0.6f)).coerceIn(0f, 0.75f))))
                    }
                    Column(Modifier.fillMaxSize()) {
                        TopHeader(tab, Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
                        when (tab) {
                            "Listen" -> ListenScreen(tracks, playing, isPlaying, hasAudioAccess, isScanning, libraryMessage, requestAudioAccess, ::startPlayback, ::togglePlayback, { controller?.seekToNextMediaItem() })
                            "Library" -> LibraryScreen(tracks, playing, hasAudioAccess, isScanning, libraryMessage, requestAudioAccess, ::startPlayback)
                            "Collections" -> CollectionsScreen(
                                tracks = tracks,
                                playlists = playlists,
                                onOpenPlayer = { tab = "Player" },
                                onCreatePlaylist = { showPlaylistDialog = true; saveQueueInDialog = false; playlistName = "" },
                                onPlayPlaylist = ::playPlaylist,
                                onDeletePlaylist = { name -> runCatching { playlistStore.delete(name) }.onSuccess { playlists = it }.onFailure { collectionMessage = it.message } },
                                onExportPlaylists = { playlistExport.launch("Nexi-playlists.json") },
                                onImportPlaylists = { playlistImport.launch(arrayOf("application/json", "text/json", "text/plain")) },
                            )
                            "Player" -> PlayerScreen(
                                track = playing,
                                isPlaying = isPlaying,
                                positionMs = playerPosition,
                                repeatMode = repeatMode,
                                shuffle = shuffleEnabled,
                                speed = playbackSpeed,
                                pitch = playbackPitch,
                                timerMinutes = sleepTimerMinutes,
                                bookmarkMs = currentBookmark,
                                queue = queueTracks,
                                library = tracks,
                                onToggle = ::togglePlayback,
                                onPrevious = { controller?.seekToPreviousMediaItem() },
                                onNext = { controller?.seekToNextMediaItem() },
                                onSeek = { position -> controller?.seekTo(position); playerPosition = position },
                                onRepeat = { controller?.repeatMode = when (repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE; Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL; else -> Player.REPEAT_MODE_OFF } },
                                onShuffle = { controller?.shuffleModeEnabled = !shuffleEnabled },
                                onSpeed = { setPlaybackRate(speed = it) },
                                onPitch = { setPlaybackRate(pitch = it) },
                                onSleepTimer = ::setSleepTimer,
                                onBookmark = {
                                    val id = playing?.id
                                    if (id != null) { prefs.edit().putLong("bookmark_$id", playerPosition).apply(); currentBookmark = playerPosition }
                                },
                                onSeekBookmark = { controller?.seekTo(it) },
                                onAddNext = ::addTrackNext,
                                onMoveQueueItem = { from, to -> controller?.moveMediaItem(from, to); syncPlayerState() },
                                onRemoveQueueItem = { index -> controller?.removeMediaItem(index); syncPlayerState() },
                                onClearQueue = { controller?.clearMediaItems(); syncPlayerState() },
                                onSaveQueue = { showPlaylistDialog = true; saveQueueInDialog = true; playlistName = "Queue" },
                            )
                            else -> CustomizeScreen(accent, dark, corner, density, wallpaperBrightness, wallpaperBlur, wallpaperPath?.let(::File)?.isFile == true,
                                onAccent = { accent = it; prefs.edit().putInt("accent", it).apply() },
                                onDark = { dark = it; prefs.edit().putBoolean("dark", it).apply() },
                                onCorner = { corner = it; prefs.edit().putFloat("corner", it).apply() },
                                onDensity = { density = it; prefs.edit().putFloat("density", it).apply() },
                                onBrightness = { wallpaperBrightness = it; prefs.edit().putFloat("wallpaperBrightness", it).apply() },
                                onBlur = { wallpaperBlur = it; prefs.edit().putFloat("wallpaperBlur", it).apply() },
                                onWallpaper = { wallpaperPicker.launch("image/*") },
                                onExportAppearance = { appearanceExport.launch("Nexi-appearance.nexi") },
                                onExportSettings = { settingsExport.launch("Nexi-settings.nexi") },
                                onImport = { importPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                                onViewCrashLogs = { crashLog = CrashLogStore.read(activity) },
                            )
                        }
                        if (playing != null) MiniPlayer(playing!!, isPlaying, ::togglePlayback, { controller?.seekToNextMediaItem() }, { tab = "Player" })
                        Navigation(tab) { tab = it }
                    }
                }
            }
        }
        if (showPlaylistDialog) AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text(if (saveQueueInDialog) "Save queue as playlist" else "New playlist") },
            text = { OutlinedTextField(playlistName, { playlistName = it }, label = { Text("Playlist name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    createPlaylist(playlistName, if (saveQueueInDialog) queueTracks.map(Track::id) else emptyList())
                    showPlaylistDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showPlaylistDialog = false }) { Text("Cancel") } },
        )
        if (collectionMessage != null) AlertDialog(
            onDismissRequest = { collectionMessage = null },
            title = { Text("Playlists") },
            text = { Text(collectionMessage.orEmpty()) },
            confirmButton = { TextButton(onClick = { collectionMessage = null }) { Text("OK") } },
        )
        if (importError != null) AlertDialog(
            onDismissRequest = { importError = null }, title = { Text("Couldn’t complete that") },
            text = { Text(importError ?: "No changes were made.") },
            confirmButton = { TextButton(onClick = { importError = null }) { Text("OK") } },
        )
        if (playbackError != null) AlertDialog(
            onDismissRequest = { playbackError = null }, title = { Text("Playback issue") },
            text = { Text(playbackError ?: "") },
            confirmButton = { TextButton(onClick = { playbackError = null }) { Text("OK") } },
        )
        if (showDependencyApproval) AlertDialog(
            onDismissRequest = { showDependencyApproval = false; importCandidate = null },
            title = { Text("This appearance needs settings") },
            text = { Text("The preset requests these setting changes: ${importCandidate?.requiredSettings?.joinToString()}. Approve them? Declining keeps every current setting unchanged.") },
            confirmButton = { TextButton(onClick = { applyPreset(true) }) { Text("Approve & import") } },
            dismissButton = { TextButton(onClick = { showDependencyApproval = false; importCandidate = null }) { Text("Decline") } },
        ) else if (importCandidate != null) AlertDialog(
            onDismissRequest = { importCandidate = null },
            title = { Text("Preview ${importCandidate?.kind} preset") },
            text = { Text(if (importCandidate?.kind == "appearance") "This will change only appearance. Playback and library settings stay untouched." else "This will change only settings included in this preset. Appearance stays untouched.") },
            confirmButton = { TextButton(onClick = { applyPreset(false) }) { Text("Apply preset") } },
            dismissButton = { TextButton(onClick = { importCandidate = null }) { Text("Cancel") } },
        )
        if (crashLog != null) AlertDialog(
            onDismissRequest = { crashLog = null },
            title = { Text("On-device crash log") },
            text = {
                val value = crashLog.orEmpty()
                if (value.isBlank()) Text("No crash log has been recorded on this device.")
                else Text(value, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    val manager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    manager.setPrimaryClip(ClipData.newPlainText("Nexi Music crash log", crashLog.orEmpty()))
                }) { Text("Copy") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { CrashLogStore.clear(activity); crashLog = "" }) { Text("Clear") }
                    TextButton(onClick = { crashLog = null }) { Text("Close") }
                }
            },
        )
    }
}

private fun audioPermission(): String = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

private data class AppliedPreset(val appearance: PresetArchive.Appearance?, val wallpaperPath: String?)

private fun applyImported(context: Context, candidate: PresetArchive.Imported, prefs: android.content.SharedPreferences, approveDependencies: Boolean): AppliedPreset {
    val preferenceSnapshot = prefs.all.toMap()
    val oldWallpaperPath = prefs.getString("wallpaperPath", null)
    val editor = prefs.edit()
    var updatedAppearance: PresetArchive.Appearance? = null
    var updatedWallpaperPath: String? = null
    var newWallpaper: File? = null
    if (candidate.kind == "appearance") {
        val appearance = requireNotNull(candidate.appearance)
        if (candidate.wallpaperBytes != null) {
            newWallpaper = WallpaperStore.save(context, candidate.wallpaperBytes)
            updatedWallpaperPath = newWallpaper.absolutePath
        }
        editor.putInt("accent", appearance.accent)
            .putBoolean("dark", appearance.dark)
            .putFloat("corner", appearance.corner)
            .putFloat("density", appearance.density)
            .putFloat("wallpaperBrightness", appearance.wallpaperBrightness)
            .putFloat("wallpaperBlur", appearance.wallpaperBlur)
        updatedAppearance = appearance
        if (approveDependencies && candidate.requiredSettings.isNotEmpty()) {
            putSettings(editor, requireNotNull(candidate.settings), candidate.requiredSettings.toSet())
        }
        if (updatedWallpaperPath != null) editor.putString("wallpaperPath", updatedWallpaperPath)
    } else {
        putSettings(editor, requireNotNull(candidate.settings), candidate.settings.keys().asSequence().toSet())
    }
    if (!editor.commit()) {
        restorePreferences(prefs, preferenceSnapshot)
        newWallpaper?.delete()
        error("Could not save the preset. Your existing settings were left unchanged.")
    }
    if (updatedAppearance != null && updatedWallpaperPath == null) updatedWallpaperPath = prefs.getString("wallpaperPath", null)
    if (newWallpaper != null && oldWallpaperPath != null && oldWallpaperPath != newWallpaper.absolutePath) {
        val oldFile = File(oldWallpaperPath)
        if (oldFile.parentFile == File(context.filesDir, "wallpapers")) oldFile.delete()
    }
    return AppliedPreset(updatedAppearance, updatedWallpaperPath)
}

private fun restorePreferences(prefs: android.content.SharedPreferences, snapshot: Map<String, *>) {
    val editor = prefs.edit().clear()
    snapshot.forEach { (key, value) ->
        when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }
    editor.commit()
}

private fun putSettings(editor: android.content.SharedPreferences.Editor, values: JSONObject, allowed: Set<String>) {
    if ("crossfadeSeconds" in allowed && values.has("crossfadeSeconds")) editor.putInt("crossfade", values.getInt("crossfadeSeconds"))
    if ("replayGain" in allowed && values.has("replayGain")) editor.putBoolean("replayGain", values.getBoolean("replayGain"))
    if ("gapless" in allowed && values.has("gapless")) editor.putBoolean("gapless", values.getBoolean("gapless"))
    if ("playbackSpeed" in allowed && values.has("playbackSpeed")) editor.putFloat("playbackSpeed", values.getDouble("playbackSpeed").toFloat())
    if ("playbackPitch" in allowed && values.has("playbackPitch")) editor.putFloat("playbackPitch", values.getDouble("playbackPitch").toFloat())
    if ("repeatMode" in allowed && values.has("repeatMode")) editor.putInt("repeatMode", values.getInt("repeatMode"))
    if ("shuffle" in allowed && values.has("shuffle")) editor.putBoolean("shuffle", values.getBoolean("shuffle"))
}

@Composable private fun TopHeader(tab: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("NEXI  /  MUSIC", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Clay, letterSpacing = 2.sp)
            Text(when (tab) { "Listen" -> "Your sound,\nyour space."; "Library" -> "The library"; "Collections" -> "Made for you"; "Player" -> "In the moment"; else -> "Make it yours." }, fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        }
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), Alignment.Center) { Icon(Icons.Filled.Tune, null, tint = Clay) }
    }
}

@Composable private fun ColumnScope.ListenScreen(
    tracks: List<Track>, playing: Track?, isPlaying: Boolean,
    hasAudioAccess: Boolean, isScanning: Boolean, libraryMessage: String?,
    onRequestAudioAccess: () -> Unit, onChoose: (Track) -> Unit,
    onToggle: () -> Unit, onNext: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("ON DEVICE", tracks.size.toString().padStart(2, '0'), "songs in your library", Modifier.weight(1f))
                MetricCard("YOUR MIX", if (tracks.isEmpty()) "—" else "∞", "no ads. just music.", Modifier.weight(1f))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(rounded(28.dp)).background(Color(0xff302b28)).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(rounded(15.dp)).background(Color(0xff51423b)), Alignment.Center) { Icon(Icons.Filled.GraphicEq, null, tint = Color(0xffffc6a9)) }
                    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("NOW IN ROTATION", color = Color(0xffdbb8a5), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp); Text(playing?.title ?: "A little room to breathe", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    IconButton(onClick = onToggle) { Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play or pause", tint = Color.White) }
                    IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, "Next track", tint = Color.White) }
                }
                Spacer(Modifier.height(18.dp)); Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color(0xff5b514b))) { Box(Modifier.fillMaxWidth(.32f).fillMaxHeight().background(Color(0xffe8aa89))) }
                Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("YOUR LOCAL LIBRARY", fontSize = 9.sp, color = Color(0xffc6b8af), letterSpacing = 1.sp); Text("PRIVATE BY DESIGN", fontSize = 9.sp, color = Color(0xffc6b8af), letterSpacing = 1.sp) }
            }
        }
        item { SectionTitle("Pick up the feeling", "PLAY SOMETHING") }
        if (tracks.isEmpty()) item { EmptyLibrary(hasAudioAccess, isScanning, libraryMessage, onRequestAudioAccess) }
        else items(tracks.take(6), key = { it.id }) { TrackRow(it, it == playing, onClick = { onChoose(it) }) }
    }
}

@Composable private fun MetricCard(label: String, number: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(rounded(22.dp)).background(MaterialTheme.colorScheme.surface).padding(15.dp)) {
        Text(label, fontSize = 9.sp, color = Muted, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.Bottom) { Text(number, fontSize = 27.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface); Spacer(Modifier.width(8.dp)); Text(caption, fontSize = 10.sp, color = Muted, modifier = Modifier.padding(bottom = 5.dp)) }
    }
}

@Composable private fun SectionTitle(title: String, eyebrow: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Text(eyebrow, fontSize = 9.sp, letterSpacing = 1.1.sp, color = Muted, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun TrackRow(track: Track, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(rounded(18.dp)).background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(rounded(13.dp)).background(if (selected) Color(0xffead8ce) else Color(0xffe9e5df)), Alignment.Center) { Icon(if (selected) Icons.Filled.GraphicEq else Icons.Filled.MusicNote, null, tint = Clay) }
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(track.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Text(formatDuration(track.duration), color = Muted, fontSize = 11.sp)
    }
}

@Composable private fun EmptyLibrary(hasAudioAccess: Boolean, isScanning: Boolean, message: String?, onRequestAudioAccess: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(rounded(24.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.LibraryMusic, null, tint = Clay, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(if (hasAudioAccess) "No music found yet" else "Your music stays on your device", fontWeight = FontWeight.SemiBold)
        Text(message ?: if (hasAudioAccess) "Add audio files to this device, then refresh your library." else "Allow audio access to find music stored on this device.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        when {
            isScanning -> CircularProgressIndicator(Modifier.size(24.dp), color = Clay, strokeWidth = 2.dp)
            !hasAudioAccess -> Button(onClick = onRequestAudioAccess) { Text("Allow audio access") }
            else -> TextButton(onClick = onRequestAudioAccess) { Text("Refresh library") }
        }
    }
}

@Composable private fun ColumnScope.LibraryScreen(
    tracks: List<Track>, playing: Track?, hasAudioAccess: Boolean, isScanning: Boolean,
    libraryMessage: String?, onRequestAudioAccess: () -> Unit, onChoose: (Track) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = tracks.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
    Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 22.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search songs, artists, albums") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true, shape = rounded(18.dp))
        Spacer(Modifier.height(8.dp)); Text("${filtered.size} TRACKS  ·  ON THIS DEVICE", fontSize = 10.sp, color = Muted, letterSpacing = 1.sp)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(filtered, key = { it.id }) { TrackRow(it, it == playing) { onChoose(it) } }
            if (filtered.isEmpty()) item {
                if (tracks.isEmpty()) EmptyLibrary(hasAudioAccess, isScanning, libraryMessage, onRequestAudioAccess)
                else Text("No tracks match ‘$query’.", color = Muted, modifier = Modifier.padding(vertical = 18.dp))
            }
        }
    }
}

@Composable private fun ColumnScope.CollectionsScreen(
    tracks: List<Track>, playlists: List<SavedPlaylist>, onOpenPlayer: () -> Unit,
    onCreatePlaylist: () -> Unit, onPlayPlaylist: (SavedPlaylist) -> Unit,
    onDeletePlaylist: (String) -> Unit, onExportPlaylists: () -> Unit, onImportPlaylists: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Your listening, organized your way.", color = Muted) }
        item { CollectionCard("Recently added", "${tracks.size} tracks", Icons.Filled.Schedule, Color(0xffe9d9cf)) }
        item { CollectionCard("Play queue", "Edit order, save it, or add what’s next", Icons.Filled.QueueMusic, Color(0xffdfe7dc), onOpenPlayer) }
        item { CustomizeCard("Your playlists", "ON THIS DEVICE") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCreatePlaylist, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(5.dp)); Text("New") }
                OutlinedButton(onClick = onExportPlaylists, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.FileUpload, null); Spacer(Modifier.width(5.dp)); Text("Export") }
                OutlinedButton(onClick = onImportPlaylists, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.FileDownload, null); Spacer(Modifier.width(5.dp)); Text("Import") }
            }
            if (playlists.isEmpty()) Text("No playlists yet. Create one or save the current queue.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            playlists.forEach { playlist ->
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.QueueMusic, null, tint = Clay)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${playlist.trackIds.size} tracks", color = Muted, fontSize = 11.sp)
                    }
                    IconButton(onClick = { onPlayPlaylist(playlist) }) { Icon(Icons.Filled.PlayArrow, "Play playlist", tint = Clay) }
                    IconButton(onClick = { onDeletePlaylist(playlist.name) }) { Icon(Icons.Filled.DeleteOutline, "Delete playlist", tint = Muted) }
                }
            }
        } }
        item { CollectionCard("Folders", "Browse your music by folder", Icons.Filled.FolderOpen, Color(0xffe5e1d4)) }
    }
}

@Composable private fun CollectionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clip(rounded(22.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(rounded(17.dp)).background(tint), Alignment.Center) { Icon(icon, null, tint = Ink) }; Spacer(Modifier.width(13.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Text(subtitle, fontSize = 12.sp, color = Muted) }; Icon(Icons.Filled.ArrowForward, null, tint = Muted, modifier = Modifier.size(17.dp))
    }
}

@Composable private fun ColumnScope.CustomizeScreen(accent: Int, dark: Boolean, corner: Float, density: Float, brightness: Float, blur: Float, hasWallpaper: Boolean, onAccent: (Int) -> Unit, onDark: (Boolean) -> Unit, onCorner: (Float) -> Unit, onDensity: (Float) -> Unit, onBrightness: (Float) -> Unit, onBlur: (Float) -> Unit, onWallpaper: () -> Unit, onExportAppearance: () -> Unit, onExportSettings: () -> Unit, onImport: () -> Unit, onViewCrashLogs: () -> Unit) {
    val swatches = listOf(0xffbd8066.toInt(), 0xff7a8b70.toInt(), 0xff6e88a2.toInt(), 0xffa17b9b.toInt(), 0xffc09c4f.toInt(), 0xff514e87.toInt())
    LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("A visual engine, not a preset menu. Tune the details until it feels like yours.", color = Muted, fontSize = 13.sp) }
        item { CustomizeCard("Color language", "ACCENT · ${String.format("#%06X", 0xFFFFFF and accent)}") {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) { swatches.forEach { color -> Box(Modifier.size(37.dp).clip(CircleShape).background(Color(color)).clickable { onAccent(color) }, Alignment.Center) { if (color == accent) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) } } }
            Spacer(Modifier.height(12.dp)); SettingSwitch("Dark studio", "A lower-light listening environment", dark, onDark)
        } }
        item { CustomizeCard("Shape & rhythm", "LAYOUT") {
            SliderSetting("Corner character", "${corner.toInt()} dp", corner, 8f..36f, onCorner)
            SliderSetting("Interface density", "${(density * 100).toInt()}%", density, .85f..1.15f, onDensity)
        } }
        item { CustomizeCard("Your backdrop", if (hasWallpaper) "WALLPAPER ACTIVE" else "WALLPAPER OPTIONAL") {
            Row(Modifier.fillMaxWidth().clip(rounded(16.dp)).background(MaterialTheme.colorScheme.background).clickable(onClick = onWallpaper).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Wallpaper, null, tint = Clay); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(if (hasWallpaper) "Change wallpaper" else "Choose a wallpaper", fontWeight = FontWeight.Medium); Text("Brightness and blur are independently tuned", fontSize = 11.sp, color = Muted) }; Icon(Icons.Filled.Add, null, tint = Muted) }
            SliderSetting("Brightness", "${(brightness * 100).toInt()}%", brightness, 0f..0.6f, onBrightness)
            SliderSetting("Background blur", "${blur.toInt()} px", blur, 0f..24f, onBlur)
        } }
        item { CustomizeCard("Share your setup", "MODULAR PRESETS · .NEXI") {
            Text("Appearance and settings travel separately. Bundled wallpapers stay inside the appearance file.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp)); ActionButton("Export appearance + wallpaper", Icons.Filled.Palette, onExportAppearance)
            Spacer(Modifier.height(8.dp)); ActionButton("Export settings only", Icons.Filled.Settings, onExportSettings)
            Spacer(Modifier.height(8.dp)); ActionButton("Import a preset", Icons.Filled.FolderOpen, onImport)
        } }
        item { CustomizeCard("Diagnostics", "LOCAL ONLY") {
            Text("Crash reports stay on this device unless you choose to copy and share them.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp)); ActionButton("View crash log", Icons.Filled.BugReport, onViewCrashLogs)
        } }
        item { Text("More controls are coming: typography, library layout, navigation, gestures, player controls, audio processing and per-screen modules.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp)) }
    }
}

@Composable private fun CustomizeCard(title: String, label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(rounded(24.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp), content = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp); Text(label, fontSize = 8.sp, color = Clay, letterSpacing = 1.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(14.dp)); content()
    })
}

@Composable private fun SettingSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(description, color = Muted, fontSize = 11.sp) }; Switch(checked, onChange) }
}

@Composable private fun SliderSetting(title: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, fontSize = 13.sp); Text(valueLabel, fontSize = 11.sp, color = Clay) }; Slider(value, onChange, valueRange = range) }
}

@Composable private fun ActionButton(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(rounded(15.dp)).background(MaterialTheme.colorScheme.background).clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Clay, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium); Spacer(Modifier.weight(1f)); Icon(Icons.Filled.ArrowForward, null, tint = Muted, modifier = Modifier.size(15.dp)) }
}

@Composable private fun MiniPlayer(track: Track, isPlaying: Boolean, onToggle: () -> Unit, onNext: () -> Unit, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp).clip(rounded(20.dp)).background(Color(0xff302b28)).clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(39.dp).clip(rounded(13.dp)).background(Color(0xff51423b)), Alignment.Center) { Icon(Icons.Filled.MusicNote, null, tint = Color(0xffffc6a9)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(track.title, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(track.artist, color = Color(0xffc6b8af), fontSize = 11.sp, maxLines = 1) }; IconButton(onClick = onToggle) { Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play or pause", tint = Color.White) }; IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, "Next", tint = Color.White) }
    }
}

@Composable private fun Navigation(current: String, onSelect: (String) -> Unit) {
    val items = listOf("Listen" to Icons.Filled.GraphicEq, "Library" to Icons.Filled.LibraryMusic, "Player" to Icons.Filled.Album, "Collections" to Icons.Filled.QueueMusic, "Style" to Icons.Filled.Palette)
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceAround) {
        items.forEach { (label, icon) -> val selected = current == label; Column(Modifier.clip(rounded(15.dp)).clickable { onSelect(label) }.padding(horizontal = 13.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = if (selected) Clay else Muted, modifier = Modifier.size(21.dp)); Text(label, fontSize = 9.sp, color = if (selected) Clay else Muted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) } }
    }
}

private fun formatDuration(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)

private fun loadWallpaper(path: String): android.graphics.Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    require(bounds.outWidth in 1..30_000 && bounds.outHeight in 1..30_000) { "Wallpaper image is unavailable" }
    require(bounds.outWidth.toLong() * bounds.outHeight <= 500_000_000L) { "Wallpaper dimensions are too large" }
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048 ||
        (bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) > 4_000_000L
    ) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()
