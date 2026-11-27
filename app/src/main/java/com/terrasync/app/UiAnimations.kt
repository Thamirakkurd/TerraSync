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
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.TextView

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

    /** Subtle crisp spring-snap for small confirmation icons & badges (no floaty bounce) */
    val SPRING_SNAP = PathInterpolator(0.22f, 1.12f, 0.36f, 1.0f)

    private const val TAG_EXITING = "terrasync_exiting_view"
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

    /** Crisp tactile haptic confirmation on high-value field actions (respects OS touch feedback settings). */
    fun hapticConfirm(view: View?) {
        if (view == null) return
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        runCatching { view.performHapticFeedback(code) }
    }

    /** Subtle light tick for ruler points, compass snap, or dock threshold. */
    fun hapticTick(view: View?) {
        if (view == null) return
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            HapticFeedbackConstants.TEXT_HANDLE_MOVE
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        runCatching { view.performHapticFeedback(code) }
    }

    /** Crisp, GPU-lean utilitarian tap feedback (avoids extra layer allocations on micro-scales). */
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
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(110L)
                        .setInterpolator(FAST_OUT_SLOW_IN)
                        .start()
                }
            }
            false
        }
    }

    /** Alias kept for call-site ergonomics. */
    fun attachSpringTouch(view: View) = attachSnappyPress(view)

    @Suppress("UNUSED_PARAMETER")
    fun applyRowRipple(ctx: Context, view: View, isDark: Boolean = false) {
        val rippleColor = Color.parseColor(if (isDark) "#22FFFFFF" else "#141C1E1B")
        val mask = GradientDrawable().apply { setColor(Color.WHITE) }
        view.background = RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask)
    }

    fun applyRoundedRipple(ctx: Context, view: View, fillColor: Int, radiusDp: Float, isDark: Boolean = false) {
        val d = ctx.resources.displayMetrics.density
        val rPx = radiusDp * d
        val content = GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = rPx
        }
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = rPx
        }
        val ripple = Color.parseColor(if (isDark) "#2AFFFFFF" else "#1A1C1E1B")
        view.background = RippleDrawable(ColorStateList.valueOf(ripple), content, mask)
    }

    /**
     * 120fps GPU-only directional chrome slide (translationX / translationY + alpha,
     * zero per-frame relayout; settles to INVISIBLE instead of GONE so CoordinatorLayout
     * never triggers a layout pass when toggling Full-Screen Clean Map Mode).
     */
    fun animateChromeSlide(
        view: View,
        hide: Boolean,
        shiftX: Float = 0f,
        shiftY: Float = 0f,
        durationMs: Long = 220L,
        startDelayMs: Long = 0L
    ) {
        view.animate().cancel()
        if (hide) {
            if (isReducedMotion(view.context)) {
                view.alpha = 0f
                view.translationX = shiftX
                view.translationY = shiftY
                view.visibility = View.INVISIBLE
                return
            }
            view.animate()
                .translationX(shiftX)
                .translationY(shiftY)
                .alpha(0f)
                .setStartDelay(if (durationMs > 0L) startDelayMs else 0L)
                .setDuration(durationMs)
                .setInterpolator(FAST_EXIT)
                .withLayer()
                .withEndAction { view.visibility = View.INVISIBLE }
                .start()
        } else {
            if (view.visibility != View.VISIBLE) {
                view.translationX = shiftX
                view.translationY = shiftY
                view.alpha = 0f
                view.visibility = View.VISIBLE
            }
            if (isReducedMotion(view.context)) {
                view.translationX = 0f
                view.translationY = 0f
                view.alpha = 1f
                return
            }
            view.animate()
                .translationX(0f)
                .translationY(0f)
                .alpha(1f)
                .setStartDelay(if (durationMs > 0L) startDelayMs else 0L)
                .setDuration(durationMs)
                .setInterpolator(EMPHASIZED_DECELERATE)
                .withLayer()
                .start()
        }
    }

    /**
     * Smoothly glides the active pill indicator inside a floating navigation capsule.
     */
    fun animateNavIndicator(indicator: View, targetX: Float, targetWidth: Int, animate: Boolean) {
        val lp = indicator.layoutParams
        if (lp != null && targetWidth > 0 && lp.width != targetWidth) {
            lp.width = targetWidth
            indicator.layoutParams = lp
        }
        indicator.animate().cancel()
        if (!animate || isReducedMotion(indicator.context)) {
            indicator.translationX = targetX
            return
        }
        indicator.animate()
            .translationX(targetX)
            .setDuration(200L)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .withLayer()
            .start()
    }

    /** Smoothly animates bottom sheet content swap with subtle vertical glide + fade. */
    fun animateSheetContentReveal(view: View) {
        view.animate().cancel()
        if (isReducedMotion(view.context)) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        val shift = 10f * view.resources.displayMetrics.density
        view.alpha = 0f
        view.translationY = shift
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(190L)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .withLayer()
            .start()
    }

    /** Crisp 180ms confirmation pop for checkmarks, stars, and active badges. */
    fun animateScalePop(view: View, startScale: Float = 0.82f, durationMs: Long = 180L) {
        if (isReducedMotion(view.context)) {
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        view.animate().cancel()
        view.scaleX = startScale
        view.scaleY = startScale
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(durationMs)
            .setInterpolator(SPRING_SNAP)
            .withLayer()
            .start()
    }

    /** Quick single-pulse confirmation bump for GPS lock or sync taps. */
    fun animatePulseBump(view: View, peakScale: Float = 1.12f) {
        if (isReducedMotion(view.context)) return
        view.animate().cancel()
        view.animate()
            .scaleX(peakScale)
            .scaleY(peakScale)
            .setDuration(90L)
            .setInterpolator(FAST_OUT_SLOW_IN)
            .withLayer()
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(140L)
                    .setInterpolator(SPRING_SNAP)
                    .withLayer()
                    .start()
            }
            .start()
    }

    /** Crisp 150ms scale/fade micro-morph when swapping an icon drawable & tint. */
    fun animateIconSwap(view: ImageView, newRes: Int, newTint: Int? = null, clearTint: Boolean = false) {
        if (isReducedMotion(view.context)) {
            view.setImageResource(newRes)
            if (clearTint) view.clearColorFilter() else newTint?.let { view.setColorFilter(it) }
            view.scaleX = 1f
            view.scaleY = 1f
            view.alpha = 1f
            return
        }
        view.animate().cancel()
        view.animate()
            .scaleX(0.76f)
            .scaleY(0.76f)
            .alpha(0.35f)
            .setDuration(70L)
            .setInterpolator(FAST_EXIT)
            .withLayer()
            .withEndAction {
                view.setImageResource(newRes)
                if (clearTint) view.clearColorFilter() else newTint?.let { view.setColorFilter(it) }
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(140L)
                    .setInterpolator(SPRING_SNAP)
                    .withLayer()
                    .start()
            }
            .start()
    }

    /** Subtle directional text crossfade for navigation maneuvers and state labels. */
    fun animateCrossfadeText(tv: TextView, newText: CharSequence, slideDp: Float = 6f) {
        if (tv.text?.toString() == newText.toString()) return
        if (isReducedMotion(tv.context)) {
            tv.text = newText
            tv.alpha = 1f
            tv.translationY = 0f
            return
        }
        val d = tv.resources.displayMetrics.density
        tv.animate().cancel()
        tv.animate()
            .alpha(0f)
            .translationY(-slideDp * d)
            .setDuration(85L)
            .setInterpolator(FAST_EXIT)
            .withEndAction {
                tv.text = newText
                tv.translationY = slideDp * d
                tv.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(145L)
                    .setInterpolator(EMPHASIZED_DECELERATE)
                    .start()
            }
            .start()
    }

    /** Smooth scale + fade entrance/exit for circular Speed HUD badge. */
    fun animateSpeedBadgeVisibility(view: View, show: Boolean) {
        if (show) {
            if (view.visibility == View.VISIBLE && view.alpha >= 0.99f && view.scaleX >= 0.99f) return
            view.animate().cancel()
            view.visibility = View.VISIBLE
            if (isReducedMotion(view.context)) {
                view.alpha = 1f
                view.scaleX = 1f
                view.scaleY = 1f
                return
            }
            view.alpha = 0f
            view.scaleX = 0.84f
            view.scaleY = 0.84f
            view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180L)
                .setInterpolator(EMPHASIZED_DECELERATE)
                .withLayer()
                .start()
        } else {
            if (view.visibility != View.VISIBLE) return
            view.animate().cancel()
            if (isReducedMotion(view.context)) {
                view.visibility = View.GONE
                view.alpha = 1f
                view.scaleX = 1f
                view.scaleY = 1f
                return
            }
            view.animate()
                .alpha(0f)
                .scaleX(0.86f)
                .scaleY(0.86f)
                .setDuration(130L)
                .setInterpolator(FAST_EXIT)
                .withLayer()
                .withEndAction {
                    view.visibility = View.GONE
                    view.alpha = 1f
                    view.scaleX = 1f
                    view.scaleY = 1f
                }
                .start()
        }
    }

    /** Subtle scale-fade entrance for AlertDialogs. */
    fun animateDialogEnter(decorView: View?) {
        if (decorView == null || isReducedMotion(decorView.context)) return
        decorView.animate().cancel()
        decorView.alpha = 0f
        decorView.scaleX = 0.95f
        decorView.scaleY = 0.95f
        decorView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(175L)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .withLayer()
            .start()
    }

    /** Lightweight reveal for dynamically populated search/filter results inside an open page. */
    fun animateResultReveal(view: View) {
        if (isReducedMotion(view.context)) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        val shift = 10f * view.resources.displayMetrics.density
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = shift
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(175L)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .withLayer()
            .start()
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
        if (view.visibility != View.VISIBLE) {
            view.visibility = View.VISIBLE
            view.alpha = 0f
            view.translationY = shift
        }
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
        if (view.visibility != View.VISIBLE) {
            view.visibility = View.VISIBLE
            view.alpha = 0f
            view.translationY = shift
        }
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

    /**
     * Smooth IME (keyboard) glide for bottom-anchored content. Applies interpolated bottom padding only
     * during the ~250ms keyboard animation (never during map pan/zoom). No-op under Reduced Motion.
     */
    fun attachImePaddingGlide(
        target: View,
        navBottomProvider: () -> Int,
        restingBottomProvider: () -> Int,
        applyBottom: (Int) -> Unit
    ) {
        var imeAnim: androidx.core.view.WindowInsetsAnimationCompat? = null
        var startBottom = 0
        var endBottom = 0
        var lastApplied = -1
        androidx.core.view.ViewCompat.setWindowInsetsAnimationCallback(
            target,
            object : androidx.core.view.WindowInsetsAnimationCompat.Callback(androidx.core.view.WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP) {
                override fun onPrepare(animation: androidx.core.view.WindowInsetsAnimationCompat) {
                    if ((animation.typeMask and androidx.core.view.WindowInsetsCompat.Type.ime()) != 0 &&
                        !isReducedMotion(target.context)
                    ) {
                        imeAnim = animation
                        startBottom = restingBottomProvider()
                        endBottom = startBottom
                        lastApplied = -1
                    }
                }

                override fun onStart(
                    animation: androidx.core.view.WindowInsetsAnimationCompat,
                    bounds: androidx.core.view.WindowInsetsAnimationCompat.BoundsCompat
                ): androidx.core.view.WindowInsetsAnimationCompat.BoundsCompat {
                    if (animation === imeAnim) {
                        endBottom = restingBottomProvider()
                        if (endBottom == startBottom) {
                            imeAnim = null
                        } else {
                            lastApplied = startBottom
                            applyBottom(startBottom)
                        }
                    }
                    return bounds
                }

                override fun onProgress(
                    insets: androidx.core.view.WindowInsetsCompat,
                    runningAnimations: List<androidx.core.view.WindowInsetsAnimationCompat>
                ): androidx.core.view.WindowInsetsCompat {
                    val anim = imeAnim ?: return insets
                    if (runningAnimations.none { it === anim }) return insets
                    val imeBottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
                    val cur = maxOf(navBottomProvider(), imeBottom)
                        .coerceIn(minOf(startBottom, endBottom), maxOf(startBottom, endBottom))
                    if (cur != lastApplied) {
                        lastApplied = cur
                        applyBottom(cur)
                    }
                    return insets
                }

                override fun onEnd(animation: androidx.core.view.WindowInsetsAnimationCompat) {
                    if (animation === imeAnim) {
                        imeAnim = null
                        lastApplied = -1
                        applyBottom(restingBottomProvider())
                    }
                }
            }
        )
    }

    /** Android 14+ predictive-back preview: subtle horizontal shift + scale on the full-screen overlay page. */
    fun applyPredictiveBackProgress(view: View, progress: Float, fromLeftEdge: Boolean) {
        val eased = android.view.animation.DecelerateInterpolator().getInterpolation(progress.coerceIn(0f, 1f))
        val maxShift = minOf(view.width * 0.06f, 28f * view.resources.displayMetrics.density)
        view.translationX = (if (fromLeftEdge) 1f else -1f) * maxShift * eased
        val scale = 1f - 0.05f * eased
        view.scaleX = scale
        view.scaleY = scale
    }

    fun cancelPredictiveBack(view: View) {
        view.animate()
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(160L)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .start()
    }

    fun resetPredictiveBack(view: View) {
        view.translationX = 0f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    fun animatePageEnter(container: View) {
        container.animate().cancel()
        resetPredictiveBack(container)
        container.visibility = View.VISIBLE
        if (isReducedMotion(container.context)) {
            container.alpha = 1f
            container.translationY = 0f
            return
        }
        val shift = 20f * container.resources.displayMetrics.density
        container.alpha = 0.01f
        container.translationY = shift
        container.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(175L)
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
            resetPredictiveBack(container)
            onEnd()
            return
        }
        val shift = 18f * container.resources.displayMetrics.density
        container.animate()
            .alpha(0f)
            .translationY(shift)
            .setDuration(135L)
            .setInterpolator(FAST_EXIT)
            .withEndAction {
                container.visibility = View.GONE
                container.alpha = 1f
                container.translationY = 0f
                resetPredictiveBack(container)
                onEnd()
            }
            .start()
    }

    private fun pruneExitingChildren(container: ViewGroup): List<View> {
        val active = ArrayList<View>()
        val stale = ArrayList<View>()
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            child.animate().cancel()
            child.elevation = 0f
            if (child.tag == TAG_EXITING) {
                stale.add(child)
            } else {
                active.add(child)
            }
        }
        stale.forEach { container.removeView(it) }
        return active
    }

    /**
     * Smooth native dual-view horizontal Push (e.g., Settings Menu -> Section Sub-Page).
     * Incoming sub-page stays 100% opaque (zero text ghosting/bleed-through) while gliding
     * cleanly over the subtly receding parent page at up to 120fps.
     */
    fun transitionSubPagePush(container: ViewGroup, incoming: View, isRtl: Boolean) {
        val outgoingViews = pruneExitingChildren(container)

        if (isReducedMotion(container.context) || outgoingViews.isEmpty()) {
            container.removeAllViews()
            container.addView(incoming)
            return
        }

        val dir = if (isRtl) -1f else 1f
        val d = container.resources.displayMetrics.density
        val widthPx = container.width.takeIf { it > 0 }?.toFloat() ?: (360f * d)
        val inStart = widthPx * dir
        val outEnd = -(widthPx * 0.14f) * dir

        incoming.translationX = inStart
        incoming.alpha = 1f
        incoming.elevation = 6f * d
        container.addView(incoming)

        outgoingViews.forEach { old ->
            old.tag = TAG_EXITING
            old.animate()
                .translationX(outEnd)
                .setDuration(220L)
                .setInterpolator(EMPHASIZED_DECELERATE)
                .withLayer()
                .withEndAction { container.removeView(old) }
                .start()
        }

        incoming.animate()
            .translationX(0f)
            .setDuration(220L)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .withLayer()
            .withEndAction { incoming.elevation = 0f }
            .start()
    }

    /**
     * Smooth native dual-view horizontal Pop (e.g., Section Sub-Page -> Settings Menu).
     * Both pages stay 100% opaque with pure GPU translationX (zero double-text bleed).
     */
    fun transitionSubPagePop(container: ViewGroup, backView: View, isRtl: Boolean) {
        val outgoingViews = pruneExitingChildren(container)

        if (isReducedMotion(container.context) || outgoingViews.isEmpty()) {
            container.removeAllViews()
            container.addView(backView)
            return
        }

        val dir = if (isRtl) -1f else 1f
        val d = container.resources.displayMetrics.density
        val widthPx = container.width.takeIf { it > 0 }?.toFloat() ?: (360f * d)
        val backStart = -(widthPx * 0.14f) * dir
        val outEnd = widthPx * dir

        backView.translationX = backStart
        backView.alpha = 1f
        container.addView(backView, 0)

        backView.animate()
            .translationX(0f)
            .setDuration(210L)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .withLayer()
            .start()

        outgoingViews.forEach { old ->
            old.tag = TAG_EXITING
            old.elevation = 6f * d
            old.animate()
                .translationX(outEnd)
                .setDuration(210L)
                .setInterpolator(EMPHASIZED_DECELERATE)
                .withLayer()
                .withEndAction { container.removeView(old) }
                .start()
        }
    }

    /** Clean in-place crossfade (e.g., switching Day/Night or App Language inside a Settings sub-page). */
    fun transitionCrossfade(container: ViewGroup, incoming: View) {
        val outgoingViews = pruneExitingChildren(container)

        if (isReducedMotion(container.context) || outgoingViews.isEmpty()) {
            container.removeAllViews()
            container.addView(incoming)
            return
        }

        outgoingViews.forEach { it.tag = TAG_EXITING }
        incoming.alpha = 0f
        container.addView(incoming)
        incoming.animate()
            .alpha(1f)
            .setDuration(160L)
            .setInterpolator(FAST_OUT_SLOW_IN)
            .withLayer()
            .withEndAction {
                outgoingViews.forEach { container.removeView(it) }
            }
            .start()
    }

    /** Crisp 160ms directional horizontal glide + alpha crossfade for stacked pin & sheet swaps. */
    fun animateContentSwap(view: View, forward: Boolean = true) {
        view.animate().cancel()
        if (isReducedMotion(view.context)) {
            view.translationX = 0f
            view.alpha = 1f
            return
        }
        val d = view.resources.displayMetrics.density
        val isRtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val dir = if (forward) (if (isRtl) -1f else 1f) else (if (isRtl) 1f else -1f)
        view.translationX = dir * 14f * d
        view.alpha = 0.25f
        view.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(160L)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .withLayer()
            .start()
    }

    /** Fast 18ms staggered vertical reveal for dynamic tab/filter switches inside an already open page. */
    fun staggerRevealChildren(parent: ViewGroup, maxAnimated: Int = 5) {
        if (isReducedMotion(parent.context) || parent.childCount == 0) return
        val d = parent.resources.displayMetrics.density
        val count = minOf(parent.childCount, maxAnimated)
        for (idx in 0 until count) {
            val child = parent.getChildAt(idx)
            child.animate().cancel()
            child.alpha = 0f
            child.translationY = 8f * d
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(idx * 18L)
                .setDuration(155L)
                .setInterpolator(EMPHASIZED_DECELERATE)
                .start()
        }
    }
}
