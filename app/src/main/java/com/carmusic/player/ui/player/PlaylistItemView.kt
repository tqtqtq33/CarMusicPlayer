package com.carmusic.player.ui.player

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.carmusic.player.R
import com.carmusic.player.data.model.Song

class PlaylistItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val ivAlbumArt: ImageView
    private val tvTitle: TextView
    private val ivPlayIndicator: ImageView

    init {
        LayoutInflater.from(context).inflate(R.layout.view_playlist_item, this, true)
        ivAlbumArt = findViewById(R.id.ivAlbumArt)
        tvTitle = findViewById(R.id.tvTitle)
        ivPlayIndicator = findViewById(R.id.ivPlayIndicator)
    }

    fun bind(song: Song, isPlaying: Boolean) {
        tvTitle.text = song.title
        ivPlayIndicator.visibility = if (isPlaying) View.VISIBLE else View.GONE

        Glide.with(context)
            .load(song.albumArtUri)
            .placeholder(R.drawable.ic_music_note_large)
            .error(R.drawable.ic_music_note_large)
            .into(ivAlbumArt)

        // 高亮當前播放的歌曲
        setBackgroundColor(
            if (isPlaying) 0x22FFFFFF.toInt() else 0x00000000
        )
    }
}
