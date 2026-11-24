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

    /** Compact Organic Maps-style teardrop pin with crisp white border & soft ground shadow */
    fun createTeardropPin(ctx: Context, colorHex: String, scale: Float = 1f): Bitmap {
        val d = ctx.resources.displayMetrics.density * scale
        val w = (21 * d).toInt().coerceAtLeast(21)
        val h = (27 * d).toInt().coerceAtLeast(27)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val cx = w / 2f
        val headR = 6.8f * d
        val headY = 8.8f * d
        val tipY = h - 1.2f * d

        // Subtle ground shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#380F172A")
        }
        c.drawOval(
            RectF(cx - 4.0f * d, tipY - 0.9f * d, cx + 4.0f * d, tipY + 1.1f * d),
            shadowPaint
        )

        // Compact teardrop silhouette
        val pinPath = Path().apply {
            moveTo(cx, tipY)
            cubicTo(
                cx - headR * 0.52f, headY + headR * 1.18f,
                cx - headR, headY + headR * 0.48f,
                cx - headR, headY
            )
            arcTo(
                RectF(cx - headR, headY - headR, cx + headR, headY + headR),
                180f, 180f, false
            )
            cubicTo(
                cx + headR, headY + headR * 0.48f,
                cx + headR * 0.52f, headY + headR * 1.18f,
                cx, tipY
            )
            close()
        }

        // Crisp white outer ring
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.0f * d
            strokeJoin = Paint.Join.ROUND
        }
        c.drawPath(pinPath, borderPaint)

        // Status color fill
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(colorHex)
            style = Paint.Style.FILL
        }
        c.drawPath(pinPath, fillPaint)

        // Clean centered white dot
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        c.drawCircle(cx, headY, headR * 0.34f, innerPaint)

        return bmp
    }

    /** Crisp Organic Maps-style crescent-and-star mosque badge */
    fun createMosqueIcon(ctx: Context): Bitmap {
        val d = ctx.resources.displayMetrics.density
        val size = (20 * d).toInt().coerceAtLeast(20)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f
        val cy = size / 2f

        // Soft circular badge with white rim (Organic Maps POI style)
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6D4C41")
            style = Paint.Style.FILL
        }
        c.drawCircle(cx, cy, 8.6f * d, rimPaint)
        c.drawCircle(cx, cy, 7.5f * d, bgPaint)

        // Crescent moon via Path.Op.DIFFERENCE
        val outer = Path().apply { addCircle(cx - 0.8f * d, cy, 4.1f * d, Path.Direction.CW) }
        val inner = Path().apply { addCircle(cx + 0.9f * d, cy - 0.5f * d, 3.3f * d, Path.Direction.CW) }
        outer.op(inner, Path.Op.DIFFERENCE)

        // 5-point star tucked inside the crescent
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
        return bmp
    }

    /** Bold blue directional navigation arrow */
    fun createGpsArrow(ctx: Context): Bitmap {
        val d = ctx.resources.displayMetrics.density
        val size = (38 * d).toInt().coerceAtLeast(36)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f
        val cy = size / 2f

        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#332563EB")
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
            color = Color.parseColor("#2563EB")
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
