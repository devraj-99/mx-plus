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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// 0 list, 1 grid 2-col, 2 grid 3-col
private var viewModeState = mutableStateOf(0)
private var sortModeState = mutableStateOf(0) // 0 date, 1 name, 2 size, 3 duration
private val favsState = mutableStateOf<Set<String>>(emptySet())

private fun favSp(context: android.content.Context) = context.getSharedPreferences("mxplus_videos", android.content.Context.MODE_PRIVATE)
private fun loadFavs(context: android.content.Context) { favsState.value = favSp(context).getStringSet("favs", emptySet()) ?: emptySet() }
private fun toggleFav(context: android.content.Context, uri: String) {
    val cur = favsState.value
    val next = if (uri in cur) cur - uri else cur + uri
    favsState.value = next
    favSp(context).edit().putStringSet("favs", next).apply()
}

private fun sortVideos(list: List<VideoItem>, mode: Int): List<VideoItem> = when (mode) {
    1 -> list.sortedBy { it.title.lowercase() }
    2 -> list.sortedByDescending { it.size }
    3 -> list.sortedByDescending { it.durationMs }
    else -> list.sortedByDescending { it.added }
}

@Composable
fun VideosScreen(access: Access, onRequestAccess: () -> Unit, onOpenSettings: () -> Unit, onRefresh: () -> Unit, onPickFile: () -> Unit, onAllFiles: () -> Unit, onPlay: (List<VideoItem>, Int) -> Unit) {
    val context = LocalContext.current
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showSort by remember { mutableStateOf(false) }
    val viewMode = viewModeState.value
    val sortMode = sortModeState.value
    val favs = favsState.value
    androidx.compose.runtime.LaunchedEffect(Unit) { loadFavs(context) }

    val videos = VideoLibrary.videos
    val filtered = remember(videos, query, sortMode, favs) {
        val base = if (query.isBlank()) videos else videos.filter { it.title.contains(query, true) }
        sortVideos(base, sortMode)
    }
    val favVideos = remember(videos, favs) { videos.filter { it.uri.toString() in favs } }
    val continueWatching = remember(videos) {
        videos.filter { Prefs.resumePosition(it.uri.toString()) > 0 }.take(12)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (searching) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search videos") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        Icon(Icons.Rounded.Close, "Close", modifier = Modifier.clickable { searching = false; query = "" })
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Videos", color = MaterialTheme.colorScheme.onBackground, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                if (videos.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    Text("${filtered.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp, modifier = Modifier.padding(bottom = 2.dp))
                }
                Spacer(Modifier.weight(1f))
                HeaderIcon(Icons.Rounded.Search, "Search") { searching = true }
                Spacer(Modifier.width(8.dp))
                HeaderIcon(if (viewMode == 0) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList, "View") {
                    viewModeState.value = (viewModeState.value + 1) % 3
                }
                Spacer(Modifier.width(8.dp))
                HeaderIcon(Icons.AutoMirrored.Rounded.Sort, "Sort") { showSort = true }
            }
        }
        when {
            access == Access.NONE && !VideoLibrary.hasAllFiles() -> PermissionCard(onRequestAccess, onPickFile, onAllFiles)
            else -> {
                if (access == Access.PARTIAL) PartialBanner(onOpenSettings)
                if (videos.isEmpty()) {
                    EmptyState(if (VideoLibrary.loading || !VideoLibrary.loaded) "Loading videos" else "No videos found", onRefresh, onPickFile, onAllFiles)
                } else if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nothing matches \"$query\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                        if (query.isBlank() && continueWatching.isNotEmpty()) {
                            item(key = "cw") { ContinueWatchingRail(continueWatching, onPlay) }
                        }
                        if (query.isBlank() && favVideos.isNotEmpty()) {
                            item(key = "fv") { FavouritesRail(favVideos, onPlay) }
                        }
                        item(key = "main") {
                            when (viewMode) {
                                0 -> VideoListColumn(filtered, favs, { toggleFav(context, it) }, onPlay)
                                else -> VideoGridSection(filtered, if (viewMode == 1) 2 else 3, onPlay)
                            }
                        }
                    }
                }
            }
        }
        if (showSort) {
            SortDialog(sortMode) { sortModeState.value = it; showSort = false }
        }
    }
}

