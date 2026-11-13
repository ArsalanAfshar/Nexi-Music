package com.nexi.music

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nexi.music.data.MusicLibrary
import com.nexi.music.data.Track
import com.nexi.music.playback.MusicPlaybackService
import com.nexi.music.presets.PresetArchive
import org.json.JSONObject
import java.io.File

private val Ink = Color(0xff282724)
private val Muted = Color(0xff8a8781)
private val Paper = Color(0xfff7f5f1)
private val Clay = Color(0xffbd8066)
private val LocalCornerScale = staticCompositionLocalOf { 1f }
@Composable private fun rounded(size: Dp) = RoundedCornerShape(size * LocalCornerScale.current)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(247, 245, 241)
        window.navigationBarColor = android.graphics.Color.rgb(247, 245, 241)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        setContent { NexiApp(this) }
    }
}

@Composable private fun NexiApp(activity: MainActivity) {
    val prefs = remember { activity.getSharedPreferences("nexi", Context.MODE_PRIVATE) }
    var accent by remember { mutableIntStateOf(prefs.getInt("accent", 0xffbd8066.toInt())) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var corner by remember { mutableFloatStateOf(prefs.getFloat("corner", 24f)) }
    var density by remember { mutableFloatStateOf(prefs.getFloat("density", 1f)) }
    var wallpaperBrightness by remember { mutableFloatStateOf(prefs.getFloat("wallpaperBrightness", .18f)) }
    var wallpaperBlur by remember { mutableFloatStateOf(prefs.getFloat("wallpaperBlur", 0f)) }
    var wallpaperPath by remember { mutableStateOf(prefs.getString("wallpaperPath", null)) }
    var tab by remember { mutableStateOf("Listen") }
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var playing by remember { mutableStateOf<Track?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importCandidate by remember { mutableStateOf<PresetArchive.Imported?>(null) }
    var showDependencyApproval by remember { mutableStateOf(false) }

    val appearanceExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) runCatching { PresetArchive.exportAppearance(activity, uri, PresetArchive.Appearance(accent, dark, corner, density, wallpaperBrightness, wallpaperBlur, wallpaperPath), wallpaperPath?.let(::File)?.takeIf(File::exists)?.readBytes()) }.onFailure { importError = it.message } }
    val settingsExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) runCatching { PresetArchive.exportSettings(activity, uri, JSONObject().put("crossfadeSeconds", prefs.getInt("crossfade", 0)).put("replayGain", prefs.getBoolean("replayGain", false)).put("gapless", prefs.getBoolean("gapless", true))) }.onFailure { importError = it.message } }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) runCatching { PresetArchive.read(activity, uri).also { importCandidate = it; showDependencyApproval = it.requiredSettings.isNotEmpty() } }.onFailure { importError = it.message } }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) runCatching { activity.contentResolver.openInputStream(uri)?.use { input -> val file = File(activity.filesDir, "wallpaper-${System.currentTimeMillis()}.image"); file.outputStream().use { output -> input.copyTo(output) }; wallpaperPath = file.absolutePath; prefs.edit().putString("wallpaperPath", wallpaperPath).apply() } }.onFailure { importError = it.message } }

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) tracks = MusicLibrary.scan(activity) }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) tracks = MusicLibrary.scan(activity) else permissionLauncher.launch(permission)
        val token = SessionToken(activity, ComponentName(activity, MusicPlaybackService::class.java))
        val future = MediaController.Builder(activity, token).buildAsync()
        future.addListener({ runCatching { controller = future.get() } }, ContextCompat.getMainExecutor(activity))
    }
    DisposableEffect(controller) { onDispose { controller?.release() } }

    val accentColor = Color(accent)
    val colorScheme = if (dark) darkColorScheme(primary = accentColor, background = Color(0xff1f201f), surface = Color(0xff292a28), onBackground = Color(0xfff2f0ec), onSurface = Color(0xfff2f0ec)) else lightColorScheme(primary = accentColor, background = Paper, surface = Color.White, onBackground = Ink, onSurface = Ink)
    MaterialTheme(colorScheme = colorScheme) {
        val baseDensity = LocalDensity.current
        CompositionLocalProvider(LocalCornerScale provides (corner / 24f), LocalDensity provides Density(density = baseDensity.density * density, fontScale = baseDensity.fontScale)) {
        Surface(Modifier.fillMaxSize(), color = colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                val wallpaper = remember(wallpaperPath) { wallpaperPath?.let { loadWallpaper(it) }?.asImageBitmap() }
                if (wallpaper != null) {
                    Image(wallpaper, null, modifier = Modifier.fillMaxSize().blur(wallpaperBlur.dp), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background((if (dark) Color.Black else colorScheme.background).copy(alpha = (0.75f * (1f - wallpaperBrightness / 0.6f)).coerceIn(0f, 0.75f))))
                }
                Column(Modifier.fillMaxSize()) {
                TopHeader(tab, Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
                when (tab) {
                    "Listen" -> ListenScreen(tracks, playing, isPlaying, onChoose = { track ->
                        playing = track; isPlaying = true
                        val items = tracks.map { MediaItem.Builder().setUri(it.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).setAlbumTitle(it.album).build()).build() }
                        controller?.setMediaItems(items, tracks.indexOf(track), 0); controller?.prepare(); controller?.play()
                    }, onToggle = { if (controller?.isPlaying == true) { controller?.pause(); isPlaying = false } else { controller?.play(); isPlaying = true } }, onNext = { controller?.seekToNextMediaItem(); val i = tracks.indexOf(playing); if (i >= 0 && tracks.isNotEmpty()) playing = tracks[(i + 1) % tracks.size] })
                    "Library" -> LibraryScreen(tracks, playing, onChoose = { track -> playing = track; isPlaying = true; val items = tracks.map { MediaItem.fromUri(it.uri) }; controller?.setMediaItems(items, tracks.indexOf(track), 0); controller?.prepare(); controller?.play() })
                    "Collections" -> CollectionsScreen(tracks)
                    else -> CustomizeScreen(accent, dark, corner, density, wallpaperBrightness, wallpaperBlur, wallpaperPath != null, onAccent = { accent = it; prefs.edit().putInt("accent", it).apply() }, onDark = { dark = it; prefs.edit().putBoolean("dark", it).apply() }, onCorner = { corner = it; prefs.edit().putFloat("corner", it).apply() }, onDensity = { density = it; prefs.edit().putFloat("density", it).apply() }, onBrightness = { wallpaperBrightness = it; prefs.edit().putFloat("wallpaperBrightness", it).apply() }, onBlur = { wallpaperBlur = it; prefs.edit().putFloat("wallpaperBlur", it).apply() }, onWallpaper = { wallpaperPicker.launch("image/*") }, onExportAppearance = { appearanceExport.launch("Nexi-appearance.nexi") }, onExportSettings = { settingsExport.launch("Nexi-settings.nexi") }, onImport = { importPicker.launch(arrayOf("application/zip", "application/octet-stream")) })
                }
                if (playing != null) MiniPlayer(playing!!, isPlaying, onToggle = { if (controller?.isPlaying == true) { controller?.pause(); isPlaying = false } else { controller?.play(); isPlaying = true } }, onNext = { controller?.seekToNextMediaItem(); val i = tracks.indexOf(playing); if (i >= 0 && tracks.isNotEmpty()) playing = tracks[(i + 1) % tracks.size] })
                Navigation(tab) { tab = it }
                }
            }
        }
        if (importError != null) AlertDialog(onDismissRequest = { importError = null }, title = { Text("Couldn’t complete that") }, text = { Text(importError ?: "") }, confirmButton = { TextButton(onClick = { importError = null }) { Text("OK") } })
        if (showDependencyApproval) AlertDialog(onDismissRequest = { showDependencyApproval = false; importCandidate = null }, title = { Text("This look needs settings") }, text = { Text("This appearance preset asks to change: ${importCandidate?.requiredSettings?.joinToString()}. Approve those changes? Nothing is changed until you choose.") }, confirmButton = { TextButton(onClick = { applyImported(activity, importCandidate, prefs, onAppearance = { a -> accent = a.accent; dark = a.dark; corner = a.corner; density = a.density; wallpaperBrightness = a.wallpaperBrightness; wallpaperBlur = a.wallpaperBlur }, onWallpaper = { path -> wallpaperPath = path }) ; showDependencyApproval = false }) { Text("Approve & import") } }, dismissButton = { TextButton(onClick = { showDependencyApproval = false; importCandidate = null }) { Text("Decline") } })
        else if (importCandidate != null) AlertDialog(onDismissRequest = { importCandidate = null }, title = { Text("Import ${importCandidate?.kind} preset?") }, text = { Text(if (importCandidate?.kind == "appearance") "Only appearance will be applied. Your playback and library settings stay untouched." else "Only settings in this preset will be applied. Your appearance stays untouched.") }, confirmButton = { TextButton(onClick = { applyImported(activity, importCandidate, prefs, onAppearance = { a -> accent = a.accent; dark = a.dark; corner = a.corner; density = a.density; wallpaperBrightness = a.wallpaperBrightness; wallpaperBlur = a.wallpaperBlur }, onWallpaper = { path -> wallpaperPath = path }); importCandidate = null }) { Text("Import") } }, dismissButton = { TextButton(onClick = { importCandidate = null }) { Text("Cancel") } })
        }
    }
}

