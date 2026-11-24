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
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator

object UiAnimations {

    /** Material 3 Emphasized Decelerate — smooth, crafted entrances */
    val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)

    /** Material 3 Emphasized Accelerate — crisp, decisive exits */
    val EMPHASIZED_ACCELERATE = PathInterpolator(0.3f, 0.0f, 0.8f, 0.15f)

    /** Standard expressive curve for micro-interactions */
    val STANDARD_EASE = PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f)

    private val TACTILE_RELEASE = OvershootInterpolator(1.2f)
    private val springAttached = java.util.Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())

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

    @SuppressLint("ClickableViewAccessibility")
    fun attachSpringTouch(view: View) {
        if (!springAttached.add(view)) return
        view.setOnTouchListener { v, event ->
            if (isReducedMotion(v.context)) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(0.94f)
                        .scaleY(0.94f)
                        .setDuration(75L)
                        .setInterpolator(STANDARD_EASE)
                        .withLayer()
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(190L)
                        .setInterpolator(TACTILE_RELEASE)
                        .withLayer()
                        .start()
                }
            }
            false
        }
    }

    fun styleFloatingTile(
        ctx: Context,
        view: View,
        bgColor: Int,
        radiusDp: Float,
        elevationDp: Float = 6f,
        rippleHex: String = "#262563EB",
        strokeColor: Int = Color.parseColor("#E2E8F0"),
        strokeDp: Float = 1f
    ) {
        val d = ctx.resources.displayMetrics.density
        val bg = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = radiusDp * d
            if (strokeDp > 0f && strokeColor != Color.TRANSPARENT) {
                setStroke((strokeDp * d).toInt().coerceAtLeast(1), strokeColor)
            }
        }
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = radiusDp * d
        }
        view.background = RippleDrawable(ColorStateList.valueOf(Color.parseColor(rippleHex)), bg, mask)
        view.elevation = elevationDp * d
        attachSpringTouch(view)
    }

    fun applyRowRipple(ctx: Context, view: View, isDark: Boolean = false) {
        val rippleColor = Color.parseColor(if (isDark) "#28FFFFFF" else "#160F172A")
        val mask = GradientDrawable().apply { setColor(Color.WHITE) }
        view.background = RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask)
    }

    fun showSlideTop(view: View, durationMs: Long = 260L) {
        view.animate().cancel()
        if (isReducedMotion(view.context)) {
            view.visibility = View.VISIBLE
            view.alpha = 1f
            view.translationY = 0f
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        if (view.visibility == View.VISIBLE && view.alpha >= 0.99f && view.translationY == 0f) return
        val shift = -(20f * view.resources.displayMetrics.density)
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationY = shift
        view.scaleX = 0.97f
        view.scaleY = 0.97f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(durationMs)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .withLayer()
            .start()
    }

    fun hideSlideTop(view: View, durationMs: Long = 175L) {
        view.animate().cancel()
        if (view.visibility != View.VISIBLE) return
        if (isReducedMotion(view.context)) {
            view.visibility = View.GONE
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        val shift = -(16f * view.resources.displayMetrics.density)
        view.animate()
            .alpha(0f)
            .translationY(shift)
            .setDuration(durationMs)
            .setInterpolator(EMPHASIZED_ACCELERATE)
            .withLayer()
            .withEndAction {
                view.visibility = View.GONE
                view.alpha = 1f
                view.translationY = 0f
                view.scaleX = 1f
                view.scaleY = 1f
            }
            .start()
    }

    fun showSlideBottom(view: View, durationMs: Long = 260L) {
        view.animate().cancel()
        if (isReducedMotion(view.context)) {
            view.visibility = View.VISIBLE
            view.alpha = 1f
            view.translationY = 0f
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        if (view.visibility == View.VISIBLE && view.alpha >= 0.99f && view.translationY == 0f) return
        val shift = 22f * view.resources.displayMetrics.density
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationY = shift
        view.scaleX = 0.98f
        view.scaleY = 0.98f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(durationMs)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .withLayer()
            .start()
    }

    fun hideSlideBottom(view: View, durationMs: Long = 175L) {
        view.animate().cancel()
        if (view.visibility != View.VISIBLE) return
        if (isReducedMotion(view.context)) {
            view.visibility = View.GONE
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        val shift = 18f * view.resources.displayMetrics.density
        view.animate()
            .alpha(0f)
            .translationY(shift)
            .setDuration(durationMs)
            .setInterpolator(EMPHASIZED_ACCELERATE)
            .withLayer()
            .withEndAction {
                view.visibility = View.GONE
                view.alpha = 1f
                view.translationY = 0f
                view.scaleX = 1f
                view.scaleY = 1f
            }
            .start()
    }

    fun fadeShow(view: View, durationMs: Long = 220L) {
        view.animate().cancel()
        if (isReducedMotion(view.context)) {
            view.visibility = View.VISIBLE
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        if (view.visibility == View.VISIBLE && view.alpha >= 0.99f) return
        if (view.visibility != View.VISIBLE) view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(durationMs)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .withLayer()
            .start()
    }

    fun fadeHide(view: View, durationMs: Long = 150L) {
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
            .setInterpolator(EMPHASIZED_ACCELERATE)
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
            container.scaleX = 1f
            container.scaleY = 1f
            return
        }
        val shift = 28f * container.resources.displayMetrics.density
        container.alpha = 0f
        container.translationY = shift
        container.scaleX = 0.985f
        container.scaleY = 0.985f
        container.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(280L)
            .setInterpolator(EMPHASIZED_DECELERATE)
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
        val shift = 28f * view.resources.displayMetrics.density
        view.alpha = 0f
        view.translationX = shift
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(240L)
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
            container.scaleX = 1f
            container.scaleY = 1f
            onEnd()
            return
        }
        val shift = 22f * container.resources.displayMetrics.density
        container.animate()
            .alpha(0f)
            .translationY(shift)
            .scaleX(0.985f)
            .scaleY(0.985f)
            .setDuration(190L)
            .setInterpolator(EMPHASIZED_ACCELERATE)
            .withLayer()
            .withEndAction {
                container.visibility = View.GONE
                container.alpha = 1f
                container.translationY = 0f
                container.scaleX = 1f
                container.scaleY = 1f
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
        val shift = 20f * view.resources.displayMetrics.density
        view.alpha = 0f
        view.translationX = shift
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(220L)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .withLayer()
            .start()
    }

    fun staggerRevealChildren(parent: ViewGroup, maxAnimated: Int = 8) {
        if (isReducedMotion(parent.context)) return
        val shift = 12f * parent.resources.displayMetrics.density
        val count = parent.childCount.coerceAtMost(maxAnimated)
        for (i in 0 until count) {
            val child = parent.getChildAt(i)
            child.animate().cancel()
            child.alpha = 0f
            child.translationY = shift
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(i * 22L)
                .setDuration(240L)
                .setInterpolator(EMPHASIZED_DECELERATE)
                .withLayer()
                .start()
        }
    }
}
