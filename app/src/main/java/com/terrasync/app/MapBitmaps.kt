package com.terrasync.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.DisplayMetrics
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object MapBitmaps {

    // Exact Organic Maps map-tack bookmark pin dimensions (logical dp at iconSize = 1.0)
    const val BOOKMARK_PIN_CANVAS_W_DP = 20.5f
    const val BOOKMARK_PIN_CANVAS_H_DP = 26.4f
    const val BOOKMARK_PIN_HEAD_DIAMETER_DP = 14.6f
    const val BOOKMARK_PIN_HALO_DP = 1.35f
    const val BOOKMARK_PIN_VISIBLE_STEM_DP = 5.8f
    const val BOOKMARK_PIN_TIP_BOTTOM_PAD_DP = 2.10f

    /**
     * Compact Organic Maps-style map-tack bookmark pin:
     * true circular head (14.6 dp colored / 17.3 dp outer with halo) sitting atop a slim, gently
     * tapering vertical stem (5.8 dp visible length below circle, 3.0 dp neck tapering to 1.6 dp
     * rounded tip), wrapped in a single continuous 1.35 dp white outer halo with zero neck seam,
     * crisp inner white dot (6.4 dp, 43.8% of head diameter), and subtle ground/separation shadow.
     * Oversampled at 1.35x DPI so 1.28x selected-pin scaling stays razor-sharp without clipping.
     */
    fun createBookmarkPin(
        ctx: Context,
        colorHex: String,
        scale: Float = 1f,
        drawCenterGlyph: ((Canvas, Float, Float, Float) -> Unit)? = null
    ): Bitmap {
        val oversample = 1.35f
        val baseDpi = ctx.resources.displayMetrics.densityDpi
            .takeIf { it > 0 } ?: DisplayMetrics.DENSITY_DEFAULT
        val targetDensityDpi = (baseDpi * oversample)
            .roundToInt()
            .coerceAtLeast(DisplayMetrics.DENSITY_DEFAULT)
        val pxPerDp = (targetDensityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT) * scale

        val w = ceil(BOOKMARK_PIN_CANVAS_W_DP * pxPerDp).toInt().coerceAtLeast(20)
        val h = ceil(BOOKMARK_PIN_CANVAS_H_DP * pxPerDp).toInt().coerceAtLeast(26)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.density = targetDensityDpi
        val c = Canvas(bmp)

        val cx = w / 2f
        val headR = (BOOKMARK_PIN_HEAD_DIAMETER_DP / 2f) * pxPerDp      // 7.30 dp
        val headY = 9.85f * pxPerDp
        val haloPx = BOOKMARK_PIN_HALO_DP * pxPerDp                     // 1.35 dp
        val innerDotR = 3.20f * pxPerDp                                 // 6.40 dp dia (43.8% of 14.6 dp)

        val neckHalfW = 1.50f * pxPerDp                                 // 3.00 dp neck width
        val tipR = 0.80f * pxPerDp                                      // 1.60 dp rounded tip width
        val visibleStemLen = BOOKMARK_PIN_VISIBLE_STEM_DP * pxPerDp     // 5.80 dp below circle bottom
        val tipBottomY = headY + headR + visibleStemLen                 // 22.95 dp
        val tipCapY = tipBottomY - tipR                                 // 22.15 dp
        val tipOuterBottomY = tipBottomY + haloPx                       // 24.30 dp

        // Exact circle intersection Y at dx = +/- neckHalfW and safe interior overlap for union
        val neckY = headY + sqrt((headR * headR - neckHalfW * neckHalfW).coerceAtLeast(0f))
        val stemTopY = headY + 5.60f * pxPerDp

        // Exact C1 tangent taper angle for straight vertical needle meeting rounded bottom cap
        val taperRad = atan2((neckHalfW - tipR).toDouble(), (tipCapY - neckY).toDouble())
        val taperDeg = Math.toDegrees(taperRad).toFloat()
        val cosT = cos(taperRad).toFloat()
        val sinT = sin(taperRad).toFloat()
        val tanT = sinT / cosT

        fun buildStemPath(expandPx: Float): Path {
            val rCap = tipR + expandPx
            val topHalfW = rCap / cosT + (tipCapY - stemTopY) * tanT
            val capRect = RectF(cx - rCap, tipCapY - rCap, cx + rCap, tipCapY + rCap)
            return Path().apply {
                moveTo(cx - topHalfW, stemTopY)
                lineTo(cx + topHalfW, stemTopY)
                lineTo(cx + rCap * cosT, tipCapY + rCap * sinT)
                arcTo(capRect, taperDeg, 180f - 2f * taperDeg, false)
                lineTo(cx - topHalfW, stemTopY)
                close()
            }
        }

        // Exact unified (head UNION stem) silhouette and exact Minkowski-dilated outer halo path
        val pinUnionPath = Path().apply {
            addCircle(cx, headY, headR, Path.Direction.CW)
            op(buildStemPath(0f), Path.Op.UNION)
        }
        val haloUnionPath = Path().apply {
            addCircle(cx, headY, headR + haloPx, Path.Direction.CW)
            op(buildStemPath(haloPx), Path.Op.UNION)
        }

        // 1. Subtle tight ground contact shadow beneath tip (~18% alpha, tiny blur)
        val groundShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(46, 0, 0, 0)
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter((0.65f * pxPerDp).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
        }
        val groundShadowY = tipOuterBottomY - 0.15f * pxPerDp
        c.drawOval(
            RectF(
                cx - 3.2f * pxPerDp,
                groundShadowY - 0.75f * pxPerDp,
                cx + 3.2f * pxPerDp,
                groundShadowY + 0.75f * pxPerDp
            ),
            groundShadowPaint
        )

        // 2. Subtle tight drop shadow behind unified halo (~14% alpha, single-pass fill for cluster clarity)
        val dropShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(36, 0, 0, 0)
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter((0.85f * pxPerDp).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
        }
        c.save()
        c.translate(0f, 0.45f * pxPerDp)
        c.drawPath(haloUnionPath, dropShadowPaint)
        c.restore()

        // 3. Continuous crisp white outer halo drawn behind unified fill (zero white seam at neck)
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        c.drawPath(haloUnionPath, haloPaint)

        // 4. Seamless dynamic-colored fill applied to the unified circular head + vertical stem
        val fillColor = runCatching { Color.parseColor(colorHex) }.getOrDefault(Color.parseColor("#E69500"))
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            style = Paint.Style.FILL
        }
        c.drawPath(pinUnionPath, fillPaint)

        // 5. Crisp solid white inner dot (~43.8% of head diameter) or optional white center glyph (~8 dp zone)
        if (drawCenterGlyph != null) {
            drawCenterGlyph(c, cx, headY, pxPerDp)
        } else {
            val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            c.drawCircle(cx, headY, innerDotR, innerDotPaint)
        }

        return bmp
    }

    /** Backwards-compatible alias forwarding to [createBookmarkPin]. */
    fun createTeardropPin(ctx: Context, colorHex: String, scale: Float = 1f): Bitmap =
        createBookmarkPin(ctx, colorHex, scale)

    /**
     * Crisp Organic Maps-style white rectangular highway route shield bordered in thin black
     * (e.g., M5, M90, E90, 2). Registered for road_1..road_6 on vector styles.
     */
    fun createHighwayShield(ctx: Context, charSlots: Int, isDark: Boolean): Bitmap {
        val d = ctx.resources.displayMetrics.density
        val slots = charSlots.coerceIn(1, 7)
        val wDp = (13f + slots * 6.2f).coerceIn(22f, 58f)
        val hDp = 17.5f
        val w = (wDp * d).toInt().coerceAtLeast(22)
        val h = (hDp * d).toInt().coerceAtLeast(18)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val pad = 1.0f * d
        val rect = RectF(pad, pad, w - pad, h - pad)
        val corner = 3.0f * d

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) Color.parseColor("#242933") else Color.WHITE
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) Color.parseColor("#B0B7C4") else Color.parseColor("#1C1C1E")
            style = Paint.Style.STROKE
            strokeWidth = (1.15f * d).coerceAtLeast(1.5f)
        }
        c.drawRoundRect(rect, corner, corner, fill)
        c.drawRoundRect(rect, corner, corner, stroke)
        return bmp
    }

    /** High-DPI Organic Maps-style circular POI badge helper. */
    private fun createPoiBadgeCanvas(ctx: Context, bgHex: String, drawGlyph: (Canvas, Float, Float, Float) -> Unit): Bitmap {
        val d = ctx.resources.displayMetrics.density
        val size = (20 * d).toInt().coerceAtLeast(20)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f
        val cy = size / 2f

        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(bgHex)
            style = Paint.Style.FILL
        }
        c.drawCircle(cx, cy, 8.8f * d, rimPaint)
        c.drawCircle(cx, cy, 7.6f * d, bgPaint)
        drawGlyph(c, cx, cy, d)
        return bmp
    }

    /** Crisp Organic Maps-style crescent-and-star mosque badge */
    fun createMosqueIcon(ctx: Context): Bitmap = createPoiBadgeCanvas(ctx, "#6D4C41") { c, cx, cy, d ->
        val outer = Path().apply { addCircle(cx - 0.8f * d, cy, 4.1f * d, Path.Direction.CW) }
        val inner = Path().apply { addCircle(cx + 0.9f * d, cy - 0.5f * d, 3.3f * d, Path.Direction.CW) }
        outer.op(inner, Path.Op.DIFFERENCE)

        val star = Path()
        val sx = cx + 2.2f * d
        val sy = cy - 0.9f * d
        val rOut = 1.9f * d
        val rIn = 0.8f * d
        for (i in 0 until 10) {
            val angle = -PI / 2.0 + i * PI / 5.0
            val r = if (i % 2 == 0) rOut else rIn
            val x = sx + (r * cos(angle)).toFloat()
            val y = sy + (r * sin(angle)).toFloat()
            if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        c.drawPath(outer, iconPaint)
        c.drawPath(star, iconPaint)
    }

    /** High-DPI Fuel station badge (Organic Maps blue) */
    fun createFuelIcon(ctx: Context): Bitmap = createPoiBadgeCanvas(ctx, "#1E88E5") { c, cx, cy, d ->
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 1.2f * d; strokeCap = Paint.Cap.ROUND
        }
        // Pump body
        c.drawRoundRect(RectF(cx - 3.6f * d, cy - 3.8f * d, cx + 1.4f * d, cy + 4.0f * d), 1.0f * d, 1.0f * d, p)
        // Screen cutout
        val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E88E5") }
        c.drawRect(RectF(cx - 2.6f * d, cy - 2.8f * d, cx + 0.4f * d, cy - 0.5f * d), cut)
        // Hose nozzle
        val hose = Path().apply {
            moveTo(cx + 1.4f * d, cy - 0.8f * d)
            lineTo(cx + 3.2f * d, cy - 0.8f * d)
            lineTo(cx + 3.2f * d, cy + 2.4f * d)
            lineTo(cx + 4.2f * d, cy + 2.4f * d)
            lineTo(cx + 4.2f * d, cy - 2.2f * d)
            lineTo(cx + 2.8f * d, cy - 3.4f * d)
        }
        c.drawPath(hose, stroke)
    }

    /** High-DPI Transit / Bus stop badge (Organic Maps transit blue) */
    fun createTransitIcon(ctx: Context): Bitmap = createPoiBadgeCanvas(ctx, "#1565C0") { c, cx, cy, d ->
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1565C0") }
        c.drawRoundRect(RectF(cx - 3.6f * d, cy - 4.0f * d, cx + 3.6f * d, cy + 3.2f * d), 1.4f * d, 1.4f * d, p)
        c.drawRect(RectF(cx - 2.5f * d, cy - 2.7f * d, cx + 2.5f * d, cy - 0.2f * d), cut)
        c.drawCircle(cx - 1.8f * d, cy + 1.6f * d, 0.85f * d, cut)
        c.drawCircle(cx + 1.8f * d, cy + 1.6f * d, 0.85f * d, cut)
        c.drawRect(RectF(cx - 2.8f * d, cy + 3.0f * d, cx - 1.4f * d, cy + 4.2f * d), p)
        c.drawRect(RectF(cx + 1.4f * d, cy + 3.0f * d, cx + 2.8f * d, cy + 4.2f * d), p)
    }

    /** High-DPI Viewpoint / Peak badge (Organic Maps forest green) */
    fun createViewpointIcon(ctx: Context): Bitmap = createPoiBadgeCanvas(ctx, "#2E8B47") { c, cx, cy, d ->
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 1.25f * d; strokeCap = Paint.Cap.ROUND
        }
        c.drawCircle(cx, cy + 1.2f * d, 1.7f * d, p)
        val angles = doubleArrayOf(-150.0, -115.0, -90.0, -65.0, -30.0)
        for (deg in angles) {
            val rad = Math.toRadians(deg)
            val x1 = cx + (2.4f * d * cos(rad)).toFloat()
            val y1 = cy + 1.2f * d + (2.4f * d * sin(rad)).toFloat()
            val x2 = cx + (4.7f * d * cos(rad)).toFloat()
            val y2 = cy + 1.2f * d + (4.7f * d * sin(rad)).toFloat()
            c.drawLine(x1, y1, x2, y2, stroke)
        }
    }

    /** High-DPI Hospital / Clinic / Pharmacy cross badge (Organic Maps crimson) */
    fun createMedicalIcon(ctx: Context): Bitmap = createPoiBadgeCanvas(ctx, "#D93025") { c, cx, cy, d ->
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        c.drawRoundRect(RectF(cx - 1.3f * d, cy - 4.0f * d, cx + 1.3f * d, cy + 4.0f * d), 0.6f * d, 0.6f * d, p)
        c.drawRoundRect(RectF(cx - 4.0f * d, cy - 1.3f * d, cx + 4.0f * d, cy + 1.3f * d), 0.6f * d, 0.6f * d, p)
    }

    /** Bold Organic Maps blue directional navigation arrow */
    fun createGpsArrow(ctx: Context): Bitmap {
        val d = ctx.resources.displayMetrics.density
        val size = (38 * d).toInt().coerceAtLeast(36)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f
        val cy = size / 2f

        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#331E88E5")
        }
        c.drawCircle(cx, cy, size * 0.46f, halo)

        val arrowPath = Path().apply {
            moveTo(cx, 5f * d)
            lineTo(size - 7.5f * d, size - 7f * d)
            lineTo(cx, size - 12.5f * d)
            lineTo(7.5f * d, size - 7f * d)
            close()
        }

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * d
            strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E88E5")
            style = Paint.Style.FILL
        }
        c.drawPath(arrowPath, stroke)
        c.drawPath(arrowPath, fill)
        return bmp
    }

    /** Compact Organic Maps-style on-route ETA pill (e.g. "12 min") */
    fun createRoutePill(ctx: Context, label: String, bgHex: String): Bitmap {
        val d = ctx.resources.displayMetrics.density
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11.5f * d
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val padH = 9f * d
        val padV = 5f * d
        val textW = textPaint.measureText(label)
        val fm = textPaint.fontMetrics
        val textH = fm.descent - fm.ascent
        val strokeW = 1.6f * d
        val w = (textW + padH * 2f + strokeW * 2f).toInt().coerceAtLeast((38 * d).toInt())
        val h = (textH + padV * 2f + strokeW * 2f).toInt().coerceAtLeast((22 * d).toInt())

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rect = RectF(strokeW, strokeW, w - strokeW, h - strokeW)
        val r = rect.height() / 2f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(bgHex)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = strokeW
        }
        c.drawRoundRect(rect, r, r, fillPaint)
        c.drawRoundRect(rect, r, r, borderPaint)

        val baseline = rect.centerY() - (fm.ascent + fm.descent) / 2f
        c.drawText(label, rect.centerX(), baseline, textPaint)
        return bmp
    }
}
