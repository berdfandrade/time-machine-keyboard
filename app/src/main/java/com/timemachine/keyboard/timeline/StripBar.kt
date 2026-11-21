package com.timemachine.keyboard.timeline

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timemachine.keyboard.history.CaptureStatus
import com.timemachine.keyboard.history.HistoryState
import com.timemachine.keyboard.keyboard.Glyph
import com.timemachine.keyboard.keyboard.Glyphs
import com.timemachine.keyboard.keyboard.KeyboardPalette
import kotlinx.coroutines.delay

const val UNDO_WINDOW_MS = 8_000L

/**
 * Faixa discreta acima das teclas: botão de histórico, o "trilho" com as últimas versões
 * (a atual em âmbar; parciais vazadas) e o estado da gravação, sempre explícito.
 */
@Composable
fun StripBar(
    state: HistoryState,
    palette: KeyboardPalette,
    /** Aviso temporário (ex.: restauração não confirmada); tem prioridade sobre o estado. */
    notice: String?,
    now: () -> Long,
    onOpenTimeline: () -> Unit,
    onOpenSettings: () -> Unit,
    onUndoRestore: (beforeId: Long) -> Unit,
    /** Sugestões de palavras, a melhor primeiro; vazio = mostra o trilho. */
    suggestions: List<String> = emptyList(),
    onPick: (String) -> Unit = {},
) {
    val label = Color(palette.labelSecondary)
    val marker = state.lastRestore
    var showUndo by remember(marker) { mutableStateOf(marker != null && now() - marker.at < UNDO_WINDOW_MS) }
    LaunchedEffect(marker) {
        if (marker != null) {
            delay((UNDO_WINDOW_MS - (now() - marker.at)).coerceAtLeast(0))
            showUndo = false
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(STRIP_HEIGHT).background(Color(palette.background)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(Glyph.HISTORY, "Abrir linha do tempo", Color(palette.label), onOpenTimeline)

        // Troca de estado (gravando, bloqueado, aviso) com fade curto, sem pular.
        val mode = when {
            notice != null -> "notice"
            suggestions.isNotEmpty() -> "suggest"
            state.status is CaptureStatus.Blocked -> "blocked"
            state.status == CaptureStatus.Recording -> "recording"
            else -> "inactive"
        }
        // O estado leva os próprios dados: o conteúdo que está saindo (fade) continua desenhando
        // as sugestões que tinha, não a lista nova (que pode estar vazia).
        AnimatedContent(
            targetState = mode to suggestions,
            contentKey = { it.first },
            modifier = Modifier.weight(1f),
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "strip",
        ) { (m, shown) ->
            Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                when (m) {
                    "suggest" -> SuggestionRow(shown, palette, onPick)
                    "notice" -> BasicText(
                        notice.orEmpty(),
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = Color(palette.danger), fontSize = 12.sp, lineHeight = 15.sp),
                    )
                    "blocked" -> (state.status as? CaptureStatus.Blocked)?.let { status ->
                        GlyphIcon(Glyph.LOCK, label, 16.dp)
                        Spacer(Modifier.width(6.dp))
                        StripText(Wording.blocked(status.reason), label)
                    }
                    "recording" -> {
                        MiniRail(
                            state = state,
                            palette = palette,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .clickable(onClickLabel = "Abrir linha do tempo", role = Role.Button, onClick = onOpenTimeline),
                        )
                        val partial = state.versions.firstOrNull { it.id == state.headId }?.isComplete == false
                        AnimatedVisibility(
                            visible = showUndo && marker != null,
                            enter = fadeIn(tween(180)) + expandHorizontally(tween(220)),
                            exit = fadeOut(tween(160)) + shrinkHorizontally(tween(220)),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StripText("Restaurado", label)
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    Modifier.clip(RoundedCornerShape(50))
                                        .clickable(role = Role.Button) { marker?.let { onUndoRestore(it.beforeId) } }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                ) {
                                    BasicText("Desfazer", style = TextStyle(color = Color(palette.accentInk), fontSize = 14.sp, fontWeight = FontWeight.Medium))
                                }
                            }
                        }
                        // Sem contagem de versões; só avisa quando a versão atual é parcial.
                        AnimatedVisibility(visible = partial && !showUndo, enter = fadeIn(tween(180)), exit = fadeOut(tween(120))) {
                            StripText("parcial", label)
                        }
                    }
                    else -> Unit
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        GlyphButton(Glyph.GEAR, "Ajustes do teclado", label, onOpenSettings)
    }
}

/** Três sugestões, a melhor no meio e em destaque, separadas por traços finos. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.SuggestionRow(
    suggestions: List<String>,
    palette: KeyboardPalette,
    onPick: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    val order = when (suggestions.size) {
        1 -> listOf(0)
        2 -> listOf(1, 0)
        else -> listOf(1, 0, 2)
    }
    order.forEachIndexed { i, index ->
        if (i > 0) Box(Modifier.width(1.dp).height(20.dp).background(Color(palette.separator)))
        val best = index == 0
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClickLabel = "Usar ${suggestions[index]}") { onPick(suggestions[index]) },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                suggestions[index],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = Color(if (best) palette.label else palette.labelSecondary),
                    fontSize = 16.sp,
                    fontWeight = if (best) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
        }
    }
}

@Composable
private fun StripText(text: String, color: Color) {
    BasicText(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(color = color, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
    )
}

/**
 * As últimas versões do caminho atual, da mais antiga (esquerda) à atual (direita).
 * Quando uma versão entra, o trilho desliza um passo para a esquerda e o ponto novo cresce.
 */
@Composable
private fun MiniRail(state: HistoryState, palette: KeyboardPalette, modifier: Modifier) {
    val byId = remember(state.versions) { state.versions.associateBy { it.id } }
    val path = remember(state.versions, state.headId) {
        generateSequence(state.headId?.let(byId::get)) { it.parentId?.let(byId::get) }.take(MINI_RAIL_DOTS).toList().reversed()
    }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(state.headId) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 260, easing = FastOutSlowInEasing))
    }
    val stroke = Color(palette.stroke)
    val dot = Color(palette.labelSecondary)
    val head = Color(palette.accentFill)
    Canvas(modifier.semantics { contentDescription = "Histórico: ${Wording.versions(state.versions.size)}" }) {
        if (path.isEmpty()) return@Canvas
        val t = progress.value
        val spacing = 14.dp.toPx()
        val y = size.height / 2
        val end = size.width - 14.dp.toPx()
        val slide = (1f - t) * spacing
        val start = end - spacing * (path.size - 1) + slide
        drawLine(stroke, Offset(start - spacing / 2, y), Offset(end, y), strokeWidth = 1.5.dp.toPx())
        path.forEachIndexed { i, s ->
            val isHead = i == path.lastIndex
            val c = Offset(if (isHead) end else start + i * spacing, y)
            when {
                isHead -> {
                    drawCircle(head, 5.dp.toPx() * (0.4f + 0.6f * t), c)
                    drawCircle(Color(palette.background), 2.dp.toPx() * t, c)
                }
                !s.isComplete -> drawCircle(dot, 2.5.dp.toPx(), c, style = Stroke(1.2.dp.toPx()))
                else -> drawCircle(dot, 2.5.dp.toPx(), c)
            }
        }
    }
}

@Composable
fun GlyphButton(glyph: Glyph, description: String, color: Color, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, if (enabled) color else color.copy(alpha = 0.35f), 22.dp)
    }
}

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        drawIntoCanvas { Glyphs.draw(it.nativeCanvas, glyph, this.size.width / 2, this.size.height / 2, this.size.width, color.toArgb()) }
    }
}

val STRIP_HEIGHT = 44.dp
private const val MINI_RAIL_DOTS = 12
