package com.carmusic.player.ui.player

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.carmusic.player.data.model.LrcLine
import kotlin.math.abs

/**
 * 車用同步歌詞視圖
 *
 * 功能：
 * - 當前行置中、放大、全亮
 * - 上下行依距離漸淡
 * - 換行時平滑滾動動畫
 * - 支援長文自動省略
 */
class LyricsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── 樣式常數 ────────────────────────────────────────────
    private val dm = resources.displayMetrics
    private val sp get() = dm.scaledDensity
    private val dp get() = dm.density

    private var ACTIVE_TEXT_SP  = 32f
    private var NORMAL_TEXT_SP  = 22f
    private val LINE_GAP_DP     = 10f
    private val SCROLL_DURATION = 380L

    // ── Paint ───────────────────────────────────────────────
    private val activePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color     = Color.WHITE
        typeface  = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val normalPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color     = 0xBBFFFFFF.toInt()
    }

    private val emptyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color     = 0x55FFFFFF.toInt()
    }

    // ── 狀態 ────────────────────────────────────────────────
    private var lyrics: List<LrcLine> = emptyList()
    private var currentIndex: Int     = -1
    private var scrollOffset: Float   = 0f    // 動畫中的滾動值
    private var targetOffset: Float   = 0f

    // 每行 Y 中心（相對於內容頂部）
    private val lineCenterY = mutableListOf<Float>()

    private val scrollAnim = ValueAnimator().apply {
        duration     = SCROLL_DURATION
        interpolator = DecelerateInterpolator(1.8f)
        addUpdateListener {
            scrollOffset = it.animatedValue as Float
            invalidate()
        }
    }

    // ── 公開 API ─────────────────────────────────────────────
    fun setLyrics(lines: List<LrcLine>) {
        lyrics       = lines
        currentIndex = -1
        scrollOffset = 0f
        targetOffset = 0f
        scrollAnim.cancel()
        rebuildLayout()
        invalidate()
    }

    fun updatePosition(positionMs: Long) {
        if (lyrics.isEmpty()) return
        val newIdx = lyrics.indexOfLast { it.timeMs <= positionMs }
        if (newIdx != currentIndex) {
            currentIndex = newIdx
            rebuildLayout()
            if (newIdx >= 0) animateToLine(newIdx)
            invalidate()
        }
    }

    fun reset() {
        setLyrics(emptyList())
    }

    fun setTextSize(activeSp: Float, normalSp: Float) {
        ACTIVE_TEXT_SP = activeSp
        NORMAL_TEXT_SP = normalSp
        rebuildLayout()
        invalidate()
    }

    // ── 內部計算 ─────────────────────────────────────────────
    private fun activeH() = ACTIVE_TEXT_SP * sp * 1.55f
    private fun normalH() = NORMAL_TEXT_SP * sp * 1.55f
    private fun gap()     = LINE_GAP_DP * dp

    private fun rebuildLayout() {
        lineCenterY.clear()
        var y = 0f
        for (i in lyrics.indices) {
            val h = if (i == currentIndex) activeH() else normalH()
            lineCenterY.add(y + h / 2f)
            y += h + gap()
        }
    }

    private fun animateToLine(idx: Int) {
        targetOffset = lineCenterY.getOrNull(idx) ?: 0f
        scrollAnim.cancel()
        scrollAnim.setFloatValues(scrollOffset, targetOffset)
        scrollAnim.start()
    }

    // ── 繪製 ─────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        if (lyrics.isEmpty()) {
            val p = emptyPaint.also { it.textSize = NORMAL_TEXT_SP * sp }
            canvas.drawText("♪  暫無歌詞  ♪", width / 2f, baselineFor(height / 2f, p), p)
            return
        }

        val cx  = width / 2f
        val cy  = height / 2f
        val pad = (paddingLeft + paddingRight).toFloat()
        val maxW = (width - pad).toInt()

        for (i in lyrics.indices) {
            val lcy   = lineCenterY.getOrNull(i) ?: continue
            val drawY = cy + (lcy - scrollOffset)

            val isActive = (i == currentIndex)
            val lineH = if (isActive) activeH() else normalH()

            if (drawY < -lineH * 3 || drawY > height + lineH * 3) continue

            val paint = if (isActive) activePaint else normalPaint
            paint.textSize = if (isActive) ACTIVE_TEXT_SP * sp else NORMAL_TEXT_SP * sp

            // 透明度：離中心越遠越淡
            val dist   = abs(drawY - cy)
            val fadeZone = cy * 0.85f
            val alpha = when {
                isActive          -> 1f
                dist < cy * 0.3f  -> 0.82f
                dist > fadeZone   -> (0.82f * (1f - (dist - fadeZone) / (cy * 0.4f))).coerceIn(0f, 0.82f)
                else              -> 0.82f
            }
            paint.alpha = (alpha * 255).toInt()

            // 長度截斷
            val text = ellipsize(lyrics[i].text, paint, maxW.toFloat())
            canvas.drawText(text, cx, baselineFor(drawY, paint), paint)
        }
    }

    /** 計算文字基準線 Y（讓文字視覺上置中於 cy） */
    private fun baselineFor(cy: Float, p: TextPaint): Float =
        cy - (p.ascent() + p.descent()) / 2f

    private fun ellipsize(text: String, paint: TextPaint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        return TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildLayout()
    }

    override fun onDetachedFromWindow() {
        scrollAnim.cancel()
        super.onDetachedFromWindow()
    }
}
