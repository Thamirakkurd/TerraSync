package com.terrasync.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object MapBitmaps {

    /** Clean Organic Maps-style teardrop pin with crisp white border & soft ground shadow */
    fun createTeardropPin(ctx: Context, colorHex: String, scale: Float = 1f): Bitmap {
        val d = ctx.resources.displayMetrics.density * scale
        val w = (22 * d).toInt().coerceAtLeast(22)
        val h = (28 * d).toInt().coerceAtLeast(28)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val cx = w / 2f
        val headR = 7.0f * d
        val headY = 9.0f * d
        val tipY = h - 1.3f * d

        // Soft ground shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#30000000")
        }
        c.drawOval(
            RectF(cx - 4.0f * d, tipY - 0.9f * d, cx + 4.0f * d, tipY + 1.1f * d),
            shadowPaint
        )

        val pinPath = Path().apply {
            moveTo(cx, tipY)
            cubicTo(
                cx - headR * 0.50f, headY + headR * 1.18f,
                cx - headR, headY + headR * 0.46f,
                cx - headR, headY
            )
            arcTo(
                RectF(cx - headR, headY - headR, cx + headR, headY + headR),
                180f, 180f, false
            )
            cubicTo(
                cx + headR, headY + headR * 0.46f,
                cx + headR * 0.50f, headY + headR * 1.18f,
                cx, tipY
            )
            close()
        }

        // Crisp white ring
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.0f * d
            strokeJoin = Paint.Join.ROUND
        }
        c.drawPath(pinPath, borderPaint)

        // Status fill
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(colorHex)
            style = Paint.Style.FILL
        }
        c.drawPath(pinPath, fillPaint)

        // Centered white dot
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        c.drawCircle(cx, headY, headR * 0.34f, innerPaint)

        return bmp
    }

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
