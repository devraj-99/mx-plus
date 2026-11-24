package com.mxplus.app

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VideoItem(
    val id: Long,
    val title: String,
    val uri: Uri,
    val durationMs: Long,
    val size: Long,
    val added: Long,
    val width: Int,
    val height: Int,
    val folder: String,
    val path: String = "",
)

enum class Access { NONE, PARTIAL, FULL }

object VideoLibrary {
    var videos by mutableStateOf<List<VideoItem>>(emptyList())
    var loaded by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var diag by mutableStateOf("")
    var storeCount by mutableStateOf(0)
    var scanCount by mutableStateOf(0)
    var storeError by mutableStateOf("")

    private val thumbs = object : LruCache<Long, Bitmap>(80) {}

    fun access(context: Context): Access {
        val full = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, full) == PackageManager.PERMISSION_GRANTED) return Access.FULL
        if (Build.VERSION.SDK_INT >= 34 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        ) return Access.PARTIAL
        return Access.NONE
    }

    fun permissionsToRequest(): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            list += Manifest.permission.READ_MEDIA_VIDEO
            list += Manifest.permission.READ_MEDIA_IMAGES
            if (Build.VERSION.SDK_INT >= 34) list += Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        } else {
            list += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return list.toTypedArray()
    }

    suspend fun refresh(context: Context) {
        if (access(context) == Access.NONE) {
            loaded = true
            return
        }
        loading = true
        val result = withContext(Dispatchers.IO) {
            val store = query(context.contentResolver)
            storeCount = store.size
            val scanned = if (hasAllFiles()) scanFiles(store.map { it.path.lowercase() }.toHashSet()) else emptyList()
            scanCount = scanned.size
            (store + scanned).sortedByDescending { it.added }
        }
        videos = result
        loaded = true
        loading = false
    }

    fun hasAllFiles(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) android.os.Environment.isExternalStorageManager() else false

    fun diagnostics(context: Context): String =
        "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "Media access: ${access(context)}\n" +
            "All files access: ${if (hasAllFiles()) "yes" else "no"}\n" +
            "System media index returned: $storeCount videos\n" +
            "Folder scan found: $scanCount extra videos" +
            (if (storeError.isNotEmpty()) "\nError: $storeError" else "")

    private val exts = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts", "flv", "wmv", "mpg", "mpeg", "m2ts", "mts", "vob", "ogv")

    @Suppress("DEPRECATION")
    private fun scanFiles(known: Set<String>): List<VideoItem> {
        val out = ArrayList<VideoItem>()
        val root = android.os.Environment.getExternalStorageDirectory()
        fun walk(dir: java.io.File, depth: Int) {
            if (depth > 9) return
            val list = dir.listFiles() ?: return
            for (f in list) {
                if (f.isDirectory) {
                    val n = f.name
                    if (n == "Android" && depth == 0) continue
                    if (n == ".thumbnails" || n == ".trash" || n == ".Trash") continue
                    walk(f, depth + 1)
                } else {
                    val ext = f.extension.lowercase()
                    if (ext in exts && f.length() > 50_000 && f.absolutePath.lowercase() !in known) {
                        out += VideoItem(
                            id = -(f.absolutePath.hashCode().toLong().let { if (it < 0) -it else it }) - 1,
                            title = f.nameWithoutExtension,
                            uri = Uri.fromFile(f),
                            durationMs = 0L,
                            size = f.length(),
                            added = f.lastModified() / 1000,
                            width = 0,
                            height = 0,
                            folder = f.parentFile?.name ?: "Other",
                            path = f.absolutePath,
                        )
                    }
                }
            }
        }
        runCatching { walk(root, 0) }
        return out
    }

    private fun query(resolver: ContentResolver): List<VideoItem> {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
        )
        val collections = listOf(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        )
        for (collection in collections) {
            val items = mutableListOf<VideoItem>()
            runCatching {
                resolver.query(collection, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
                    val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val iAdded = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                    val iW = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                    val iH = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                    val iB = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                    val iData = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    while (c.moveToNext()) {
                        val id = c.getLong(iId)
                        val name = c.getString(iName) ?: "Video"
                        items += VideoItem(
                            id = id,
                            title = name.substringBeforeLast('.', name),
                            uri = android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                            durationMs = c.getLong(iDur),
                            size = c.getLong(iSize),
                            added = c.getLong(iAdded),
                            width = c.getInt(iW),
                            height = c.getInt(iH),
                            folder = c.getString(iB) ?: "Other",
                            path = c.getString(iData) ?: "",
                        )
                    }
                }
            }.onFailure { storeError = (it::class.simpleName ?: "error") + ": " + (it.message ?: "").take(120) }
            if (items.isNotEmpty()) return items
        }
        return emptyList()
    }

    @Suppress("DEPRECATION")
    suspend fun thumbnail(context: Context, v: VideoItem): Bitmap? {
        thumbs.get(v.id)?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                if (v.id < 0) {
                    android.media.ThumbnailUtils.createVideoThumbnail(v.path, MediaStore.Video.Thumbnails.MINI_KIND)
                } else if (Build.VERSION.SDK_INT >= 29) {
                    context.contentResolver.loadThumbnail(v.uri, Size(360, 202), null)
                } else {
                    MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, v.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
                }
            }.getOrNull()
        }
        if (bmp != null) thumbs.put(v.id, bmp)
        return bmp
    }
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}
