package com.timemachine.keyboard.toolbar

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timemachine.keyboard.keyboard.Glyph
import com.timemachine.keyboard.keyboard.KeyboardPalette
import com.timemachine.keyboard.settings.KeyboardMode
import com.timemachine.keyboard.settings.KeyboardSettings
import com.timemachine.keyboard.settings.ThemeMode
import com.timemachine.keyboard.timeline.GlyphButton
import com.timemachine.keyboard.timeline.GlyphIcon
import com.timemachine.keyboard.timeline.STRIP_HEIGHT

/**
 * Ajustes rápidos no lugar das teclas, como a barra de ferramentas de outros teclados.
 * Interruptores mostram o estado em texto ("Ligada"/"Desligada"), não só pela cor.
 */
@Composable
fun QuickSettingsPanel(
    settings: KeyboardSettings,
    palette: KeyboardPalette,
    onToggleHaptics: () -> Unit,
    onToggleSound: () -> Unit,
    onTogglePrivate: () -> Unit,
    onCycleTheme: () -> Unit,
    onModes: () -> Unit,
    onResize: () -> Unit,
    onToggleNumberRow: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
) {
    PanelScaffold("Ajustes", palette, onClose) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(Glyph.VIBRATE, "Vibração", onOff(settings.haptics), settings.haptics, palette, onToggleHaptics)
            Tile(Glyph.SOUND, "Som", onOff(settings.sound, feminine = false), settings.sound, palette, onToggleSound)
            Tile(Glyph.LOCK, "Privado", onOff(settings.privateMode, feminine = false), settings.privateMode, palette, onTogglePrivate)
            Tile(Glyph.THEME, "Tema", themeName(settings.theme), null, palette, onCycleTheme)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(Glyph.LAYOUT, "Modos", modeName(settings.mode), null, palette, onModes)
            Tile(Glyph.RESIZE, "Tamanho", "${settings.keyboardHeight}%", null, palette, onResize)
            Tile(Glyph.PLUS, "Números", if (settings.numberRow) "Visíveis" else "Ocultos", settings.numberRow, palette, onToggleNumberRow)
            Tile(Glyph.GEAR, "Mais", "Abrir app", null, palette, onOpenSettings)
        }
    }
}

