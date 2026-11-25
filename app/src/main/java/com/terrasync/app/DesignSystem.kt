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
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * TerraSync Warm Minimalist Visual Design System
 *
 * Light Mode: Warm off-white canvas (#FAF9F6), crisp opaque white floating map controls (#FFFFFF),
 * flat warm-stone tonal cards/buttons (#F0EFEA), emerald primary brand accent (#18794E),
 * warm obsidian primary CTAs (#262422), warm terracotta highlight badges (#EE6A26 / #FCE8DC),
 * and subtle 1dp hairline dividers (#E8E7E2).
 *
 * Dark Mode: Harmonized warm-charcoal companion (#161715 / #20221F / #2B2D29 / #238E5E / #F4F3EE).
 * Preserves all existing pill radii and Done/Issue/Pending status color semantics.
 */
data class ThemePalette(
    val isDark: Boolean,
    val surfaceCanvas: Int,
    val surfaceFloating: Int,
    val surfaceTonal: Int,
    val accentPrimary: Int,
    val accentPrimarySoft: Int,
    val actionDark: Int,
    val onActionDark: Int,
    val accentWarm: Int,
    val accentWarmContainer: Int,
    val dismissSurface: Int,
    val onDismissSurface: Int,
    val borderSubtle: Int,
    val textPrimary: Int,
    val textBody: Int,
    val textMuted: Int,
    val onDarkSurface: Int,
    val inactiveNavIcon: Int,
    // Semantic aliases mapped to the unified tokens
    val pageBackdrop: Int,
    val cardSurface: Int,
    val cardBorder: Int,
    val floatingSurface: Int,
    val sheetSurface: Int,
    val chipSurface: Int,
    val textSecondary: Int,
    val iconTint: Int,
    val iconMuted: Int,
    val divider: Int,
    val handleColor: Int,
    val accent: Int,
    val accentSoft: Int,
    // Preserved Done / Pending / Issue status colors
    val green: Int,
    val greenSoft: Int,
    val amber: Int,
    val amberSoft: Int,
    val red: Int,
    val redSoft: Int,
    val rippleColor: Int,
    val navBannerBg: Int,
    val navBannerSub: Int,
    val shadowSpot: Int,
    val shadowAmbient: Int,
    val floatingBorder: Int
)

object DesignSystem {

    fun palette(isDark: Boolean): ThemePalette {
        return if (isDark) {
            val canvas = Color.parseColor("#161715")
            val floating = Color.parseColor("#20221F")
            val tonal = Color.parseColor("#2B2D29")
            val primary = Color.parseColor("#238E5E")
            val primarySoft = Color.parseColor("#26238E5E")
            val border = Color.parseColor("#30332E")
            val textPri = Color.parseColor("#F4F4F0")
            val textBdy = Color.parseColor("#C4C6C0")
            val textMut = Color.parseColor("#8F928C")
            ThemePalette(
                isDark = true,
                surfaceCanvas = canvas,
                surfaceFloating = floating,
                surfaceTonal = tonal,
                accentPrimary = primary,
                accentPrimarySoft = primarySoft,
                actionDark = Color.parseColor("#F4F3EE"),
                onActionDark = Color.parseColor("#181A17"),
                accentWarm = Color.parseColor("#F27B3F"),
                accentWarmContainer = Color.parseColor("#38251C"),
                dismissSurface = Color.parseColor("#3F423C"),
                onDismissSurface = Color.parseColor("#F4F4F0"),
                borderSubtle = border,
                textPrimary = textPri,
                textBody = textBdy,
                textMuted = textMut,
                onDarkSurface = Color.WHITE,
                inactiveNavIcon = Color.parseColor("#C4C6C0"),
                pageBackdrop = canvas,
                cardSurface = tonal,
                cardBorder = border,
                floatingSurface = floating,
                sheetSurface = canvas,
                chipSurface = tonal,
                textSecondary = textMut,
                iconTint = Color.parseColor("#E6E6E1"),
                iconMuted = textMut,
                divider = border,
                handleColor = Color.parseColor("#474A44"),
                accent = primary,
                accentSoft = primarySoft,
                green = Color.parseColor("#34C759"),
                greenSoft = Color.parseColor("#2434C759"),
                amber = Color.parseColor("#F5A623"),
                amberSoft = Color.parseColor("#26F5A623"),
                red = Color.parseColor("#FF5C52"),
                redSoft = Color.parseColor("#26FF5C52"),
                rippleColor = Color.parseColor("#26FFFFFF"),
                navBannerBg = Color.parseColor("#165E3D"),
                navBannerSub = Color.parseColor("#CBEAD9"),
                shadowSpot = Color.parseColor("#E6000000"),
                shadowAmbient = Color.parseColor("#80000000"),
                floatingBorder = Color.parseColor("#353832")
            )
        } else {
            val canvas = Color.parseColor("#FAF9F6")
            val floating = Color.parseColor("#FFFFFF")
            val tonal = Color.parseColor("#F0EFEA")
            val primary = Color.parseColor("#18794E")
            val primarySoft = Color.parseColor("#1A18794E")
            val border = Color.parseColor("#E8E7E2")
            val textPri = Color.parseColor("#1C1E1B")
            val textBdy = Color.parseColor("#4A4D48")
            val textMut = Color.parseColor("#6E716B")
            ThemePalette(
                isDark = false,
                surfaceCanvas = canvas,
                surfaceFloating = floating,
                surfaceTonal = tonal,
                accentPrimary = primary,
                accentPrimarySoft = primarySoft,
                actionDark = Color.parseColor("#262422"),
                onActionDark = Color.WHITE,
                accentWarm = Color.parseColor("#EE6A26"),
                accentWarmContainer = Color.parseColor("#FCE8DC"),
                dismissSurface = Color.parseColor("#B8BBB6"),
                onDismissSurface = Color.WHITE,
                borderSubtle = border,
                textPrimary = textPri,
                textBody = textBdy,
                textMuted = textMut,
                onDarkSurface = Color.WHITE,
                inactiveNavIcon = Color.parseColor("#2C2E2B"),
                pageBackdrop = canvas,
                cardSurface = tonal,
                cardBorder = border,
                floatingSurface = floating,
                sheetSurface = canvas,
                chipSurface = tonal,
                textSecondary = textMut,
                iconTint = Color.parseColor("#2C2E2B"),
                iconMuted = textMut,
                divider = border,
                handleColor = Color.parseColor("#D4D3CE"),
                accent = primary,
                accentSoft = primarySoft,
                green = Color.parseColor("#2E8B47"),
                greenSoft = Color.parseColor("#1A2E8B47"),
                amber = Color.parseColor("#E69500"),
                amberSoft = Color.parseColor("#1FE69500"),
                red = Color.parseColor("#D93025"),
                redSoft = Color.parseColor("#1AD93025"),
                rippleColor = Color.parseColor("#181C1E1B"),
                navBannerBg = Color.parseColor("#18794E"),
                navBannerSub = Color.parseColor("#D4F1E2"),
                shadowSpot = Color.parseColor("#8A000000"),
                shadowAmbient = Color.parseColor("#38000000"),
                floatingBorder = Color.parseColor("#DFDED8")
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

    /** Opaque floating map control (#FFFFFF / #20221F) + subtle 1dp hairline outline + soft diffused shadow. */
    fun applyFloatingControl(
        ctx: Context,
        view: View,
        palette: ThemePalette,
        radiusDp: Float,
        elevationDp: Float = 5f,
        activeFillColor: Int? = null
    ) {
        val d = ctx.resources.displayMetrics.density
        val fill = activeFillColor ?: palette.surfaceFloating
        val stroke = if (activeFillColor == null) palette.floatingBorder else Color.TRANSPARENT
        val content = buildOpaqueDrawable(
            ctx = ctx,
            fillColor = fill,
            radiusDp = radiusDp,
            topOnly = false,
            strokeColor = stroke,
            strokeDp = if (stroke != Color.TRANSPARENT) 1f else 0f
        )
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = radiusDp * d
        }
        val ripple = if (activeFillColor != null) Color.parseColor("#30FFFFFF") else palette.rippleColor
        view.background = RippleDrawable(ColorStateList.valueOf(ripple), content, mask)
        view.elevation = elevationDp * d
        applySoftShadow(view, palette)
        UiAnimations.attachSnappyPress(view)
    }

    /** Opaque floating overlay surface (Bottom Sheet 28dp top corners, Ruler/Lasso/Speed cards, Nav Dock capsule). */
    fun applyFloatingSurface(
        ctx: Context,
        view: View,
        palette: ThemePalette,
        radiusDp: Float = 18f,
        elevationDp: Float = 7f,
        topOnly: Boolean = false,
        fillOverride: Int? = null,
        strokeOverride: Int? = null
    ) {
        val d = ctx.resources.displayMetrics.density
        val fill = fillOverride ?: if (topOnly) palette.surfaceCanvas else palette.surfaceFloating
        val stroke = strokeOverride ?: if (topOnly) palette.borderSubtle else palette.floatingBorder
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

    /** Flat tonal grouped container (#F0EFEA light / #2B2D29 dark) with zero inner drop shadow. */
    fun applyGroupedCardSurface(
        ctx: Context,
        view: View,
        palette: ThemePalette,
        radiusDp: Float = 16f,
        fillOverride: Int? = null
    ) {
        view.background = buildOpaqueDrawable(
            ctx = ctx,
            fillColor = fillOverride ?: palette.surfaceTonal,
            radiusDp = radiusDp,
            topOnly = false,
            strokeColor = if (palette.isDark) palette.borderSubtle else Color.TRANSPARENT,
            strokeDp = if (palette.isDark) 0.75f else 0f
        )
        view.elevation = 0f
        view.clipToOutline = true
    }

    /** High-contrast emerald turn-by-turn navigation banner. */
    fun applyNavBannerSurface(ctx: Context, view: View, palette: ThemePalette) {
        val d = ctx.resources.displayMetrics.density
        view.background = buildOpaqueDrawable(
            ctx = ctx,
            fillColor = palette.navBannerBg,
            radiusDp = 20f,
            topOnly = false
        )
        view.elevation = 8f * d
        view.clipToOutline = true
        applySoftShadow(view, palette)
    }

    /** Primary dark obsidian CTA button (#262422 light / #F4F3EE dark) matching the design reference. */
    fun applyPrimaryCtaButton(btn: MaterialButton, palette: ThemePalette) {
        btn.setTextColor(palette.onActionDark)
        btn.setBackgroundColor(palette.actionDark)
        btn.iconTint = ColorStateList.valueOf(palette.onActionDark)
        btn.strokeWidth = 0
        btn.elevation = 0f
        btn.stateListAnimator = null
    }

    /** Secondary flat tonal action button (#F0EFEA light / #2B2D29 dark) matching the design reference. */
    fun applySecondaryTonalButton(btn: MaterialButton, palette: ThemePalette) {
        btn.setTextColor(palette.textPrimary)
        btn.setBackgroundColor(palette.surfaceTonal)
        btn.iconTint = ColorStateList.valueOf(palette.textPrimary)
        btn.strokeWidth = 0
        btn.elevation = 0f
        btn.stateListAnimator = null
    }

    /** Soft circular tinted header glyph badge (#FCE8DC / #38251C with #EE6A26 / #F27B3F glyph). */
    fun createHeaderBadge(
        ctx: Context,
        iconRes: Int,
        palette: ThemePalette,
        bgColor: Int = palette.accentWarmContainer,
        glyphColor: Int = palette.accentWarm,
        sizeDp: Int = 44
    ): FrameLayout {
        val d = ctx.resources.displayMetrics.density
        val sizePx = (sizeDp * d).toInt()
        val iconPx = ((sizeDp * 0.50f) * d).toInt()
        return FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
            }
            addView(ImageView(ctx).apply {
                setImageResource(iconRes)
                setColorFilter(glyphColor)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER)
            })
        }
    }

    /** Circular warm-gray sheet dismiss `×` button (#B8BBB6 with white ×, 40dp touch target). */
    fun createSheetCloseButton(
        ctx: Context,
        palette: ThemePalette,
        onClick: () -> Unit
    ): FrameLayout {
        val d = ctx.resources.displayMetrics.density
        val hitPx = (40 * d).toInt()
        val discPx = (30 * d).toInt()
        val iconPx = (13 * d).toInt()
        val wrap = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(hitPx, hitPx)
            contentDescription = "Close"
            isClickable = true
            isFocusable = true
            UiAnimations.attachSnappyPress(this, 0.92f)
            setOnClickListener { onClick() }
        }
        val disc = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(discPx, discPx, Gravity.CENTER)
            val content = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(palette.dismissSurface)
            }
            val mask = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), content, mask)
        }
        disc.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(palette.onDismissSurface)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER)
        })
        wrap.addView(disc)
        return wrap
    }

    fun applySoftShadow(view: View, palette: ThemePalette) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.outlineSpotShadowColor = palette.shadowSpot
            view.outlineAmbientShadowColor = palette.shadowAmbient
        }
    }

    /** Apply tabular numerals (`tnum`) so live speed, coordinates, ruler, distances, and counts never jitter. */
    fun applyTabularNumerals(tv: TextView) {
        tv.fontFeatureSettings = "tnum"
    }

    /** Clean bold 15.5sp sentence-case section header matching the design reference. */
    fun createSectionHeader(ctx: Context, title: String, palette: ThemePalette): TextView {
        val d = ctx.resources.displayMetrics.density
        return TextView(ctx).apply {
            text = title
            textSize = 15.5f
            letterSpacing = -0.01f
            setTypeface(null, Typeface.BOLD)
            setTextColor(palette.textPrimary)
            setPadding((4 * d).toInt(), (20 * d).toInt(), (4 * d).toInt(), (8 * d).toInt())
        }
    }

    fun createGroupedCard(
        ctx: Context,
        palette: ThemePalette,
        radiusDp: Float = 16f,
        paddingHDp: Int = 14,
        paddingVDp: Int = 6,
        bottomMarginDp: Int = 12
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
            setBackgroundColor(palette.borderSubtle)
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
            intArrayOf(
                palette.accentPrimary,
                if (palette.isDark) Color.parseColor("#3E413B") else Color.parseColor("#D6D5CE")
            )
        )
        sw.thumbTintList = ColorStateList(
            states,
            intArrayOf(Color.WHITE, if (palette.isDark) Color.parseColor("#D4D6D0") else Color.WHITE)
        )
        sw.trackDecorationTintList = ColorStateList.valueOf(Color.TRANSPARENT)
    }

    fun styleSegmentedButton(
        btn: MaterialButton,
        active: Boolean,
        palette: ThemePalette,
        activeBgColor: Int = palette.accentPrimary,
        onTonalCard: Boolean = false
    ) {
        val activeText = if (activeBgColor == palette.actionDark) palette.onActionDark else Color.WHITE
        val d = btn.resources.displayMetrics.density
        btn.setTextColor(if (active) activeText else palette.textPrimary)
        if (active) {
            btn.setBackgroundColor(activeBgColor)
            btn.strokeWidth = 0
        } else if (onTonalCard) {
            // Distinct crisp surface on top of #F0EFEA / #2B2D29 grouped cards
            btn.setBackgroundColor(palette.surfaceFloating)
            btn.strokeColor = ColorStateList.valueOf(palette.borderSubtle)
            btn.strokeWidth = (1f * d).toInt().coerceAtLeast(1)
        } else {
            btn.setBackgroundColor(palette.surfaceTonal)
            btn.strokeWidth = 0
        }
        btn.elevation = 0f
        btn.stateListAnimator = null
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
            minimumHeight = (54 * d).toInt()
            setPadding((2 * d).toInt(), (8 * d).toInt(), (2 * d).toInt(), (8 * d).toInt())
            UiAnimations.applyRowRipple(ctx, this, palette.isDark)
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
                textSize = 12.5f
                setTextColor(palette.textMuted)
                setPadding(0, (2 * d).toInt(), 0, 0)
            })
        }
        val sw = MaterialSwitch(ctx).apply {
            isChecked = checked
            styleMaterialSwitch(this, palette)
            setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        }
        row.setOnClickListener { sw.toggle() }
        row.addView(textCol)
        row.addView(sw)
        return row
    }

    /** Creates a clean, rounded, dark/light-aware EditText for dialogs and forms. */
    fun createThemedEditText(
        ctx: Context,
        palette: ThemePalette,
        hintText: String,
        initialText: String = "",
        singleLine: Boolean = true,
        minLines: Int = 1,
        onTonalCard: Boolean = false
    ): EditText {
        val d = ctx.resources.displayMetrics.density
        return EditText(ctx).apply {
            hint = hintText
            if (initialText.isNotEmpty()) {
                setText(initialText)
                setSelection(initialText.length)
            }
            textSize = 14.5f
            setTextColor(palette.textPrimary)
            setHintTextColor(palette.textMuted)
            if (singleLine) {
                setSingleLine(true)
            } else {
                setSingleLine(false)
                this.minLines = minLines
                gravity = Gravity.TOP or Gravity.START
            }
            setPadding((14 * d).toInt(), (11 * d).toInt(), (14 * d).toInt(), (11 * d).toInt())
            background = buildOpaqueDrawable(
                ctx = ctx,
                fillColor = if (onTonalCard) palette.surfaceFloating else palette.surfaceTonal,
                radiusDp = 12f,
                strokeColor = palette.borderSubtle,
                strokeDp = 1f
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    /** Creates a themed header view for AlertDialogs (19.5sp Bold, tight tracking). */
    fun createThemedDialogTitle(ctx: Context, title: String, palette: ThemePalette): TextView {
        val d = ctx.resources.displayMetrics.density
        return TextView(ctx).apply {
            text = title
            textSize = 19.5f
            letterSpacing = -0.01f
            setTypeface(null, Typeface.BOLD)
            setTextColor(palette.textPrimary)
            setPadding((22 * d).toInt(), (20 * d).toInt(), (22 * d).toInt(), (6 * d).toInt())
        }
    }

    /** Creates a themed list adapter for AlertDialog item pickers so items stay crisp in Dark mode. */
    fun createThemedDialogListAdapter(ctx: Context, items: Array<String>, palette: ThemePalette): ArrayAdapter<String> {
        val d = ctx.resources.displayMetrics.density
        return object : ArrayAdapter<String>(ctx, android.R.layout.simple_list_item_1, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = (convertView as? TextView) ?: TextView(ctx).apply {
                    textSize = 14.5f
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = (50 * d).toInt()
                    setPadding((22 * d).toInt(), (12 * d).toInt(), (22 * d).toInt(), (12 * d).toInt())
                    UiAnimations.applyRowRipple(ctx, this, palette.isDark)
                }
                tv.text = getItem(position)
                tv.setTextColor(palette.textPrimary)
                return tv
            }
        }
    }

    /** Applies 24dp dialog corners, warm canvas surface, contrast typography, and crisp entrance. */
    fun styleAlertDialog(
        ctx: Context,
        dialog: AlertDialog,
        palette: ThemePalette,
        destructivePositive: Boolean = false
    ) {
        dialog.window?.setBackgroundDrawable(
            buildOpaqueDrawable(
                ctx = ctx,
                fillColor = palette.surfaceCanvas,
                radiusDp = 24f,
                strokeColor = palette.borderSubtle,
                strokeDp = 1f
            )
        )
        dialog.findViewById<TextView>(android.R.id.message)?.apply {
            setTextColor(palette.textBody)
            textSize = 14f
            setLineSpacing(2f * ctx.resources.displayMetrics.density, 1.08f)
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(if (destructivePositive) palette.red else palette.accentPrimary)
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(palette.textMuted)
            isAllCaps = false
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
            setTextColor(palette.textPrimary)
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
        }
        dialog.listView?.let { lv ->
            lv.divider = GradientDrawable().apply {
                setColor(palette.borderSubtle)
                setSize(-1, (1f * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(1))
            }
            lv.dividerHeight = (1f * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        }
        UiAnimations.animateDialogEnter(dialog.window?.decorView)
    }
}