private fun applyImported(context: Context, candidate: PresetArchive.Imported?, prefs: android.content.SharedPreferences, onAppearance: (PresetArchive.Appearance) -> Unit, onWallpaper: (String) -> Unit) {
    if (candidate?.kind == "appearance") candidate.appearance?.let { a -> onAppearance(a); prefs.edit().putInt("accent", a.accent).putBoolean("dark", a.dark).putFloat("corner", a.corner).putFloat("density", a.density).putFloat("wallpaperBrightness", a.wallpaperBrightness).putFloat("wallpaperBlur", a.wallpaperBlur).apply() }
    if (candidate?.kind == "settings") candidate.settings?.let { values -> applySettings(prefs, values, values.keys().asSequence().toSet()) }
    if (candidate?.kind == "appearance" && candidate.requiredSettings.isNotEmpty()) candidate.settings?.let { values -> applySettings(prefs, values, candidate.requiredSettings.toSet()) }
    if (candidate?.wallpaperBytes != null) {
        val file = File(context.filesDir, "wallpaper-${System.currentTimeMillis()}.image")
        file.writeBytes(candidate.wallpaperBytes)
        prefs.edit().putString("wallpaperPath", file.absolutePath).apply()
        onWallpaper(file.absolutePath)
    }
}

private fun applySettings(prefs: android.content.SharedPreferences, values: JSONObject, allowed: Set<String>) {
    val edit = prefs.edit()
    if ("crossfadeSeconds" in allowed && values.has("crossfadeSeconds")) edit.putInt("crossfade", values.optInt("crossfadeSeconds").coerceIn(0, 12))
    if ("replayGain" in allowed && values.has("replayGain")) edit.putBoolean("replayGain", values.optBoolean("replayGain"))
    if ("gapless" in allowed && values.has("gapless")) edit.putBoolean("gapless", values.optBoolean("gapless"))
    edit.apply()
}

