package com.nuvio.app.features.photos

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val EditorBg = Color(0xFF000000)
private val EditorPanel = Color(0xFF1C1C1E)
private val EditorAccent = Color(0xFFFFD60A)

private enum class EditTab(val label: String) { Crop("Crop"), Adjust("Adjust"), Filters("Filters"), Markup("Markup"), Text("Text") }

private enum class AdjustKey(val label: String) {
    Exposure("Exposure"), Brightness("Brightness"), Contrast("Contrast"), Saturation("Saturation"),
    Warmth("Warmth"), Highlights("Highlights"), Shadows("Shadows"), Vignette("Vignette"),
}

private fun EditState.adjust(k: AdjustKey): Float = when (k) {
    AdjustKey.Exposure -> exposure
    AdjustKey.Brightness -> brightness
    AdjustKey.Contrast -> contrast
    AdjustKey.Saturation -> saturation
    AdjustKey.Warmth -> warmth
    AdjustKey.Highlights -> highlights
    AdjustKey.Shadows -> shadows
    AdjustKey.Vignette -> vignette
}

private fun EditState.withAdjust(k: AdjustKey, v: Float): EditState = when (k) {
    AdjustKey.Exposure -> copy(exposure = v)
    AdjustKey.Brightness -> copy(brightness = v)
    AdjustKey.Contrast -> copy(contrast = v)
    AdjustKey.Saturation -> copy(saturation = v)
    AdjustKey.Warmth -> copy(warmth = v)
    AdjustKey.Highlights -> copy(highlights = v)
    AdjustKey.Shadows -> copy(shadows = v)
    AdjustKey.Vignette -> copy(vignette = v)
}

private val markupColors = listOf(Color.White, Color.Black, Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFD60A), Color(0xFF34C759), Color(0xFF0A84FF), Color(0xFFBF5AF2))