@Composable
private fun HeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun SortDialog(selected: Int, onPick: (Int) -> Unit) {
    val labels = listOf("Newest first", "Name (A to Z)", "Largest first", "Longest first")
    Dialog(onDismissRequest = { onPick(selected) }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(vertical = 14.dp)) {
            Text("Sort by", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            labels.forEachIndexed { i, label ->
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clickable { onPick(i) }.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        color = if (i == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingRail(list: List<VideoItem>, onPlay: (List<VideoItem>, Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Continue watching", color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list.size) { i ->
                val v = list[i]
                RailCard(v, onClick = { onPlay(list, i) })
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun FavouritesRail(list: List<VideoItem>, onPlay: (List<VideoItem>, Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFFF4D6D), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Favourites", color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list.size) { i ->
                val v = list[i]
                RailCard(v, onClick = { onPlay(list, i) })
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun RailCard(v: VideoItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, v.id) { value = VideoLibrary.thumbnail(context, v)?.asImageBitmap() }
    Column(Modifier.width(168.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        Box(Modifier.width(168.dp).height(94.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            val b = bmp
            if (b != null) {
                Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center).size(30.dp))
            }
            val resumeMs = Prefs.resumePosition(v.uri.toString())
            if (resumeMs > 0 && v.durationMs > 0) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(Modifier.fillMaxWidth(resumeMs.toFloat() / v.durationMs).height(4.dp).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        Text(
            v.title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
private fun VideoListColumn(list: List<VideoItem>, favs: Set<String>, onFav: (String) -> Unit, onPlay: (List<VideoItem>, Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        list.forEachIndexed { i, v ->
            VideoRow(v, v.uri.toString() in favs, { onFav(v.uri.toString()) }) { onPlay(list, i) }
        }
    }
}

@Composable
private fun VideoGridSection(list: List<VideoItem>, cols: Int, onPlay: (List<VideoItem>, Int) -> Unit) {
    Column(Modifier.padding(horizontal = 14.dp)) {
        list.chunked(cols).forEachIndexed { rowIdx, rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { v ->
                    val i = rowIdx * cols + rowItems.indexOf(v)
                    Box(Modifier.weight(1f).padding(bottom = 10.dp)) {
                        GridCard(v) { onPlay(list, i) }
                    }
                }
                repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun GridCard(v: VideoItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, v.id) { value = VideoLibrary.thumbnail(context, v)?.asImageBitmap() }
    Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            val b = bmp
            if (b != null) {
                Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center).size(30.dp))
            }
            Text(
                formatTime(v.durationMs),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Column(Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)) {
            Text(v.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val res = if (v.height > 0) "${v.height}p  ·  " else ""
            Text("$res${formatSize(v.size)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
private fun VideoRow(v: VideoItem, isFav: Boolean, onFav: () -> Unit, onClick: () -> Unit) {
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
        Box(Modifier.size(36.dp).clickable(onClick = onFav), contentAlignment = Alignment.Center) {
            Icon(
                if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                "Favourite",
                tint = if (isFav) Color(0xFFFF4D6D) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(text: String, onRefresh: () -> Unit, onPickFile: () -> Unit, onAllFiles: () -> Unit) {
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
        Text(VideoLibrary.diagnostics(LocalContext.current), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
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

@Composable
fun FoldersScreen(access: Access, onRequestAccess: () -> Unit, onRefresh: () -> Unit, onPickFile: () -> Unit, onAllFiles: () -> Unit, onPlay: (List<VideoItem>, Int) -> Unit) {
    var open by remember { mutableStateOf<String?>(null) }
    val videos = VideoLibrary.videos
    androidx.activity.compose.BackHandler(enabled = open != null) { open = null }
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { loadFavs(context) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        val folder = open
        if (folder == null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.Bottom) {
                Text("Folders", color = MaterialTheme.colorScheme.onBackground, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
            if (access == Access.NONE && !VideoLibrary.hasAllFiles()) {
                PermissionCard(onRequestAccess, onPickFile, onAllFiles)
            } else if (videos.isEmpty()) {
                EmptyState(if (VideoLibrary.loading || !VideoLibrary.loaded) "Loading videos" else "No videos found", onRefresh, onPickFile, onAllFiles)
            } else {
                val groups = videos.groupBy { it.folder }.toList().sortedBy { it.first.lowercase() }
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp)) {
                    items(groups.size) { gi ->
                        val (name, list) = groups[gi]
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
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { open = null },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = MaterialTheme.colorScheme.onBackground) }
                Spacer(Modifier.width(12.dp))
                Text(folder, color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                val list = videos.filter { it.folder == folder }
                list.forEachIndexed { i, v ->
                    VideoRow(v, v.uri.toString() in favsState.value, { toggleFav(context, v.uri.toString()) }) { onPlay(list, i) }
                }
            }
        }
    }
}