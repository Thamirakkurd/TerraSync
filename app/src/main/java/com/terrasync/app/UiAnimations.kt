package com.terrasync.app

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator

/**
 * Fast, snappy, utilitarian animations aligned with Organic Maps:
 * zero floaty springs or heavy overshoot, hardware-layered transitions,
 * and full Reduced Motion support.
 */
object UiAnimations {

    val FAST_OUT_SLOW_IN = PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f)
    val FAST_EXIT = PathInterpolator(0.4f, 0.0f, 1.0f, 1.0f)

    private val pressAttached = java.util.Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())

    fun isReducedMotion(ctx: Context): Boolean {
        if (DevicePrefs.isReducedMotion(ctx)) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()) {
            return true
        }
        val scale = runCatching {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        return scale == 0f
    }

    /** Crisp, subtle utilitarian tap feedback (no bouncy overshoot). */
    @SuppressLint("ClickableViewAccessibility")
    fun attachSnappyPress(view: View) {
        if (!pressAttached.add(view)) return
        view.setOnTouchListener { v, event ->
            if (isReducedMotion(v.context)) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(0.96f)
                        .scaleY(0.96f)
                        .setDuration(55L)
                        .setInterpolator(FAST_OUT_SLOW_IN)
                        .withLayer()
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(95L)
                        .setInterpolator(FAST_OUT_SLOW_IN)
                        .withLayer()
                        .start()
                }
            }
            false
        }
    }

    /** Alias kept for call-site ergonomics. */
    fun attachSpringTouch(view: View) = attachSnappyPress(view)

    fun applyRowRipple(ctx: Context, view: View, isDark: Boolean = false) {
        val rippleColor = Color.parseColor(if (isDark) "#22FFFFFF" else "#141C1C1E")
        val mask = GradientDrawable().apply { setColor(Color.WHITE) }
        view.background = RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask)
    }

    fun showSlideTop(view: View, durationMs: Long = 150L) {
        view.animate().cancel()
        if (isReducedMotion(view.context)) {
            view.visibility = View.VISIBLE
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        if (view.visibility == View.VISIBLE && view.alpha >= 0.99f && view.translationY == 0f) return
        val shift = -(12f * view.resources.displayMetrics.density)
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationY = shift
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(durationMs)
            .setInterpolator(FAST_OUT_SLOW_IN)
            .withLayer()
            .start()
    }

    fun hideSlideTop(view: View, durationMs: Long = 120L) {
        view.animate().cancel()
        if (view.visibility != View.VISIBLE) return
        if (isReducedMotion(view.context)) {
            view.visibility = View.GONE
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        val shift = -(10f * view.resources.displayMetrics.density)
        view.animate()
            .alpha(0f)
            .translationY(shift)
            .setDuration(durationMs)
            .setInterpolator(FAST_EXIT)
            .withLayer()
            .withEndAction {
                view.visibility = View.GONE
                view.alpha = 1f
                view.translationY = 0f
            }
            .start()
    }

    fun showSlideBottom(view: View, durationMs: Long = 150L) {
        view.animate().cancel()
        if (isReducedMotion(view.context)) {
            view.visibility = View.VISIBLE
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        if (view.visibility == View.VISIBLE && view.alpha >= 0.99f && view.translationY == 0f) return
        val shift = 14f * view.resources.displayMetrics.density
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationY = shift
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(durationMs)
            .setInterpolator(FAST_OUT_SLOW_IN)
            .withLayer()
            .start()
    }

    fun hideSlideBottom(view: View, durationMs: Long = 120L) {
        view.animate().cancel()
        if (view.visibility != View.VISIBLE) return
        if (isReducedMotion(view.context)) {
            view.visibility = View.GONE
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        val shift = 12f * view.resources.displayMetrics.density
        view.animate()
            .alpha(0f)
            .translationY(shift)
            .setDuration(durationMs)
            .setInterpolator(FAST_EXIT)
            .withLayer()
            .withEndAction {
                view.visibility = View.GONE
                view.alpha = 1f
                view.translationY = 0f
            }
            .start()
    }

    fun fadeShow(view: View, durationMs: Long = 130L) {
        view.animate().cancel()
        if (isReducedMotion(view.context)) {
            view.visibility = View.VISIBLE
            view.alpha = 1f
            return
        }
        if (view.visibility == View.VISIBLE && view.alpha >= 0.99f) return
        if (view.visibility != View.VISIBLE) view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(durationMs)
            .setInterpolator(FAST_OUT_SLOW_IN)
            .withLayer()
            .start()
    }

    fun fadeHide(view: View, durationMs: Long = 110L) {
        view.animate().cancel()
        if (view.visibility != View.VISIBLE) return
        if (isReducedMotion(view.context)) {
            view.visibility = View.GONE
            view.alpha = 1f
            return
        }
        view.animate()
            .alpha(0f)
            .setDuration(durationMs)
            .setInterpolator(FAST_EXIT)
            .withLayer()
            .withEndAction {
                view.visibility = View.GONE
                view.alpha = 1f
            }
            .start()
    }

    fun animatePageEnter(container: View) {
        container.animate().cancel()
        container.visibility = View.VISIBLE
        if (isReducedMotion(container.context)) {
            container.alpha = 1f
            container.translationY = 0f
            return
        }
        val shift = 14f * container.resources.displayMetrics.density
        container.alpha = 0f
        container.translationY = shift
        container.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(155L)
            .setInterpolator(FAST_OUT_SLOW_IN)
            .withLayer()
            .start()
    }

    fun animateSubPageEnter(view: View) {
        view.animate().cancel()
        if (isReducedMotion(view.context)) {
            view.alpha = 1f
            view.translationX = 0f
            return
        }
        val shift = 18f * view.resources.displayMetrics.density
        view.alpha = 0f
        view.translationX = shift
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(150L)
            .setInterpolator(FAST_OUT_SLOW_IN)
            .withLayer()
            .start()
    }

    fun animatePageExit(container: View, onEnd: () -> Unit) {
        container.animate().cancel()
        if (isReducedMotion(container.context)) {
            container.visibility = View.GONE
            container.alpha = 1f
            container.translationY = 0f
            onEnd()
            return
        }
        val shift = 12f * container.resources.displayMetrics.density
        container.animate()
            .alpha(0f)
            .translationY(shift)
            .setDuration(125L)
            .setInterpolator(FAST_EXIT)
            .withLayer()
            .withEndAction {
                container.visibility = View.GONE
                container.alpha = 1f
                container.translationY = 0f
                onEnd()
            }
            .start()
    }

    fun animateContentSwap(view: View) {
        view.animate().cancel()
        if (isReducedMotion(view.context)) {
            view.alpha = 1f
            view.translationX = 0f
            return
        }
        val shift = 12f * view.resources.displayMetrics.density
        view.alpha = 0f
        view.translationX = shift
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(135L)
            .setInterpolator(FAST_OUT_SLOW_IN)
            .withLayer()
            .start()
    }

    /** Fast, subtle single-pass list reveal (no floaty staggered cascading). */
    fun staggerRevealChildren(parent: ViewGroup, maxAnimated: Int = 6) {
        if (isReducedMotion(parent.context)) return
        parent.animate().cancel()
        parent.alpha = 0f
        parent.animate()
            .alpha(1f)
            .setDuration(130L)
            .setInterpolator(FAST_OUT_SLOW_IN)
            .withLayer()
            .start()
    }
}
