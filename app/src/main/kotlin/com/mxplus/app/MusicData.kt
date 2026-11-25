package com.mxplus.app

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SongItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
)

object MusicLibrary {
    var songs by mutableStateOf<List<SongItem>>(emptyList())
    var loaded by mutableStateOf(false)
    var loading by mutableStateOf(false)

    private val arts = object : LruCache<Long, Bitmap>(60) {}

    suspend fun refresh(context: Context) {
        loading = true
        songs = withContext(Dispatchers.IO) { query(context.contentResolver) }
        loaded = true
        loading = false
    }

    private fun query(resolver: ContentResolver): List<SongItem> {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )
        val out = ArrayList<SongItem>()
        runCatching {
            resolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    out += SongItem(
                        id = id,
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        title = c.getString(iTitle) ?: "Track",
                        artist = c.getString(iArtist)?.takeIf { it != "<unknown>" } ?: "Unknown artist",
                        album = c.getString(iAlbum) ?: "",
                        durationMs = c.getLong(iDur),
                    )
                }
            }
        }
        return out
    }

    suspend fun artwork(context: Context, s: SongItem): Bitmap? {
        arts.get(s.id)?.let { return it }
        if (Build.VERSION.SDK_INT < 29) return null
        val bmp = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(s.uri, Size(420, 420), null) }.getOrNull()
        }
        if (bmp != null) arts.put(s.id, bmp)
        return bmp
    }
}