@Composable
fun PhotoEditorDialog(photo: PhotoItem, onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source by produceState<Bitmap?>(null, photo.uri) {
        value = withContext(Dispatchers.IO) { runCatching { ThumbCache.decodeSampled(context.contentResolver, photo.uri, 1280) }.getOrNull() }
    }
    var state by remember { mutableStateOf(EditState()) }
    var tab by remember { mutableStateOf(EditTab.Crop) }
    var adjustKey by remember { mutableStateOf(AdjustKey.Exposure) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var markupColor by remember { mutableStateOf(markupColors[2]) }
    var markupWidth by remember { mutableStateOf(0.012f) }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var textDialog by remember { mutableStateOf(false) }
    var textColor by remember { mutableStateOf(Color.White) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(source, state) {
        val src = source ?: return@LaunchedEffect
        kotlinx.coroutines.delay(30)
        preview = withContext(Dispatchers.Default) { PhotoEditorEngine.render(src, state, 1280) }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = true),
    ) {
        Column(modifier = Modifier.fillMaxSize().background(EditorBg).statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) { Text("Cancel", color = Color.White) }
                Box(Modifier.weight(1f))
                TextButton(onClick = { state = EditState() }) { Text("Revert", color = Color.White.copy(alpha = 0.7f)) }
                TextButton(
                    enabled = !saving && source != null,
                    onClick = {
                        saving = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                runCatching {
                                    val big = ThumbCache.decodeSampled(context.contentResolver, photo.uri, 4096)
                                        ?: error("decode")
                                    val out = PhotoEditorEngine.render(big, state, 4096)
                                    saveBitmap(context, out, photo.name)
                                }.isSuccess
                            }
                            saving = false
                            Toast.makeText(context, if (ok) "Saved as a new photo" else "Could not save", Toast.LENGTH_SHORT).show()
                            if (ok) onSaved()
                        }
                    },
                ) { Text(if (saving) "Saving" else "Save", color = EditorAccent, fontWeight = FontWeight.Bold) }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged { boxSize = it },
                contentAlignment = Alignment.Center,
            ) {
                val pv = preview
                if (pv == null) {
                    Text("Loading", color = Color.White.copy(alpha = 0.6f))
                } else {
                    val scale = minOf(boxSize.width.toFloat() / pv.width, boxSize.height.toFloat() / pv.height)
                    val iw = pv.width * scale
                    val ih = pv.height * scale
                    val left = (boxSize.width - iw) / 2f
                    val top = (boxSize.height - ih) / 2f
                    Image(
                        bitmap = pv.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (tab == EditTab.Markup || tab == EditTab.Text) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(tab, markupColor, markupWidth, iw, ih) {
                                    detectDragGestures(
                                        onDragStart = { o ->
                                            if (tab == EditTab.Markup) currentStroke = listOf(o)
                                        },
                                        onDragEnd = {
                                            if (tab == EditTab.Markup && currentStroke.size > 1) {
                                                val pts = currentStroke.map { Pair(((it.x - left) / iw).coerceIn(0f, 1f), ((it.y - top) / ih).coerceIn(0f, 1f)) }
                                                state = state.copy(strokes = state.strokes + StrokeItem(pts, markupColor.toArgb(), markupWidth))
                                            }
                                            currentStroke = emptyList()
                                        },
                                        onDragCancel = { currentStroke = emptyList() },
                                    ) { change, drag ->
                                        change.consume()
                                        if (tab == EditTab.Markup) {
                                            currentStroke = currentStroke + change.position
                                        } else if (state.texts.isNotEmpty()) {
                                            val last = state.texts.last()
                                            val nx = (last.x + drag.x / iw).coerceIn(0f, 1f)
                                            val ny = (last.y + drag.y / ih).coerceIn(0f, 1f)
                                            state = state.copy(texts = state.texts.dropLast(1) + last.copy(x = nx, y = ny))
                                        }
                                    }
                                },
                        ) {
                            if (currentStroke.size > 1) {
                                val path = Path()
                                path.moveTo(currentStroke[0].x, currentStroke[0].y)
                                for (i in 1 until currentStroke.size) path.lineTo(currentStroke[i].x, currentStroke[i].y)
                                drawPath(
                                    path,
                                    markupColor,
                                    style = Stroke(width = markupWidth * maxOf(iw, ih), cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().background(EditorPanel).padding(top = 8.dp)) {
                when (tab) {
                    EditTab.Crop -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Chip("Rotate", false) { state = state.copy(rotation = (state.rotation + 3) % 4) }
                            Chip("Flip", state.flip) { state = state.copy(flip = !state.flip) }
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val aspects = listOf("Original" to 0f, "Square" to 1f, "4:3" to 4f / 3f, "3:4" to 3f / 4f, "16:9" to 16f / 9f, "9:16" to 9f / 16f)
                            items(aspects) { (label, a) -> Chip(label, state.aspect == a) { state = state.copy(aspect = a) } }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Straighten", color = Color.White, fontSize = 13.sp, modifier = Modifier.width(84.dp))
                            Slider(
                                value = state.straighten,
                                onValueChange = { state = state.copy(straighten = it) },
                                valueRange = -30f..30f,
                                modifier = Modifier.weight(1f),
                            )
                            Text(String.format("%.0f", state.straighten), color = Color.White, fontSize = 13.sp, modifier = Modifier.width(32.dp))
                        }
                    }
                    EditTab.Adjust -> {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(AdjustKey.entries.toList()) { k ->
                                Chip(k.label, adjustKey == k, dot = state.adjust(k) != 0f) { adjustKey = k }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = state.adjust(adjustKey),
                                onValueChange = { state = state.withAdjust(adjustKey, it) },
                                valueRange = -1f..1f,
                                modifier = Modifier.weight(1f),
                            )
                            Text(String.format("%d", (state.adjust(adjustKey) * 100).toInt()), color = Color.White, fontSize = 13.sp, modifier = Modifier.width(40.dp))
                        }
                    }
                    EditTab.Filters -> {
                        val small = remember(source) { source?.let { Bitmap.createScaledBitmap(it, 96, (96f * it.height / it.width).toInt().coerceAtLeast(1), true) } }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(PhotoFilter.entries.toList()) { f ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { state = state.copy(filter = f) }) {
                                    val thumb = remember(small, f) { small?.let { PhotoEditorEngine.render(it, EditState(filter = f, aspect = 1f), 96) } }
                                    Box(
                                        Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                                            .background(if (state.filter == f) EditorAccent else Color.DarkGray)
                                            .padding(if (state.filter == f) 2.dp else 0.dp),
                                    ) {
                                        if (thumb != null) Image(thumb.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)))
                                    }
                                    Text(f.label, color = if (state.filter == f) EditorAccent else Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                    EditTab.Markup -> {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            items(markupColors) { c ->
                                Box(
                                    Modifier.size(30.dp).clip(CircleShape).background(c)
                                        .padding(if (markupColor == c) 0.dp else 0.dp)
                                        .clickable { markupColor = c },
                                ) {
                                    if (markupColor == c) Box(Modifier.align(Alignment.Center).size(10.dp).clip(CircleShape).background(if (c == Color.White) Color.Black else Color.White))
                                }
                            }
                            item { Chip("Undo", false) { if (state.strokes.isNotEmpty()) state = state.copy(strokes = state.strokes.dropLast(1)) } }
                            item { Chip("Clear", false) { state = state.copy(strokes = emptyList()) } }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Size", color = Color.White, fontSize = 13.sp, modifier = Modifier.width(44.dp))
                            Slider(value = markupWidth, onValueChange = { markupWidth = it }, valueRange = 0.004f..0.04f, modifier = Modifier.weight(1f))
                        }
                    }
                    EditTab.Text -> {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            item { Chip("Add text", false) { textDialog = true } }
                            items(markupColors) { c ->
                                Box(Modifier.size(30.dp).clip(CircleShape).background(c).clickable {
                                    textColor = c
                                    if (state.texts.isNotEmpty()) state = state.copy(texts = state.texts.dropLast(1) + state.texts.last().copy(color = c.toArgb()))
                                })
                            }
                            item { Chip("Delete", false) { if (state.texts.isNotEmpty()) state = state.copy(texts = state.texts.dropLast(1)) } }
                        }
                        Text("Drag on the photo to move the last text", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    EditTab.entries.forEach { t ->
                        Text(
                            t.label,
                            color = if (tab == t) EditorAccent else Color.White,
                            fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { tab = t }.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (textDialog) {
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { textDialog = false },
            title = { Text("Add text") },
            text = { TextField(value = value, onValueChange = { value = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (value.isNotBlank()) {
                        state = state.copy(texts = state.texts + TextItem(value.trim(), 0.5f, 0.5f, textColor.toArgb(), 0.07f))
                    }
                    textDialog = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { textDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, dot: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) EditorAccent else Color(0xFF2C2C2E))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label + if (dot && !selected) " \u2022" else "",
            color = if (selected) Color.Black else Color.White,
            fontSize = 13.sp,
        )
    }
}

private fun saveBitmap(context: Context, bmp: Bitmap, originalName: String): Uri {
    val base = originalName.substringBeforeLast('.', originalName)
    val name = "${base}_edit_${System.currentTimeMillis() % 100000}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MX Plus")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val collection = if (Build.VERSION.SDK_INT >= 29) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val uri = context.contentResolver.insert(collection, values) ?: error("insert")
    context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) } ?: error("stream")
    if (Build.VERSION.SDK_INT >= 29) {
        val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        context.contentResolver.update(uri, done, null, null)
    }
    return uri
}