@Composable private fun TopHeader(tab: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("NEXI  /  MUSIC", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Clay, letterSpacing = 2.sp)
            Text(when (tab) { "Listen" -> "Your sound,\nyour space."; "Library" -> "The library"; "Collections" -> "Made for you"; else -> "Make it yours." }, fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        }
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), Alignment.Center) { Icon(Icons.Filled.Tune, null, tint = Clay) }
    }
}

@Composable private fun ColumnScope.ListenScreen(tracks: List<Track>, playing: Track?, isPlaying: Boolean, onChoose: (Track) -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
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
        if (tracks.isEmpty()) item { EmptyLibrary() }
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

@Composable private fun EmptyLibrary() {
    Column(Modifier.fillMaxWidth().clip(rounded(24.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.LibraryMusic, null, tint = Clay, modifier = Modifier.size(28.dp)); Spacer(Modifier.height(8.dp)); Text("Your music lives here", fontWeight = FontWeight.SemiBold); Text("Allow audio access to find music stored on this device.", color = Muted, fontSize = 12.sp)
    }
}

@Composable private fun ColumnScope.LibraryScreen(tracks: List<Track>, playing: Track?, onChoose: (Track) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = tracks.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
    Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 22.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search songs, artists, albums") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true, shape = rounded(18.dp))
        Spacer(Modifier.height(8.dp)); Text("${filtered.size} TRACKS  ·  ON THIS DEVICE", fontSize = 10.sp, color = Muted, letterSpacing = 1.sp)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) { items(filtered, key = { it.id }) { TrackRow(it, it == playing) { onChoose(it) } }; if (filtered.isEmpty()) item { EmptyLibrary() } }
    }
}

