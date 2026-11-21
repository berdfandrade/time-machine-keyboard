package com.timemachine.keyboard.ime

import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.timemachine.keyboard.TimeMachineApp
import com.timemachine.keyboard.emoji.EmojiPanel
import com.timemachine.keyboard.history.FieldKey
import com.timemachine.keyboard.predict.WordPredictor
import com.timemachine.keyboard.history.HistoryEngine
import com.timemachine.keyboard.keyboard.EnterAction
import com.timemachine.keyboard.keyboard.FieldVariant
import com.timemachine.keyboard.keyboard.KeyAction
import com.timemachine.keyboard.keyboard.KeyPreviewOverlay
import com.timemachine.keyboard.keyboard.KeyboardPage
import com.timemachine.keyboard.keyboard.KeyboardPalette
import com.timemachine.keyboard.keyboard.KeyboardView
import com.timemachine.keyboard.keyboard.Layouts
import com.timemachine.keyboard.keyboard.ShiftController
import com.timemachine.keyboard.privacy.PrivacyPolicy
import com.timemachine.keyboard.restoration.RestorationEngine
import com.timemachine.keyboard.settings.KeyboardEdges
import com.timemachine.keyboard.settings.KeyboardHeight
import com.timemachine.keyboard.settings.KeyboardMode
import com.timemachine.keyboard.settings.Side
import com.timemachine.keyboard.toolbar.Edge
import com.timemachine.keyboard.toolbar.ModesPanel
import com.timemachine.keyboard.toolbar.OneHandedSidePanel
import com.timemachine.keyboard.settings.KeyboardSettings
import com.timemachine.keyboard.settings.MainActivity
import com.timemachine.keyboard.settings.ThemeMode
import com.timemachine.keyboard.toolbar.QuickSettingsPanel
import com.timemachine.keyboard.toolbar.ResizeOverlay
import com.timemachine.keyboard.timeline.StripBar
import com.timemachine.keyboard.timeline.TimelinePanel
import com.timemachine.keyboard.timeline.Wording
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.BreakIterator
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * O teclado. Lê e escreve no campo só pelo InputConnection, e só enquanto a visão do teclado
 * está aberta (entre onStartInputView e onFinishInputView). Não usa AccessibilityService, não
 * tem permissão de Internet e não registra texto em log.
 */
class TimeMachineIME : InputMethodService() {

    private val app get() = application as TimeMachineApp
    private val history: HistoryEngine get() = app.history
    private val owner = ImeViewTreeOwner()
    private val scope = MainScope()
    private lateinit var restoration: RestorationEngine

    private var settings = KeyboardSettings()
    private val palette = mutableStateOf(KeyboardPalette.Dark)
    private val originLabel = mutableStateOf<String?>(null)
    /** Muda a cada abertura da linha do tempo, para ela recomeçar na versão anterior à atual. */
    private val timelineOpening = mutableIntStateOf(0)
    private val notice = mutableStateOf<String?>(null)
    private val settingsState = mutableStateOf(KeyboardSettings())
    /** Sugestões de palavras exibidas na faixa (a melhor primeiro). */
    private val suggestions = mutableStateOf<List<String>>(emptyList())
    private val emojiRecents = mutableStateOf<List<String>>(emptyList())

    /** O que ocupa o lugar das teclas. */
    private enum class Panel { NONE, TIMELINE, SETTINGS, MODES, EMOJI }
    private val panel = mutableStateOf(Panel.NONE)

    /** Tamanho em edição na moldura de redimensionar (ainda não salvo); null = fora do modo. */
    private data class Geometry(val heightPercent: Float, val insetLeftDp: Float, val insetRightDp: Float, val bottomDp: Float)
    private val editing = mutableStateOf<Geometry?>(null)
    /** Área do teclado na raiz (esquerda, largura, altura), para a moldura coincidir com ela. */
    private val frameRect = mutableStateOf(Triple(0, 0, 0))

