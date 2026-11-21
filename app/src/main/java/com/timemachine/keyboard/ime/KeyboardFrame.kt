package com.timemachine.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Raiz da visão do teclado.
 *
 * - [base] (faixa + teclas) define a altura. Fica na "área do teclado": a largura total menos os
 *   recuos laterais e, no modo uma mão, menos o painel lateral.
 * - [contentLayers] (linha do tempo, ajustes) cobrem exatamente a área do teclado.
 * - [side] é o painel do modo uma mão, no espaço que sobra ao lado.
 * - [fullLayers] (moldura de redimensionar, prévia das teclas) cobrem a raiz inteira, inclusive
 *   os recuos e a elevação, para as alças das bordas poderem ser arrastadas.
 *
 * Um FrameLayout comum não serve: filhas MATCH_PARENT recebem AT_MOST e pedem a tela toda.
 */
@SuppressLint("ViewConstructor")
class KeyboardFrame(
    context: Context,
    private val base: View,
    private val side: View,
    private val contentLayers: List<View>,
    private val fullLayers: List<View>,
) : ViewGroup(context) {

    /** Recuos e elevação em px. */
    var insetLeft = 0
    var insetRight = 0
    var bottomOffset = 0
    /** Largura do painel lateral (modo uma mão) em px; 0 = sem painel. */
    var sideWidth = 0
    var sideOnLeft = true

    /** Área do teclado em coordenadas da raiz, calculada no último onMeasure. */
    var contentLeft = 0
        private set
    var contentWidth = 0
        private set

    init {
        addView(base)
        addView(side)
        contentLayers.forEach(::addView)
        fullLayers.forEach(::addView)
    }

    fun applyGeometry(insetLeft: Int, insetRight: Int, bottomOffset: Int, sideWidth: Int, sideOnLeft: Boolean) {
        if (this.insetLeft == insetLeft && this.insetRight == insetRight && this.bottomOffset == bottomOffset &&
            this.sideWidth == sideWidth && this.sideOnLeft == sideOnLeft
        ) return
        this.insetLeft = insetLeft
        this.insetRight = insetRight
        this.bottomOffset = bottomOffset
        this.sideWidth = sideWidth
        this.sideOnLeft = sideOnLeft
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // A área do teclado nunca fica menor que metade da tela.
        val reserved = (insetLeft + insetRight + sideWidth).coerceAtMost(width / 2)
        val scale = if (insetLeft + insetRight + sideWidth == 0) 0f else reserved.toFloat() / (insetLeft + insetRight + sideWidth)
        val left = (insetLeft * scale).toInt()
        val sideW = (sideWidth * scale).toInt()
        contentWidth = width - reserved
        contentLeft = left + if (sideOnLeft) sideW else 0

        val exactContent = MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY)
        base.measure(exactContent, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val keyboardHeight = base.measuredHeight
        val exactHeight = MeasureSpec.makeMeasureSpec(keyboardHeight, MeasureSpec.EXACTLY)
        contentLayers.forEach { if (it.visibility != GONE) it.measure(exactContent, exactHeight) }
        if (side.visibility != GONE) side.measure(MeasureSpec.makeMeasureSpec(sideW, MeasureSpec.EXACTLY), exactHeight)

        val total = keyboardHeight + bottomOffset
        val exactWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val exactTotal = MeasureSpec.makeMeasureSpec(total, MeasureSpec.EXACTLY)
        fullLayers.forEach { if (it.visibility != GONE) it.measure(exactWidth, exactTotal) }
        setMeasuredDimension(width, total)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        base.layout(contentLeft, 0, contentLeft + base.measuredWidth, base.measuredHeight)
        contentLayers.forEach {
            if (it.visibility != GONE) it.layout(contentLeft, 0, contentLeft + it.measuredWidth, it.measuredHeight)
        }
        if (side.visibility != GONE) {
            val x = if (sideOnLeft) contentLeft - side.measuredWidth else contentLeft + contentWidth
            side.layout(x, 0, x + side.measuredWidth, side.measuredHeight)
        }
        fullLayers.forEach { if (it.visibility != GONE) it.layout(0, 0, it.measuredWidth, it.measuredHeight) }
    }
}
