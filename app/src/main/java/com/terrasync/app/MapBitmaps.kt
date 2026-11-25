package com.terrasync.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.DisplayMetrics
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

object MapBitmaps {

    // Exact Organic Maps pinched-waist + round bottom droplet bookmark pin (32 x 31 grid -> 28 x 27.125 dp)
    private const val BOOKMARK_PIN_GRID_W = 32.0f
    private const val BOOKMARK_PIN_GRID_H = 31.0f
    private const val BOOKMARK_PIN_DROPLET_CENTER_Y = 26.80f
    const val BOOKMARK_PIN_CANVAS_W_DP = 28.0f
    const val BOOKMARK_PIN_CANVAS_H_DP = BOOKMARK_PIN_CANVAS_W_DP * (BOOKMARK_PIN_GRID_H / BOOKMARK_PIN_GRID_W) // 27.125 dp
    const val BOOKMARK_PIN_TIP_BOTTOM_PAD_DP =
        (BOOKMARK_PIN_GRID_H - BOOKMARK_PIN_DROPLET_CENTER_Y) *
            (BOOKMARK_PIN_CANVAS_W_DP / BOOKMARK_PIN_GRID_W) // 3.675 dp -> droplet center (16.0, 26.80) lands dead-center on GPS

    /**
     * Exact Organic Maps-style pinched-waist + round bottom droplet bookmark pin:
     * - Normalized 32.0 x 31.0 grid scaled uniformly into 28.0 x 27.125 dp at native device density
     * - Completely flat & crisp 3-layer stack (zero shadows, zero dark contact dot):
     *   1. Uniform white outer outline (strokeWidth = 2.6 * unit -> ~1.14 dp visible white border)
     *   2. Solid dynamic color fill (Pending / Done / Issue / Target)
     *   3. Centered solid white inner dot at (16.0, 12.0), radius 4.2 * unit (~46.7% of head diameter)
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
        val unit = (BOOKMARK_PIN_CANVAS_W_DP / BOOKMARK_PIN_GRID_W) * d

        val w = ceil(BOOKMARK_PIN_CANVAS_W_DP * d).toInt().coerceAtLeast(28)
        val h = ceil(BOOKMARK_PIN_CANVAS_H_DP * d).toInt().coerceAtLeast(27)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.density = baseDpi
        val c = Canvas(bmp)

        // Center horizontally and align normalized y = 31.0 flush with bitmap bottom edge h
        // so droplet center (16.0, 26.80) sits strictly 3.675 dp above bottom edge on every DPI.
        val ox = (w - BOOKMARK_PIN_GRID_W * unit) / 2f
        val oy = h - BOOKMARK_PIN_GRID_H * unit

        c.save()
        c.translate(ox, oy)

        val path = Path().apply {
            // Step A: Upper circular head (center (16.0, 12.0), r = 9.0, 155° clockwise by 230° to 25°)
            arcTo(RectF(7.0f * unit, 3.0f * unit, 25.0f * unit, 21.0f * unit), 155f, 230f, true)
            // Step B: Smooth right shoulder curving inward into narrow pinched waist (17.25, 23.40)
            cubicTo(22.26f * unit, 19.88f * unit, 17.25f * unit, 20.80f * unit, 17.25f * unit, 23.40f * unit)
            // Step C: Flare smoothly outward into right edge of round bottom droplet (18.45, 26.80)
            cubicTo(17.25f * unit, 24.70f * unit, 18.45f * unit, 25.40f * unit, 18.45f * unit, 26.80f * unit)
            // Step D: Round bottom droplet semicircle centered at (16.0, 26.80), r = 2.45 (bottom y = 29.25)
            arcTo(RectF(13.55f * unit, 24.35f * unit, 18.45f * unit, 29.25f * unit), 0f, 180f, false)
            // Step E: Narrow smoothly back inward to left waist (14.75, 23.40)
            cubicTo(13.55f * unit, 25.40f * unit, 14.75f * unit, 24.70f * unit, 14.75f * unit, 23.40f * unit)
            // Step F: Sweep smoothly back outward to left shoulder (7.843, 15.804) and close
            cubicTo(14.75f * unit, 20.80f * unit, 9.74f * unit, 19.88f * unit, 7.843f * unit, 15.804f * unit)
            close()
        }

        // Layer 1: Crisp uniform white outer outline (~1.14 dp visible white border outside fill)
        val whiteOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.6f * unit
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        c.drawPath(path, whiteOutlinePaint)

        // Layer 2: Solid dynamic colored fill
        val fillColor = runCatching { Color.parseColor(colorHex) }.getOrDefault(Color.parseColor("#E69500"))
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            style = Paint.Style.FILL
        }
        c.drawPath(path, fillPaint)

        // Layer 3: Balanced inner white center dot at (16.0, 12.0), r = 4.2 * unit (~46.7% of head dia)
        val headCx = 16.0f * unit
        val headCy = 12.0f * unit
        if (drawCenterGlyph != null) {
            drawCenterGlyph(c, headCx, headCy, d)
        } else {
            val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            c.drawCircle(headCx, headCy, 4.2f * unit, innerDotPaint)
        }

        c.restore()
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