    private var root: View? = null
    private var keyboardColumn: LinearLayout? = null
    private var keyboardView: KeyboardView? = null
    private var overlay: KeyPreviewOverlay? = null
    private var panelView: ComposeView? = null
    private var resizeView: ComposeView? = null
    private var sidePanelView: ComposeView? = null

    private val shift = ShiftController()
    private var page = KeyboardPage.LETTERS
    private var variant = FieldVariant.TEXT
    private var enter = EnterAction(EnterAction.Kind.NEWLINE)
    private var fieldKey: FieldKey? = null
    private var selectionStart = 0
    private var selectionEnd = 0
    private var lastWasDelete = false
    private var inputViewActive = false

    override fun onCreate() {
        super.onCreate()
        owner.onCreate()
        restoration = RestorationEngine(history)
        app.loadLanguageData()
        settings = app.settings.settings.value
        scope.launch {
            app.settings.settings.collect {
                val privacyChanged = it.privateMode != settings.privateMode || it.excludedApps != settings.excludedApps
                settings = it
                settingsState.value = it
                // Mudança de modo, lado ou tamanho fora da moldura: desliza até a nova forma.
                applySettings(animate = !isResizing)
                renderKeyboard()
                if (privacyChanged && inputViewActive) currentInputEditorInfo?.let { info -> startCapture(info, restarting = false) }
            }
        }
    }

    override fun onDestroy() {
        history.end()
        owner.onDestroy()
        scope.cancel()
        super.onDestroy()
    }

