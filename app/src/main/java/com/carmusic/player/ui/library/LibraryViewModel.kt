package com.carmusic.player.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.carmusic.player.data.model.Song
import com.carmusic.player.data.repository.MusicRepository
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MusicRepository(app)

    private val _songs = MutableLiveData<List<Song>>(emptyList())
    val songs: LiveData<List<Song>> = _songs

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var allSongs = listOf<Song>()
    private var sortMode = SortMode.TITLE

    enum class SortMode { TITLE, ARTIST, ALBUM }

    fun loadSongs() {
        _loading.value = true
        viewModelScope.launch {
            try {
                allSongs = repo.getAllSongs()
                applyFilter("")
            } catch (e: Exception) {
                _error.postValue("無法讀取音樂：${e.message}")
            } finally {
                _loading.postValue(false)
            }
        }
    }

    fun search(query: String) = applyFilter(query)

    fun setSortMode(mode: SortMode) {
        sortMode = mode
        applyFilter("")
    }

    private fun applyFilter(query: String) {
        val filtered = if (query.isBlank()) allSongs
        else allSongs.filter { s ->
            s.title.contains(query, true) ||
            s.artist.contains(query, true) ||
            s.album.contains(query, true)
        }

        val sorted = when (sortMode) {
            SortMode.TITLE  -> filtered.sortedBy { it.title.lowercase() }
            SortMode.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            SortMode.ALBUM  -> filtered.sortedBy { it.album.lowercase() }
        }
        _songs.postValue(sorted)
    }
}
