package com.mxplus.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VideosScreen(access: Access, onRequestAccess: () -> Unit, onOpenSettings: () -> Unit, onRefresh: () -> Unit, onPickFile: () -> Unit, onAllFiles: () -> Unit, onPlay: (List<VideoItem>, Int) -> Unit) {
    val videos = VideoLibrary.videos
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Header("Videos", if (videos.isNotEmpty()) "${videos.size}" else null)
        when {
            access == Access.NONE && !VideoLibrary.hasAllFiles() -> PermissionCard(onRequestAccess, onPickFile, onAllFiles)
            else -> {
                if (access == Access.PARTIAL) PartialBanner(onOpenSettings)
                if (videos.isEmpty()) {
                    EmptyState(if (VideoLibrary.loading || !VideoLibrary.loaded) "Loading videos" else "No videos found", onRefresh, onPickFile, onAllFiles)
                } else {
                    VideoList(videos, onPlay)
                }
            }
        }
    }
}

@Composable
fun FoldersScreen(access: Access, onRequestAccess: () -> Unit, onRefresh: () -> Unit, onPickFile: () -> Unit, onAllFiles: () -> Unit, onPlay: (List<VideoItem>, Int) -> Unit) {
    var open by remember { mutableStateOf<String?>(null) }
    val videos = VideoLibrary.videos
    androidx.activity.compose.BackHandler(enabled = open != null) { open = null }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        val folder = open
        if (folder == null) {
            Header("Folders", null)
            if (access == Access.NONE && !VideoLibrary.hasAllFiles()) {
                PermissionCard(onRequestAccess, onPickFile, onAllFiles)
            } else if (videos.isEmpty()) {
                EmptyState(if (VideoLibrary.loading || !VideoLibrary.loaded) "Loading videos" else "No videos found", onRefresh, onPickFile, onAllFiles)
            } else {
                val groups = videos.groupBy { it.folder }.toList().sortedBy { it.first.lowercase() }
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp)) {
                    items(groups) { (name, list) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { open = name }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${list.size} video" + if (list.size == 1) "" else "s", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { open = null },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = MaterialTheme.colorScheme.onBackground) }
                Spacer(Modifier.width(12.dp))
                Text(folder, color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            VideoList(videos.filter { it.folder == folder }, onPlay)
        }
    }
}

@Composable
private fun Header(title: String, count: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        if (count != null) {
            Spacer(Modifier.width(10.dp))
            Text(count, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun VideoList(list: List<VideoItem>, onPlay: (List<VideoItem>, Int) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp)) {
        items(list.size) { i ->
            VideoRow(list[i]) { onPlay(list, i) }
        }
    }
}

@Composable
private fun VideoRow(v: VideoItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, v.id) { value = VideoLibrary.thumbnail(context, v)?.asImageBitmap() }
    val resumeMs = if (Prefs.resume) Prefs.resumePosition(v.uri.toString()) else 0L
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(128.dp).height(72.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            val b = bmp
            if (b != null) {
                Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center).size(28.dp))
            }
            Text(
                formatTime(v.durationMs),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 5.dp, vertical = 1.dp),
            )
            if (resumeMs > 0 && v.durationMs > 0) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth(resumeMs.toFloat() / v.durationMs).height(3.dp).background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(v.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            val res = if (v.height > 0) "${v.height}p  ·  " else ""
            Text("$res${formatSize(v.size)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EmptyState(text: String, onRefresh: () -> Unit, onPickFile: () -> Unit, onAllFiles: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Refresh", onRefresh)
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Open a video file", onPickFile)
        if (!VideoLibrary.hasAllFiles()) {
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Scan all files", onAllFiles)
        }
        Spacer(Modifier.height(20.dp))
        Text(VideoLibrary.diagnostics(context), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit, onPickFile: () -> Unit, onAllFiles: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Allow access to your videos", color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("MX Plus only reads media on this phone. Nothing is uploaded.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Allow", onRequest)
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Scan all files", onAllFiles)
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Open a video file", onPickFile)
    }
}

@Composable
private fun PartialBanner(onOpenSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onOpenSettings).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Only some videos are shared with MX Plus. Tap to allow all.", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.primary).clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 12.dp),
    ) { Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}