    /** Sem modo tela cheia em paisagem: o app continua visível acima do teclado. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        val context = this
        val overlay = KeyPreviewOverlay(context)
        val keyboard = KeyboardView(context, overlay).apply { onAction = ::onKeyAction }
        val strip = ComposeView(context).apply {
            setContent {
                val state by history.state.collectAsState()
                StripBar(
                    state = state,
                    palette = palette.value,
                    notice = notice.value,
                    now = System::currentTimeMillis,
                    onOpenTimeline = ::openTimeline,
                    onOpenSettings = { openPanel(Panel.SETTINGS) },
                    onUndoRestore = ::restore,
                    suggestions = suggestions.value,
                    onPick = ::pickSuggestion,
                )
            }
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(strip, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(keyboard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        val panels = ComposeView(context).apply {
            visibility = View.GONE
            setContent {
                val state by history.state.collectAsState()
                AnimatedContent(
                    targetState = panel.value,
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 16 }) togetherWith fadeOut(tween(120))
                    },
                    label = "panel",
                ) { current ->
                when (current) {
                    Panel.TIMELINE -> key(timelineOpening.intValue) {
                        TimelinePanel(
                            state = state,
                            palette = palette.value,
                            originLabel = originLabel.value,
                            now = System::currentTimeMillis,
                            onRestore = ::restore,
                            onDelete = { history.deleteSnapshot(it) },
                            onClear = { history.clearSession() },
                            onClose = ::closePanel,
                            onTick = { haptic(HapticFeedbackConstants.CLOCK_TICK) },
                        )
                    }
                    Panel.SETTINGS -> QuickSettingsPanel(
                        settings = settingsState.value,
                        palette = palette.value,
                        onToggleHaptics = { app.settings.update { it.copy(haptics = !it.haptics) } },
                        onToggleSound = { app.settings.update { it.copy(sound = !it.sound) } },
                        onTogglePrivate = { app.settings.update { it.copy(privateMode = !it.privateMode) } },
                        onCycleTheme = { app.settings.update { it.copy(theme = ThemeMode.entries[(it.theme.ordinal + 1) % ThemeMode.entries.size]) } },
                        onModes = { openPanel(Panel.MODES) },
                        onResize = ::startResize,
                        onToggleNumberRow = { app.settings.update { it.copy(numberRow = !it.numberRow) } },
                        onOpenSettings = ::openSettingsApp,
                        onClose = ::closePanel,
                    )
                    Panel.MODES -> ModesPanel(
                        mode = settingsState.value.mode,
                        palette = palette.value,
                        onSelect = { m -> app.settings.update { it.copy(mode = m) }; closePanel() },
                        onClose = ::closePanel,
                    )
                    Panel.EMOJI -> {
                        val categories by app.emojis.collectAsState()
                        EmojiPanel(
                            categories = categories,
                            recents = emojiRecents.value,
                            palette = palette.value,
                            onEmoji = ::commitEmoji,
                            onBackspace = ::backspace,
                            onSpace = { commit(" ") },
                            onAbc = ::closePanel,
                        )
                    }
                    Panel.NONE -> Unit
                }
                }
            }
        }
        val resize = ComposeView(context).apply {
            visibility = View.GONE
            setContent {
                val g = editing.value ?: return@setContent
                val (left, width, height) = frameRect.value
                ResizeOverlay(
                    frameLeftPx = left,
                    frameWidthPx = width,
                    frameHeightPx = height,
                    heightPercent = KeyboardHeight.clamp(g.heightPercent.roundToStep()),
                    palette = palette.value,
                    onDrag = ::dragEdge,
                    onDragEnd = ::snapEdit,
                    onReset = { editing.value = Geometry(KeyboardHeight.DEFAULT.toFloat(), 0f, 0f, 0f); applyGeometry(animate = true) },
                    onDone = ::finishResize,
                )
            }
        }
        val side = ComposeView(context).apply {
            visibility = View.GONE
            setContent {
                val s = settingsState.value
                OneHandedSidePanel(
                    onLeft = s.oneHandedSide == Side.RIGHT,
                    palette = palette.value,
                    onSwap = { app.settings.update { it.copy(oneHandedSide = if (it.oneHandedSide == Side.RIGHT) Side.LEFT else Side.RIGHT) } },
                    onExpand = { app.settings.update { it.copy(mode = KeyboardMode.FULL) } },
                )
            }
        }
        // Painéis cobrem a área do teclado; moldura e prévia cobrem a raiz inteira.
        val frame = KeyboardFrame(context, column, side, listOf(panels), listOf(resize, overlay))
        frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val rect = Triple(frame.contentLeft, frame.contentWidth, column.height)
            if (frameRect.value != rect) frameRect.value = rect
        }
        owner.attachTo(frame)
        window.window?.decorView?.let(owner::attachTo)

        root = frame
        applied = null // visão nova: a primeira geometria é aplicada sem animação
        keyboardColumn = column
        keyboardView = keyboard
        this.overlay = overlay
        panelView = panels
        resizeView = resize
        sidePanelView = side
        applySettings()
        renderKeyboard()
        return frame
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        inputViewActive = true
        page = Layouts.pageFor(info.inputType)
        variant = Layouts.variantFor(info.inputType)
        enter = EnterAction.resolve(info.inputType, info.imeOptions, info.actionLabel)
        selectionStart = info.initialSelStart
        selectionEnd = info.initialSelEnd
        lastWasDelete = false
        if (!restarting) shift.reset()
        closePanel(animate = false)
        startCapture(info, restarting)
        updateAutoCaps()
        renderKeyboard()
        updateSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        inputViewActive = false
        suggestions.value = emptyList()
        cancelResize()
        closePanel(animate = false)
        history.end()
        fieldKey = null
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selectionStart = newSelStart
        selectionEnd = newSelEnd
        if (!inputViewActive) return
        history.onFieldChanged()
        updateAutoCaps()
        renderShift()
        updateSuggestions()
    }

    // ---- captura ------------------------------------------------------------------------------

    private fun startCapture(info: EditorInfo, restarting: Boolean) {
        val packageName = info.packageName.orEmpty()
        val reason = PrivacyPolicy.evaluate(info.inputType, info.imeOptions, packageName, settings.privateMode, settings.excludedApps)
        if (reason != null) {
            fieldKey = null
            originLabel.value = null
            history.block(reason)
            return
        }
        val key = FieldKey(packageName, info.fieldId, info.inputType and (InputType.TYPE_MASK_CLASS or InputType.TYPE_MASK_VARIATION))
        fieldKey = key
        originLabel.value = appLabel(packageName)
        if (restarting && history.activeKey == key) {
            history.onFieldChanged()
        } else {
            history.begin(key) { FieldReader.read(currentInputConnection) }
        }
    }

    private fun appLabel(packageName: String): String? = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (_: Exception) {
        null
    }

    // ---- teclas -------------------------------------------------------------------------------

    private fun onKeyAction(action: KeyAction) {
        when (action) {
            is KeyAction.Text -> commit(action.text)
            KeyAction.Space -> commit(" ")
            KeyAction.Backspace -> backspace()
            KeyAction.Enter -> performEnter()
            KeyAction.Shift -> { shift.onShiftTap(SystemClock.uptimeMillis()); renderShift() }
            is KeyAction.Page -> { page = action.page; renderKeyboard() }
            KeyAction.Emoji -> {
                emojiRecents.value = app.emojiRecents.get()
                openPanel(Panel.EMOJI)
            }
            KeyAction.OpenHistory -> openTimeline()
            is KeyAction.MoveCursor -> moveCursor(action.steps)
            is KeyAction.Arrow -> sendDownUpKeyEvents(action.keyCode)
        }
    }

    private val isRawKeyField: Boolean
        get() = (currentInputEditorInfo?.inputType ?: 0) and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL

    private fun commit(text: String) {
        val ic = currentInputConnection ?: return
        // Fim de palavra (espaço, pontuação): aprende a palavra, se permitido neste campo.
        if (text.isNotEmpty() && !WordPredictor.isWordChar(text[0])) learnWordBeforeCursor(ic)
        // Digitar sobre uma seleção a substitui: guarda a versão antes.
        if (selectionStart != selectionEnd) history.captureNow()
        lastWasDelete = false
        if (isRawKeyField) text.forEach(::sendKeyChar) else ic.commitText(text, 1)
        history.onOwnEdit(text.length)
        if (page == KeyboardPage.LETTERS) {
            shift.onCharacterTyped()
            renderShift()
        } else if (text == " " && (page == KeyboardPage.SYMBOLS || page == KeyboardPage.SYMBOLS_ALT)) {
            page = KeyboardPage.LETTERS
            renderKeyboard()
        }
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        // O primeiro apagar de uma sequência é destrutivo: grava a versão antes dele.
        if (!lastWasDelete) history.captureNow()
        lastWasDelete = true
        when {
            isRawKeyField -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            selectionStart != selectionEnd -> ic.commitText("", 1)
            else -> {
                val before = ic.getTextBeforeCursor(GRAPHEME_LOOKBEHIND, 0)?.toString().orEmpty()
                if (before.isEmpty()) sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                // apaga um caractere visível inteiro: emoji com tom de pele ou família (ZWJ) sai de uma vez
                else ic.deleteSurroundingText(lastGraphemeLength(before), 0)
            }
        }
        history.onOwnEdit(1)
    }

    private fun performEnter() {
        val ic = currentInputConnection ?: return
        // Enviar costuma limpar o campo: guarda a mensagem antes.
        history.captureNow()
        learnWordBeforeCursor(ic)
        lastWasDelete = false
        when (enter.kind) {
            EnterAction.Kind.NEWLINE -> if (isRawKeyField) sendKeyChar('\n') else { ic.commitText("\n", 1); history.onOwnEdit(1) }
            EnterAction.Kind.ENTER_KEY -> sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            EnterAction.Kind.EDITOR_ACTION -> ic.performEditorAction(enter.actionId)
        }
    }

    private fun moveCursor(steps: Int) {
        val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(abs(steps)) { sendDownUpKeyEvents(code) }
    }

    private fun updateAutoCaps() {
        if (page != KeyboardPage.LETTERS) return
        val info = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        if (info.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return
        shift.applyAutoCaps(ic.getCursorCapsMode(info.inputType) != 0)
    }

    // ---- previsão e emojis --------------------------------------------------------------------

    /** Sugestões permitidas: campo de texto comum, não senha, sem pedido do app para não sugerir. */
    private fun predictionsAllowed(): Boolean {
        val info = currentInputEditorInfo ?: return false
        val type = info.inputType
        val inputClass = type and InputType.TYPE_MASK_CLASS
        return settings.predictions && inputViewActive &&
            inputClass == InputType.TYPE_CLASS_TEXT &&
            variant == FieldVariant.TEXT &&
            type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0 &&
            !PrivacyPolicy.isPassword(inputClass, type and InputType.TYPE_MASK_VARIATION)
    }

