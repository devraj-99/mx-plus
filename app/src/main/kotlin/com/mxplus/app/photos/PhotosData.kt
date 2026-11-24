package com.mxplus.app.photos

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class PhotoItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateTakenMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val relativePath: String,
)

object PhotosStore {
    suspend fun query(context: Context): List<PhotoItem> = withContext(Dispatchers.IO) {
        val out = ArrayList<PhotoItem>()
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATA,
        )
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val iW = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val iData = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val taken = c.getLong(iTaken)
                    val added = c.getLong(iAdded) * 1000L
                    out.add(
                        PhotoItem(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            name = c.getString(iName) ?: "Photo",
                            dateTakenMs = if (taken > 0) taken else added,
                            sizeBytes = c.getLong(iSize),
                            width = c.getInt(iW),
                            height = c.getInt(iH),
                            relativePath = c.getString(iData) ?: "",
                        ),
                    )
                }
            }
        }
        out.sortByDescending { it.dateTakenMs }
        out
    }

    fun dayKey(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
    fun monthKey(ms: Long): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(ms))

    fun dayLabel(ms: Long): String {
        val cal = Calendar.getInstance()
        val today = dayKey(cal.timeInMillis)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = dayKey(cal.timeInMillis)
        val key = dayKey(ms)
        return when (key) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault()).format(Date(ms))
        }
    }

    fun monthLabel(ms: Long): String = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(ms))

    fun fullDate(ms: Long): String = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(ms))
}

object ThumbCache {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8L).toInt().coerceAtLeast(8 * 1024 * 1024),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun thumb(resolver: ContentResolver, uri: Uri, px: Int): Bitmap? {
        val key = "$uri#$px"
        cache.get(key)?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 29) {
                    resolver.loadThumbnail(uri, Size(px, px), null)
                } else {
                    decodeSampled(resolver, uri, px)
                }
            }.getOrNull() ?: runCatching { decodeSampled(resolver, uri, px) }.getOrNull()
        }
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    fun decodeSampled(resolver: ContentResolver, uri: Uri, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val rotation = exifRotation(resolver, uri)
        if (rotation == 0) return raw
        val m = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }

    fun exifRotation(resolver: ContentResolver, uri: Uri): Int = runCatching {
        resolver.openInputStream(uri)?.use { stream ->
            val exif = androidx.exifinterface.media.ExifInterface(stream)
            when (exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1)) {
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)
}
