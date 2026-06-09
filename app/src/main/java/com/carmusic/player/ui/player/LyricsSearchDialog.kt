package com.carmusic.player.ui.player

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.carmusic.player.data.api.LyricsApi
import com.carmusic.player.data.model.Song
import com.carmusic.player.util.LrcParser
import kotlinx.coroutines.launch

class LyricsSearchDialog(
    private val context: Context,
    private val song: Song,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onLyricsSelected: (String) -> Unit
) {
    private val api = LyricsApi()

    // ── 從檔案名稱解析出「歌手-歌名」或「歌名」 ────────────────
    // 支援格式範例：
    //   "01. 歌手-歌名 未知演奏者"
    //   "02.歌手 - 歌名"
    //   "歌手-歌名"
    private data class ParsedQuery(val primary: String, val fallback: String?)

    private fun parseFilenameQuery(song: Song): ParsedQuery {
        val raw = song.path.substringAfterLast("/").substringBeforeLast(".")

        // 1. 移除開頭的數字與標點（如 "01. "、"02."）
        val stripped = raw.trimStart()
            .replace(Regex("^\\d+[.、。\\-\\s]+"), "")
            .trim()

        // 2. 移除結尾的「未知演奏者」、「Unknown」、「(未知)」等常見無意義尾綴
        val cleaned = stripped
            .replace(Regex("\\s*(未知的?演奏者|Unknown Artist|未知歌手|unknown|N\\/A)\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()

        // 3. 嘗試拆分「歌手-歌名」或「歌手 - 歌名」
        val dashIndex = cleaned.indexOfFirst { it == '-' || it == '－' || it == '—' }
        return if (dashIndex > 0 && dashIndex < cleaned.length - 1) {
            val artist = cleaned.substring(0, dashIndex).trim()
            val title  = cleaned.substring(dashIndex + 1).trim()
            // primary = "歌手-歌名"；fallback = "歌名-歌手"（反轉順序重試）
            ParsedQuery(
                primary  = "$artist $title",
                fallback = "$title $artist"
            )
        } else {
            // 沒有明確分隔符，直接用清理後的名稱，再試原始 Song.title/artist
            val fallback = if (song.artist.isNotBlank() && song.artist != "<unknown>")
                "${song.title} ${song.artist}" else null
            ParsedQuery(primary = cleaned, fallback = fallback)
        }
    }

    // ── 顯示對話框 ───────────────────────────────────────────
    fun show() {
        // === 畫面層級 ===
        // rootLayout
        //   ├─ searchRow (橫排：etSearch + btnSearch)
        //   ├─ progressBar
        //   ├─ tvStatus (狀態訊息)
        //   ├─ listView (搜尋結果)
        //   └─ btnBack (返回鍵 — 搜尋結果頁才顯示)

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 8)
        }

        // 搜尋列
        val searchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }

        // 預填搜尋文字（使用解析後的檔名）
        val parsedQuery = parseFilenameQuery(song)
        val etSearch = EditText(context).apply {
            hint = "歌手 歌名"
            setText(parsedQuery.primary)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginEnd = 8 }
        }

        val btnSearch = Button(context).apply {
            text = "搜尋"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        searchRow.addView(etSearch)
        searchRow.addView(btnSearch)
        rootLayout.addView(searchRow)

        // 進度條
        val progressBar = ProgressBar(context).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }
        rootLayout.addView(progressBar)

        // 狀態文字
        val tvStatus = TextView(context).apply {
            visibility = View.GONE
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }
        rootLayout.addView(tvStatus)

        // 搜尋結果列表
        val listView = ListView(context).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 480
            ).apply { bottomMargin = 12 }
        }
        rootLayout.addView(listView)

        // ── 功能3：返回鍵（結果頁才顯示） ──────────────────────
        val btnBack = Button(context).apply {
            text = "← 返回搜尋"
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(btnBack)

        // ── 建立 Dialog ─────────────────────────────────────────
        val dialog = AlertDialog.Builder(context)
            .setTitle("搜尋歌詞")
            .setView(rootLayout)
            .setNegativeButton("關閉", null)
            .create()

        // ── 返回鍵：回到搜尋輸入狀態 ────────────────────────────
        fun showSearchState() {
            listView.visibility    = View.GONE
            btnBack.visibility     = View.GONE
            tvStatus.visibility    = View.GONE
            searchRow.visibility   = View.VISIBLE
        }

        fun showResultState() {
            searchRow.visibility   = View.VISIBLE
            listView.visibility    = View.VISIBLE
            btnBack.visibility     = View.VISIBLE
        }

        btnBack.setOnClickListener { showSearchState() }

        // ── 搜尋邏輯（含功能4：fallback 重試）──────────────────
        suspend fun doSearch(query: String): List<com.carmusic.player.data.api.LyricsApi.LyricsResult> {
            return api.searchLyrics(query)
        }

        btnSearch.setOnClickListener {
            val query = etSearch.text.toString().trim()
            if (query.isEmpty()) {
                Toast.makeText(context, "請輸入搜尋關鍵字", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            listView.visibility    = View.GONE
            btnBack.visibility     = View.GONE
            tvStatus.visibility    = View.GONE
            listView.adapter       = null

            lifecycleScope.launch {
                try {
                    var results = doSearch(query)

                    // ── 功能4：搜尋不到時嘗試 fallback ──────────────
                    if (results.isEmpty()) {
                        val fallback = parsedQuery.fallback
                        if (fallback != null && fallback != query) {
                            tvStatus.text       = "「$query」無結果，改搜尋「$fallback」..."
                            tvStatus.visibility = View.VISIBLE
                            results = doSearch(fallback)
                        }
                    }

                    progressBar.visibility = View.GONE

                    if (results.isEmpty()) {
                        tvStatus.text       = "未找到歌詞，請嘗試其他關鍵字"
                        tvStatus.visibility = View.VISIBLE
                        showSearchState()
                        btnBack.visibility = View.GONE
                        return@launch
                    }

                    tvStatus.visibility = View.GONE
                    val resultTexts = results.map { "${it.trackName} - ${it.artistName}" }
                    listView.adapter = ArrayAdapter(
                        context,
                        android.R.layout.simple_list_item_1,
                        resultTexts
                    )
                    showResultState()

                    listView.setOnItemClickListener { _, _, position, _ ->
                        val selected = results[position]
                        val lrcText  = selected.bestLyrics ?: return@setOnItemClickListener
                        onLyricsSelected(lrcText)
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "搜尋失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ── 功能4：開啟對話框時自動搜尋 ─────────────────────────
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            tvStatus.text = "自動搜尋中：${parsedQuery.primary}"
            tvStatus.visibility = View.VISIBLE

            try {
                var results = doSearch(parsedQuery.primary)

                // 找不到就試 fallback
                if (results.isEmpty() && parsedQuery.fallback != null) {
                    tvStatus.text = "改為搜尋：${parsedQuery.fallback}"
                    results = doSearch(parsedQuery.fallback)
                }

                progressBar.visibility = View.GONE

                if (results.isEmpty()) {
                    tvStatus.text = "自動搜尋無結果，請手動輸入關鍵字"
                    return@launch
                }

                tvStatus.visibility = View.GONE
                val resultTexts = results.map { "${it.trackName} - ${it.artistName}" }
                listView.adapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_list_item_1,
                    resultTexts
                )
                showResultState()

                listView.setOnItemClickListener { _, _, position, _ ->
                    val selected = results[position]
                    val lrcText  = selected.bestLyrics ?: return@setOnItemClickListener
                    onLyricsSelected(lrcText)
                    dialog.dismiss()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                tvStatus.text = "自動搜尋失敗，請手動輸入關鍵字"
                tvStatus.visibility = View.VISIBLE
            }
        }

        dialog.show()
    }
}
