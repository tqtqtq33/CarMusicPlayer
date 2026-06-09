package com.carmusic.player.ui.library

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.carmusic.player.PlaylistHolder
import com.carmusic.player.databinding.ActivityLibraryBinding
import com.carmusic.player.ui.player.PlayerActivity

class LibraryActivity : AppCompatActivity() {

    private lateinit var b: ActivityLibraryBinding
    private val vm: LibraryViewModel by viewModels()
    private lateinit var adapter: SongAdapter

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.loadSongs()
        else Toast.makeText(this, "需要讀取音樂檔案的權限", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(b.root)

        setupRecycler()
        setupSearch()
        setupSortButtons()
        observeViewModel()
        checkPermission()
    }

    private fun setupRecycler() {
        adapter = SongAdapter { song ->
            val songs = vm.songs.value ?: return@SongAdapter
            val idx   = songs.indexOf(song)
            adapter.setHighlight(song.id)
            PlayerActivity.start(this, songs, idx)
        }
        b.recyclerView.layoutManager = LinearLayoutManager(this)
        b.recyclerView.adapter        = adapter
        b.recyclerView.setHasFixedSize(true)
        b.recyclerView.itemAnimator   = null
    }

    private fun setupSearch() {
        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b2: Int, c: Int) {
                vm.search(s?.toString() ?: "")
            }
        })
    }

    private fun setupSortButtons() {
        b.btnSortTitle.setOnClickListener  {
            vm.setSortMode(LibraryViewModel.SortMode.TITLE)
            updateSortHighlight(0)
        }
        b.btnSortArtist.setOnClickListener {
            vm.setSortMode(LibraryViewModel.SortMode.ARTIST)
            updateSortHighlight(1)
        }
        b.btnSortAlbum.setOnClickListener  {
            vm.setSortMode(LibraryViewModel.SortMode.ALBUM)
            updateSortHighlight(2)
        }
        updateSortHighlight(0)
    }

    private fun updateSortHighlight(active: Int) {
        val buttons = listOf(b.btnSortTitle, b.btnSortArtist, b.btnSortAlbum)
        buttons.forEachIndexed { i, btn ->
            btn.alpha = if (i == active) 1f else 0.45f
        }
    }

    private fun observeViewModel() {
        vm.songs.observe(this) { songs ->
            adapter.submitList(songs)
            b.tvEmpty.visibility = if (songs.isEmpty() && !vm.loading.value!!)
                View.VISIBLE else View.GONE
            b.tvSongCount.text = "${songs.size} 首歌曲"

            // 載入完成後，若有上次播放記錄就自動跳回播放器接續播放
            if (songs.isNotEmpty()) {
                resumeLastSessionIfNeeded(songs)
            }
        }

        vm.loading.observe(this) { loading ->
            b.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        vm.error.observe(this) { msg ->
            msg ?: return@observe
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private var hasResumed = false  // 只觸發一次

    private fun resumeLastSessionIfNeeded(songs: List<com.carmusic.player.data.model.Song>) {
        if (hasResumed) return
        hasResumed = true

        val prefs = getSharedPreferences("car_music_prefs", Context.MODE_PRIVATE)
        val lastId  = prefs.getLong("last_song_id", -1L)
        val lastIdx = prefs.getInt("last_index", -1)
        if (lastId == -1L || lastIdx == -1) return

        // 確認歌曲仍存在於曲庫
        val song = songs.getOrNull(lastIdx)?.takeIf { it.id == lastId }
            ?: songs.firstOrNull { it.id == lastId }
            ?: return

        val idx = songs.indexOf(song)
        PlaylistHolder.songs        = songs
        PlaylistHolder.currentIndex = idx
        PlayerActivity.resume(this)
    }

    private fun checkPermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED)
            vm.loadSongs()
        else
            permLauncher.launch(perm)
    }
}
