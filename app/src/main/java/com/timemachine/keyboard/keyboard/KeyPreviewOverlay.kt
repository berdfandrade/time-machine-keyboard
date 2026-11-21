package com.timemachine.keyboard.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * Camada sobre todo o teclado (incluindo a faixa superior) onde ficam a prévia da tecla e o
 * seletor de acentos. Assim a prévia da fileira de cima pode subir além das teclas sem
 * PopupWindow. Não recebe toques: o KeyboardView continua dono do gesto.
 */
class KeyPreviewOverlay(context: Context) : View(context) {
    var palette: KeyboardPalette = KeyboardPalette.Dark
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val radius = 8f * density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private var previewKey: RectF? = null
    private var previewLabel: String? = null

    private var variants: List<String> = emptyList()
    private val variantPanel = RectF()
    private var variantCell = 0f
    var selectedVariant = 0
        private set

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun showPreview(keyInOverlay: RectF, label: String) {
        previewKey = RectF(keyInOverlay)
        previewLabel = label
        invalidate()
    }

    fun hidePreview() {
        if (previewKey == null) return
        previewKey = null
        previewLabel = null
        invalidate()
    }

    fun showVariants(keyInOverlay: RectF, options: List<String>) {
        hidePreview()
        variants = options
        variantCell = maxOf(keyInOverlay.width(), 34f * density)
        val width = variantCell * options.size
        val height = keyInOverlay.height() * 1.1f
        var left = keyInOverlay.left
        if (left + width > this.width - 4f * density) left = this.width - 4f * density - width
        left = left.coerceAtLeast(4f * density)
        val top = (keyInOverlay.top - height - 6f * density).coerceAtLeast(0f)
        variantPanel.set(left, top, left + width, top + height)
        selectedVariant = 0
        invalidate()
    }

    /** Atualiza a variante sob o dedo; [x] em coordenadas do overlay. */
    fun trackVariant(x: Float): Boolean {
        if (variants.isEmpty()) return false
        val index = ((x - variantPanel.left) / variantCell).toInt().coerceIn(0, variants.lastIndex)
        if (index != selectedVariant) {
            selectedVariant = index
            invalidate()
            return true
        }
        return false
    }

    fun selectedVariantText(): String? = variants.getOrNull(selectedVariant)

    fun hideVariants() {
        if (variants.isEmpty()) return
        variants = emptyList()
        invalidate()
    }

    fun hideAll() {
        hidePreview()
        hideVariants()
    }

    override fun onDraw(canvas: Canvas) {
        val key = previewKey
        val label = previewLabel
        if (key != null && label != null) {
            val w = key.width() + 12f * density
            val h = key.height() * 1.25f
            var left = key.centerX() - w / 2f
            left = left.coerceIn(2f * density, width - 2f * density - w)
            val top = (key.top - h - 4f * density).coerceAtLeast(0f)
            val rect = RectF(left, top, left + w, top + h)
            fill.color = palette.keyShadow
            canvas.drawRoundRect(rect.left, rect.top + density, rect.right, rect.bottom + density, radius, radius, fill)
            fill.color = palette.key
            canvas.drawRoundRect(rect, radius, radius, fill)
            text.color = palette.label
            text.textSize = 30f * density
            canvas.drawText(label, rect.centerX(), rect.centerY() - (text.descent() + text.ascent()) / 2f, text)
        }

        if (variants.isNotEmpty()) {
            fill.color = palette.keyShadow
            canvas.drawRoundRect(variantPanel.left, variantPanel.top + density, variantPanel.right, variantPanel.bottom + density, radius, radius, fill)
            fill.color = palette.key
            canvas.drawRoundRect(variantPanel, radius, radius, fill)
            variants.forEachIndexed { i, v ->
                val cell = RectF(
                    variantPanel.left + i * variantCell, variantPanel.top,
                    variantPanel.left + (i + 1) * variantCell, variantPanel.bottom,
                )
                if (i == selectedVariant) {
                    fill.color = palette.action
                    canvas.drawRoundRect(cell.left + 3 * density, cell.top + 3 * density, cell.right - 3 * density, cell.bottom - 3 * density, radius - 2 * density, radius - 2 * density, fill)
                }
                text.color = if (i == selectedVariant) palette.onAction else palette.label
                text.textSize = (if (v.length > 2) 15f else 24f) * density
                canvas.drawText(v, cell.centerX(), cell.centerY() - (text.descent() + text.ascent()) / 2f, text)
            }
        }
    }
}
