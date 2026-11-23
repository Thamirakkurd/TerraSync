package com.terrasync.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

object MapBitmaps {

    /** Creates an Organic Maps-style teardrop pin bitmap with drop shadow & white center ring */
    fun createTeardropPin(ctx: Context, colorHex: String, scale: Float = 1f): Bitmap {
        val d = ctx.resources.displayMetrics.density * scale
        val w = (34 * d).toInt().coerceAtLeast(32)
        val h = (44 * d).toInt().coerceAtLeast(42)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val cx = w / 2f
        val headR = 11.5f * d
        val headY = 14.5f * d
        val tipY = h - 4f * d

        // Ground shadow ellipse
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#440F172A")
        }
        c.drawOval(RectF(cx - 7f * d, tipY - 2.2f * d, cx + 7f * d, tipY + 2.6f * d), shadowPaint)

        // Teardrop path
        val pinPath = Path().apply {
            moveTo(cx, tipY)
            cubicTo(cx - headR * 0.65f, headY + headR * 1.15f, cx - headR, headY + headR * 0.45f, cx - headR, headY)
            arcTo(RectF(cx - headR, headY - headR, cx + headR, headY + headR), 180f, 180f, false)
            cubicTo(cx + headR, headY + headR * 0.45f, cx + headR * 0.65f, headY + headR * 1.15f, cx, tipY)
            close()
        }

        // Crisp white outer border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.4f * d
            strokeJoin = Paint.Join.ROUND
        }
        c.drawPath(pinPath, borderPaint)

        // Colored fill
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(colorHex)
            style = Paint.Style.FILL
        }
        c.drawPath(pinPath, fillPaint)

        // Clean inner white ring
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        c.drawCircle(cx, headY, headR * 0.40f, innerPaint)

        return bmp
    }

    /** Creates an Organic Maps-style bold blue directional navigation arrow */
    fun createGpsArrow(ctx: Context): Bitmap {
        val d = ctx.resources.displayMetrics.density
        val size = (38 * d).toInt().coerceAtLeast(36)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f
        val cy = size / 2f

        // Soft halo
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
}
