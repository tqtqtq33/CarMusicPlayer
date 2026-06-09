package com.carmusic.player.util

import com.carmusic.player.data.model.LrcLine

object LrcParser {

    // 支援 [mm:ss.xx] 與 [mm:ss.xxx] 格式
    private val TIME_TAG = Regex("""\[(\d{1,2}):(\d{2})[.:](\d{2,3})\]""")
    private val META_TAG = Regex("""^\[(\w+):(.*)\]$""")

    data class ParsedLyrics(
        val lines: List<LrcLine>,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val offsetMs: Long = 0L
    )

    fun parse(lrcContent: String): ParsedLyrics {
        val lines = mutableListOf<LrcLine>()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var offsetMs = 0L

        for (rawLine in lrcContent.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val timeTags = TIME_TAG.findAll(line).toList()

            if (timeTags.isEmpty()) {
                // 嘗試解析 metadata tag: [ar:XXX]
                META_TAG.find(line)?.let { m ->
                    val key = m.groupValues[1].lowercase()
                    val value = m.groupValues[2].trim()
                    when (key) {
                        "ti", "title"  -> title  = value
                        "ar", "artist" -> artist = value
                        "al", "album"  -> album  = value
                        "offset"       -> offsetMs = value.toLongOrNull() ?: 0L
                    }
                }
                continue
            }

            // 歌詞本文 = 去除所有時間標籤後剩餘
            val text = TIME_TAG.replace(line, "").trim()
            if (text.isEmpty()) continue

            for (tag in timeTags) {
                val min = tag.groupValues[1].toLong()
                val sec = tag.groupValues[2].toLong()
                val sub = tag.groupValues[3]
                val subMs = when (sub.length) {
                    2    -> sub.toLong() * 10L
                    3    -> sub.toLong()
                    else -> 0L
                }
                val timeMs = min * 60_000L + sec * 1_000L + subMs + offsetMs
                lines.add(LrcLine(timeMs.coerceAtLeast(0L), text))
            }
        }

        return ParsedLyrics(
            lines  = lines.sortedBy { it.timeMs },
            title  = title,
            artist = artist,
            album  = album,
            offsetMs = offsetMs
        )
    }

    /** 將純文字（無時間軸）轉為假 LrcLine 列表 */
    fun parsePlainText(text: String): List<LrcLine> =
        text.lines()
            .mapIndexed { i, line -> LrcLine(i * 3000L, line.trim()) }
            .filter { it.text.isNotEmpty() }
}
