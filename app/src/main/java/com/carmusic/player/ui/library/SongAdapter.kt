package com.carmusic.player.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.carmusic.player.R
import com.carmusic.player.data.model.Song
import com.carmusic.player.databinding.ItemSongBinding

class SongAdapter(
    private val onClick: (Song) -> Unit
) : ListAdapter<Song, SongAdapter.VH>(DIFF) {

    private var highlightId: Long = -1L

    fun setHighlight(id: Long) {
        highlightId = id
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position), getItem(position).id == highlightId)

    inner class VH(private val b: ItemSongBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(song: Song, isHighlight: Boolean) {
            b.tvTitle.text  = song.title
            b.tvArtist.text = if (song.folder.isNotEmpty()) 
                "${song.artist} (${song.folder})" 
                else 
                song.artist
            b.tvDuration.text = formatMs(song.durationMs)
            b.tvFormat.text = song.format

            // 無損標記顏色區別
            b.tvFormat.setTextColor(
                if (song.isLossless) 0xFF1DB954.toInt() else 0xFF888888.toInt()
            )

            // 當前播放高亮
            b.root.setBackgroundColor(
                if (isHighlight) 0x221DB954.toInt() else 0x00000000
            )

            Glide.with(b.root.context)
                .load(song.albumArtUri)
                .placeholder(R.drawable.ic_music_note_large)
                .error(R.drawable.ic_music_note_large)
                .into(b.ivAlbumArt)

            b.root.setOnClickListener { onClick(song) }
        }

        private fun formatMs(ms: Long): String {
            val s = ms / 1000
            return "%d:%02d".format(s / 60, s % 60)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(o: Song, n: Song) = o.id == n.id
            override fun areContentsTheSame(o: Song, n: Song) = o == n
        }
    }
}
