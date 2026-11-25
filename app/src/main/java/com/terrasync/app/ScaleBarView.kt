package com.terrasync.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Lightweight minimalist bracketed map scale bar (`|_________|`) matching the warm
 * minimalist cartography reference. Uses a fixed layout width and updates its drawn
 * span via [invalidate] only, avoiding any CoordinatorLayout relayout during 120Hz
 * pinch-zoom or pan gestures.
 */
class ScaleBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private var barWidthPx: Int = (80f * density).toInt()

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 3.5f * density
        color = Color.parseColor("#E6FAF9F6")
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.6f * density
        color = Color.parseColor("#1C1E1B")
    }

    fun setBarStyle(strokeColor: Int, haloColor: Int) {
        if (linePaint.color == strokeColor && haloPaint.color == haloColor) return
        linePaint.color = strokeColor
        haloPaint.color = haloColor
        invalidate()
    }

    fun setBarWidthPx(widthPx: Int) {
        val clamped = widthPx.coerceAtLeast((24f * density).toInt())
        if (barWidthPx == clamped) return
        barWidthPx = clamped
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 2f * density
        val maxSpan = (width - pad * 2f).coerceAtLeast(16f * density)
        val span = barWidthPx.toFloat().coerceIn(16f * density, maxSpan)
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val xStart = if (isRtl) (width - pad - span) else pad
        val xEnd = xStart + span
        val yBottom = (height - pad).coerceAtLeast(pad + 2f * density)
        val yTop = (yBottom - 4.5f * density).coerceAtLeast(pad)

        // Subtle cartographic halo for legibility over roads & satellite imagery
        canvas.drawLine(xStart, yTop, xStart, yBottom, haloPaint)
        canvas.drawLine(xStart, yBottom, xEnd, yBottom, haloPaint)
        canvas.drawLine(xEnd, yTop, xEnd, yBottom, haloPaint)

        // Crisp 1.6dp bracketed scale bar
        canvas.drawLine(xStart, yTop, xStart, yBottom, linePaint)
        canvas.drawLine(xStart, yBottom, xEnd, yBottom, linePaint)
        canvas.drawLine(xEnd, yTop, xEnd, yBottom, linePaint)
    }
}
