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
 * Crisp, GPU-accelerated utilitarian motion system for Organic Maps minimalism:
 * smooth dual-view horizontal push/pop navigation for Settings & Bookmarks,
 * zero map bleed-through, subtle tactile feedback, and full Reduced Motion support.
 */
object UiAnimations {

    /** Smooth emphasized decelerate for page/sheet entrances & shared-axis slides */
    val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)

    /** Standard fast-out-slow-in for micro-interactions */
    val FAST_OUT_SLOW_IN = PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f)

    /** Clean decisive exit curve */
    val FAST_EXIT = PathInterpolator(0.35f, 0.0f, 0.85f, 0.15f)

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
    fun attachSnappyPress(view: View, pressScale: Float = 0.96f) {
        if (!pressAttached.add(view)) return
        view.setOnTouchListener { v, event ->
            if (isReducedMotion(v.context)) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(pressScale)
                        .scaleY(pressScale)
                        .setDuration(60L)
                        .setInterpolator(FAST_OUT_SLOW_IN)
                        .withLayer()
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(110L)
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

    fun showSlideTop(view: View, durationMs: Long = 175L) {
        view.animate().cancel()
        if (isReducedMotion(view.context)) {
            view.visibility = View.VISIBLE
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        if (view.visibility == View.VISIBLE && view.alpha >= 0.99f && view.translationY == 0f) return
        val shift = -(14f * view.resources.displayMetrics.density)
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationY = shift
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(durationMs)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .withLayer()
            .start()
    }

    fun hideSlideTop(view: View, durationMs: Long = 135L) {
        view.animate().cancel()
        if (view.visibility != View.VISIBLE) return
        if (isReducedMotion(view.context)) {
            view.visibility = View.GONE
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        val shift = -(12f * view.resources.displayMetrics.density)
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

    fun showSlideBottom(view: View, durationMs: Long = 180L) {
        view.animate().cancel()
        if (isReducedMotion(view.context)) {
            view.visibility = View.VISIBLE
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        if (view.visibility == View.VISIBLE && view.alpha >= 0.99f && view.translationY == 0f) return
        val shift = 16f * view.resources.displayMetrics.density
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationY = shift
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(durationMs)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .withLayer()
            .start()
    }

    fun hideSlideBottom(view: View, durationMs: Long = 135L) {
        view.animate().cancel()
        if (view.visibility != View.VISIBLE) return
        if (isReducedMotion(view.context)) {
            view.visibility = View.GONE
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        val shift = 14f * view.resources.displayMetrics.density
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

    fun fadeShow(view: View, durationMs: Long = 145L) {
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

    fun fadeHide(view: View, durationMs: Long = 120L) {
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
        val shift = 22f * container.resources.displayMetrics.density
        container.alpha = 0f
        container.translationY = shift
        container.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200L)
            .setInterpolator(EMPHASIZED_DECELERATE)
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
        val shift = 18f * container.resources.displayMetrics.density
        container.animate()
            .alpha(0f)
            .translationY(shift)
            .setDuration(155L)
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

    /**
     * Smooth native dual-view horizontal Push (e.g., Settings Menu -> Section Sub-Page).
     * Outgoing view glides slightly left while incoming sub-page slides cleanly from right.
     * Zero map bleed-through.
     */
    fun transitionSubPagePush(container: ViewGroup, incoming: View, isRtl: Boolean) {
        val outgoingViews = (0 until container.childCount).map { container.getChildAt(it) }
        outgoingViews.forEach { it.animate().cancel() }

        if (isReducedMotion(container.context) || outgoingViews.isEmpty()) {
            container.removeAllViews()
            container.addView(incoming)
            return
        }

        val dir = if (isRtl) -1f else 1f
        val d = container.resources.displayMetrics.density
        val inStart = 76f * d * dir
        val outEnd = -28f * d * dir

        incoming.translationX = inStart
        incoming.alpha = 0.72f
        container.addView(incoming)

        outgoingViews.forEach { old ->
            old.animate()
                .translationX(outEnd)
                .alpha(0.84f)
                .setDuration(210L)
                .setInterpolator(EMPHASIZED_DECELERATE)
                .withLayer()
                .withEndAction { container.removeView(old) }
                .start()
        }

        incoming.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(220L)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .withLayer()
            .start()
    }

    /**
     * Smooth native dual-view horizontal Pop (e.g., Section Sub-Page -> Settings Menu).
     * Previous menu glides gently back from left while outgoing sub-page slides out right.
     */
    fun transitionSubPagePop(container: ViewGroup, backView: View, isRtl: Boolean) {
        val outgoingViews = (0 until container.childCount).map { container.getChildAt(it) }
        outgoingViews.forEach { it.animate().cancel() }

        if (isReducedMotion(container.context) || outgoingViews.isEmpty()) {
            container.removeAllViews()
            container.addView(backView)
            return
        }

        val dir = if (isRtl) -1f else 1f
        val d = container.resources.displayMetrics.density
        val backStart = -28f * d * dir
        val outEnd = 76f * d * dir

        backView.translationX = backStart
        backView.alpha = 0.85f
        container.addView(backView, 0)

        backView.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(205L)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .withLayer()
            .start()

        outgoingViews.forEach { old ->
            old.animate()
                .translationX(outEnd)
                .alpha(0f)
                .setDuration(185L)
                .setInterpolator(FAST_OUT_SLOW_IN)
                .withLayer()
                .withEndAction { container.removeView(old) }
                .start()
        }
    }

    /** Clean in-place crossfade (e.g., switching Day/Night or App Language inside a Settings sub-page). */
    fun transitionCrossfade(container: ViewGroup, incoming: View) {
        val outgoingViews = (0 until container.childCount).map { container.getChildAt(it) }
        outgoingViews.forEach { it.animate().cancel() }

        if (isReducedMotion(container.context) || outgoingViews.isEmpty()) {
            container.removeAllViews()
            container.addView(incoming)
            return
        }

        incoming.alpha = 0f
        container.addView(incoming)
        incoming.animate()
            .alpha(1f)
            .setDuration(150L)
            .setInterpolator(FAST_OUT_SLOW_IN)
            .withLayer()
            .withEndAction {
                outgoingViews.forEach { container.removeView(it) }
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
        val shift = 14f * view.resources.displayMetrics.density
        view.alpha = 0f
        view.translationX = shift
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(155L)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .withLayer()
            .start()
    }

    /** Subtle optional content reveal (no double-fade when container already animates). */
    fun staggerRevealChildren(parent: ViewGroup, maxAnimated: Int = 6) {
        // Container-level push/pop/enter handles page transitions cleanly without double-fading.
    }
}
