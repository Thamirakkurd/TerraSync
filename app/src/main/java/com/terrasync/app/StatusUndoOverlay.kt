package com.terrasync.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Floating 5-second Undo controller for accidental field status changes (Pending / Done / Issue).
 */
class StatusUndoController(
    private val banner: LinearLayout,
    private val tvMessage: TextView,
    private val btnAction: TextView
) {
    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    fun applyTheme(ctx: Context, p: ThemePalette) {
        val d = ctx.resources.displayMetrics.density
        val radiusPx = 22f * d
        banner.background = DesignSystem.buildOpaqueDrawable(
            ctx = ctx,
            fillColor = p.actionDark,
            radiusDp = 22f,
            strokeColor = p.borderSubtle,
            strokeDp = if (p.isDark) 1f else 0f
        )
        banner.elevation = 8f * d
        banner.clipToOutline = true
        DesignSystem.applyDirectionalShadow(banner, p)
        tvMessage.setTextColor(p.onActionDark)
        btnAction.setTextColor(p.accentWarm)
        UiAnimations.applyRoundedRipple(
            ctx,
            btnAction,
            android.graphics.Color.argb(44, 240, 101, 31),
            14f,
            isDark = !p.isDark
        )
        UiAnimations.attachSnappyPress(btnAction, 0.94f)
        banner.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        banner.tag = radiusPx
    }

    fun show(
        ctx: Context,
        previousPin: PinItem,
        message: String,
        onUndo: (PinItem) -> Unit
    ) {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        banner.layoutDirection = if (AppStrings.isRtl(ctx)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        tvMessage.text = message
        btnAction.text = AppStrings.undo(ctx)
        btnAction.setOnClickListener {
            dismissRunnable?.let { handler.removeCallbacks(it) }
            dismissRunnable = null
            UiAnimations.hapticConfirm(btnAction)
            UiAnimations.hideSlideBottom(banner, 140L)
            onUndo(previousPin)
        }
        UiAnimations.showSlideBottom(banner, 175L)
        val r = Runnable {
            dismissRunnable = null
            UiAnimations.hideSlideBottom(banner, 140L)
        }
        dismissRunnable = r
        handler.postDelayed(r, 5200L)
    }

    fun dismiss(animate: Boolean = true) {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null
        if (animate) {
            UiAnimations.hideSlideBottom(banner, 140L)
        } else {
            banner.animate().cancel()
            banner.visibility = View.GONE
            banner.alpha = 1f
            banner.translationY = 0f
        }
    }
}
