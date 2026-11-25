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
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object MapBitmaps {

    // Exact Organic Maps map-tack bookmark pin dimensions (logical dp at native 1.0x density)
    const val BOOKMARK_PIN_CANVAS_W_DP = 24.0f
    const val BOOKMARK_PIN_CANVAS_H_DP = 26.0f
    const val BOOKMARK_PIN_HEAD_DIAMETER_DP = 14.0f
    const val BOOKMARK_PIN_INNER_DOT_DIAMETER_DP = 5.2f
    const val BOOKMARK_PIN_VISIBLE_STEM_DP = 4.3f
    const val BOOKMARK_PIN_WHITE_STROKE_DP = 1.2f          // 0.6 dp visible hairline outside fill
    const val BOOKMARK_PIN_TIP_Y_DP = 20.8f                // headY(9.5) + headR(7.0) + stem(4.3)
    const val BOOKMARK_PIN_TIP_BOTTOM_PAD_DP =
        BOOKMARK_PIN_CANVAS_H_DP - BOOKMARK_PIN_TIP_Y_DP   // 5.20 dp offset -> tip lands dead-center on GPS

    /**
     * Exact Organic Maps-style map-tack bookmark pin rendered at native device density:
     * - 24 x 26 dp logical canvas centered at cx = 12.0 dp
     * - Circular head at (12.0, 9.5), diameter 14.0 dp (r = 7.0 dp), bottom at y = 16.5 dp
     * - Short, sharply tapered 4.3 dp tack stem (y = 16.5 -> 20.8 dp) with smooth concave fillets
     *   from (9.8/14.2, 16.1) pinching to 2.1 dp wide at y = 17.5 down to a 0.55 dp rounded tip
     * - Clean 4-layer stack: subtle grounding micro-shadow only (alpha 42, dy +0.5 dp, blur 0.9 dp),
     *   razor-thin hairline white stroke (1.2 dp = 0.6 dp outer edge), solid dynamic color fill,
     *   and crisp 5.2 dp inner white center dot (~37% of head diameter, 4.4 dp colored ring wall).
     */
    fun createBookmarkPin(
        ctx: Context,
        colorHex: String,
        scale: Float = 1f,
        drawCenterGlyph: ((Canvas, Float, Float, Float) -> Unit)? = null
    ): Bitmap {
        val baseDpi = ctx.resources.displayMetrics.densityDpi
            .takeIf { it > 0 } ?: DisplayMetrics.DENSITY_DEFAULT
        val d = (baseDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT) * scale

        val w = ceil(BOOKMARK_PIN_CANVAS_W_DP * d).toInt().coerceAtLeast(24)
        val h = ceil(BOOKMARK_PIN_CANVAS_H_DP * d).toInt().coerceAtLeast(26)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.density = baseDpi
        val c = Canvas(bmp)

        val cx = w / 2f
        val headY = 9.5f * d
        val headR = (BOOKMARK_PIN_HEAD_DIAMETER_DP / 2f) * d            // 7.0 dp
        val innerDotR = (BOOKMARK_PIN_INNER_DOT_DIAMETER_DP / 2f) * d   // 2.6 dp (5.2 dp dia)

        // Short tack stem with smooth concave fillets into underside of circle
        val filletTopY = 16.1f * d
        val filletTopHalfW = 2.2f * d                                   // x = 9.8 / 14.2 dp
        val stemOverlapTopY = 15.0f * d                                 // safely inside circle for clean UNION
        val neckY = 17.5f * d
        val neckHalfW = 1.05f * d                                       // ~2.1 dp wide at y = 17.5
        val tipR = 0.55f * d                                            // crisp tiny rounded tip
        val tipCapY = (BOOKMARK_PIN_TIP_Y_DP - 0.55f) * d               // 20.25 dp -> bottom at 20.80 dp

        // Exact external tangent angle from (cx +/- neckHalfW, neckY) to tip cap (cx, tipCapY, tipR)
        val distNT = hypot(neckHalfW.toDouble(), (tipCapY - neckY).toDouble())
        val taperRad = atan2(neckHalfW.toDouble(), (tipCapY - neckY).toDouble()) -
            asin((tipR.toDouble() / distNT).coerceIn(-1.0, 1.0))
        val taperDeg = Math.toDegrees(taperRad).toFloat()
        val cosT = cos(taperRad).toFloat()
        val sinT = sin(taperRad).toFloat()
        val tanT = sinT / cosT

        // Quadratic fillet control point lies on straight needle flank extended up to y = 16.45 dp (C1 smooth)
        val filletCtrlY = 16.45f * d
        val filletCtrlHalfW = neckHalfW + (neckY - filletCtrlY) * tanT

        val tipCapRect = RectF(cx - tipR, tipCapY - tipR, cx + tipR, tipCapY + tipR)
        val stemPath = Path().apply {
            moveTo(cx - filletTopHalfW, stemOverlapTopY)
            lineTo(cx + filletTopHalfW, stemOverlapTopY)
            lineTo(cx + filletTopHalfW, filletTopY)
            quadTo(cx + filletCtrlHalfW, filletCtrlY, cx + neckHalfW, neckY)
            lineTo(cx + tipR * cosT, tipCapY + tipR * sinT)
            arcTo(tipCapRect, taperDeg, 180f - 2f * taperDeg, false)
            lineTo(cx - neckHalfW, neckY)
            quadTo(cx - filletCtrlHalfW, filletCtrlY, cx - filletTopHalfW, filletTopY)
            close()
        }

        val pinUnionPath = Path().apply {
            addCircle(cx, headY, headR, Path.Direction.CW)
            op(stemPath, Path.Op.UNION)
        }

        // Layer 1: Subtle grounding micro-shadow ONLY (no dark outer outline stroke)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(42, 0, 0, 0)
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter((0.9f * d).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
        }
        c.save()
        c.translate(0f, 0.5f * d)
        c.drawPath(pinUnionPath, shadowPaint)
        c.restore()

        // Layer 2: Razor-thin hairline white border (1.2 dp stroke = 0.6 dp visible outside fill)
        val whiteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = BOOKMARK_PIN_WHITE_STROKE_DP * d
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        c.drawPath(pinUnionPath, whiteBorderPaint)

        // Layer 3: Solid dynamic color fill (seamless circular head + tack stem)
        val fillColor = runCatching { Color.parseColor(colorHex) }.getOrDefault(Color.parseColor("#E69500"))
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            style = Paint.Style.FILL
        }
        c.drawPath(pinUnionPath, fillPaint)

        // Layer 4: Smaller crisp inner white center dot (5.2 dp dia, ~37% of 14.0 dp head)
        // or optional custom white category icon sized inside ~6.5 dp at (cx, headY)
        if (drawCenterGlyph != null) {
            drawCenterGlyph(c, cx, headY, d)
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
