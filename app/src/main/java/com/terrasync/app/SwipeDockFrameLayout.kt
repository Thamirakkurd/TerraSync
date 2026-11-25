package com.terrasync.app

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Floating bottom navigation dock container that cleanly detects downward swipes
 * to enter Full-Screen Clean Map Mode while leaving single taps on the 4 inner
 * navigation pills completely untouched.
 */
class SwipeDockFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onSwipeDown: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val triggerDistancePx = 28f * resources.displayMetrics.density
    private var downX = 0f
    private var downY = 0f
    private var trackingSwipe = false
    private var triggered = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                trackingSwipe = false
                triggered = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.rawY - downY
                val dx = abs(ev.rawX - downX)
                if (dy > touchSlop && dy > dx * 1.25f) {
                    trackingSwipe = true
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                trackingSwipe = false
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                trackingSwipe = true
                triggered = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (trackingSwipe && !triggered) {
                    val dy = event.rawY - downY
                    if (dy >= triggerDistancePx) {
                        triggered = true
                        trackingSwipe = false
                        onSwipeDown?.invoke()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (trackingSwipe && !triggered) {
                    val dy = event.rawY - downY
                    if (dy >= triggerDistancePx * 0.85f) {
                        triggered = true
                        onSwipeDown?.invoke()
                    }
                }
                trackingSwipe = false
            }
        }
        return super.onTouchEvent(event)
    }
}
