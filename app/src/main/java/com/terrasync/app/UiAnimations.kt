package com.terrasync.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

object UiAnimations {

    @SuppressLint("ClickableViewAccessibility")
    fun attachSpringTouch(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(0.92f)
                        .scaleY(0.92f)
                        .setDuration(80)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .setInterpolator(OvershootInterpolator(1.8f))
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

    fun styleFloatingPill(
        ctx: Context,
        view: View,
        bgColor: Int = Color.WHITE,
        radiusDp: Float = 28f,
        elevationDp: Float = 8f,
        strokeColor: Int = Color.parseColor("#E2E8F0")
    ) {
        val d = ctx.resources.displayMetrics.density
        view.background = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = radiusDp * d
            setStroke((1f * d).toInt().coerceAtLeast(1), strokeColor)
        }
        view.elevation = elevationDp * d
        view.clipToOutline = true
    }

    fun applyRowRipple(ctx: Context, view: View, isDark: Boolean = false) {
        val rippleColor = Color.parseColor(if (isDark) "#28FFFFFF" else "#1A0F172A")
        val mask = GradientDrawable().apply { setColor(Color.WHITE) }
        view.background = RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask)
    }

    fun applyPillItemRipple(ctx: Context, view: View, radiusDp: Float = 24f) {
        val d = ctx.resources.displayMetrics.density
        val rippleColor = Color.parseColor("#222563EB")
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = radiusDp * d
        }
        view.background = RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask)
        attachSpringTouch(view)
    }

    fun showSlideTop(view: View, durationMs: Long = 220L) {
        view.animate().cancel()
        if (view.visibility == View.VISIBLE && view.alpha >= 0.99f && view.translationY == 0f) return
        val shift = -(24f * view.resources.displayMetrics.density)
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationY = shift
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun hideSlideTop(view: View, durationMs: Long = 160L) {
        view.animate().cancel()
        if (view.visibility != View.VISIBLE) return
        val shift = -(20f * view.resources.displayMetrics.density)
        view.animate()
            .alpha(0f)
            .translationY(shift)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                view.visibility = View.GONE
                view.alpha = 1f
                view.translationY = 0f
            }
            .start()
    }

    fun showSlideBottom(view: View, durationMs: Long = 220L) {
        view.animate().cancel()
        if (view.visibility == View.VISIBLE && view.alpha >= 0.99f && view.translationY == 0f) return
        val shift = 24f * view.resources.displayMetrics.density
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationY = shift
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun hideSlideBottom(view: View, durationMs: Long = 160L) {
        view.animate().cancel()
        if (view.visibility != View.VISIBLE) return
        val shift = 20f * view.resources.displayMetrics.density
        view.animate()
            .alpha(0f)
            .translationY(shift)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                view.visibility = View.GONE
                view.alpha = 1f
                view.translationY = 0f
            }
            .start()
    }

    fun fadeShow(view: View, durationMs: Long = 180L) {
        view.animate().cancel()
        if (view.visibility == View.VISIBLE && view.alpha >= 0.99f) return
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun fadeHide(view: View, durationMs: Long = 150L) {
        view.animate().cancel()
        if (view.visibility != View.VISIBLE) return
        view.animate()
            .alpha(0f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                view.visibility = View.GONE
                view.alpha = 1f
            }
            .start()
    }
}
