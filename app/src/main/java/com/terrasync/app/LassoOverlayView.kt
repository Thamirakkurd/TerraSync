package com.terrasync.app

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.hypot

class LassoOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val points = mutableListOf<PointF>()
    private val path = Path()
    private var isClosed = false
    private val density = resources.displayMetrics.density
    private val dashIntervals = floatArrayOf(10f * density, 6f * density)
    private val dashCycle = 16f * density
    private var dashAnimator: ValueAnimator? = null
    private var fillAnimator: ValueAnimator? = null
    private var fillAlphaInt = 0x26

    var onLoopStarted: (() -> Unit)? = null
    var onLoopCompleted: ((List<PointF>) -> Unit)? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2622C55E")
        style = Paint.Style.FILL
    }

    private val haloStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 5.4f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#16A34A")
        style = Paint.Style.STROKE
        strokeWidth = 3.2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        pathEffect = DashPathEffect(dashIntervals, 0f)
    }

    private fun startDashAnimation() {
        if (UiAnimations.isReducedMotion(context)) {
            strokePaint.pathEffect = DashPathEffect(dashIntervals, 0f)
            return
        }
        if (dashAnimator?.isRunning == true) return
        dashAnimator?.cancel()
        dashAnimator = ValueAnimator.ofFloat(0f, dashCycle).apply {
            duration = 650L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val phase = anim.animatedValue as Float
                strokePaint.pathEffect = DashPathEffect(dashIntervals, -phase)
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    private fun stopAnimations() {
        dashAnimator?.cancel()
        dashAnimator = null
        fillAnimator?.cancel()
        fillAnimator = null
    }

    fun clearLoop() {
        stopAnimations()
        points.clear()
        path.reset()
        isClosed = false
        fillAlphaInt = 0x26
        fillPaint.alpha = fillAlphaInt
        strokePaint.pathEffect = DashPathEffect(dashIntervals, 0f)
        invalidate()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) {
            stopAnimations()
        } else if (points.size >= 2) {
            startDashAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        stopAnimations()
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                stopAnimations()
                points.clear()
                path.reset()
                isClosed = false
                points.add(PointF(x, y))
                path.moveTo(x, y)
                onLoopStarted?.invoke()
                startDashAnimation()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val last = points.lastOrNull()
                if (last == null || hypot((x - last.x).toDouble(), (y - last.y).toDouble()) >= 4.0 * density) {
                    points.add(PointF(x, y))
                    path.lineTo(x, y)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (points.size >= 3) {
                    path.close()
                    isClosed = true
                    if (!UiAnimations.isReducedMotion(context)) {
                        fillAnimator?.cancel()
                        fillAnimator = ValueAnimator.ofInt(0, 0x28).apply {
                            duration = 170L
                            addUpdateListener { anim ->
                                fillAlphaInt = anim.animatedValue as Int
                                fillPaint.alpha = fillAlphaInt
                                postInvalidateOnAnimation()
                            }
                            start()
                        }
                    } else {
                        fillAlphaInt = 0x28
                        fillPaint.alpha = fillAlphaInt
                    }
                    invalidate()
                    onLoopCompleted?.invoke(points.toList())
                } else {
                    clearLoop()
                }
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size >= 2) {
            if (isClosed) canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, haloStrokePaint)
            canvas.drawPath(path, strokePaint)
        }
    }

    companion object {
        fun isPointInPolygon(x: Float, y: Float, poly: List<PointF>): Boolean {
            if (poly.size < 3) return false
            var inside = false
            var j = poly.lastIndex
            for (i in poly.indices) {
                val xi = poly[i].x
                val yi = poly[i].y
                val xj = poly[j].x
                val yj = poly[j].y
                val intersect = ((yi > y) != (yj > y)) &&
                    (x < (xj - xi) * (y - yi) / ((yj - yi).takeIf { it != 0f } ?: 1e-6f) + xi)
                if (intersect) inside = !inside
                j = i
            }
            return inside
        }
    }
}
