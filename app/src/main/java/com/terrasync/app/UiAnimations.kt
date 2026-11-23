package com.terrasync.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator

object UiAnimations {

    @SuppressLint("ClickableViewAccessibility")
    fun attachSpringTouch(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    v.animate()
                        .scaleX(0.91f)
                        .scaleY(0.91f)
                        .setDuration(85)
                        .setInterpolator(null)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(220)
                        .setInterpolator(OvershootInterpolator(2.2f))
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
        rippleHex: String = "#332563EB"
    ) {
        val d = ctx.resources.displayMetrics.density
        val bg = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = radiusDp * d
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
        val rippleColor = Color.parseColor(if (isDark) "#28FFFFFF" else "#1A0F172A")
        val mask = GradientDrawable().apply { setColor(Color.WHITE) }
        view.background = RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask)
    }
}
