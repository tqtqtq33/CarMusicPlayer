package com.carmusic.player.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.carmusic.player.PlaylistHolder
import com.carmusic.player.R
import com.carmusic.player.data.model.Song
import com.carmusic.player.databinding.ActivityPlayerBinding

class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_INDEX   = "extra_index"
        private const val EXTRA_RESUME  = "extra_resume"  // true = 接續上次播放

        fun start(context: Context, songs: List<Song>, index: Int) {
            PlaylistHolder.songs        = songs
            PlaylistHolder.currentIndex = index
            context.startActivity(Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_INDEX, index)
            })
        }

        /** 從通知 / 背景重新開啟，接續播放 */
        fun resume(context: Context) {
            context.startActivity(Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_RESUME, true)
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }
    }

    private lateinit var binding: ActivityPlayerBinding
    private val vm: PlayerViewModel by viewModels()
    private var isSeeking = false
    private val playlistItemViews = mutableListOf<PlaylistItemView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupControls()
        setupPlaylist()
        observeViewModel()

        // 啟動前景服務（確保背景播放）
        startService(Intent(this, MusicService::class.java))

        val resume = intent.getBooleanExtra(EXTRA_RESUME, false)
        if (resume) {
            // 接續上次播放（從 SharedPreferences 恢復）
            restoreLastSession()
        } else {
            val idx  = intent.getIntExtra(EXTRA_INDEX, PlaylistHolder.currentIndex)
            val song = PlaylistHolder.songs.getOrNull(idx)
            song?.let {
                PlaylistHolder.currentIndex = idx
                vm.playSong(it)
            }
        }
    }

    // ── 接續上次播放 ──────────────────────────────────────────
    private fun restoreLastSession() {
        val state = vm.getLastPlayedState() ?: run {
            // 沒有記錄就從頭播
            PlaylistHolder.songs.firstOrNull()?.let { vm.playSong(it) }
            return
        }
        val (songId, index, posMs) = state
        val song = PlaylistHolder.songs.getOrNull(index)
            ?: PlaylistHolder.songs.firstOrNull { it.id == songId }
        if (song != null) {
            PlaylistHolder.currentIndex = PlaylistHolder.songs.indexOf(song)
            vm.playSong(song, seekMs = posMs)
        } else {
            PlaylistHolder.songs.firstOrNull()?.let { vm.playSong(it) }
        }
    }

    // ── onNewIntent：Activity 已存在時回到此 Activity ─────────
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_RESUME, false)) {
            restoreLastSession()
        }
    }

    override fun onStop() {
        super.onStop()
        // 退出 UI 時儲存播放位置
        vm.savePosition()
    }

    // ── UI 事件 ──────────────────────────────────────────────
    private fun setupControls() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnPlayPause.setOnClickListener { vm.playPause() }
        binding.btnPrev.setOnClickListener { vm.playPrev() }
        binding.btnNext.setOnClickListener { vm.playNext() }
        binding.btnPlayMode.setOnClickListener { togglePlayMode() }
        binding.btnSearchLyrics.setOnClickListener { showLyricsSearchDialog() }

        binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.tvCurrentTime.text = formatMs(progress.toLong())
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar) { isSeeking = true }
            override fun onStopTrackingTouch(sb: android.widget.SeekBar) {
                isSeeking = false
                vm.seekTo(sb.progress.toLong())
            }
        })
    }

    private fun setupPlaylist() {
        binding.playlistContainer.removeAllViews()
        playlistItemViews.clear()
        for (song in PlaylistHolder.songs) {
            val itemView = PlaylistItemView(this).apply {
                setOnClickListener {
                    val idx = PlaylistHolder.songs.indexOf(song)
                    PlaylistHolder.currentIndex = idx
                    vm.playSong(song)
                }
            }
            binding.playlistContainer.addView(itemView)
            playlistItemViews.add(itemView)
        }
    }

    private fun updatePlaylistHighlight() {
        playlistItemViews.forEachIndexed { idx, view ->
            view.bind(PlaylistHolder.songs[idx], idx == PlaylistHolder.currentIndex)
        }
    }

    private fun togglePlayMode() {
        Toast.makeText(this, "播放模式切換功能", Toast.LENGTH_SHORT).show()
    }

    private fun showLyricsSearchDialog() {
        vm.song.value?.let { song ->
            LyricsSearchDialog(this, song, this.lifecycleScope) { newLyrics ->
                vm.setManualLyrics(newLyrics)
            }.show()
        }
    }

    // ── 方向盤實體按鍵映射 ────────────────────────────────────
    // 方向盤通常會發出 MEDIA_NEXT / MEDIA_PREVIOUS，也有車機用
    // KEYCODE_DPAD_LEFT / RIGHT；兩組都攔截以保持相容性。
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            // 方向盤「下一首」按鍵
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                vm.playNext()
                true
            }
            // 方向盤「上一首」按鍵
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                vm.playPrev()
                true
            }
            // 方向盤「播放/暫停」
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                vm.playPause()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // 讓 Activity 接收 Media Button 事件（方向盤）
    override fun onResume() {
        super.onResume()
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
    }

    // ── 觀察 ViewModel ───────────────────────────────────────
    private fun observeViewModel() {
        vm.song.observe(this) { song ->
            song ?: return@observe
            binding.tvTitle.text  = song.title
            binding.tvArtist.text = song.artist
            updatePlaylistHighlight()
        }

        vm.isPlaying.observe(this) { playing ->
            binding.btnPlayPause.setImageResource(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play
            )
        }

        vm.position.observe(this) { ms ->
            if (!isSeeking) {
                binding.seekBar.progress = ms.toInt()
                binding.tvCurrentTime.text = formatMs(ms)
            }
            binding.lyricsView.updatePosition(ms)
        }

        vm.duration.observe(this) { dur ->
            binding.seekBar.max     = dur.toInt()
            binding.tvDuration.text = formatMs(dur)
        }

        vm.lyrics.observe(this) { lines ->
            binding.lyricsView.setLyrics(lines)
        }

        vm.lyricsStatus.observe(this) { status ->
            binding.btnSearchLyrics.visibility =
                if (vm.isNetworkAvailable()) View.VISIBLE else View.GONE

            when (status) {
                PlayerViewModel.LyricsStatus.Loading  -> {
                    binding.lyricsProgress.visibility = View.VISIBLE
                    binding.tvLyricsHint.visibility   = View.GONE
                }
                PlayerViewModel.LyricsStatus.NotFound -> {
                    binding.lyricsProgress.visibility = View.GONE
                    binding.tvLyricsHint.text         = "找不到歌詞"
                    binding.tvLyricsHint.visibility   = View.VISIBLE
                }
                PlayerViewModel.LyricsStatus.NoNetwork -> {
                    binding.lyricsProgress.visibility = View.GONE
                    binding.tvLyricsHint.text         = "無網路連接"
                    binding.tvLyricsHint.visibility   = View.VISIBLE
                }
                PlayerViewModel.LyricsStatus.Error    -> {
                    binding.lyricsProgress.visibility = View.GONE
                    binding.tvLyricsHint.text         = "歌詞載入失敗"
                    binding.tvLyricsHint.visibility   = View.VISIBLE
                }
                else -> {
                    binding.lyricsProgress.visibility = View.GONE
                    binding.tvLyricsHint.visibility   = View.GONE
                }
            }
        }

        vm.hasPrev.observe(this) { binding.btnPrev.alpha = if (it) 1f else 0.35f }
        vm.hasNext.observe(this) { binding.btnNext.alpha = if (it) 1f else 0.35f }
    }

    private fun formatMs(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }
}
