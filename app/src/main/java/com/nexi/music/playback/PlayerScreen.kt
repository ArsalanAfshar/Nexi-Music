package com.nexi.music.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexi.music.data.Track

private val PlayerClay = Color(0xffbd8066)
private val PlayerMuted = Color(0xff77746e)

@Composable
fun ColumnScope.PlayerScreen(
    track: Track?, isPlaying: Boolean, positionMs: Long, repeatMode: Int, shuffle: Boolean,
    speed: Float, pitch: Float, timerMinutes: Int, bookmarkMs: Long?, queue: List<Track>, library: List<Track>,
    onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    onSeek: (Long) -> Unit, onRepeat: () -> Unit, onShuffle: () -> Unit,
    onSpeed: (Float) -> Unit, onPitch: (Float) -> Unit, onSleepTimer: (Int) -> Unit, onBookmark: () -> Unit, onSeekBookmark: (Long) -> Unit,
    onAddNext: (Track) -> Unit, onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit, onClearQueue: () -> Unit, onSaveQueue: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0xff302b28)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xff51423b)), Alignment.Center) {
                    Icon(Icons.Filled.Album, null, tint = Color(0xffffc6a9), modifier = Modifier.size(76.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(track?.title ?: "Choose something to play", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track?.artist ?: "Your music stays on this device", color = Color(0xffc6b8af), fontSize = 13.sp, maxLines = 1)
                Slider(
                    value = positionMs.coerceAtMost(track?.duration ?: 1L).toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..(track?.duration?.coerceAtLeast(1L)?.toFloat() ?: 1f),
                    enabled = track != null,
                    colors = SliderDefaults.colors(thumbColor = Color(0xffffc6a9), activeTrackColor = Color(0xffffc6a9)),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatPlayerTime(positionMs), color = Color(0xffc6b8af), fontSize = 11.sp)
                    Text(formatPlayerTime(track?.duration ?: 0L), color = Color(0xffc6b8af), fontSize = 11.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onShuffle) { Icon(Icons.Filled.Shuffle, "Shuffle ${if (shuffle) "on" else "off"}", tint = if (shuffle) Color(0xffffc6a9) else Color.White) }
                    IconButton(onClick = onPrevious) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(30.dp)) }
                    FilledIconButton(onClick = onToggle, enabled = track != null, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xffffc6a9), contentColor = Color(0xff302b28)), modifier = Modifier.size(58.dp)) {
                        Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play or pause", modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(30.dp)) }
                    IconButton(onClick = onRepeat) { Icon(Icons.Filled.Repeat, "Repeat mode ${when (repeatMode) { 1 -> "one"; 2 -> "all"; else -> "off" }}", tint = if (repeatMode != 0) Color(0xffffc6a9) else Color.White) }
                }
            }
        }
        item {
            ControlCard("Playback tools") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("Speed", fontWeight = FontWeight.Medium); Text("${"%.2f".format(speed)}×", color = PlayerClay, fontSize = 12.sp) }
                    Slider(speed, onSpeed, valueRange = .5f..2f, modifier = Modifier.widthIn(min = 130.dp, max = 210.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("Pitch", fontWeight = FontWeight.Medium); Text("${"%.2f".format(pitch)}×", color = PlayerClay, fontSize = 12.sp) }
                    Slider(pitch, onPitch, valueRange = .5f..2f, modifier = Modifier.widthIn(min = 130.dp, max = 210.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 15, 30, 60).forEach { minutes ->
                        FilterChip(selected = timerMinutes == minutes, onClick = { onSleepTimer(minutes) }, label = { Text(if (minutes == 0) "Off" else "${minutes}m") })
                    }
                }
                Text(if (timerMinutes == 0) "Sleep timer off" else "Playback pauses after $timerMinutes minutes", color = PlayerMuted, fontSize = 11.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(bookmarkMs?.let { "Saved bookmark · ${formatPlayerTime(it)}" } ?: "No bookmark saved", color = PlayerMuted, fontSize = 11.sp)
                        if (bookmarkMs != null) TextButton(onClick = { onSeekBookmark(bookmarkMs) }, contentPadding = PaddingValues(0.dp)) { Text("Jump to bookmark") }
                    }
                    TextButton(onClick = onBookmark, enabled = track != null) { Icon(Icons.Filled.BookmarkAdd, null); Spacer(Modifier.width(5.dp)); Text("Save") }
                }
            }
        }
        item {
            ControlCard("Up next · ${queue.size}") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSaveQueue, enabled = queue.isNotEmpty(), modifier = Modifier.weight(1f)) { Icon(Icons.Filled.PlaylistAdd, null); Spacer(Modifier.width(5.dp)); Text("Save as playlist") }
                    TextButton(onClick = onClearQueue, enabled = queue.isNotEmpty()) { Text("Clear") }
                }
                if (queue.isEmpty()) Text("Your queue is empty.", color = PlayerMuted, fontSize = 12.sp)
                queue.forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", color = PlayerMuted, fontSize = 11.sp, modifier = Modifier.width(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (item.id == track?.id) FontWeight.SemiBold else FontWeight.Normal)
                            Text(item.artist, color = PlayerMuted, fontSize = 11.sp, maxLines = 1)
                        }
                        IconButton(onClick = { if (index > 0) onMoveQueueItem(index, index - 1) }, enabled = index > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { if (index < queue.lastIndex) onMoveQueueItem(index, index + 1) }, enabled = index < queue.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
                        IconButton(onClick = { onRemoveQueueItem(index) }) { Icon(Icons.Filled.Close, "Remove from queue") }
                    }
                }
            }
        }
        item { Text("Add a song next", fontWeight = FontWeight.SemiBold, fontSize = 18.sp) }
        itemsIndexed(library.take(60), key = { _, item -> item.id }) { _, item ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).clickable { onAddNext(item) }.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.artist, color = PlayerMuted, fontSize = 11.sp)
                }
                Icon(Icons.Filled.PlaylistAdd, "Add next", tint = PlayerClay)
            }
        }
    }
}

@Composable private fun ControlCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(9.dp))
        content()
    }
}

private fun formatPlayerTime(ms: Long): String = "%d:%02d".format(ms.coerceAtLeast(0L) / 60000, (ms.coerceAtLeast(0L) / 1000) % 60)
