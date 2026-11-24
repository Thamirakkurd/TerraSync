package com.terrasync.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Organic Maps Minimalist Design System
 * Clean opaque high-contrast surfaces, pure white (#FFFFFF) pages with soft
 * light-gray (#F2F2F5) grouped rounded cards, thin dividers, crisp opaque white
 * floating map controls with soft low-elevation shadows, restrained functional
 * blue/green accents, and matching clean Dark Mode.
 */
data class ThemePalette(
    val isDark: Boolean,
    val pageBackdrop: Int,
    val cardSurface: Int,
    val cardBorder: Int,
    val floatingSurface: Int,
    val sheetSurface: Int,
    val chipSurface: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val iconTint: Int,
    val iconMuted: Int,
    val divider: Int,
    val handleColor: Int,
    val accent: Int,
    val accentSoft: Int,
    val green: Int,
    val greenSoft: Int,
    val amber: Int,
    val amberSoft: Int,
    val red: Int,
    val redSoft: Int,
    val rippleColor: Int,
    val navBannerBg: Int,
    val navBannerSub: Int,
    val shadowSpot: Int
)

object DesignSystem {

    fun palette(isDark: Boolean): ThemePalette {
        return if (isDark) {
            ThemePalette(
                isDark = true,
                pageBackdrop = Color.parseColor("#111317"),
                cardSurface = Color.parseColor("#1C1F26"),
                cardBorder = Color.parseColor("#282C35"),
                floatingSurface = Color.parseColor("#1E222A"),
                sheetSurface = Color.parseColor("#181B21"),
                chipSurface = Color.parseColor("#2A2F3A"),
                textPrimary = Color.parseColor("#F2F3F5"),
                textSecondary = Color.parseColor("#9A9FA8"),
                iconTint = Color.parseColor("#E6E8EC"),
                iconMuted = Color.parseColor("#6E7480"),
                divider = Color.parseColor("#2A2E38"),
                handleColor = Color.parseColor("#474D59"),
                accent = Color.parseColor("#3B9BFF"),
                accentSoft = Color.parseColor("#1F3B9BFF"),
                green = Color.parseColor("#34C759"),
                greenSoft = Color.parseColor("#1F34C759"),
                amber = Color.parseColor("#F5A623"),
                amberSoft = Color.parseColor("#24F5A623"),
                red = Color.parseColor("#FF5C52"),
                redSoft = Color.parseColor("#24FF5C52"),
                rippleColor = Color.parseColor("#26FFFFFF"),
                navBannerBg = Color.parseColor("#194D2B"),
                navBannerSub = Color.parseColor("#B9E4C9"),
                shadowSpot = Color.parseColor("#66000000")
            )
        } else {
            ThemePalette(
                isDark = false,
                pageBackdrop = Color.parseColor("#FFFFFF"),
                cardSurface = Color.parseColor("#F2F2F5"),
                cardBorder = Color.TRANSPARENT,
                floatingSurface = Color.parseColor("#FFFFFF"),
                sheetSurface = Color.parseColor("#FFFFFF"),
                chipSurface = Color.parseColor("#E8E8EE"),
                textPrimary = Color.parseColor("#1C1C1E"),
                textSecondary = Color.parseColor("#6E6E73"),
                iconTint = Color.parseColor("#3A3A3C"),
                iconMuted = Color.parseColor("#8E8E93"),
                divider = Color.parseColor("#E3E3E8"),
                handleColor = Color.parseColor("#CFCFD6"),
                accent = Color.parseColor("#1E88E5"),
                accentSoft = Color.parseColor("#181E88E5"),
                green = Color.parseColor("#2E8B47"),
                greenSoft = Color.parseColor("#1A2E8B47"),
                amber = Color.parseColor("#E69500"),
                amberSoft = Color.parseColor("#1FE69500"),
                red = Color.parseColor("#D93025"),
                redSoft = Color.parseColor("#1AD93025"),
                rippleColor = Color.parseColor("#1A1C1C1E"),
                navBannerBg = Color.parseColor("#226B3A"),
                navBannerSub = Color.parseColor("#D4F0DD"),
                shadowSpot = Color.parseColor("#24000000")
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

    fun buildOpaqueDrawable(
        ctx: Context,
        fillColor: Int,
        radiusDp: Float,
        topOnly: Boolean = false,
        strokeColor: Int = Color.TRANSPARENT,
        strokeDp: Float = 0f
    ): Drawable {
        val d = ctx.resources.displayMetrics.density
        val rPx = radiusDp * d
        return GradientDrawable().apply {
            setColor(fillColor)
            if (topOnly) {
                cornerRadii = radiiArray(rPx, true)
            } else {
                cornerRadius = rPx
            }
            if (strokeDp > 0f && strokeColor != Color.TRANSPARENT) {
                setStroke((strokeDp * d).toInt().coerceAtLeast(1), strokeColor)
            }
        }
    }

    /** Crisp opaque floating map control with soft low-elevation shadow (no harsh border). */
    fun applyFloatingControl(
        ctx: Context,
        view: View,
        palette: ThemePalette,
        radiusDp: Float,
        elevationDp: Float = 4f,
        activeFillColor: Int? = null
    ) {
        val d = ctx.resources.displayMetrics.density
        val fill = activeFillColor ?: palette.floatingSurface
        val stroke = if (palette.isDark && activeFillColor == null) palette.cardBorder else Color.TRANSPARENT
        val content = buildOpaqueDrawable(
            ctx = ctx,
            fillColor = fill,
            radiusDp = radiusDp,
            topOnly = false,
            strokeColor = stroke,
            strokeDp = if (stroke != Color.TRANSPARENT) 0.75f else 0f
        )
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = radiusDp * d
        }
        val ripple = if (activeFillColor != null) Color.parseColor("#38FFFFFF") else palette.rippleColor
        view.background = RippleDrawable(ColorStateList.valueOf(ripple), content, mask)
        view.elevation = elevationDp * d
        applySoftShadow(view, palette)
        UiAnimations.attachSnappyPress(view)
    }

    /** Crisp opaque floating overlay card (Ruler, Lasso, Speed HUD, Route-From pill, Bottom Sheet). */
    fun applyFloatingSurface(
        ctx: Context,
        view: View,
        palette: ThemePalette,
        radiusDp: Float = 18f,
        elevationDp: Float = 6f,
        topOnly: Boolean = false,
        fillOverride: Int? = null,
        strokeOverride: Int? = null
    ) {
        val d = ctx.resources.displayMetrics.density
        val fill = fillOverride ?: if (topOnly) palette.sheetSurface else palette.floatingSurface
        val stroke = strokeOverride ?: if (palette.isDark) palette.cardBorder else Color.TRANSPARENT
        view.background = buildOpaqueDrawable(
            ctx = ctx,
            fillColor = fill,
            radiusDp = radiusDp,
            topOnly = topOnly,
            strokeColor = stroke,
            strokeDp = if (stroke != Color.TRANSPARENT) 1f else 0f
        )
        view.elevation = elevationDp * d
        view.clipToOutline = true
        applySoftShadow(view, palette)
    }

    /** Grouped rounded-card container (#F2F2F5 light / #1C1F26 dark) for Settings, Bookmarks, Search, About. */
    fun applyGroupedCardSurface(
        ctx: Context,
        view: View,
        palette: ThemePalette,
        radiusDp: Float = 16f,
        fillOverride: Int? = null
    ) {
        view.background = buildOpaqueDrawable(
            ctx = ctx,
            fillColor = fillOverride ?: palette.cardSurface,
            radiusDp = radiusDp,
            topOnly = false,
            strokeColor = if (palette.isDark) palette.cardBorder else Color.TRANSPARENT,
            strokeDp = if (palette.isDark) 0.75f else 0f
        )
        view.elevation = 0f
        view.clipToOutline = true
    }

    /** High-contrast Organic Maps green turn-by-turn navigation banner. */
    fun applyNavBannerSurface(ctx: Context, view: View, palette: ThemePalette) {
        val d = ctx.resources.displayMetrics.density
        view.background = buildOpaqueDrawable(
            ctx = ctx,
            fillColor = palette.navBannerBg,
            radiusDp = 18f,
            topOnly = false
        )
        view.elevation = 8f * d
        view.clipToOutline = true
        applySoftShadow(view, palette)
    }

    fun applySoftShadow(view: View, palette: ThemePalette) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.outlineSpotShadowColor = palette.shadowSpot
            view.outlineAmbientShadowColor = palette.shadowSpot
        }
    }

    /** Apply tabular numerals (`tnum`) so live speed, coordinates, ruler, and scale digits never jitter. */
    fun applyTabularNumerals(tv: TextView) {
        tv.fontFeatureSettings = "tnum"
    }

    fun createSectionHeader(ctx: Context, title: String, palette: ThemePalette): TextView {
        val d = ctx.resources.displayMetrics.density
        return TextView(ctx).apply {
            text = title.uppercase()
            textSize = 12f
            letterSpacing = 0.06f
            setTypeface(null, Typeface.BOLD)
            setTextColor(palette.textSecondary)
            setPadding((6 * d).toInt(), (16 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
        }
    }

    fun createGroupedCard(
        ctx: Context,
        palette: ThemePalette,
        radiusDp: Float = 16f,
        paddingHDp: Int = 14,
        paddingVDp: Int = 6,
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
            applyGroupedCardSurface(ctx, this, palette, radiusDp = radiusDp)
        }
    }

    fun createHairlineDivider(ctx: Context, palette: ThemePalette, insetStartDp: Int = 0, insetEndDp: Int = 0): View {
        val d = ctx.resources.displayMetrics.density
        return View(ctx).apply {
            setBackgroundColor(palette.divider)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (1f * d).toInt().coerceAtLeast(1)
            ).apply {
                marginStart = (insetStartDp * d).toInt()
                marginEnd = (insetEndDp * d).toInt()
            }
        }
    }

    fun styleMaterialSwitch(sw: MaterialSwitch, palette: ThemePalette) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        sw.trackTintList = ColorStateList(
            states,
            intArrayOf(palette.accent, if (palette.isDark) Color.parseColor("#383E4A") else Color.parseColor("#D1D1D6"))
        )
        sw.thumbTintList = ColorStateList(
            states,
            intArrayOf(Color.WHITE, if (palette.isDark) Color.parseColor("#C7CBD4") else Color.WHITE)
        )
        sw.trackDecorationTintList = ColorStateList.valueOf(Color.TRANSPARENT)
    }

    fun styleSegmentedButton(
        btn: MaterialButton,
        active: Boolean,
        palette: ThemePalette,
        activeBgColor: Int = palette.accent
    ) {
        btn.setTextColor(if (active) Color.WHITE else palette.textPrimary)
        btn.setBackgroundColor(if (active) activeBgColor else palette.chipSurface)
        btn.strokeWidth = 0
        btn.elevation = 0f
    }

    fun createSettingsSwitchRow(
        ctx: Context,
        title: String,
        subtitle: String?,
        checked: Boolean,
        palette: ThemePalette,
        onChange: (Boolean) -> Unit
    ): LinearLayout {
        val d = ctx.resources.displayMetrics.density
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = (52 * d).toInt()
            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
        }
        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (12 * d).toInt()
            }
        }
        textCol.addView(TextView(ctx).apply {
            text = title
            textSize = 14.5f
            setTextColor(palette.textPrimary)
        })
        if (!subtitle.isNullOrBlank()) {
            textCol.addView(TextView(ctx).apply {
                text = subtitle
                textSize = 12f
                setTextColor(palette.textSecondary)
                setPadding(0, (2 * d).toInt(), 0, 0)
            })
        }
        val sw = MaterialSwitch(ctx).apply {
            isChecked = checked
            styleMaterialSwitch(this, palette)
            setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        }
        row.addView(textCol)
        row.addView(sw)
        return row
    }
}