@Composable private fun ColumnScope.CollectionsScreen(tracks: List<Track>) {
    LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Your listening, organized your way.", color = Muted) }
        item { CollectionCard("Recently added", "${tracks.size} tracks", Icons.Filled.Schedule, Color(0xffe9d9cf)) }
        item { CollectionCard("Favorites", "Keep the ones that stay with you", Icons.Filled.FavoriteBorder, Color(0xffeadfe0)) }
        item { CollectionCard("Play queue", "Shape what plays next", Icons.Filled.QueueMusic, Color(0xffdfe7dc)) }
        item { CollectionCard("Folders", "Browse your music by folder", Icons.Filled.FolderOpen, Color(0xffe5e1d4)) }
        item { Text("Playlists, smart mixes, folders and queue editing are designed to stay on-device.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable private fun CollectionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Row(Modifier.fillMaxWidth().clip(rounded(22.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(rounded(17.dp)).background(tint), Alignment.Center) { Icon(icon, null, tint = Ink) }; Spacer(Modifier.width(13.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Text(subtitle, fontSize = 12.sp, color = Muted) }; Icon(Icons.Filled.ArrowForward, null, tint = Muted, modifier = Modifier.size(17.dp))
    }
}

@Composable private fun ColumnScope.CustomizeScreen(accent: Int, dark: Boolean, corner: Float, density: Float, brightness: Float, blur: Float, hasWallpaper: Boolean, onAccent: (Int) -> Unit, onDark: (Boolean) -> Unit, onCorner: (Float) -> Unit, onDensity: (Float) -> Unit, onBrightness: (Float) -> Unit, onBlur: (Float) -> Unit, onWallpaper: () -> Unit, onExportAppearance: () -> Unit, onExportSettings: () -> Unit, onImport: () -> Unit) {
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

@Composable private fun MiniPlayer(track: Track, isPlaying: Boolean, onToggle: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp).clip(rounded(20.dp)).background(Color(0xff302b28)).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(39.dp).clip(rounded(13.dp)).background(Color(0xff51423b)), Alignment.Center) { Icon(Icons.Filled.MusicNote, null, tint = Color(0xffffc6a9)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(track.title, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(track.artist, color = Color(0xffc6b8af), fontSize = 11.sp, maxLines = 1) }; IconButton(onClick = onToggle) { Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play or pause", tint = Color.White) }; IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, "Next", tint = Color.White) }
    }
}

@Composable private fun Navigation(current: String, onSelect: (String) -> Unit) {
    val items = listOf("Listen" to Icons.Filled.GraphicEq, "Library" to Icons.Filled.LibraryMusic, "Collections" to Icons.Filled.QueueMusic, "Style" to Icons.Filled.Palette)
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceAround) {
        items.forEach { (label, icon) -> val selected = current == label; Column(Modifier.clip(rounded(15.dp)).clickable { onSelect(label) }.padding(horizontal = 13.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = if (selected) Clay else Muted, modifier = Modifier.size(21.dp)); Text(label, fontSize = 9.sp, color = if (selected) Clay else Muted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) } }
    }
}

private fun formatDuration(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)

private fun loadWallpaper(path: String): android.graphics.Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()