/** Cabeçalho com "Voltar ao teclado" e uma grade que ocupa o resto. */
@Composable
private fun PanelScaffold(
    title: String,
    palette: KeyboardPalette,
    onClose: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color(palette.background))) {
        Row(Modifier.fillMaxWidth().height(STRIP_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
            GlyphButton(Glyph.CLOSE, "Voltar ao teclado", Color(palette.label), onClose)
            BasicText(title, style = TextStyle(color = Color(palette.label), fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        }
        Column(
            Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** Escolha do modo: completo, uma mão ou polegar, cada um com um desenho do resultado. */
@Composable
fun ModesPanel(mode: KeyboardMode, palette: KeyboardPalette, onSelect: (KeyboardMode) -> Unit, onClose: () -> Unit) {
    PanelScaffold("Modos", palette, onClose) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in KeyboardMode.entries) {
                val selected = m == mode
                Column(
                    Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(14.dp))
                        .background(Color(if (selected) palette.key else palette.keyFunction))
                        .then(if (selected) Modifier.border(2.dp, Color(palette.label), RoundedCornerShape(14.dp)) else Modifier)
                        .clickable(role = Role.RadioButton) { onSelect(m) }
                        .semantics { this.selected = selected }
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    ModeDrawing(m, palette, Modifier.fillMaxWidth().weight(1f))
                    Spacer(Modifier.height(8.dp))
                    BasicText(modeName(m), style = TextStyle(color = Color(palette.label), fontSize = 14.sp, fontWeight = FontWeight.Medium))
                    BasicText(
                        if (selected) "Atual" else " ",
                        style = TextStyle(color = Color(palette.labelSecondary), fontSize = 12.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeDrawing(mode: KeyboardMode, palette: KeyboardPalette, modifier: Modifier) {
    val keyColor = Color(palette.labelSecondary)
    Canvas(modifier) {
        val w = size.width
        // proporção de teclado (mais largo que alto), centralizado no espaço disponível
        val h = minOf(size.height, w * 0.62f)
        val top = (size.height - h) / 2f
        val rows = 4
        val pad = 1.5.dp.toPx()
        fun grid(left: Float, right: Float, cols: Int = 7) {
            val cw = (right - left) / cols
            val rh = h / rows
            for (r in 0 until rows) for (c in 0 until cols) {
                drawRoundRect(
                    keyColor,
                    topLeft = Offset(left + c * cw + pad, top + r * rh + pad),
                    size = Size(cw - 2 * pad, rh - 2 * pad),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
        when (mode) {
            KeyboardMode.FULL -> grid(0f, w)
            KeyboardMode.ONE_HANDED -> grid(w * 0.28f, w, cols = 5)
            KeyboardMode.THUMB -> { grid(0f, w * 0.4f, cols = 3); grid(w * 0.6f, w, cols = 3) }
        }
    }
}

/** Painel ao lado do teclado no modo uma mão: trocar de lado ou voltar ao completo. */
@Composable
fun OneHandedSidePanel(onLeft: Boolean, palette: KeyboardPalette, onSwap: () -> Unit, onExpand: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color(palette.background)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        GlyphButton(
            // o painel fica do lado vazio; a seta aponta para onde o teclado vai
            if (onLeft) Glyph.CHEVRON_LEFT else Glyph.CHEVRON_RIGHT,
            if (onLeft) "Mover teclado para a esquerda" else "Mover teclado para a direita",
            Color(palette.label), onSwap,
        )
        GlyphButton(Glyph.EXPAND, "Teclado completo", Color(palette.label), onExpand)
    }
}

fun modeName(mode: KeyboardMode) = when (mode) {
    KeyboardMode.FULL -> "Completo"
    KeyboardMode.ONE_HANDED -> "Uma mão"
    KeyboardMode.THUMB -> "Polegar"
}

private fun onOff(on: Boolean, feminine: Boolean = true) = when {
    on && feminine -> "Ligada"
    on -> "Ligado"
    feminine -> "Desligada"
    else -> "Desligado"
}

private fun themeName(mode: ThemeMode) = when (mode) {
    ThemeMode.DARK -> "Escuro"
    ThemeMode.LIGHT -> "Claro"
    ThemeMode.SYSTEM -> "Sistema"
}

/** [checked] null = ação; true/false = interruptor. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.Tile(
    glyph: Glyph,
    title: String,
    state: String,
    checked: Boolean?,
    palette: KeyboardPalette,
    onClick: () -> Unit,
) {
    val on = checked == true
    val background = if (on) palette.action else palette.keyFunction
    val ink = Color(if (on) palette.onAction else palette.label)
    val sub = if (on) Color(palette.onAction).copy(alpha = 0.75f) else Color(palette.labelSecondary)
    Column(
        Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color(background))
            .clickable(role = if (checked == null) Role.Button else Role.Switch, onClick = onClick)
            .semantics { if (checked != null) stateDescription = state }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlyphIcon(glyph, ink, 24.dp)
        Spacer(Modifier.height(6.dp))
        BasicText(title, maxLines = 1, style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center))
        BasicText(state, maxLines = 1, style = TextStyle(color = sub, fontSize = 12.sp, textAlign = TextAlign.Center))
    }
}

enum class Edge { TOP, BOTTOM, LEFT, RIGHT }

/**
 * Moldura de redimensionar sobre o teclado, com uma alça em cada borda: em cima muda a altura,
 * dos lados estreita o teclado, embaixo o levanta. O teclado muda ao vivo por baixo.
 * O arrasto usa coordenadas absolutas da tela, porque a moldura se move enquanto o teclado
 * muda de tamanho. Os mesmos ajustes existem em controles deslizantes no app.
 */
@Composable
fun ResizeOverlay(
    frameLeftPx: Int,
    frameWidthPx: Int,
    frameHeightPx: Int,
    heightPercent: Int,
    palette: KeyboardPalette,
    onDrag: (edge: Edge, dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    val label = Color(palette.label)
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(
        Modifier.fillMaxSize().background(Color(palette.background).copy(alpha = 0.90f))
            // absorve toques: as teclas por baixo não digitam durante o ajuste
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        with(density) {
            Box(
                Modifier.offset { IntOffset(frameLeftPx, 0) }
                    .size(frameWidthPx.toDp(), frameHeightPx.toDp())
                    .padding(6.dp)
                    .border(2.dp, label, RoundedCornerShape(10.dp)),
            ) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText("Arraste as bordas", style = TextStyle(color = label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(2.dp))
                    BasicText("Altura $heightPercent%", style = TextStyle(color = Color(palette.labelSecondary), fontSize = 13.sp, fontFamily = FontFamily.Monospace))
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(palette.keyFunction))
                                .clickable(role = Role.Button, onClick = onReset).padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) { BasicText("Redefinir", style = TextStyle(color = label, fontSize = 15.sp, fontWeight = FontWeight.Medium)) }
                        Spacer(Modifier.width(12.dp))
                        Box(
                            Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(palette.action))
                                .clickable(role = Role.Button, onClick = onDone).padding(horizontal = 28.dp),
                            contentAlignment = Alignment.Center,
                        ) { BasicText("OK", style = TextStyle(color = Color(palette.onAction), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)) }
                    }
                }
                Handle(Edge.TOP, label, onDrag, onDragEnd, Modifier.align(Alignment.TopCenter))
                Handle(Edge.BOTTOM, label, onDrag, onDragEnd, Modifier.align(Alignment.BottomCenter))
                Handle(Edge.LEFT, label, onDrag, onDragEnd, Modifier.align(Alignment.CenterStart))
                Handle(Edge.RIGHT, label, onDrag, onDragEnd, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

@SuppressLint("ReturnFromAwaitPointerEventScope")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Handle(edge: Edge, color: Color, onDrag: (Edge, Float, Float) -> Unit, onDragEnd: () -> Unit, modifier: Modifier) {
    val last = remember { floatArrayOf(Float.NaN, Float.NaN) }
    val vertical = edge == Edge.LEFT || edge == Edge.RIGHT
    val name = when (edge) {
        Edge.TOP -> "Alça de cima: altura"
        Edge.BOTTOM -> "Alça de baixo: posição"
        Edge.LEFT -> "Alça esquerda: largura"
        Edge.RIGHT -> "Alça direita: largura"
    }
    Box(
        modifier.then(if (vertical) Modifier.size(44.dp, 96.dp) else Modifier.size(120.dp, 36.dp))
            .semantics { contentDescription = name }
            .pointerInteropFilter { e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { last[0] = e.rawX; last[1] = e.rawY }
                    MotionEvent.ACTION_MOVE -> if (!last[0].isNaN()) {
                        onDrag(edge, e.rawX - last[0], e.rawY - last[1])
                        last[0] = e.rawX; last[1] = e.rawY
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        last[0] = Float.NaN
                        onDragEnd()
                    }
                }
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.then(if (vertical) Modifier.size(6.dp, 44.dp) else Modifier.size(48.dp, 6.dp))
                .clip(RoundedCornerShape(50)).background(color),
        )
    }
}
