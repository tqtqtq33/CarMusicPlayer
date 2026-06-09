package com.carmusic.player.data.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val path: String,
    val uriString: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val mimeType: String,
    val year: Int = 0,
    val folder: String = ""
) : Parcelable {

    val uri: Uri get() = Uri.parse(uriString)

    val durationSec: Int get() = (durationMs / 1000).toInt()

    val albumArtUri: Uri
        get() = Uri.parse("content://media/external/audio/albumart/$albumId")

    val format: String
        get() = when {
            mimeType.contains("flac", ignoreCase = true) -> "FLAC"
            mimeType.contains("wav", ignoreCase = true)  -> "WAV"
            mimeType.contains("mp3", ignoreCase = true)  -> "MP3"
            mimeType.contains("aac", ignoreCase = true)  -> "AAC"
            mimeType.contains("ogg", ignoreCase = true)  -> "OGG"
            mimeType.contains("opus", ignoreCase = true) -> "OPUS"
            else -> mimeType.substringAfterLast("/").uppercase().take(5)
        }

    val isLossless: Boolean
        get() = mimeType.contains("flac", ignoreCase = true) ||
                mimeType.contains("wav", ignoreCase = true)

    fun formatFileSize(): String {
        val mb = fileSizeBytes / (1024.0 * 1024.0)
        return if (mb >= 1) "%.1f MB".format(mb)
        else "${fileSizeBytes / 1024} KB"
    }
}
