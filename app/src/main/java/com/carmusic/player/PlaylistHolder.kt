package com.carmusic.player

import com.carmusic.player.data.model.Song

object PlaylistHolder {
    var songs: List<Song> = emptyList()
    var currentIndex: Int = 0
}
