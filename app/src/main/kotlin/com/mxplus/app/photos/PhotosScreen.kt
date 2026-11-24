package com.mxplus.app.photos

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.lazy.grid.items
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

private sealed class GridEntry {
    data class Header(val key: String, val label: String) : GridEntry()
    data class Cell(val index: Int, val photo: PhotoItem) : GridEntry()
}

private fun hasImagePermission(context: Context): Boolean {
    fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    return if (Build.VERSION.SDK_INT >= 33) {
        granted(Manifest.permission.READ_MEDIA_IMAGES) ||
            (Build.VERSION.SDK_INT >= 34 && granted("android.permission.READ_MEDIA_VISUAL_USER_SELECTED"))
    } else {
        granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun favPrefs(context: Context) = context.getSharedPreferences("mxplus_photos", Context.MODE_PRIVATE)

@Composable
fun PhotosScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var permitted by remember { mutableStateOf(hasImagePermission(context)) }
    var loaded by remember { mutableStateOf(false) }
    val photos = remember { mutableStateListOf<PhotoItem>() }
    val favs = remember { mutableStateListOf<Long>().also { l -> favPrefs(context).getStringSet("favs", emptySet())?.mapNotNull { it.toLongOrNull() }?.let { l.addAll(it) } } }
    var onlyFavs by remember { mutableStateOf(false) }
    var columns by remember { mutableStateOf(4) }
    var viewerIndex by remember { mutableStateOf(-1) }
    var editingPhoto by remember { mutableStateOf<PhotoItem?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitted = hasImagePermission(context)
    }

    LaunchedEffect(permitted, reloadTick) {
        if (permitted) {
            val list = PhotosStore.query(context)
            photos.clear()
            photos.addAll(list)
            loaded = true
        }
    }

    fun toggleFav(p: PhotoItem) {
        if (favs.contains(p.id)) favs.remove(p.id) else favs.add(p.id)
        favPrefs(context).edit().putStringSet("favs", favs.map { it.toString() }.toSet()).apply()
    }

    val shown = if (onlyFavs) photos.filter { favs.contains(it.id) } else photos.toList()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Photos",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                FilterChipText("All", !onlyFavs) { onlyFavs = false }
                Box(Modifier.size(8.dp))
                FilterChipText("Favourites", onlyFavs) { onlyFavs = true }
            }
            when {
                !permitted -> PermissionCard {
                    val perms = if (Build.VERSION.SDK_INT >= 33) {
                        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                    } else {
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    permLauncher.launch(perms)
                }
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading photos", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (onlyFavs) "No favourites yet" else "No photos found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> PhotoGrid(
                    photos = shown,
                    columns = columns,
                    onColumnsChange = { columns = it },
                    onOpen = { idx -> viewerIndex = idx },
                )
            }
        }

        if (viewerIndex in shown.indices) {
            PhotoViewer(
                photos = shown,
                startIndex = viewerIndex,
                favs = favs,
                onToggleFav = { toggleFav(it) },
                onEdit = { editingPhoto = it },
                onDeleted = { reloadTick++ },
                onClose = { viewerIndex = -1 },
            )
        }
        val ep = editingPhoto
        if (ep != null) {
            PhotoEditorDialog(
                photo = ep,
                onClose = { editingPhoto = null },
                onSaved = {
                    editingPhoto = null
                    reloadTick++
                },
            )
        }
    }
}

