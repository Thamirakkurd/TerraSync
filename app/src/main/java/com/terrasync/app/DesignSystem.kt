package com.terrasync.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton

enum class UiDesignStyle(val id: String, val label: String) {
    CLASSIC("classic", "Classic"),
    LIQUID_GLASS("liquid_glass", "Liquid Glass");

    companion object {
        fun fromId(id: String?): UiDesignStyle =
            entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

data class ThemePalette(
    val isDark: Boolean,
    val isGlass: Boolean,
    val pageBackdrop: Int,
    val cardSurface: Int,
    val cardBorder: Int,
    val tileSurface: Int,
    val tileBorder: Int,
    val sheetSurface: Int,
    val chipSurface: Int,
    val chipBorder: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val iconTint: Int,
    val divider: Int,
    val handleColor: Int,
    val accent: Int,
    val accentSoft: Int,
    val teal: Int,
    val green: Int,
    val amber: Int,
    val red: Int,
    val rippleColor: Int,
    val navBannerTop: Int,
    val navBannerBottom: Int,
    val shadowSpot: Int
)

/**
 * Hardware-accelerated frosted Liquid Glass drawable with translucent base,
 * upper specular refraction sheen, and directional rim highlight.
 */
class LiquidGlassDrawable(
    private val cornerRadiiPx: FloatArray,
    private val baseTopColor: Int,
    private val baseBottomColor: Int,
    private val sheenTopColor: Int,
    private val rimStartColor: Int,
    private val rimMidColor: Int,
    private val rimEndColor: Int,
    private val strokeWidthPx: Float
) : Drawable() {

    private val rectF = RectF()
    private val strokeRectF = RectF()
    private val clipPath = Path()
    private val strokePath = Path()

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val innerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val w = bounds.width().toFloat().coerceAtLeast(1f)
        val h = bounds.height().toFloat().coerceAtLeast(1f)

        rectF.set(bounds)
        clipPath.reset()
        clipPath.addRoundRect(rectF, cornerRadiiPx, Path.Direction.CW)

        val inset = strokeWidthPx * 0.5f
        strokeRectF.set(
            bounds.left + inset,
            bounds.top + inset,
            bounds.right - inset,
            bounds.bottom - inset
        )
        strokePath.reset()
        strokePath.addRoundRect(strokeRectF, cornerRadiiPx, Path.Direction.CW)

        basePaint.shader = LinearGradient(
            bounds.left.toFloat(), bounds.top.toFloat(),
            bounds.left.toFloat(), bounds.bottom.toFloat(),
            baseTopColor, baseBottomColor, Shader.TileMode.CLAMP
        )
        sheenPaint.shader = LinearGradient(
            bounds.left.toFloat(), bounds.top.toFloat(),
            bounds.left.toFloat(), bounds.top + h * 0.52f,
            sheenTopColor, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        innerGlowPaint.shader = LinearGradient(
            bounds.left.toFloat(), bounds.bottom - h * 0.28f,
            bounds.left.toFloat(), bounds.bottom.toFloat(),
            Color.TRANSPARENT, (sheenTopColor and 0x00FFFFFF) or 0x1A000000, Shader.TileMode.CLAMP
        )
        rimPaint.shader = LinearGradient(
            bounds.left.toFloat(), bounds.top.toFloat(),
            bounds.left + w, bounds.top + h,
            intArrayOf(rimStartColor, rimMidColor, rimEndColor),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(clipPath, basePaint)
        canvas.drawPath(clipPath, sheenPaint)
        canvas.drawPath(clipPath, innerGlowPaint)
        if (strokeWidthPx > 0f) {
            canvas.drawPath(strokePath, rimPaint)
        }
    }

    override fun getOutline(outline: Outline) {
        val uniformRadius = cornerRadiiPx[0]
        val isUniform = cornerRadiiPx.all { it == uniformRadius }
        if (isUniform) {
            outline.setRoundRect(bounds, uniformRadius)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            outline.setPath(clipPath)
        } else {
            @Suppress("DEPRECATION")
            outline.setConvexPath(clipPath)
        }
    }

    override fun setAlpha(alpha: Int) {
        basePaint.alpha = alpha
        sheenPaint.alpha = alpha
        innerGlowPaint.alpha = alpha
        rimPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        basePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

object DesignSystem {

    fun palette(isDark: Boolean, style: UiDesignStyle): ThemePalette {
        val glass = style == UiDesignStyle.LIQUID_GLASS
        return if (isDark) {
            ThemePalette(
                isDark = true,
                isGlass = glass,
                pageBackdrop = Color.parseColor(if (glass) "#E60B1220" else "#0B1220"),
                cardSurface = Color.parseColor(if (glass) "#B81E293B" else "#162032"),
                cardBorder = Color.parseColor(if (glass) "#40FFFFFF" else "#263348"),
                tileSurface = Color.parseColor(if (glass) "#C41E293B" else "#1E293B"),
                tileBorder = Color.parseColor(if (glass) "#4DFFFFFF" else "#334155"),
                sheetSurface = Color.parseColor(if (glass) "#D4162032" else "#151F30"),
                chipSurface = Color.parseColor(if (glass) "#33F8FAFC" else "#263449"),
                chipBorder = Color.parseColor(if (glass) "#38FFFFFF" else "#334155"),
                textPrimary = Color.parseColor("#F8FAFC"),
                textSecondary = Color.parseColor("#94A3B8"),
                iconTint = Color.parseColor("#F1F5F9"),
                divider = Color.parseColor(if (glass) "#26FFFFFF" else "#263348"),
                handleColor = Color.parseColor(if (glass) "#66FFFFFF" else "#475569"),
                accent = Color.parseColor("#3B82F6"),
                accentSoft = Color.parseColor("#2B3B82F6"),
                teal = Color.parseColor("#14B8A6"),
                green = Color.parseColor("#22C55E"),
                amber = Color.parseColor("#F59E0B"),
                red = Color.parseColor("#EF4444"),
                rippleColor = Color.parseColor("#30FFFFFF"),
                navBannerTop = Color.parseColor(if (glass) "#D60F172A" else "#F20F172A"),
                navBannerBottom = Color.parseColor(if (glass) "#BA0B1220" else "#EE0F172A"),
                shadowSpot = Color.parseColor("#70000000")
            )
        } else {
            ThemePalette(
                isDark = false,
                isGlass = glass,
                pageBackdrop = Color.parseColor(if (glass) "#E8F4F7FB" else "#F8FAFC"),
                cardSurface = Color.parseColor(if (glass) "#CCFFFFFF" else "#FFFFFF"),
                cardBorder = Color.parseColor(if (glass) "#CCFFFFFF" else "#E2E8F0"),
                tileSurface = Color.parseColor(if (glass) "#D6FFFFFF" else "#FFFFFF"),
                tileBorder = Color.parseColor(if (glass) "#D9FFFFFF" else "#E2E8F0"),
                sheetSurface = Color.parseColor(if (glass) "#DEFFFFFF" else "#FFFFFF"),
                chipSurface = Color.parseColor(if (glass) "#8CE2E8F0" else "#F1F5F9"),
                chipBorder = Color.parseColor(if (glass) "#B8FFFFFF" else "#E2E8F0"),
                textPrimary = Color.parseColor("#0F172A"),
                textSecondary = Color.parseColor("#64748B"),
                iconTint = Color.parseColor("#1E293B"),
                divider = Color.parseColor(if (glass) "#260F172A" else "#E2E8F0"),
                handleColor = Color.parseColor(if (glass) "#550F172A" else "#CBD5E1"),
                accent = Color.parseColor("#2563EB"),
                accentSoft = Color.parseColor("#1A2563EB"),
                teal = Color.parseColor("#0F766E"),
                green = Color.parseColor("#16A34A"),
                amber = Color.parseColor("#F59E0B"),
                red = Color.parseColor("#EF4444"),
                rippleColor = Color.parseColor("#242563EB"),
                navBannerTop = Color.parseColor(if (glass) "#D90F172A" else "#F20F172A"),
                navBannerBottom = Color.parseColor(if (glass) "#BD1E293B" else "#EE0F172A"),
                shadowSpot = Color.parseColor(if (glass) "#300F172A" else "#240F172A")
            )
        }
    }

    private fun radiiArray(radiusPx: Float, topOnly: Boolean): FloatArray {
        return if (topOnly) {
            floatArrayOf(radiusPx, radiusPx, radiusPx, radiusPx, 0f, 0f, 0f, 0f)
        } else {
            FloatArray(8) { radiusPx }
        }
    }

    fun buildSurfaceDrawable(
        ctx: Context,
        palette: ThemePalette,
        radiusDp: Float,
        topOnly: Boolean = false,
        fillOverride: Int? = null,
        strokeOverride: Int? = null,
        strokeDp: Float = 1.1f
    ): Drawable {
        val d = ctx.resources.displayMetrics.density
        val rPx = radiusDp * d
        val strokePx = if (strokeDp > 0f) (strokeDp * d).coerceAtLeast(1f) else 0f

        if (palette.isGlass) {
            val baseTop: Int
            val baseBottom: Int
            val sheenTop: Int
            val rimStart: Int
            val rimMid: Int
            val rimEnd: Int
            if (fillOverride != null) {
                val rgb = fillOverride and 0x00FFFFFF
                baseTop = (0xE6 shl 24) or rgb
                baseBottom = (0xCC shl 24) or rgb
                sheenTop = Color.parseColor("#4AFFFFFF")
                rimStart = Color.parseColor("#A8FFFFFF")
                rimMid = Color.parseColor("#44FFFFFF")
                rimEnd = Color.parseColor("#22FFFFFF")
            } else if (palette.isDark) {
                baseTop = Color.parseColor("#D11E293B")
                baseBottom = Color.parseColor("#B50F172A")
                sheenTop = Color.parseColor("#2EFFFFFF")
                rimStart = strokeOverride ?: Color.parseColor("#73FFFFFF")
                rimMid = Color.parseColor("#2EFFFFFF")
                rimEnd = Color.parseColor("#1AFFFFFF")
            } else {
                baseTop = Color.parseColor("#DEFFFFFF")
                baseBottom = Color.parseColor("#C2EFF4FA")
                sheenTop = Color.parseColor("#75FFFFFF")
                rimStart = strokeOverride ?: Color.parseColor("#E6FFFFFF")
                rimMid = Color.parseColor("#66FFFFFF")
                rimEnd = Color.parseColor("#3894A3B8")
            }
            return LiquidGlassDrawable(
                cornerRadiiPx = radiiArray(rPx, topOnly),
                baseTopColor = baseTop,
                baseBottomColor = baseBottom,
                sheenTopColor = sheenTop,
                rimStartColor = rimStart,
                rimMidColor = rimMid,
                rimEndColor = rimEnd,
                strokeWidthPx = strokePx
            )
        }

        return GradientDrawable().apply {
            setColor(fillOverride ?: palette.tileSurface)
            if (topOnly) {
                cornerRadii = radiiArray(rPx, true)
            } else {
                cornerRadius = rPx
            }
            val sc = strokeOverride ?: palette.tileBorder
            if (strokePx > 0f && sc != Color.TRANSPARENT) {
                setStroke(strokePx.toInt().coerceAtLeast(1), sc)
            }
        }
    }

    fun applyFloatingTile(
        ctx: Context,
        view: View,
        palette: ThemePalette,
        radiusDp: Float,
        elevationDp: Float = 6f,
        activeFillColor: Int? = null,
        strokeOverride: Int? = null
    ) {
        val d = ctx.resources.displayMetrics.density
        val content = buildSurfaceDrawable(
            ctx = ctx,
            palette = palette,
            radiusDp = radiusDp,
            topOnly = false,
            fillOverride = activeFillColor,
            strokeOverride = if (activeFillColor != null && !palette.isGlass) Color.TRANSPARENT else strokeOverride
        )
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = radiusDp * d
        }
        val ripple = if (activeFillColor != null) Color.parseColor("#44FFFFFF") else palette.rippleColor
        view.background = RippleDrawable(ColorStateList.valueOf(ripple), content, mask)
        view.elevation = (if (palette.isGlass) elevationDp + 2f else elevationDp) * d
        applySoftShadow(view, palette)
        UiAnimations.attachSpringTouch(view)
    }

    fun applyCardSurface(
        ctx: Context,
        view: View,
        palette: ThemePalette,
        radiusDp: Float = 18f,
        elevationDp: Float = 2f,
        topOnly: Boolean = false,
        fillOverride: Int? = null,
        strokeOverride: Int? = null
    ) {
        val d = ctx.resources.displayMetrics.density
        val resolvedFill = fillOverride ?: if (palette.isGlass) null else if (topOnly) palette.sheetSurface else palette.cardSurface
        val resolvedStroke = strokeOverride ?: if (palette.isGlass) null else palette.cardBorder
        view.background = buildSurfaceDrawable(
            ctx = ctx,
            palette = palette,
            radiusDp = radiusDp,
            topOnly = topOnly,
            fillOverride = resolvedFill,
            strokeOverride = resolvedStroke,
            strokeDp = if (palette.isGlass) 1.15f else 1f
        )
        view.elevation = (if (palette.isGlass) elevationDp + 2f else elevationDp) * d
        view.clipToOutline = true
        applySoftShadow(view, palette)
    }

    fun applyNavBannerSurface(ctx: Context, view: View, palette: ThemePalette) {
        val d = ctx.resources.displayMetrics.density
        val rPx = 20f * d
        view.background = LiquidGlassDrawable(
            cornerRadiiPx = radiiArray(rPx, false),
            baseTopColor = palette.navBannerTop,
            baseBottomColor = palette.navBannerBottom,
            sheenTopColor = Color.parseColor(if (palette.isGlass) "#38FFFFFF" else "#16FFFFFF"),
            rimStartColor = Color.parseColor(if (palette.isGlass) "#70FFFFFF" else "#33FFFFFF"),
            rimMidColor = Color.parseColor("#24FFFFFF"),
            rimEndColor = Color.parseColor("#14FFFFFF"),
            strokeWidthPx = 1.1f * d
        )
        view.elevation = 10f * d
        view.clipToOutline = true
        applySoftShadow(view, palette)
    }

    fun applySoftShadow(view: View, palette: ThemePalette) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.outlineSpotShadowColor = palette.shadowSpot
            view.outlineAmbientShadowColor = palette.shadowSpot
        }
    }

    fun createSectionHeader(ctx: Context, title: String, palette: ThemePalette): TextView {
        val d = ctx.resources.displayMetrics.density
        return TextView(ctx).apply {
            text = title.uppercase()
            textSize = 11.5f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(palette.textSecondary)
            setPadding((4 * d).toInt(), (14 * d).toInt(), (4 * d).toInt(), (6 * d).toInt())
        }
    }

    fun createGroupedCard(
        ctx: Context,
        palette: ThemePalette,
        radiusDp: Float = 18f,
        paddingHDp: Int = 14,
        paddingVDp: Int = 12,
        bottomMarginDp: Int = 10
    ): LinearLayout {
        val d = ctx.resources.displayMetrics.density
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((paddingHDp * d).toInt(), (paddingVDp * d).toInt(), (paddingHDp * d).toInt(), (paddingVDp * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (bottomMarginDp * d).toInt()
            }
            applyCardSurface(ctx, this, palette, radiusDp = radiusDp, elevationDp = if (palette.isGlass) 4f else 1.5f)
        }
    }

    fun styleSegmentedButton(
        btn: MaterialButton,
        active: Boolean,
        palette: ThemePalette,
        activeBgColor: Int = palette.accent
    ) {
        btn.setTextColor(if (active) Color.WHITE else palette.textPrimary)
        btn.setBackgroundColor(if (active) activeBgColor else palette.chipSurface)
        btn.strokeWidth = if (!active && palette.isGlass) (1f * btn.resources.displayMetrics.density).toInt().coerceAtLeast(1) else 0
        btn.strokeColor = ColorStateList.valueOf(palette.chipBorder)
    }
}