    /** Aprender só onde a gravação do histórico também é permitida (fieldKey != null). */
    private fun learningAllowed(): Boolean = settings.learnWords && fieldKey != null && predictionsAllowed()

    private fun updateSuggestions() {
        val ic = currentInputConnection
        if (ic == null || !predictionsAllowed() || selectionStart != selectionEnd) {
            if (suggestions.value.isNotEmpty()) suggestions.value = emptyList()
            return
        }
        val after = ic.getTextAfterCursor(1, 0)?.toString().orEmpty()
        if (after.isNotEmpty() && WordPredictor.isWordChar(after[0])) {
            // cursor no meio de uma palavra: trocar só a metade seria estranho
            if (suggestions.value.isNotEmpty()) suggestions.value = emptyList()
            return
        }
        val (current, previous) = WordPredictor.splitContext(ic.getTextBeforeCursor(CONTEXT_CHARS, 0)?.toString().orEmpty())
        val next = app.predictor.suggest(current, previous)
        if (next != suggestions.value) suggestions.value = next
    }

    private fun learnWordBeforeCursor(ic: android.view.inputmethod.InputConnection) {
        if (!learningAllowed()) return
        val (word, previous) = WordPredictor.splitContext(ic.getTextBeforeCursor(CONTEXT_CHARS, 0)?.toString().orEmpty())
        if (word.isNotEmpty()) app.predictor.learn(word, previous)
    }

