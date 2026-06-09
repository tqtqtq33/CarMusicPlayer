package com.carmusic.player.ui.player

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.carmusic.player.PlaylistHolder
import com.carmusic.player.data.model.LrcLine
import com.carmusic.player.data.model.Song
import com.carmusic.player.data.repository.LyricsRepository
import com.carmusic.player.util.LrcParser
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val PREFS_NAME  = "car_music_prefs"
        private const val KEY_SONG_ID = "last_song_id"
        private const val KEY_INDEX   = "last_index"
        private const val KEY_POSITION = "last_position"
    }

    private val prefs: SharedPreferences =
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val lyricsRepo = LyricsRepository(app)
    private val context    = app

    // ── ExoPlayer (本地實例，用於 PlayerActivity 控制) ────────
    val player: ExoPlayer = ExoPlayer.Builder(app)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    // ── LiveData ─────────────────────────────────────────────
    private val _song             = MutableLiveData<Song?>()
    val song: LiveData<Song?>     = _song

    private val _isPlaying        = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _position         = MutableLiveData(0L)
    val position: LiveData<Long>  = _position

    private val _duration         = MutableLiveData(0L)
    val duration: LiveData<Long>  = _duration

    private val _lyrics           = MutableLiveData<List<LrcLine>>(emptyList())
    val lyrics: LiveData<List<LrcLine>> = _lyrics

    private val _lyricsStatus     = MutableLiveData<LyricsStatus>(LyricsStatus.Idle)
    val lyricsStatus: LiveData<LyricsStatus> = _lyricsStatus

    private val _hasPrev          = MutableLiveData(false)
    val hasPrev: LiveData<Boolean> = _hasPrev

    private val _hasNext          = MutableLiveData(false)
    val hasNext: LiveData<Boolean> = _hasNext

    enum class LyricsStatus { Idle, Loading, Found, NotFound, NoNetwork, Error }

    private var progressJob: Job? = null
    private var lyricsJob: Job?   = null

    // ── 初始化 ───────────────────────────────────────────────
    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.postValue(isPlaying)
                if (isPlaying) startProgressLoop() else stopProgressLoop()
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    _duration.postValue(player.duration.coerceAtLeast(0))
                }
                if (state == Player.STATE_ENDED) {
                    autoPlayNext()
                }
            }
        })
    }

    // ── 播放控制 ─────────────────────────────────────────────
    fun playSong(song: Song, seekMs: Long = 0L) {
        _song.value = song
        _lyrics.value = emptyList()
        _lyricsStatus.value = LyricsStatus.Idle
        updateNavState()

        player.setMediaItem(MediaItem.fromUri(song.uri))
        player.prepare()
        if (seekMs > 0) player.seekTo(seekMs)
        player.play()

        // 儲存目前播放狀態
        saveLastPlayed(song, PlaylistHolder.currentIndex)

        fetchLyrics(song)
    }

    fun playPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms)
        _position.value = ms
    }

    fun playPrev() {
        val list = PlaylistHolder.songs
        val idx  = PlaylistHolder.currentIndex
        if (idx > 0) {
            PlaylistHolder.currentIndex = idx - 1
            playSong(list[PlaylistHolder.currentIndex])
        }
    }

    fun playNext() {
        val list = PlaylistHolder.songs
        val idx  = PlaylistHolder.currentIndex
        if (idx < list.size - 1) {
            PlaylistHolder.currentIndex = idx + 1
            playSong(list[PlaylistHolder.currentIndex])
        }
    }

    private fun autoPlayNext() {
        val list = PlaylistHolder.songs
        val idx  = PlaylistHolder.currentIndex
        val currentSong = list.getOrNull(idx)

        if (idx < list.size - 1) {
            val nextSong = list[idx + 1]
            if (currentSong?.folder == nextSong.folder) {
                PlaylistHolder.currentIndex = idx + 1
                playSong(nextSong)
                return
            }
        }

        val currentFolder = currentSong?.folder ?: ""
        for (i in idx + 1 until list.size) {
            if (list[i].folder != currentFolder) {
                PlaylistHolder.currentIndex = i
                playSong(list[i])
                return
            }
        }
    }

    // ── 上次播放狀態持久化 ────────────────────────────────────
    private fun saveLastPlayed(song: Song, index: Int) {
        prefs.edit()
            .putLong(KEY_SONG_ID, song.id)
            .putInt(KEY_INDEX, index)
            .apply()
    }

    fun savePosition() {
        prefs.edit().putLong(KEY_POSITION, player.currentPosition).apply()
    }

    /** 回傳上次記錄的 (songId, index, positionMs)；若無則回傳 null */
    fun getLastPlayedState(): Triple<Long, Int, Long>? {
        val id  = prefs.getLong(KEY_SONG_ID, -1L)
        val idx = prefs.getInt(KEY_INDEX, -1)
        val pos = prefs.getLong(KEY_POSITION, 0L)
        if (id == -1L || idx == -1) return null
        return Triple(id, idx, pos)
    }

    // ── 歌詞 ─────────────────────────────────────────────────
    private fun fetchLyrics(song: Song) {
        lyricsJob?.cancel()
        _lyricsStatus.value = LyricsStatus.Loading
        lyricsJob = viewModelScope.launch {
            when (val result = lyricsRepo.getLyrics(song)) {
                is LyricsRepository.LyricsState.Success -> {
                    _lyrics.postValue(result.lines)
                    _lyricsStatus.postValue(LyricsStatus.Found)
                }
                is LyricsRepository.LyricsState.NotFound ->
                    _lyricsStatus.postValue(LyricsStatus.NotFound)
                is LyricsRepository.LyricsState.NoNetwork ->
                    _lyricsStatus.postValue(LyricsStatus.NoNetwork)
                is LyricsRepository.LyricsState.Error ->
                    _lyricsStatus.postValue(LyricsStatus.Error)
                else -> {}
            }
        }
    }

    // ── 進度輪詢 ─────────────────────────────────────────────
    private fun startProgressLoop() {
        stopProgressLoop()
        progressJob = viewModelScope.launch {
            while (isActive) {
                _position.postValue(player.currentPosition)
                val dur = player.duration
                if (dur > 0) _duration.postValue(dur)
                delay(200L)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun updateNavState() {
        val idx = PlaylistHolder.currentIndex
        _hasPrev.value = idx > 0
        _hasNext.value = idx < PlaylistHolder.songs.size - 1
    }

    // ── 歌詞手動設置 ─────────────────────────────────────────
    fun setManualLyrics(lrcText: String) {
        val parsed = LrcParser.parse(lrcText)
        if (parsed.lines.isNotEmpty()) {
            _lyrics.postValue(parsed.lines)
            _lyricsStatus.postValue(LyricsStatus.Found)
        }
    }

    // ── 網路檢查 ─────────────────────────────────────────────
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        return try {
            connectivityManager?.activeNetwork != null
        } catch (_: Exception) { false }
    }

    // ── 清理 ─────────────────────────────────────────────────
    override fun onCleared() {
        savePosition()
        stopProgressLoop()
        lyricsJob?.cancel()
        player.release()
        super.onCleared()
    }
}