@Composable
private fun FilterChipText(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Allow access to your photos", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Box(Modifier.height(8.dp))
        Text("MX Plus only reads photos on this phone. Nothing is uploaded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onRequest)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) { Text("Allow", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun PhotoGrid(
    photos: List<PhotoItem>,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    onOpen: (Int) -> Unit,
) {
    val entries = remember(photos, columns) {
        val list = ArrayList<GridEntry>(photos.size + 64)
        var lastKey = ""
        photos.forEachIndexed { i, p ->
            val key = if (columns <= 3) PhotosStore.dayKey(p.dateTakenMs) else PhotosStore.monthKey(p.dateTakenMs)
            if (key != lastKey) {
                lastKey = key
                list.add(GridEntry.Header(key, if (columns <= 3) PhotosStore.dayLabel(p.dateTakenMs) else PhotosStore.monthLabel(p.dateTakenMs)))
            }
            list.add(GridEntry.Cell(i, p))
        }
        list
    }
    val gridState: LazyGridState = rememberLazyGridState()
    var accumulated by remember { mutableFloatStateOf(1f) }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(columns) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var lastDist = 0f
                    accumulated = 1f
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            val a = pressed[0].position
                            val b = pressed[1].position
                            val dx = a.x - b.x
                            val dy = a.y - b.y
                            val d = sqrt(dx * dx + dy * dy)
                            if (lastDist > 0f) accumulated *= d / lastDist
                            lastDist = d
                            if (accumulated > 1.3f) {
                                onColumnsChange((columns - 1).coerceAtLeast(2))
                                accumulated = 1f
                            } else if (accumulated < 0.77f) {
                                onColumnsChange((columns + 1).coerceAtMost(6))
                                accumulated = 1f
                            }
                            event.changes.forEach { it.consume() }
                        } else {
                            lastDist = 0f
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentPadding = PaddingValues(bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        items(
            items = entries,
            key = { e -> when (e) { is GridEntry.Header -> "h-" + e.key; is GridEntry.Cell -> "p-" + e.photo.id } },
            span = { e -> if (e is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
        ) { e ->
            when (e) {
                is GridEntry.Header -> Text(
                    e.label,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                is GridEntry.Cell -> Thumb(
                    uri = e.photo.uri,
                    px = if (columns >= 5) 256 else 384,
                    modifier = Modifier.aspectRatio(1f).clickable { onOpen(e.index) },
                )
            }
        }
    }
}

@Composable
internal fun Thumb(uri: android.net.Uri, px: Int, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val bmp by produceState<android.graphics.Bitmap?>(null, uri, px) {
        value = ThumbCache.thumb(context.contentResolver, uri, px)
    }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = scale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PhotoViewer(
    photos: List<PhotoItem>,
    startIndex: Int,
    favs: List<Long>,
    onToggleFav: (PhotoItem) -> Unit,
    onEdit: (PhotoItem) -> Unit,
    onDeleted: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = startIndex, pageCount = { photos.size })
    val strip = rememberLazyListState()
    var zoomed by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var chromeVisible by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    val current = photos.getOrNull(pager.currentPage)

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            onDeleted()
            onClose()
        }
    }

    LaunchedEffect(pager.currentPage) {
        zoomed = false
        strip.animateScrollToItem((pager.currentPage - 2).coerceAtLeast(0))
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val bgAlpha = (1f - (abs(dragY) / 900f)).coerceIn(0.2f, 1f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = bgAlpha))
                .pointerInput(zoomed) {
                    if (!zoomed) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (abs(dragY) > 260f) onClose() else dragY = 0f
                            },
                            onDragCancel = { dragY = 0f },
                        ) { _, amount -> dragY += amount }
                    }
                },
        ) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !zoomed,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    translationY = dragY
                    val s = 1f - (abs(dragY) / 3000f)
                    scaleX = s
                    scaleY = s
                },
                pageSpacing = 12.dp,
            ) { page ->
                ZoomablePhoto(
                    photo = photos[page],
                    active = page == pager.currentPage,
                    onZoomChanged = { z -> if (page == pager.currentPage) zoomed = z },
                    onTap = { chromeVisible = !chromeVisible },
                )
            }

            if (chromeVisible && dragY == 0f) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    if (current != null) {
                        Column(Modifier.weight(1f)) {
                            Text(PhotosStore.dayLabel(current.dateTakenMs), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(PhotosStore.fullDate(current.dateTakenMs), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }
                }
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding(),
                ) {
                    LazyRow(
                        state = strip,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        itemsIndexed(photos) { i, p ->
                            val sel = i == pager.currentPage
                            Thumb(
                                uri = p.uri,
                                px = 128,
                                modifier = Modifier
                                    .size(if (sel) 48.dp else 36.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { scope.launch { pager.scrollToPage(i) } },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {
                            current?.let { p ->
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/*"
                                    putExtra(Intent.EXTRA_STREAM, p.uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }) { Icon(Icons.Rounded.IosShare, contentDescription = "Share", tint = Color.White) }
                        val isFav = current != null && favs.contains(current.id)
                        IconButton(onClick = { current?.let(onToggleFav) }) {
                            Icon(
                                if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favourite",
                                tint = if (isFav) Color(0xFFFF4D6D) else Color.White,
                            )
                        }
                        IconButton(onClick = { showInfo = true }) { Icon(Icons.Rounded.Info, contentDescription = "Info", tint = Color.White) }
                        IconButton(onClick = { current?.let(onEdit) }) { Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = Color.White) }
                        IconButton(onClick = {
                            current?.let { p ->
                                if (Build.VERSION.SDK_INT >= 30) {
                                    val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(p.uri))
                                    deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                                } else {
                                    val rows = runCatching { context.contentResolver.delete(p.uri, null, null) }.getOrDefault(0)
                                    if (rows > 0) {
                                        onDeleted()
                                        onClose()
                                    }
                                }
                            }
                        }) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Color.White) }
                    }
                }
            }
        }
    }

    if (showInfo && current != null) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Close") } },
            title = { Text(current.name) },
            text = {
                Column {
                    Text("Date: " + PhotosStore.fullDate(current.dateTakenMs))
                    Text("Size: " + formatBytes(current.sizeBytes))
                    Text("Resolution: ${current.width} x ${current.height}")
                    if (current.relativePath.isNotBlank()) Text("Path: ${current.relativePath}", fontSize = 12.sp)
                }
            },
        )
    }
}

private fun formatBytes(b: Long): String = when {
    b >= 1_048_576L -> String.format("%.1f MB", b / 1_048_576.0)
    b >= 1024L -> String.format("%d KB", b / 1024L)
    else -> "$b B"
}

@Composable
private fun ZoomablePhoto(
    photo: PhotoItem,
    active: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val bmp by produceState<android.graphics.Bitmap?>(null, photo.uri) {
        val thumb = ThumbCache.thumb(context.contentResolver, photo.uri, 512)
        value = thumb
        val full = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { ThumbCache.decodeSampled(context.contentResolver, photo.uri, 2400) }.getOrNull()
        }
        if (full != null) value = full
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            offset = Offset.Zero
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                        onZoomChanged(scale > 1.05f)
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } >= 2
                        if (multi || scale > 1.02f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale > 1.02f) {
                                val maxX = size.width * (scale - 1f) / 2f
                                val maxY = size.height * (scale - 1f) / 2f
                                Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY),
                                )
                            } else {
                                Offset.Zero
                            }
                            onZoomChanged(scale > 1.02f)
                            event.changes.forEach { c: PointerInputChange -> if (c.positionChanged()) c.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale < 1.02f) {
                        scale = 1f
                        offset = Offset.Zero
                        onZoomChanged(false)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            )
        }
    }
}
