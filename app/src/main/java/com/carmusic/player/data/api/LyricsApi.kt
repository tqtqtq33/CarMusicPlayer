package com.carmusic.player.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LyricsApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val BASE = "https://lrclib.net/api"

    /**
     * 精確查詢 (透過 title + artist + 時長)
     * 回傳 syncedLyrics (LRC 格式) 或 plainLyrics
     */
    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String = "",
        durationSec: Int = 0
    ): LyricsResult? = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder()
            sb.append("track_name=").append(enc(title))
            sb.append("&artist_name=").append(enc(artist))
            if (album.isNotBlank()) sb.append("&album_name=").append(enc(album))
            if (durationSec > 0) sb.append("&duration=").append(durationSec)

            val req = Request.Builder()
                .url("$BASE/get?$sb")
                .header("User-Agent", "CarMusicPlayer/1.0 (Android)")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                parseResult(JSONObject(body))
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 模糊搜尋 (備用，當精確查詢失敗時使用)
     */
    suspend fun searchLyrics(query: String): List<LyricsResult> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$BASE/search?q=${enc(query)}")
                .header("User-Agent", "CarMusicPlayer/1.0 (Android)")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                val arr = JSONArray(body)
                (0 until arr.length()).mapNotNull { i ->
                    parseResult(arr.getJSONObject(i))
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseResult(json: JSONObject): LyricsResult? {
        val synced = json.optString("syncedLyrics").trim()
        val plain  = json.optString("plainLyrics").trim()
        if (synced.isEmpty() && plain.isEmpty()) return null
        return LyricsResult(
            id           = json.optInt("id"),
            trackName    = json.optString("trackName"),
            artistName   = json.optString("artistName"),
            albumName    = json.optString("albumName"),
            syncedLyrics = synced.takeIf { it.isNotEmpty() },
            plainLyrics  = plain.takeIf  { it.isNotEmpty() },
            hasSynced    = synced.isNotEmpty()
        )
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    data class LyricsResult(
        val id: Int,
        val trackName: String,
        val artistName: String,
        val albumName: String,
        val syncedLyrics: String?,
        val plainLyrics: String?,
        val hasSynced: Boolean
    ) {
        /** 優先回傳有時間軸的 LRC，退而求其次用純文字 */
        val bestLyrics: String? get() = syncedLyrics ?: plainLyrics
    }
}
