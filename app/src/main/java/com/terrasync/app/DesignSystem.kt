package com.terrasync.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import java.lang.ref.WeakReference

/**
 * TerraSync Two-Material-Worlds Visual Design System
 *
 * World 1 (Floating Over Map):
 * - Softly frosted, semi-transparent warm-white controls (#E8F8F8F5 Light / #E3212320 Dark)
 *   and frosted outer navigation tray (#E2F6F6F2 / #DC1C1E1B) so map terrain gently tints through,
 *   paired with crisp directional downward drop shadows (minimal ambient halo).
 *
 * World 2 (Bottom Sheets, Dialogs & Full Screens):
 * - Calm solid warm alabaster canvas (#F9F9F7 Light / #161715 Dark), 28dp top corners,
 *   centered 36x4dp drag handle (#D6D7D3), zero inner drop shadows, zero divider lines inside sheets,
 *   deep espresso-charcoal primary CTAs (#2E292B), forest emerald utility & active accents (#18794E),
 *   apricot-orange highlight badges (#F0651F on #FCE6D8), and borderless warm-stone secondary pills (#F1F1EE).
 */
data class ThemePalette(
    val isDark: Boolean,
    val surfaceCanvas: Int,
    val surfaceFloating: Int,
    val surfaceFrosted: Int,
    val surfaceFrostedTray: Int,
    val navPillNeutral: Int,
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

data class SheetActionItem(
    val title: String,
    val iconRes: Int,
    val subtitle: String? = null,
    val checked: Boolean = false,
    val destructive: Boolean = false,
    val tintOverride: Int? = null,
    val onClick: () -> Unit
)

object DesignSystem {

    const val RADIUS_CHIP_DP = 12
    const val RADIUS_TILE_DP = 14
    const val RADIUS_CARD_DP = 16
    const val GAP_DP = 8

    fun palette(isDark: Boolean): ThemePalette {
        return if (isDark) {
            val canvas = Color.parseColor("#161715")
            val floating = Color.parseColor("#2A2C28")
            val frosted = Color.parseColor("#222421")
            val frostedTray = Color.parseColor("#222421")
            val navPill = Color.parseColor("#2E302C")
            val tonal = Color.parseColor("#21231F")
            val primary = Color.parseColor("#238E5E")
            val primarySoft = Color.parseColor("#26238E5E")
            val border = Color.parseColor("#3A3D37")
            val textPri = Color.parseColor("#F4F4F0")
            val textBdy = Color.parseColor("#C4C6C0")
            val textMut = Color.parseColor("#8F928C")
            ThemePalette(
                isDark = true,
                surfaceCanvas = canvas,
                surfaceFloating = floating,
                surfaceFrosted = frosted,
                surfaceFrostedTray = frostedTray,
                navPillNeutral = navPill,
                surfaceTonal = tonal,
                accentPrimary = primary,
                accentPrimarySoft = primarySoft,
                actionDark = Color.parseColor("#238E5E"),
                onActionDark = Color.WHITE,
                accentWarm = Color.parseColor("#F27B3F"),
                accentWarmContainer = Color.parseColor("#38251C"),
                dismissSurface = Color.parseColor("#3F423C"),
                onDismissSurface = Color.parseColor("#F4F4F0"),
                borderSubtle = border,
                textPrimary = textPri,
                textBody = textBdy,
                textMuted = textMut,
                onDarkSurface = Color.WHITE,
                inactiveNavIcon = Color.parseColor("#ECECE7"),
                pageBackdrop = canvas,
                cardSurface = tonal,
                cardBorder = border,
                floatingSurface = frosted,
                sheetSurface = canvas,
                chipSurface = tonal,
                textSecondary = textMut,
                iconTint = Color.parseColor("#ECECE7"),
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
                rippleColor = Color.parseColor("#28FFFFFF"),
                navBannerBg = Color.parseColor("#165E3D"),
                navBannerSub = Color.parseColor("#CBEAD9"),
                shadowSpot = Color.parseColor("#E6000000"),
                shadowAmbient = Color.parseColor("#38000000"),
                floatingBorder = Color.parseColor("#363A34")
            )
        } else {
            val canvas = Color.parseColor("#F9F9F7")
            val floating = Color.parseColor("#FFFFFF")
            val frosted = Color.parseColor("#F3F5F1")
            val frostedTray = Color.parseColor("#F3F5F1")
            val navPill = Color.parseColor("#FFFFFF")
            val tonal = Color.parseColor("#F1F1EE")
            val primary = Color.parseColor("#18794E")
            val primarySoft = Color.parseColor("#1A18794E")
            val border = Color.parseColor("#E6E6E1")
            val textPri = Color.parseColor("#1C1E1B")
            val textBdy = Color.parseColor("#4A4D48")
            val textMut = Color.parseColor("#6E716B")
            ThemePalette(
                isDark = false,
                surfaceCanvas = canvas,
                surfaceFloating = floating,
                surfaceFrosted = frosted,
                surfaceFrostedTray = frostedTray,
                navPillNeutral = navPill,
                surfaceTonal = tonal,
                accentPrimary = primary,
                accentPrimarySoft = primarySoft,
                actionDark = Color.parseColor("#2E292B"),
                onActionDark = Color.WHITE,
                accentWarm = Color.parseColor("#F0651F"),
                accentWarmContainer = Color.parseColor("#FCE6D8"),
                dismissSurface = Color.parseColor("#BDBFB9"),
                onDismissSurface = Color.WHITE,
                borderSubtle = border,
                textPrimary = textPri,
                textBody = textBdy,
                textMuted = textMut,
                onDarkSurface = Color.WHITE,
                inactiveNavIcon = Color.parseColor("#262422"),
                pageBackdrop = canvas,
                cardSurface = tonal,
                cardBorder = border,
                floatingSurface = frosted,
                sheetSurface = canvas,
                chipSurface = tonal,
                textSecondary = textMut,
                iconTint = Color.parseColor("#262422"),
                iconMuted = textMut,
                divider = border,
                handleColor = Color.parseColor("#D6D7D3"),
                accent = primary,
                accentSoft = primarySoft,
                green = Color.parseColor("#2E8B47"),
                greenSoft = Color.parseColor("#1A2E8B47"),
                amber = Color.parseColor("#E69500"),
                amberSoft = Color.parseColor("#1FE69500"),
                red = Color.parseColor("#D93025"),
                redSoft = Color.parseColor("#1AD93025"),
                rippleColor = Color.parseColor("#1A1C1E1B"),
                navBannerBg = Color.parseColor("#18794E"),
                navBannerSub = Color.parseColor("#D4F1E2"),
                shadowSpot = Color.parseColor("#88000000"),
                shadowAmbient = Color.parseColor("#1A000000"),
                floatingBorder = Color.parseColor("#E2E6DF")
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

    /**
     * Opaque milky-matte floating map control (#F3F5F1 Light / #222421 Dark)
     * with full hardware outline alpha so crisp directional downward drop shadows render cleanly.
     */
    fun applyFloatingControl(
        ctx: Context,
        view: View,
        palette: ThemePalette,
        radiusDp: Float,
        elevationDp: Float = 6f,
        activeFillColor: Int? = null
    ) {
        val d = ctx.resources.displayMetrics.density
        val fill = activeFillColor ?: palette.surfaceFrosted
        val stroke = if (activeFillColor == null) palette.floatingBorder else Color.TRANSPARENT
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
        val ripple = if (activeFillColor != null) Color.parseColor("#30FFFFFF") else palette.rippleColor
        view.background = RippleDrawable(ColorStateList.valueOf(ripple), content, mask)
        view.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        view.elevation = elevationDp * d
        applyDirectionalShadow(view, palette)
        UiAnimations.attachSnappyPress(view)
    }

    /**
     * Floating overlay surface: solid calm canvas for Bottom Sheets (topOnly=true),
     * opaque milky-matte sage-tinted off-white for floating map trays/cards (topOnly=false)
     * with proper ViewOutlineProvider.BACKGROUND for crisp downward elevation shadows.
     */
    fun applyFloatingSurface(
        ctx: Context,
        view: View,
        palette: ThemePalette,
        radiusDp: Float = 20f,
        elevationDp: Float = 8f,
        topOnly: Boolean = false,
        fillOverride: Int? = null,
        strokeOverride: Int? = null
    ) {
        val d = ctx.resources.displayMetrics.density
        val fill = fillOverride ?: if (topOnly) palette.surfaceCanvas else palette.surfaceFrosted
        val stroke = strokeOverride ?: if (topOnly) Color.TRANSPARENT else palette.floatingBorder
        view.background = buildOpaqueDrawable(
            ctx = ctx,
            fillColor = fill,
            radiusDp = radiusDp,
            topOnly = topOnly,
            strokeColor = stroke,
            strokeDp = if (stroke != Color.TRANSPARENT) 0.75f else 0f
        )
        view.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        view.elevation = elevationDp * d
        view.clipToOutline = true
        applyDirectionalShadow(view, palette)
    }

    /** Flat tonal grouped container (#F1F1EE light / #1F211E dark) with crisp dark border stroke. */
    fun applyGroupedCardSurface(
        ctx: Context,
        view: View,
        palette: ThemePalette,
        radiusDp: Float = 18f,
        fillOverride: Int? = null
    ) {
        view.background = buildOpaqueDrawable(
            ctx = ctx,
            fillColor = fillOverride ?: palette.surfaceTonal,
            radiusDp = radiusDp,
            topOnly = false,
            strokeColor = if (palette.isDark) palette.cardBorder else Color.TRANSPARENT,
            strokeDp = if (palette.isDark) 1f else 0f
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
            radiusDp = 22f,
            topOnly = false
        )
        view.elevation = 8f * d
        view.clipToOutline = true
        applyDirectionalShadow(view, palette)
    }

    /** Primary deep espresso-charcoal CTA button (#2E292B Light / #F4F3EE Dark) matching the concept. */
    fun applyPrimaryCtaButton(btn: MaterialButton, palette: ThemePalette) {
        btn.setTextColor(palette.onActionDark)
        btn.setBackgroundColor(palette.actionDark)
        btn.iconTint = ColorStateList.valueOf(palette.onActionDark)
        btn.strokeWidth = 0
        btn.elevation = 0f
        btn.stateListAnimator = null
    }

    /** Borderless warm-stone secondary action pill (#F1F1EE Light / #2B2D29 Dark) matching the concept. */
    fun applySecondaryTonalButton(btn: MaterialButton, palette: ThemePalette) {
        btn.setTextColor(palette.textPrimary)
        btn.setBackgroundColor(palette.surfaceTonal)
        btn.iconTint = ColorStateList.valueOf(palette.textPrimary)
        btn.strokeWidth = 0
        btn.elevation = 0f
        btn.stateListAnimator = null
    }

    /** Large circular apricot-tinted header badge (default 54dp, #FCE6D8 / #F0651F). */
    fun createHeaderBadge(
        ctx: Context,
        iconRes: Int,
        palette: ThemePalette,
        bgColor: Int = palette.accentWarmContainer,
        glyphColor: Int = palette.accentWarm,
        sizeDp: Int = 54
    ): FrameLayout {
        val d = ctx.resources.displayMetrics.density
        val sizePx = (sizeDp * d).toInt()
        val iconPx = ((sizeDp * 0.48f) * d).toInt()
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

    /** Tucked circular warm-gray sheet dismiss `×` button (28dp disc in 40dp touch target, #BDBFB9). */
    fun createSheetCloseButton(
        ctx: Context,
        palette: ThemePalette,
        onClick: () -> Unit
    ): FrameLayout {
        val d = ctx.resources.displayMetrics.density
        val hitPx = (40 * d).toInt()
        val discPx = (28 * d).toInt()
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

    /** Crisp directional downward shadow (strong spot, minimal ambient to eliminate muddy all-around halos). */
    fun applyDirectionalShadow(view: View, palette: ThemePalette) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.outlineSpotShadowColor = palette.shadowSpot
            view.outlineAmbientShadowColor = palette.shadowAmbient
        }
    }

    fun applySoftShadow(view: View, palette: ThemePalette) = applyDirectionalShadow(view, palette)

    /** Apply tabular numerals (`tnum`) so live speed, coordinates, ruler, distances, and counts never jitter. */
    fun applyTabularNumerals(tv: TextView) {
        tv.fontFeatureSettings = "tnum"
    }

    /** Clean bold 16sp sentence-case section header separated by whitespace (no harsh dividers). */
    fun createSectionHeader(ctx: Context, title: String, palette: ThemePalette): TextView {
        val d = ctx.resources.displayMetrics.density
        return TextView(ctx).apply {
            text = title
            textSize = 16f
            letterSpacing = -0.01f
            setTypeface(null, Typeface.BOLD)
            setTextColor(palette.textPrimary)
            setPadding((2 * d).toInt(), (20 * d).toInt(), (2 * d).toInt(), (8 * d).toInt())
        }
    }

    fun createGroupedCard(
        ctx: Context,
        palette: ThemePalette,
        radiusDp: Float = 18f,
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
                radiusDp = 14f,
                strokeColor = palette.borderSubtle,
                strokeDp = 1f
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    /**
     * Styled rounded action bottom sheet replacing stock Material 3 PopupMenus & picker dialogs.
     * 28dp top corners, warm #F9F9F7 / #161715 solid canvas, centered 36x4dp drag handle,
     * header + circular close button, crisp outlined icons, generous 52dp one-handed tap targets,
     * and distinct red styling for destructive actions.
     */
    @Volatile private var activeActionSheetRef: WeakReference<BottomSheetDialog>? = null
    @Volatile private var lastActionSheetMs: Long = 0L

    fun showActionBottomSheet(
        ctx: Context,
        palette: ThemePalette,
        title: String,
        subtitle: String? = null,
        actions: List<SheetActionItem>,
        dimAmount: Float? = null,
        headerContent: ((LinearLayout, () -> Unit) -> Unit)? = null,
        extraContent: ((LinearLayout) -> Unit)? = null
    ): BottomSheetDialog {
        val now = SystemClock.elapsedRealtime()
        val existing = activeActionSheetRef?.get()
        if (existing != null && existing.isShowing) {
            if (now - lastActionSheetMs < 450L) {
                return existing
            }
            runCatching { existing.dismiss() }
        }
        val d = ctx.resources.displayMetrics.density
        val dialog = BottomSheetDialog(ctx)
        lastActionSheetMs = now
        activeActionSheetRef = WeakReference(dialog)
        dialog.setOnDismissListener {
            if (activeActionSheetRef?.get() === dialog) {
                activeActionSheetRef = null
            }
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = buildOpaqueDrawable(
                ctx = ctx,
                fillColor = palette.surfaceCanvas,
                radiusDp = 28f,
                topOnly = true
            )
            setPadding((16 * d).toInt(), (10 * d).toInt(), (16 * d).toInt(), (24 * d).toInt())
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.setPadding((16 * d).toInt(), (10 * d).toInt(), (16 * d).toInt(), navBottom + (24 * d).toInt())
            insets
        }

        // Centered drag handle (36 x 4dp, #D6D7D3)
        root.addView(View(ctx).apply {
            background = GradientDrawable().apply {
                setColor(palette.handleColor)
                cornerRadius = 4f * d
            }
            layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (4 * d).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (14 * d).toInt()
            }
        })

        // Header row: bold title (+ optional subtitle) + tucked 28dp circular close button
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (14 * d).toInt()
            }
        }
        val titleCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * d).toInt()
            }
        }
        titleCol.addView(TextView(ctx).apply {
            text = title
            textSize = 19.5f
            letterSpacing = -0.01f
            setTypeface(null, Typeface.BOLD)
            setTextColor(palette.textPrimary)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        if (!subtitle.isNullOrBlank()) {
            titleCol.addView(TextView(ctx).apply {
                text = subtitle
                textSize = 13f
                setTextColor(palette.textMuted)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, (2 * d).toInt(), 0, 0)
            })
        }
        header.addView(titleCol)
        header.addView(createSheetCloseButton(ctx, palette) { dialog.dismiss() })
        root.addView(header)

        // Scrollable action rows (zero divider lines; clean tonal rows with generous touch targets)
        val scroll = ScrollView(ctx).apply {
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val listCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        headerContent?.invoke(listCol) { dialog.dismiss() }

        val normalItems = actions.filterNot { it.destructive }
        val destructiveItems = actions.filter { it.destructive }

        fun addActionRow(parent: LinearLayout, item: SheetActionItem, bottomMarginDp: Int) {
            val rowBg = when {
                item.destructive -> palette.redSoft
                item.checked -> palette.accentPrimarySoft
                else -> palette.surfaceTonal
            }
            val iconColor = item.tintOverride ?: when {
                item.destructive -> palette.red
                item.checked -> palette.accentPrimary
                else -> palette.textPrimary
            }
            val labelColor = when {
                item.destructive -> palette.red
                item.checked -> palette.accentPrimary
                else -> palette.textPrimary
            }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = (52 * d).toInt()
                setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (bottomMarginDp * d).toInt()
                }
                UiAnimations.applyRoundedRipple(ctx, this, rowBg, 16f, palette.isDark)
                UiAnimations.attachSnappyPress(this, 0.985f)
                setOnClickListener {
                    dialog.dismiss()
                    item.onClick()
                }
            }
            val iconCircle = FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt()).apply {
                    marginEnd = (12 * d).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(
                        when {
                            item.destructive -> if (palette.isDark) Color.parseColor("#33FF5C52") else Color.parseColor("#24D93025")
                            item.checked -> if (palette.isDark) Color.parseColor("#33238E5E") else Color.parseColor("#2418794E")
                            else -> palette.surfaceCanvas
                        }
                    )
                }
            }
            iconCircle.addView(ImageView(ctx).apply {
                setImageResource(item.iconRes)
                setColorFilter(iconColor)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams((20 * d).toInt(), (20 * d).toInt(), Gravity.CENTER)
            })
            row.addView(iconCircle)

            val txtCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = (8 * d).toInt()
                }
            }
            txtCol.addView(TextView(ctx).apply {
                text = item.title
                textSize = 15f
                setTypeface(null, if (item.destructive || item.checked) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(labelColor)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            if (!item.subtitle.isNullOrBlank()) {
                txtCol.addView(TextView(ctx).apply {
                    text = item.subtitle
                    textSize = 12f
                    setTextColor(if (item.destructive) palette.red else palette.textMuted)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, (1 * d).toInt(), 0, 0)
                })
            }
            row.addView(txtCol)

            if (item.checked) {
                row.addView(ImageView(ctx).apply {
                    setImageResource(R.drawable.ic_check)
                    setColorFilter(palette.accentPrimary)
                    layoutParams = LinearLayout.LayoutParams((20 * d).toInt(), (20 * d).toInt())
                })
            }
            parent.addView(row)
        }

        normalItems.forEachIndexed { idx, item ->
            val gap = if (idx == normalItems.lastIndex && destructiveItems.isNotEmpty()) 14 else 6
            addActionRow(listCol, item, gap)
        }
        destructiveItems.forEachIndexed { idx, item ->
            val gap = if (idx == destructiveItems.lastIndex) 0 else 6
            addActionRow(listCol, item, gap)
        }
        extraContent?.invoke(listCol)

        scroll.addView(listCol)
        root.addView(scroll)

        dialog.setContentView(root)
        dimAmount?.let { dialog.window?.setDimAmount(it) }
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.setBackgroundColor(Color.TRANSPARENT)
            dialog.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).apply {
                    isAppearanceLightStatusBars = !palette.isDark
                    isAppearanceLightNavigationBars = !palette.isDark
                }
            }
            dialog.behavior.apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
        }
        dialog.show()
        return dialog
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

    /** Applies 28dp rounded corners, solid warm alabaster/charcoal canvas, pill actions, and crisp entrance. */
    fun styleAlertDialog(
        ctx: Context,
        dialog: AlertDialog,
        palette: ThemePalette,
        destructivePositive: Boolean = false
    ) {
        val d = ctx.resources.displayMetrics.density
        dialog.window?.setBackgroundDrawable(
            buildOpaqueDrawable(
                ctx = ctx,
                fillColor = palette.surfaceCanvas,
                radiusDp = 28f,
                strokeColor = if (palette.isDark) palette.borderSubtle else Color.TRANSPARENT,
                strokeDp = if (palette.isDark) 1f else 0f
            )
        )
        dialog.findViewById<TextView>(android.R.id.message)?.apply {
            setTextColor(palette.textBody)
            textSize = 14f
            setLineSpacing(2f * d, 1.10f)
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(if (destructivePositive) palette.red else palette.accentPrimary)
            isAllCaps = false
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(palette.textMuted)
            isAllCaps = false
            textSize = 14.5f
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
            setTextColor(palette.textPrimary)
            isAllCaps = false
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
        }
        dialog.listView?.let { lv ->
            lv.divider = null
            lv.dividerHeight = 0
        }
        UiAnimations.animateDialogEnter(dialog.window?.decorView)
    }

    /** Wrap mixed-script titles/folders in Unicode isolates so adjacent separators/units stay aligned. */
    fun isolateBidi(raw: String): String = if (raw.isBlank()) raw else "\u2068$raw\u2069"

    fun joinBidi(vararg parts: String, sep: String = " • "): String =
        parts.filter { it.isNotBlank() }.joinToString(sep) { isolateBidi(it) }

    /** Reusable single-line pill MaterialButton with zero default vertical insets & snappy press. */
    fun createCompactButton(
        ctx: Context,
        label: String,
        palette: ThemePalette,
        radiusDp: Int = RADIUS_CHIP_DP,
        textSizeSp: Float = 13f,
        primaryCta: Boolean = false,
        onClick: () -> Unit
    ): MaterialButton {
        val d = ctx.resources.displayMetrics.density
        return MaterialButton(ctx).apply {
            text = label
            textSize = textSizeSp
            isAllCaps = false
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding((10 * d).toInt(), 0, (10 * d).toInt(), 0)
            cornerRadius = (radiusDp * d).toInt()
            if (primaryCta) applyPrimaryCtaButton(this, palette) else applySecondaryTonalButton(this, palette)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener { onClick() }
        }
    }

    fun createGroupedRowBackground(
        ctx: Context,
        palette: ThemePalette,
        isFirst: Boolean,
        isLast: Boolean,
        radiusDp: Float = 16f,
        fillOverride: Int? = null
    ): GradientDrawable {
        val d = ctx.resources.displayMetrics.density
        val r = radiusDp * d
        val topR = if (isFirst) r else 0f
        val botR = if (isLast) r else 0f
        return GradientDrawable().apply {
            setColor(fillOverride ?: palette.cardSurface)
            cornerRadii = floatArrayOf(topR, topR, topR, topR, botR, botR, botR, botR)
        }
    }
}
