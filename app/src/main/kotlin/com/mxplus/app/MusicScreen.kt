package com.mxplus.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

object MusicPlayerHost {
    var player by mutableStateOf<ExoPlayer?>(null)
    var queue by mutableStateOf<List<SongItem>>(emptyList())
    var index by mutableIntStateOf(0)

    fun play(context: android.content.Context, songs: List<SongItem>, start: Int) {
        var p = player
        if (p == null) {
            p = ExoPlayer.Builder(context.applicationContext).setHandleAudioBecomingNoisy(true).build()
            player = p
            ActivePlayer.player = p
        }
        if (Prefs.bgPlay) {
            runCatching {
                val i = android.content.Intent(context.applicationContext, PlaybackService::class.java)
                androidx.core.content.ContextCompat.startForegroundService(context.applicationContext, i)
            }
        }
        queue = songs
        index = start
        p.setMediaItems(
            songs.map { MediaItem.Builder().setUri(it.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).build()).build() },
            start,
            0L,
        )
        p.prepare()
        p.play()
    }

    fun release() {
        player?.release()
        player = null
    }
}

fun hasAudioPermission(context: android.content.Context): Boolean {
    val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun MusicScreen(haze: HazeState, backdrop: com.kyant.backdrop.backdrops.LayerBackdrop?) {
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(hasAudioPermission(context)) }
    var nowPlaying by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitted = hasAudioPermission(context)
        tick++
    }
    LaunchedEffect(permitted, tick) { if (permitted) MusicLibrary.refresh(context) }



    val songs = MusicLibrary.songs
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.Bottom) {
                Text("Music", color = MaterialTheme.colorScheme.onBackground, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                if (songs.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    Text("${songs.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            when {
                !permitted -> Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Allow access to your music", color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("MX Plus only reads audio on this phone. Nothing is uploaded.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton("Allow") {
                        val perms = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        launcher.launch(perms)
                    }
                }
                songs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (MusicLibrary.loading || !MusicLibrary.loaded) "Loading music" else "No music found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 17.sp,
                    )
                }
                else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 190.dp)) {
                    items(songs.size) { i ->
                        SongRow(songs[i], i == MusicPlayerHost.index && MusicPlayerHost.player != null) {
                            MusicPlayerHost.play(context, songs, i)
                            nowPlaying = true
                        }
                    }
                }
            }
        }

        // Mini player
        val p = MusicPlayerHost.player
        val current = MusicPlayerHost.queue.getOrNull(MusicPlayerHost.index)
        if (p != null && current != null && !nowPlaying) {
            var playing by remember { mutableStateOf(p.isPlaying) }
            LaunchedEffect(p) {
                while (true) {
                    playing = p.isPlaying
                    val idx = p.currentMediaItemIndex
                    if (idx != MusicPlayerHost.index) MusicPlayerHost.index = idx
                    delay(400)
                }
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 20.dp, end = 20.dp, bottom = 96.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(24.dp), haze, isDarkTheme(), backdrop)
                    .pressable { nowPlaying = true }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArt(current, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(current.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(current.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(40.dp).pressable { if (p.isPlaying) p.pause() else p.play() }, contentAlignment = Alignment.Center) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                }
                Box(Modifier.size(40.dp).pressable { if (p.hasNextMediaItem()) p.seekToNextMediaItem() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SkipNext, "Next", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
                }
            }
        }

        if (nowPlaying && MusicPlayerHost.player != null) {
            NowPlayingDialog(haze, backdrop) { nowPlaying = false }
        }
    }
}

@Composable
private fun SongRow(s: SongItem, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).pressable(onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(s, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                s.title,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(s.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Text(formatTime(s.durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun AlbumArt(s: SongItem, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val bmp by produceState<android.graphics.Bitmap?>(null, s.id) { value = MusicLibrary.artwork(context, s) }
    Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size / 2))
        }
    }
}

@Composable
private fun NowPlayingDialog(haze: HazeState, backdrop: com.kyant.backdrop.backdrops.LayerBackdrop?, onClose: () -> Unit) {
    val p = MusicPlayerHost.player ?: return
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    var repeat by remember { mutableIntStateOf(p.repeatMode) }
    var shuffle by remember { mutableStateOf(p.shuffleModeEnabled) }

    LaunchedEffect(p) {
        while (true) {
            pos = p.currentPosition
            dur = p.duration.coerceAtLeast(0L)
            playing = p.isPlaying
            val idx = p.currentMediaItemIndex
            if (idx != MusicPlayerHost.index) MusicPlayerHost.index = idx
            delay(250)
        }
    }

    val song = MusicPlayerHost.queue.getOrNull(MusicPlayerHost.index)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).pressable(onClose), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Now Playing", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(40.dp))
                }
                Spacer(Modifier.height(20.dp))
                if (song != null) {
                    val context = LocalContext.current
                    val bmp by produceState<android.graphics.Bitmap?>(null, song.id) { value = MusicLibrary.artwork(context, song) }
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        val b = bmp
                        if (b != null) {
                            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(96.dp))
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(song.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(20.dp))
                Slider(
                    value = (scrub ?: pos.toFloat()).coerceIn(0f, dur.coerceAtLeast(1L).toFloat()),
                    onValueChange = { scrub = it },
                    onValueChangeFinished = {
                        scrub?.let { p.seekTo(it.toLong()) }
                        scrub = null
                    },
                    valueRange = 0f..dur.coerceAtLeast(1L).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime((scrub ?: pos.toFloat()).toLong()), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(formatTime(dur), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).pressable {
                        shuffle = !shuffle
                        p.shuffleModeEnabled = shuffle
                    }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    }
                    Box(Modifier.size(60.dp).pressable { p.seekToPreviousMediaItem() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.SkipPrevious, "Previous", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(36.dp))
                    }
                    Box(
                        Modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).pressable { if (p.isPlaying) p.pause() else p.play() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(40.dp))
                    }
                    Box(Modifier.size(60.dp).pressable { p.seekToNextMediaItem() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.SkipNext, "Next", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(36.dp))
                    }
                    Box(Modifier.size(44.dp).pressable {
                        repeat = when (repeat) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                        p.repeatMode = repeat
                    }, contentAlignment = Alignment.Center) {
                        Icon(
                            if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            "Repeat",
                            tint = if (repeat == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}
