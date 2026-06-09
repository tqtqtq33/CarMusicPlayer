package com.carmusic.player.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.carmusic.player.data.api.LyricsApi
import com.carmusic.player.data.model.LrcLine
import com.carmusic.player.data.model.Song
import com.carmusic.player.util.LrcParser
import java.io.File

class LyricsRepository(private val context: Context) {

    private val api = LyricsApi()
    private val TAG = "LyricsRepo"

    sealed class LyricsState {
        data class Success(val lines: List<LrcLine>, val isSynced: Boolean) : LyricsState()
        object Loading : LyricsState()
        object NotFound : LyricsState()
        object NoNetwork : LyricsState()
        data class Error(val message: String) : LyricsState()
    }

    suspend fun getLyrics(song: Song): LyricsState {
        // 1. 優先讀取本地同目錄的 .lrc 檔案
        val localResult = tryLocalLrc(song)
        if (localResult != null) {
            Log.d(TAG, "本地歌詞命中: ${song.title}")
            return LyricsState.Success(localResult.lines, localResult.lines.any { it.timeMs > 0 })
        }

        // 2. 嘗試 App 快取目錄
        val cacheResult = tryCachedLrc(song)
        if (cacheResult != null) {
            Log.d(TAG, "快取歌詞命中: ${song.title}")
            return LyricsState.Success(cacheResult.lines, cacheResult.lines.any { it.timeMs > 0 })
        }

        // 3. 檢查網路連接
        if (!isNetworkAvailable()) {
            Log.d(TAG, "無網路連接: ${song.title}")
            return LyricsState.NoNetwork
        }

        // 4. 向 LRCLIB API 請求
        return try {
            val result = api.getLyrics(
                title       = song.title,
                artist      = song.artist,
                album       = song.album,
                durationSec = song.durationSec
            ) ?: run {
                // 精確查詢失敗，改用模糊搜尋
                api.searchLyrics("${song.title} ${song.artist}")
                    .firstOrNull { it.hasSynced }
                    ?: api.searchLyrics(song.title).firstOrNull()
            }

            if (result == null) {
                Log.d(TAG, "API 無歌詞: ${song.title}")
                return LyricsState.NotFound
            }

            val lrcText = result.bestLyrics ?: return LyricsState.NotFound
            val parsed  = if (result.hasSynced) LrcParser.parse(lrcText)
                          else LrcParser.ParsedLyrics(LrcParser.parsePlainText(lrcText))

            if (parsed.lines.isEmpty()) return LyricsState.NotFound

            // 儲存至快取
            saveLrcCache(song, lrcText)
            Log.d(TAG, "API 歌詞成功 (synced=${result.hasSynced}): ${song.title}")

            LyricsState.Success(parsed.lines, result.hasSynced)
        } catch (e: Exception) {
            Log.e(TAG, "歌詞取得失敗: ${e.message}")
            LyricsState.Error(e.message ?: "網路錯誤")
        }
    }

    // ── 本地 .lrc 同名檔案 ──────────────────────────────────
    private fun tryLocalLrc(song: Song): LrcParser.ParsedLyrics? {
        if (song.path.isBlank()) return null
        val lrcFile = File(song.path.substringBeforeLast('.') + ".lrc")
        if (!lrcFile.exists()) return null
        val parsed = LrcParser.parse(lrcFile.readText())
        return if (parsed.lines.isNotEmpty()) parsed else null
    }

    // ── App 快取目錄 ─────────────────────────────────────────
    private fun tryCachedLrc(song: Song): LrcParser.ParsedLyrics? {
        val file = cacheFile(song)
        if (!file.exists()) return null
        // 快取超過 30 天重新抓取
        if (System.currentTimeMillis() - file.lastModified() > 30L * 24 * 3600 * 1000) {
            file.delete()
            return null
        }
        val parsed = LrcParser.parse(file.readText())
        return if (parsed.lines.isNotEmpty()) parsed else null
    }

    private fun saveLrcCache(song: Song, content: String) {
        try {
            val file = cacheFile(song)
            file.parentFile?.mkdirs()
            file.writeText(content)
        } catch (_: Exception) {}
    }

    private fun cacheFile(song: Song): File {
        val name = "${song.artist}_${song.title}"
            .replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fff_\\-]"), "_")
            .take(80) + ".lrc"
        return File(context.cacheDir, "lyrics/$name")
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        return try {
            val activeNetwork = connectivityManager?.activeNetwork
            activeNetwork != null
        } catch (_: Exception) {
            false
        }
    }
}
