package com.timemachine.keyboard.keyboard

import android.annotation.SuppressLint
import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import java.util.Locale
import kotlin.math.abs

/**
 * Teclas desenhadas em Canvas. Cuida de toques (multitoque com rollover), repetição do apagar,
 * variantes ao manter pressionado, o gesto da barra de espaço e a acessibilidade (TalkBack).
 * Não conhece o InputConnection: emite [KeyAction] para o serviço.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val overlay: KeyPreviewOverlay) : View(context) {

    var onAction: (KeyAction) -> Unit = {}

    var palette: KeyboardPalette = KeyboardPalette.Dark
        set(value) { field = value; invalidate() }

    var layout: KeyboardLayout? = null
        set(value) {
            if (field == value) return
            cancelAllPointers()
            field = value
            requestLayout()
            computeKeys()
            invalidate()
            touchHelper.invalidateRoot()
        }

    var shiftState: ShiftState = ShiftState.OFF
        set(value) {
            if (field == value) return
            field = value
            invalidate()
            touchHelper.invalidateRoot()
        }

    /** Distância vertical entre fileiras, em px. */
    var rowPitch: Float = 56f * resources.displayMetrics.density
        set(value) { if (field != value) { field = value; requestLayout() } }

    /** Fração da largura deixada vazia no centro (modo polegar); 0 = teclado contínuo. */
    var splitGap: Float = 0f
        set(value) { if (field != value) { field = value; computeKeys(); invalidate(); touchHelper.invalidateRoot() } }

    /** Tempo para manter pressionado (variantes, troca de teclado). */
    var longPressMs: Long = 350L

    var hapticsEnabled = true
    var soundEnabled = false
    var keyPreviewEnabled = true
    var spaceHoldOpensHistory = true

    private val density = resources.displayMetrics.density
    private val fontScale = resources.configuration.fontScale.coerceIn(1f, 1.15f)
    private val sidePadding = 3f * density
    private val topPadding = 6f * density
    private val bottomPadding = 6f * density
    private val hGap = 6f * density
    private val vGap = 11f * density
    private val corner = 6f * density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val cursorStep = 11f * density
    private val audio = context.getSystemService(AudioManager::class.java)

    private class KeyBox(val key: Key, val rect: RectF, val hit: RectF, val row: Int)
    private val boxes = ArrayList<KeyBox>()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val regular = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    // ---- toques -----------------------------------------------------------------------------

    private enum class Mode { NORMAL, VARIANTS, CURSOR, CONSUMED }

    private inner class Pointer(val id: Int, var box: KeyBox, val downX: Float) {
        var mode = Mode.NORMAL
        var anchorX = downX
        val longPress = Runnable { onLongPress(this) }
    }

    private val pointers = LinkedHashMap<Int, Pointer>()
    /** Repete a tecla segurada (apagar e setas). */
    private var repeating: KeyAction? = null
    private val repeatKey = object : Runnable {
        override fun run() {
            repeating?.let(onAction)
            postDelayed(this, REPEAT_INTERVAL_MS)
        }
    }

    private val touchHelper = KeyAccessibility()

    /**
     * Ao soltar, o destaque da tecla some em ~90 ms (o toque em si continua instantâneo).
     * Escala com "Escala de duração da animação" do sistema; com animações desligadas, some na hora.
     */
    private val releaseFadeMs: Long = (RELEASE_FADE_MS * Settings.Global.getFloat(
        context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
    )).toLong()
    private val releasedAt = HashMap<KeyBox, Long>()
    private val colorMix = ArgbEvaluator()

    init {
        ViewCompat.setAccessibilityDelegate(this, touchHelper)
        isHapticFeedbackEnabled = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rows = layout?.rows?.size ?: 4
        val height = (topPadding + rows * rowPitch + bottomPadding).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        computeKeys()
    }

    private fun computeKeys() {
        boxes.clear()
        val rows = layout?.rows ?: return
        if (width == 0) return
        val gap = (width - 2 * sidePadding) * splitGap
        val unit = (width - 2 * sidePadding - gap) / 10f
        rows.forEachIndexed { r, row ->
            val units = row.sumOf { it.width.toDouble() }.toFloat()
            var x = sidePadding + (10f - units) * unit / 2f
            val top = topPadding + r * rowPitch
            // Polegar: a barra de espaço atravessa o vão; nas outras fileiras o vão fica no meio.
            val spaceIndex = row.indexOfFirst { it.action == KeyAction.Space }
            var acc = 0f
            val splitAfter = if (gap > 0f && spaceIndex < 0) row.indexOfFirst { acc += it.width; acc >= units / 2f } else -1
            row.forEachIndexed { i, key ->
                val w = key.width * unit + if (i == spaceIndex) gap else 0f
                val rect = RectF(x + hGap / 2, top + vGap / 2, x + w - hGap / 2, top + rowPitch - vGap / 2)
                val hit = RectF(
                    if (i == 0) 0f else x - (if (i == splitAfter + 1 && splitAfter >= 0) gap / 2f else 0f),
                    if (r == 0) 0f else top,
                    if (i == row.lastIndex) width.toFloat() else x + w + (if (i == splitAfter) gap / 2f else 0f),
                    if (r == rows.lastIndex) height.toFloat() else top + rowPitch,
                )
                boxes += KeyBox(key, rect, hit, r)
                x += w
                if (i == splitAfter) x += gap
            }
        }
    }

    private fun boxAt(x: Float, y: Float): KeyBox? =
        boxes.firstOrNull { it.hit.contains(x, y) }
            ?: boxes.minByOrNull { val dx = it.rect.centerX() - x; val dy = it.rect.centerY() - y; dx * dx + dy * dy }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val box = boxAt(event.getX(index), event.getY(index)) ?: return true
                // rollover: um novo dedo confirma imediatamente as teclas de caractere pendentes
                pointers.values.filter { it.mode == Mode.NORMAL && it.box.key.action is KeyAction.Text }.forEach {
                    removeCallbacks(it.longPress)
                    fire(it.box.key)
                    it.mode = Mode.CONSUMED
                }
                val pointer = Pointer(event.getPointerId(index), box, event.getX(index))
                pointers[pointer.id] = pointer
                onPress(pointer)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pointer = pointers[event.getPointerId(i)] ?: continue
                    onMove(pointer, event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pointer = pointers.remove(event.getPointerId(event.actionIndex)) ?: return true
                onRelease(pointer)
            }
            MotionEvent.ACTION_CANCEL -> cancelAllPointers()
        }
        return true
    }

    private fun onPress(p: Pointer) {
        val key = p.box.key
        feedback(key)
        invalidate()
        when (key.action) {
            KeyAction.Backspace, is KeyAction.Arrow -> {
                onAction(key.action)
                p.mode = Mode.CONSUMED
                repeating = key.action
                removeCallbacks(repeatKey)
                postDelayed(repeatKey, REPEAT_START_MS)
            }
            KeyAction.Shift -> {
                onAction(KeyAction.Shift)
                p.mode = Mode.CONSUMED
            }
            KeyAction.Space -> if (spaceHoldOpensHistory) postDelayed(p.longPress, longPressMs + 100)
            is KeyAction.Text -> {
                showPreview(p.box)
                if (key.variants.size > 1) postDelayed(p.longPress, longPressMs)
            }
            else -> Unit
        }
    }

    private fun onLongPress(p: Pointer) {
        if (p.mode != Mode.NORMAL || pointers[p.id] !== p) return
        when (p.box.key.action) {
            KeyAction.Space -> {
                p.mode = Mode.CONSUMED
                performHaptic(HapticFeedbackConstants.LONG_PRESS)
                onAction(KeyAction.OpenHistory)
            }
            is KeyAction.Text -> {
                p.mode = Mode.VARIANTS
                overlay.showVariants(toOverlay(p.box.rect), p.box.key.variants.map(::applyCase))
                performHaptic(HapticFeedbackConstants.LONG_PRESS)
            }
            else -> Unit
        }
    }

    private fun onMove(p: Pointer, x: Float, y: Float) {
        when (p.mode) {
            Mode.VARIANTS -> if (overlay.trackVariant(x + overlayOffsetX())) performHaptic(HapticFeedbackConstants.CLOCK_TICK)
            Mode.CURSOR -> {
                val steps = ((x - p.anchorX) / cursorStep).toInt()
                if (steps != 0) {
                    p.anchorX += steps * cursorStep
                    onAction(KeyAction.MoveCursor(steps))
                    performHaptic(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                }
            }
            Mode.NORMAL -> when (p.box.key.action) {
                KeyAction.Space -> if (abs(x - p.downX) > touchSlop) {
                    // arrastar a barra de espaço move o cursor; cancela o "segurar"
                    removeCallbacks(p.longPress)
                    p.mode = Mode.CURSOR
                    p.anchorX = x
                }
                is KeyAction.Text -> {
                    val now = boxAt(x, y)
                    if (now != null && now !== p.box && now.key.action is KeyAction.Text) {
                        removeCallbacks(p.longPress)
                        p.box = now
                        showPreview(now)
                        invalidate()
                        if (now.key.variants.size > 1) postDelayed(p.longPress, longPressMs)
                    }
                }
                else -> Unit
            }
            Mode.CONSUMED -> Unit
        }
    }

    private fun onRelease(p: Pointer) {
        removeCallbacks(p.longPress)
        if (releaseFadeMs > 0) releasedAt[p.box] = SystemClock.uptimeMillis()
        if (p.box.key.action == repeating) {
            removeCallbacks(repeatKey)
            repeating = null
        }
        when (p.mode) {
            Mode.VARIANTS -> {
                overlay.selectedVariantText()?.let { onAction(KeyAction.Text(it)) }
                overlay.hideVariants()
            }
            Mode.NORMAL -> fire(p.box.key)
            Mode.CURSOR, Mode.CONSUMED -> Unit
        }
        if (pointers.values.none { it.box.key.action is KeyAction.Text }) overlay.hidePreview()
        invalidate()
    }

    private fun cancelAllPointers() {
        releasedAt.clear()
        pointers.values.forEach { removeCallbacks(it.longPress) }
        pointers.clear()
        removeCallbacks(repeatKey)
        repeating = null
        overlay.hideAll()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelAllPointers()
        super.onDetachedFromWindow()
    }

    /** Executa a tecla (toque solto, rollover ou ação de acessibilidade). */
    private fun fire(key: Key) {
        when (val action = key.action) {
            is KeyAction.Text -> onAction(KeyAction.Text(applyCase(action.text)))
            else -> onAction(action)
        }
    }

    private fun applyCase(s: String): String =
        if (shiftState != ShiftState.OFF && layout?.page == KeyboardPage.LETTERS) s.uppercase(LOCALE) else s

    private fun feedback(key: Key) {
        performHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
        if (soundEnabled) {
            val effect = when (key.action) {
                KeyAction.Backspace -> AudioManager.FX_KEYPRESS_DELETE
                KeyAction.Space -> AudioManager.FX_KEYPRESS_SPACEBAR
                KeyAction.Enter -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio?.playSoundEffect(effect, -1f)
        }
    }

    private fun performHaptic(constant: Int) {
        if (hapticsEnabled) performHapticFeedback(constant)
    }

    private fun showPreview(box: KeyBox) {
        if (!keyPreviewEnabled) return
        val label = box.key.label ?: return
        overlay.showPreview(toOverlay(box.rect), applyCase(label))
    }

    private val locationSelf = IntArray(2)
    private val locationOverlay = IntArray(2)
    private fun overlayOffsetX(): Float {
        getLocationInWindow(locationSelf); overlay.getLocationInWindow(locationOverlay)
        return (locationSelf[0] - locationOverlay[0]).toFloat()
    }

    private fun toOverlay(rect: RectF): RectF {
        getLocationInWindow(locationSelf)
        overlay.getLocationInWindow(locationOverlay)
        return RectF(rect).apply {
            offset((locationSelf[0] - locationOverlay[0]).toFloat(), (locationSelf[1] - locationOverlay[1]).toFloat())
        }
    }

    // ---- desenho ----------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val pressed = pointers.values.mapTo(HashSet()) { it.box }
        val now = SystemClock.uptimeMillis()
        releasedAt.entries.removeAll { now - it.value >= releaseFadeMs || it.key in pressed || it.key !in boxes }
        for (box in boxes) {
            val fade = releasedAt[box]?.let { 1f - (now - it).toFloat() / releaseFadeMs } ?: 0f
            drawKey(canvas, box, box in pressed, fade)
        }
        if (releasedAt.isNotEmpty()) postInvalidateOnAnimation()
    }

    /** [fade] de 1 a 0: quanto ainda resta do destaque de "pressionada" depois de soltar. */
    private fun drawKey(canvas: Canvas, box: KeyBox, isPressed: Boolean, fade: Float = 0f) {
        val key = box.key
        val r = box.rect
        val shiftActive = key.action == KeyAction.Shift && shiftState != ShiftState.OFF
        val pressedColor = if (key.style == KeyStyle.ACTION) palette.labelSecondary else palette.keyPressed
        val restColor = when {
            shiftActive -> palette.key
            key.style == KeyStyle.FUNCTION -> palette.keyFunction
            key.style == KeyStyle.ACTION -> palette.action
            else -> palette.key
        }
        val background = when {
            isPressed -> pressedColor
            fade > 0f -> colorMix.evaluate(fade, restColor, pressedColor) as Int
            else -> restColor
        }
        fill.color = palette.keyShadow
        canvas.drawRoundRect(r.left, r.top + density, r.right, r.bottom + density, corner, corner, fill)
        fill.color = background
        canvas.drawRoundRect(r, corner, corner, fill)

        val ink = if (key.style == KeyStyle.ACTION) palette.onAction else palette.label
        val glyph = when {
            key.action == KeyAction.Shift -> when (shiftState) {
                ShiftState.OFF -> Glyph.SHIFT
                ShiftState.ONCE -> Glyph.SHIFT_ON
                ShiftState.LOCKED -> Glyph.CAPS_LOCK
            }
            else -> key.glyph
        }
        if (glyph != null) {
            Glyphs.draw(canvas, glyph, r.centerX(), r.centerY(), minOf(22f * density, r.height() * 0.6f), ink)
            return
        }
        val label = key.label ?: return
        val isChar = key.style == KeyStyle.CHARACTER
        val display = if (isChar) applyCase(label) else label
        text.typeface = if (isChar) regular else medium
        text.color = if (key.style == KeyStyle.SPACE) palette.labelSecondary else ink
        text.textSize = density * fontScale * when {
            key.style == KeyStyle.SPACE -> 13f
            isChar && display.length == 1 -> 23f
            isChar -> 17f
            else -> 15f
        }
        canvas.drawText(display, r.centerX(), r.centerY() - (text.descent() + text.ascent()) / 2f, text)
    }

    // ---- acessibilidade ---------------------------------------------------------------------

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        touchHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    private fun describe(key: Key): String = when (key.action) {
        KeyAction.Shift -> when (shiftState) {
            ShiftState.OFF -> "Maiúsculas"
            ShiftState.ONCE -> "Maiúsculas, ativado"
            ShiftState.LOCKED -> "Caps Lock, ativado"
        }
        is KeyAction.Text -> key.description ?: applyCase(key.label ?: "")
        else -> key.description ?: key.label ?: ""
    }

    private inner class KeyAccessibility : ExploreByTouchHelper(this) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val i = boxes.indexOfFirst { it.hit.contains(x, y) }
            return if (i >= 0) i else INVALID_ID
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            boxes.indices.forEach { virtualViewIds += it }
        }

        override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
            val box = boxes.getOrNull(virtualViewId)
            if (box == null) {
                node.contentDescription = ""
                node.setBoundsInParent(Rect(0, 0, 1, 1))
                return
            }
            node.contentDescription = describe(box.key)
            node.className = "android.widget.Button"
            node.setBoundsInParent(Rect().also { box.rect.roundOut(it) })
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        }

        override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            val box = boxes.getOrNull(virtualViewId) ?: return false
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
            fire(box.key)
            return true
        }
    }

    private companion object {
        val LOCALE: Locale = Locale.forLanguageTag("pt-BR")
        const val RELEASE_FADE_MS = 90f
        const val REPEAT_START_MS = 400L
        const val REPEAT_INTERVAL_MS = 55L
    }
}
