package com.carmusic.player.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.carmusic.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    suspend fun getAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Song>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.YEAR
        )

        // 只列出音樂檔案（排除鈴聲、通知等）
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 10000"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dataCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durCol      = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val yearCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)

            while (cursor.moveToNext()) {
                val id  = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )
                val title  = cursor.getString(titleCol)  ?: "未知歌曲"
                val artist = cursor.getString(artistCol) ?: "未知藝術家"
                // 過濾 Android 預設的「未知藝術家」字串
                val cleanArtist = if (artist.startsWith("<") || artist == "unknown") "未知藝術家" else artist

                list.add(Song(
                    id            = id,
                    title         = title,
                    artist        = cleanArtist,
                    album         = cursor.getString(albumCol) ?: "未知專輯",
                    albumId       = cursor.getLong(albumIdCol),
                    path          = cursor.getString(dataCol) ?: "",
                    uriString     = uri.toString(),
                    durationMs    = cursor.getLong(durCol),
                    fileSizeBytes = cursor.getLong(sizeCol),
                    mimeType      = cursor.getString(mimeCol) ?: "audio/*",
                    year          = cursor.getInt(yearCol),
                    folder        = extractFolderName(cursor.getString(dataCol) ?: "")
                ))
            }
        }
        list
    }

    private fun extractFolderName(filePath: String): String {
        if (filePath.isBlank()) return ""
        val lastSeparator = filePath.lastIndexOf('/')
        if (lastSeparator <= 0) return ""
        val folderPath = filePath.substring(0, lastSeparator)
        val folderSeparator = folderPath.lastIndexOf('/')
        return if (folderSeparator >= 0) {
            folderPath.substring(folderSeparator + 1)
        } else {
            folderPath
        }
    }
}
