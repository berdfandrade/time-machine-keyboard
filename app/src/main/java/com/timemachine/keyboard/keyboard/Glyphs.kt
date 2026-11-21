package com.timemachine.keyboard.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

enum class Glyph {
    SHIFT, SHIFT_ON, CAPS_LOCK, BACKSPACE, RETURN, GLOBE, SEARCH, HISTORY, CLOSE, CHEVRON_LEFT, CHEVRON_RIGHT, LOCK,
    GEAR, VIBRATE, SOUND, RESIZE, THEME, MINUS, PLUS, CHEVRON_UP, CHEVRON_DOWN, EXPAND, LAYOUT, SMILE,
}

/**
 * Ícones de traço desenhados em uma grade 24×24, com espessura proporcional ao tamanho para
 * acompanhar o peso do texto ao lado. Uma única fonte de verdade para o teclado (View) e para
 * a faixa e a linha do tempo (Compose, via nativeCanvas).
 */
object Glyphs {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val oval = RectF()

    /** Desenha [glyph] centralizado em ([cx], [cy]) ocupando um quadrado de [size] px. */
    fun draw(canvas: Canvas, glyph: Glyph, cx: Float, cy: Float, size: Float, color: Int) {
        val u = size / 24f
        val ox = cx - size / 2f
        val oy = cy - size / 2f
        fun x(v: Float) = ox + v * u
        fun y(v: Float) = oy + v * u
        paint.color = color
        paint.strokeWidth = 1.8f * u
        paint.style = Paint.Style.STROKE
        path.reset()

        when (glyph) {
            Glyph.SHIFT, Glyph.SHIFT_ON, Glyph.CAPS_LOCK -> {
                val bottom = if (glyph == Glyph.CAPS_LOCK) 17f else 19f
                path.moveTo(x(12f), y(3.5f))
                path.lineTo(x(20f), y(11.5f))
                path.lineTo(x(15.5f), y(11.5f))
                path.lineTo(x(15.5f), y(bottom))
                path.lineTo(x(8.5f), y(bottom))
                path.lineTo(x(8.5f), y(11.5f))
                path.lineTo(x(4f), y(11.5f))
                path.close()
                if (glyph != Glyph.SHIFT) paint.style = Paint.Style.FILL_AND_STROKE
                canvas.drawPath(path, paint)
                if (glyph == Glyph.CAPS_LOCK) {
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(x(8.5f), y(21f), x(15.5f), y(21f), paint)
                }
            }
            Glyph.BACKSPACE -> {
                path.moveTo(x(8f), y(5f))
                path.lineTo(x(21f), y(5f))
                path.lineTo(x(21f), y(19f))
                path.lineTo(x(8f), y(19f))
                path.lineTo(x(2.5f), y(12f))
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawLine(x(11.5f), y(9f), x(17.5f), y(15f), paint)
                canvas.drawLine(x(17.5f), y(9f), x(11.5f), y(15f), paint)
            }
            Glyph.RETURN -> {
                path.moveTo(x(19f), y(5f))
                path.lineTo(x(19f), y(13f))
                path.lineTo(x(5.5f), y(13f))
                canvas.drawPath(path, paint)
                path.reset()
                path.moveTo(x(9.5f), y(9f))
                path.lineTo(x(5.5f), y(13f))
                path.lineTo(x(9.5f), y(17f))
                canvas.drawPath(path, paint)
            }
            Glyph.GLOBE -> {
                canvas.drawCircle(x(12f), y(12f), 9f * u, paint)
                oval.set(x(8f), y(3f), x(16f), y(21f))
                canvas.drawOval(oval, paint)
                canvas.drawLine(x(3f), y(12f), x(21f), y(12f), paint)
            }
            Glyph.SEARCH -> {
                canvas.drawCircle(x(10.5f), y(10.5f), 6.5f * u, paint)
                canvas.drawLine(x(15.5f), y(15.5f), x(20.5f), y(20.5f), paint)
            }
            Glyph.HISTORY -> {
                // relógio com seta anti-horária: "voltar no tempo"
                oval.set(x(4f), y(4f), x(20f), y(20f))
                canvas.drawArc(oval, 200f, 290f, false, paint)
                path.moveTo(x(2.6f), y(6.4f))
                path.lineTo(x(4.5f), y(9.3f))
                path.lineTo(x(7.6f), y(7.6f))
                canvas.drawPath(path, paint)
                path.reset()
                path.moveTo(x(12f), y(7.5f))
                path.lineTo(x(12f), y(12f))
                path.lineTo(x(15f), y(14f))
                canvas.drawPath(path, paint)
            }
            Glyph.CLOSE -> {
                canvas.drawLine(x(6f), y(6f), x(18f), y(18f), paint)
                canvas.drawLine(x(18f), y(6f), x(6f), y(18f), paint)
            }
            Glyph.CHEVRON_LEFT, Glyph.CHEVRON_RIGHT -> {
                val l = glyph == Glyph.CHEVRON_LEFT
                path.moveTo(x(if (l) 15f else 9f), y(5f))
                path.lineTo(x(if (l) 8f else 16f), y(12f))
                path.lineTo(x(if (l) 15f else 9f), y(19f))
                canvas.drawPath(path, paint)
            }
            Glyph.GEAR -> {
                canvas.drawCircle(x(12f), y(12f), 3f * u, paint)
                canvas.drawCircle(x(12f), y(12f), 6.5f * u, paint)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val c = kotlin.math.cos(a).toFloat()
                    val s = kotlin.math.sin(a).toFloat()
                    canvas.drawLine(x(12f + 6.5f * c), y(12f + 6.5f * s), x(12f + 9f * c), y(12f + 9f * s), paint)
                }
            }
            Glyph.VIBRATE -> {
                oval.set(x(8f), y(4f), x(16f), y(20f))
                canvas.drawRoundRect(oval, 2f * u, 2f * u, paint)
                canvas.drawLine(x(4.5f), y(8.5f), x(4.5f), y(15.5f), paint)
                canvas.drawLine(x(19.5f), y(8.5f), x(19.5f), y(15.5f), paint)
                canvas.drawLine(x(2f), y(10.5f), x(2f), y(13.5f), paint)
                canvas.drawLine(x(22f), y(10.5f), x(22f), y(13.5f), paint)
            }
            Glyph.SOUND -> {
                path.moveTo(x(3.5f), y(9.5f))
                path.lineTo(x(7.5f), y(9.5f))
                path.lineTo(x(12.5f), y(5f))
                path.lineTo(x(12.5f), y(19f))
                path.lineTo(x(7.5f), y(14.5f))
                path.lineTo(x(3.5f), y(14.5f))
                path.close()
                canvas.drawPath(path, paint)
                oval.set(x(11f), y(8f), x(19f), y(16f))
                canvas.drawArc(oval, -50f, 100f, false, paint)
                oval.set(x(10f), y(4.5f), x(22f), y(19.5f))
                canvas.drawArc(oval, -50f, 100f, false, paint)
            }
            Glyph.RESIZE -> {
                canvas.drawLine(x(12f), y(5f), x(12f), y(19f), paint)
                path.moveTo(x(8f), y(8.5f)); path.lineTo(x(12f), y(4.5f)); path.lineTo(x(16f), y(8.5f))
                path.moveTo(x(8f), y(15.5f)); path.lineTo(x(12f), y(19.5f)); path.lineTo(x(16f), y(15.5f))
                canvas.drawPath(path, paint)
                canvas.drawLine(x(4f), y(2.5f), x(20f), y(2.5f), paint)
                canvas.drawLine(x(4f), y(21.5f), x(20f), y(21.5f), paint)
            }
            Glyph.THEME -> {
                canvas.drawCircle(x(12f), y(12f), 8.5f * u, paint)
                path.moveTo(x(12f), y(3.5f))
                path.arcTo(x(3.5f), y(3.5f), x(20.5f), y(20.5f), 90f, 180f, false)
                path.close()
                paint.style = Paint.Style.FILL_AND_STROKE
                canvas.drawPath(path, paint)
            }
            Glyph.MINUS -> canvas.drawLine(x(5f), y(12f), x(19f), y(12f), paint)
            Glyph.PLUS -> {
                canvas.drawLine(x(5f), y(12f), x(19f), y(12f), paint)
                canvas.drawLine(x(12f), y(5f), x(12f), y(19f), paint)
            }
            Glyph.CHEVRON_UP, Glyph.CHEVRON_DOWN -> {
                val up = glyph == Glyph.CHEVRON_UP
                path.moveTo(x(5f), y(if (up) 15f else 9f))
                path.lineTo(x(12f), y(if (up) 8f else 16f))
                path.lineTo(x(19f), y(if (up) 15f else 9f))
                canvas.drawPath(path, paint)
            }
            Glyph.EXPAND -> {
                // duas setas para fora: voltar ao teclado completo
                canvas.drawLine(x(4f), y(12f), x(20f), y(12f), paint)
                path.moveTo(x(8f), y(8f)); path.lineTo(x(4f), y(12f)); path.lineTo(x(8f), y(16f))
                path.moveTo(x(16f), y(8f)); path.lineTo(x(20f), y(12f)); path.lineTo(x(16f), y(16f))
                canvas.drawPath(path, paint)
            }
            Glyph.LAYOUT -> {
                // teclado estilizado: modos
                oval.set(x(2.5f), y(6f), x(21.5f), y(18f))
                canvas.drawRoundRect(oval, 2f * u, 2f * u, paint)
                for (cx in listOf(6.5f, 10f, 13.5f, 17f)) canvas.drawPoint(x(cx), y(10f), paint)
                canvas.drawLine(x(8f), y(14f), x(16f), y(14f), paint)
            }
            Glyph.SMILE -> {
                canvas.drawCircle(x(12f), y(12f), 9f * u, paint)
                oval.set(x(7.5f), y(8f), x(16.5f), y(17f))
                canvas.drawArc(oval, 30f, 120f, false, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x(9f), y(10f), 1.2f * u, paint)
                canvas.drawCircle(x(15f), y(10f), 1.2f * u, paint)
            }
            Glyph.LOCK -> {
                oval.set(x(5.5f), y(10.5f), x(18.5f), y(20.5f))
                canvas.drawRoundRect(oval, 2f * u, 2f * u, paint)
                path.moveTo(x(8.5f), y(10.5f))
                path.lineTo(x(8.5f), y(7.5f))
                path.cubicTo(x(8.5f), y(3f), x(15.5f), y(3f), x(15.5f), y(7.5f))
                path.lineTo(x(15.5f), y(10.5f))
                canvas.drawPath(path, paint)
            }
        }
    }
}