    /** Troca a palavra que está sendo digitada pela sugestão e põe um espaço. */
    private fun pickSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val (current, previous) = WordPredictor.splitContext(ic.getTextBeforeCursor(CONTEXT_CHARS, 0)?.toString().orEmpty())
        ic.beginBatchEdit()
        if (current.isNotEmpty()) ic.deleteSurroundingText(current.length, 0)
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
        lastWasDelete = false
        history.onOwnEdit(word.length + 1)
        if (learningAllowed()) app.predictor.learn(word, previous)
        if (page == KeyboardPage.LETTERS) {
            shift.onCharacterTyped()
            renderShift()
        }
    }

    private fun commitEmoji(emoji: String) {
        commit(emoji)
        emojiRecents.value = app.emojiRecents.add(emoji)
    }

    /** Tamanho, em unidades UTF-16, do último caractere visível (grafema) de [text]. */
    private fun lastGraphemeLength(text: String): Int {
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val end = it.last()
        val start = it.previous()
        return if (start == BreakIterator.DONE) 1 else (end - start).coerceAtLeast(1)
    }

    // ---- linha do tempo -----------------------------------------------------------------------

    private fun openTimeline() {
        history.captureNow()
        timelineOpening.intValue++
        openPanel(Panel.TIMELINE)
    }

    /** Painel visível (ou entrando). Separado de [panel] porque o conteúdo some só no fim da saída. */
    private var panelShown = false

    private fun openPanel(target: Panel) {
        val view = panelView ?: return
        val column = keyboardColumn ?: return
        cancelResize()
        overlay?.hideAll()
        if (target == Panel.TIMELINE) haptic(HapticFeedbackConstants.CONTEXT_CLICK)
        panel.value = target // já aberto: o AnimatedContent faz a troca entre painéis
        if (panelShown) return
        panelShown = true
        view.animate().cancel()
        column.animate().cancel()
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationY = dp(18f)
        view.animate().alpha(1f).translationY(0f).setDuration(PANEL_IN_MS).setInterpolator(EASE_OUT)
            .withEndAction { if (panelShown) column.visibility = View.INVISIBLE }.start()
        column.animate().alpha(0f).setDuration(PANEL_IN_MS).start()
    }

    /** Fecha o painel. [animate] = false quando o campo muda (nada deve ficar animando por cima). */
    private fun closePanel(animate: Boolean = true) {
        val view = panelView ?: return
        val column = keyboardColumn ?: return
        if (!panelShown) return
        panelShown = false
        view.animate().cancel()
        column.animate().cancel()
        column.visibility = View.VISIBLE
        val finish = {
            if (!panelShown) {
                view.visibility = View.GONE
                view.translationY = 0f
                panel.value = Panel.NONE
            }
        }
        if (!animate) {
            column.alpha = 1f
            finish()
            return
        }
        column.animate().alpha(1f).setDuration(PANEL_OUT_MS).setInterpolator(EASE_OUT).start()
        view.animate().alpha(0f).translationY(dp(12f)).setDuration(PANEL_OUT_MS).setInterpolator(EASE_IN)
            .withEndAction(finish).start()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private fun openSettingsApp() {
        closePanel(animate = false)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        requestHideSelf(0)
    }

    // ---- redimensionar ------------------------------------------------------------------------

    private val isResizing: Boolean get() = editing.value != null

    private fun startResize() {
        closePanel()
        editing.value = Geometry(
            settings.keyboardHeight.toFloat(),
            settings.insetLeftDp.toFloat(),
            settings.insetRightDp.toFloat(),
            settings.bottomOffsetDp.toFloat(),
        )
        resizeView?.let { v ->
            v.animate().cancel()
            v.alpha = 0f
            v.visibility = View.VISIBLE
            v.animate().alpha(1f).setDuration(PANEL_IN_MS).setInterpolator(EASE_OUT).start()
        }
        applyGeometry()
    }

    private fun hideResizeOverlay() {
        resizeView?.let { v ->
            v.animate().cancel()
            v.animate().alpha(0f).setDuration(PANEL_OUT_MS).setInterpolator(EASE_IN)
                .withEndAction { if (!isResizing) v.visibility = View.GONE }.start()
        }
    }

    /**
     * Arrasto de uma borda da moldura, em px de tela. Cada borda segue o dedo 1:1: em cima a
     * altura, embaixo a elevação, dos lados os recuos.
     */
    private fun dragEdge(edge: Edge, dx: Float, dy: Float) {
        val g = editing.value ?: return
        val density = resources.displayMetrics.density
        val next = when (edge) {
            Edge.TOP -> {
                val rows = keyboardView?.layout?.rows?.size ?: 4
                val pxPerPercent = rows * rowPitchFor(1f)
                g.copy(heightPercent = (g.heightPercent - dy / pxPerPercent).coerceIn(KeyboardHeight.MIN.toFloat(), KeyboardHeight.MAX.toFloat()))
            }
            Edge.BOTTOM -> g.copy(bottomDp = (g.bottomDp - dy / density).coerceIn(0f, KeyboardEdges.MAX_BOTTOM_DP.toFloat()))
            Edge.LEFT -> g.copy(insetLeftDp = (g.insetLeftDp + dx / density).coerceIn(0f, KeyboardEdges.MAX_SIDE_DP.toFloat()))
            Edge.RIGHT -> g.copy(insetRightDp = (g.insetRightDp - dx / density).coerceIn(0f, KeyboardEdges.MAX_SIDE_DP.toFloat()))
        }
        if (next.heightPercent.roundToStep() != g.heightPercent.roundToStep()) haptic(HapticFeedbackConstants.CLOCK_TICK)
        editing.value = next
        applyGeometry()
    }

    /** Ao soltar, a altura encaixa no passo de 5%. */
    private fun snapEdit() {
        val g = editing.value ?: return
        editing.value = g.copy(heightPercent = KeyboardHeight.clamp(g.heightPercent.roundToStep()).toFloat())
        applyGeometry()
    }

    private fun finishResize() {
        val g = editing.value ?: return
        editing.value = null
        hideResizeOverlay()
        app.settings.update {
            it.copy(
                keyboardHeight = KeyboardHeight.clamp(g.heightPercent.roundToStep()),
                insetLeftDp = g.insetLeftDp.roundToInt(),
                insetRightDp = g.insetRightDp.roundToInt(),
                bottomOffsetDp = g.bottomDp.roundToInt(),
            )
        }
        applySettings()
    }

    /** Sai do modo redimensionar sem salvar. */
    private fun cancelResize() {
        if (!isResizing) return
        editing.value = null
        hideResizeOverlay()
        applyGeometry(animate = true)
    }

    /** Geometria efetivamente desenhada, em px (e o lado do painel de uma mão). */
    private data class Applied(
        val insetLeft: Float, val insetRight: Float, val bottom: Float,
        val side: Float, val sideOnLeft: Boolean, val pitch: Float, val gap: Float,
    )
    private var applied: Applied? = null
    private var geometryAnimator: ValueAnimator? = null

    /**
     * Altura, modo e bordas: o que está em edição na moldura ou o que está salvo.
     * Com [animate], o teclado desliza da geometria atual até a nova (troca de modo, de lado,
     * Redefinir); durante o arrasto da moldura, segue o dedo sem animação.
     */
    private fun applyGeometry(animate: Boolean = false) {
        val density = resources.displayMetrics.density
        val g = editing.value
        val oneHanded = settings.mode == KeyboardMode.ONE_HANDED
        val target = Applied(
            insetLeft = (g?.insetLeftDp ?: settings.insetLeftDp.toFloat()) * density,
            insetRight = (g?.insetRightDp ?: settings.insetRightDp.toFloat()) * density,
            bottom = (g?.bottomDp ?: settings.bottomOffsetDp.toFloat()) * density,
            side = if (oneHanded) resources.displayMetrics.widthPixels * ONE_HANDED_SIDE else 0f,
            // teclado encostado à direita = painel à esquerda
            sideOnLeft = settings.oneHandedSide == Side.RIGHT,
            pitch = rowPitchFor(g?.heightPercent ?: settings.keyboardHeight.toFloat()),
            gap = if (settings.mode == KeyboardMode.THUMB) THUMB_GAP else 0f,
        )
        keyboardView?.longPressMs = settings.longPressMs.toLong()
        val from = applied
        geometryAnimator?.cancel()
        if (!animate || from == null || from == target) {
            render(target)
            return
        }
        // Trocar o lado do modo uma mão: o painel encolhe, muda de lado e cresce de novo.
        val flips = from.sideOnLeft != target.sideOnLeft && from.side > 0f && target.side > 0f
        val middle = from.copy(side = 0f)
        geometryAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = GEOMETRY_MS
            interpolator = EASE_IN_OUT
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                render(
                    when {
                        !flips -> lerp(from, target, t)
                        t < 0.5f -> lerp(from, middle, t * 2f)
                        else -> lerp(middle.copy(sideOnLeft = target.sideOnLeft), target, t * 2f - 1f)
                    },
                )
            }
            start()
        }
    }

    private fun lerp(a: Applied, b: Applied, t: Float): Applied {
        fun mix(x: Float, y: Float) = x + (y - x) * t
        return Applied(
            mix(a.insetLeft, b.insetLeft), mix(a.insetRight, b.insetRight), mix(a.bottom, b.bottom),
            mix(a.side, b.side), if (b.side > 0f) b.sideOnLeft else a.sideOnLeft,
            mix(a.pitch, b.pitch), mix(a.gap, b.gap),
        )
    }

    private fun render(a: Applied) {
        applied = a
        keyboardView?.apply {
            rowPitch = a.pitch
            splitGap = a.gap
        }
        sidePanelView?.visibility = if (a.side >= 1f) View.VISIBLE else View.GONE
        (root as? KeyboardFrame)?.applyGeometry(
            insetLeft = a.insetLeft.roundToInt(),
            insetRight = a.insetRight.roundToInt(),
            bottomOffset = a.bottom.roundToInt(),
            sideWidth = a.side.roundToInt(),
            sideOnLeft = a.sideOnLeft,
        )
    }

    private fun Float.roundToStep(): Int = (Math.round(this / KeyboardHeight.STEP) * KeyboardHeight.STEP)

    private fun rowPitchFor(percent: Float): Float {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return KeyboardHeight.BASE_ROW_DP * percent / 100f * resources.displayMetrics.density * (if (landscape) 0.78f else 1f)
    }

    private fun restore(snapshotId: Long): RestorationEngine.Result {
        val result = restoration.restore(currentInputConnection, fieldKey, snapshotId)
        when (result) {
            is RestorationEngine.Result.Restored, RestorationEngine.Result.AlreadyCurrent -> {
                haptic(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
                closePanel()
            }
            is RestorationEngine.Result.Unconfirmed -> {
                closePanel()
                // Editores como o Compose aplicam a mudança no quadro seguinte: confere depois.
                root?.postDelayed({
                    if (restoration.confirm(currentInputConnection, result.target)) {
                        haptic(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
                    } else {
                        showNotice(Wording.failure(RestorationEngine.Failure.VERIFY_FAILED))
                    }
                    history.onFieldChanged()
                }, CONFIRM_DELAY_MS)
            }
            is RestorationEngine.Result.Failed ->
                haptic(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
        }
        return result
    }

    private val clearNotice = Runnable { notice.value = null }

    private fun showNotice(text: String) {
        notice.value = text
        root?.removeCallbacks(clearNotice)
        root?.postDelayed(clearNotice, NOTICE_MS)
    }

    // ---- aparência ----------------------------------------------------------------------------

    private fun applySettings(animate: Boolean = false) {
        palette.value = KeyboardPalette.resolve(settings.theme, resources.configuration)
        val p = palette.value
        root?.setBackgroundColor(p.background)
        overlay?.palette = p
        keyboardView?.apply {
            palette = p
            hapticsEnabled = settings.haptics
            soundEnabled = settings.sound
            keyPreviewEnabled = settings.keyPreview
            spaceHoldOpensHistory = settings.spaceHoldOpensHistory
        }
        applyGeometry(animate)
        @Suppress("DEPRECATION")
        window.window?.navigationBarColor = p.background
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySettings()
    }

    private fun renderKeyboard() {
        val view = keyboardView ?: return
        view.layout = Layouts.build(
            page, variant, enter,
            numberRow = settings.numberRow, arrowKeys = settings.arrowKeys,
        )
        renderShift()
    }

    private fun renderShift() {
        keyboardView?.shiftState = shift.state
    }

    private fun haptic(constant: Int) {
        if (settings.haptics) root?.performHapticFeedback(constant)
    }

    private companion object {
        const val CONFIRM_DELAY_MS = 300L
        const val NOTICE_MS = 6_000L
        /** Texto lido antes do cursor para achar a palavra atual e a anterior. */
        const val CONTEXT_CHARS = 64
        /** Basta para o maior emoji composto (família com tons de pele). */
        const val GRAPHEME_LOOKBEHIND = 32
        /** Fração da largura vazia no centro, no modo polegar. */
        const val THUMB_GAP = 0.22f
        const val PANEL_IN_MS = 200L
        const val PANEL_OUT_MS = 160L
        const val GEOMETRY_MS = 260L
        val EASE_OUT = PathInterpolator(0f, 0f, 0.2f, 1f)
        val EASE_IN = PathInterpolator(0.4f, 0f, 1f, 1f)
        val EASE_IN_OUT = PathInterpolator(0.4f, 0f, 0.2f, 1f)
        /** Fração da tela ocupada pelo painel lateral, no modo uma mão. */
        const val ONE_HANDED_SIDE = 0.18f
    }
}
