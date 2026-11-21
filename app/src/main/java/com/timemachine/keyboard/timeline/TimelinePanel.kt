package com.timemachine.keyboard.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timemachine.keyboard.history.CaptureStatus
import com.timemachine.keyboard.history.HistoryState
import com.timemachine.keyboard.history.Snapshot
import com.timemachine.keyboard.history.TextDiff
import com.timemachine.keyboard.keyboard.Glyph
import com.timemachine.keyboard.keyboard.KeyboardPalette
import com.timemachine.keyboard.restoration.RestorationEngine
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * A linha do tempo ocupa o lugar das teclas, com a mesma altura (nada na tela do app pula).
 * De cima para baixo: cabeçalho, trilho de versões com ramos, a versão selecionada, prévia
 * (texto ou mudanças em relação ao atual) e ações. Inspecionar não altera o campo; só
 * "Restaurar" altera.
 */
@Composable
fun TimelinePanel(
    state: HistoryState,
    palette: KeyboardPalette,
    originLabel: String?,
    now: () -> Long,
    onRestore: (Long) -> RestorationEngine.Result,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onTick: () -> Unit,
) {
    val label = Color(palette.label)
    val secondary = Color(palette.labelSecondary)
    val versions = state.versions
    var selectedId by remember { mutableStateOf(initialSelection(state)) }
    LaunchedEffect(versions, state.headId) {
        if (versions.none { it.id == selectedId }) selectedId = initialSelection(state)
    }
    val selectedIndex = versions.indexOfFirst { it.id == selectedId }
    val selected = versions.getOrNull(selectedIndex)
    val head = versions.firstOrNull { it.id == state.headId }

    var showChanges by remember { mutableStateOf(true) }
    var message by remember(selectedId) { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember(selectedId) { mutableStateOf(false) }
    LaunchedEffect(confirmClear) { if (confirmClear) { delay(CONFIRM_MS); confirmClear = false } }
    LaunchedEffect(confirmDelete) { if (confirmDelete) { delay(CONFIRM_MS); confirmDelete = false } }

    Column(Modifier.fillMaxSize().background(Color(palette.background))) {
        // Cabeçalho
        Row(Modifier.fillMaxWidth().height(STRIP_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
            GlyphButton(Glyph.CLOSE, "Voltar ao teclado", label, onClose)
            Column(Modifier.weight(1f)) {
                BasicText("Linha do tempo", style = TextStyle(color = label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                val subtitle = originLabel.orEmpty()
                if (subtitle.isNotEmpty()) {
                    BasicText(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = mono(secondary, 12))
                }
            }
            if (versions.isNotEmpty()) {
                TextAction(
                    text = if (confirmClear) "Limpar tudo?" else "Limpar",
                    color = if (confirmClear) Color(palette.danger) else secondary,
                ) {
                    if (confirmClear) { onClear(); confirmClear = false } else confirmClear = true
                }
            }
            Spacer(Modifier.width(4.dp))
        }

        if (selected == null) {
            EmptyState(state, palette)
            return@Column
        }

        Rail(versions, state.headId, selectedId, palette) { id -> selectedId = id; onTick() }

        // Versão selecionada
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphButton(Glyph.CHEVRON_LEFT, "Versão anterior", label, enabled = selectedIndex > 0, onClick = {
                selectedId = versions[selectedIndex - 1].id; onTick()
            })
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(metaLine(selected, versions), maxLines = 1, style = mono(label, 13))
                BasicText(tagLine(selected, state.headId, now()), maxLines = 1,
                    style = mono(if (selected.id == state.headId) Color(palette.accentInk) else secondary, 12))
            }
            GlyphButton(Glyph.CHEVRON_RIGHT, "Próxima versão", label, enabled = selectedIndex < versions.lastIndex, onClick = {
                selectedId = versions[selectedIndex + 1].id; onTick()
            })
        }

        // Prévia
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp)
                .clip(RoundedCornerShape(10.dp)).background(Color(palette.surface))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val diffShown = showChanges && head != null && canDiff(selected, head)
                val note = message ?: when {
                    !selected.isComplete -> "Versão parcial: só o trecho perto do cursor foi lido."
                    diffShown -> "riscado sai · sublinhado volta"
                    else -> null
                }
                if (note != null) {
                    BasicText(note, style = TextStyle(color = if (message != null) Color(palette.danger) else secondary, fontSize = 12.sp))
                    Spacer(Modifier.height(4.dp))
                }
                BasicText(
                    if (diffShown) changes(head!!, selected, palette) else plain(selected.text, palette),
                    style = TextStyle(color = label, fontSize = 15.sp, lineHeight = 21.sp),
                )
            }
        }

        // Ações
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val isHead = selected.id == state.headId
            TextAction(
                text = if (confirmDelete) "Excluir versão?" else "Excluir",
                color = Color(palette.danger),
                enabled = !isHead,
            ) {
                if (confirmDelete) { onDelete(selected.id); confirmDelete = false } else confirmDelete = true
            }
            Spacer(Modifier.weight(1f))
            Segmented(listOf("Texto", "Mudanças"), if (showChanges) 1 else 0, palette) { showChanges = it == 1 }
            Spacer(Modifier.weight(1f))
            PrimaryAction(
                text = when {
                    isHead -> "Atual"
                    !selected.isComplete -> "Parcial"
                    else -> "Restaurar"
                },
                enabled = !isHead && selected.isComplete,
                palette = palette,
            ) {
                when (val result = onRestore(selected.id)) {
                    is RestorationEngine.Result.Failed -> message = Wording.failure(result.reason)
                    else -> message = null
                }
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

private fun initialSelection(state: HistoryState): Long? {
    val head = state.versions.firstOrNull { it.id == state.headId }
    // Abre na versão anterior à atual: é o que a pessoa quase sempre procura.
    return head?.parentId?.takeIf { id -> state.versions.any { it.id == id } }
        ?: state.versions.lastOrNull { it.id != state.headId }?.id
        ?: head?.id
}

private fun metaLine(s: Snapshot, versions: List<Snapshot>): String {
    val parent = versions.firstOrNull { it.id == s.parentId }
    val delta = parent?.let {
        val d = TextDiff.between(it.text, s.text)
        buildString {
            if (d.inserted.isNotEmpty()) append(" +").append(d.inserted.length)
            if (d.removed.isNotEmpty()) append(" −").append(d.removed.length)
        }
    }.orEmpty()
    return "${Wording.time(s.timestamp)} · ${Wording.operation(s.operation)}$delta"
}

private fun tagLine(s: Snapshot, headId: Long?, now: Long): String = listOfNotNull(
    "versão atual".takeIf { s.id == headId },
    "parcial".takeIf { !s.isComplete },
    Wording.relative(s.timestamp, now),
).joinToString(" · ")

private fun canDiff(selected: Snapshot, head: Snapshot) =
    selected.id != head.id && selected.isComplete && head.isComplete

private fun plain(text: String, palette: KeyboardPalette): AnnotatedString =
    if (text.isEmpty()) buildAnnotatedString {
        withStyle(SpanStyle(color = Color(palette.labelSecondary))) { append("(vazio)") }
    } else AnnotatedString(text)

/** Do texto atual para a versão selecionada: o que sai riscado, o que volta sublinhado. */
private fun changes(current: Snapshot, selected: Snapshot, palette: KeyboardPalette): AnnotatedString {
    val d = TextDiff.between(current.text, selected.text)
    if (d.isEmpty) return plain(selected.text, palette)
    return buildAnnotatedString {
        append(if (d.prefix.length > CONTEXT) "…" + d.prefix.takeLast(CONTEXT) else d.prefix)
        withStyle(SpanStyle(color = Color(palette.danger), textDecoration = TextDecoration.LineThrough)) { append(d.removed) }
        withStyle(
            SpanStyle(
                color = Color(palette.accentInk),
                textDecoration = TextDecoration.Underline,
                background = Color(palette.accentFill).copy(alpha = 0.16f),
            ),
        ) { append(d.inserted) }
        append(if (d.suffix.length > CONTEXT) d.suffix.take(CONTEXT) + "…" else d.suffix)
    }
}

@Composable
private fun Rail(versions: List<Snapshot>, headId: Long?, selectedId: Long?, palette: KeyboardPalette, onSelect: (Long) -> Unit) {
    val layout = remember(versions, headId) { RailLayout.of(versions, headId) }
    val density = LocalDensity.current
    val column = 30.dp
    val lane = 16.dp
    val railHeight = 36.dp + lane * (layout.laneCount - 1)
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth().height(railHeight)) {
        val content = maxOf(maxWidth, column * layout.nodes.size + 32.dp)
        val contentPx = with(density) { content.toPx() }
        val colPx = with(density) { column.toPx() }
        val endPad = with(density) { 22.dp.toPx() }
        val firstX = contentPx - endPad - colPx * (layout.nodes.size - 1)
        val viewportPx = with(density) { maxWidth.toPx() }
        val selectedIndex = layout.nodes.indexOfFirst { it.snapshot.id == selectedId }
        LaunchedEffect(selectedIndex, contentPx) {
            if (selectedIndex >= 0) {
                val target = (firstX + selectedIndex * colPx - viewportPx / 2).roundToInt()
                scroll.animateScrollTo(target.coerceIn(0, scroll.maxValue))
            }
        }
        Box(Modifier.horizontalScroll(scroll)) {
            Canvas(
                Modifier.width(content).height(railHeight)
                    .semantics { contentDescription = "Trilho com ${Wording.versions(versions.size)}. Use Versão anterior e Próxima versão." }
                    .pointerInput(layout, firstX) {
                        detectTapGestures { offset ->
                            val i = ((offset.x - firstX) / colPx).roundToInt()
                            layout.nodes.getOrNull(i)?.let { onSelect(it.snapshot.id) }
                        }
                    },
            ) {
                val lanePx = lane.toPx()
                val top = 18.dp.toPx()
                fun at(n: RailNode) = Offset(firstX + n.column * colPx, top + n.lane * lanePx)
                val stroke = Color(palette.stroke)
                val sw = 1.5.dp.toPx()
                for (e in layout.edges) {
                    val a = at(e.from)
                    val b = at(e.to)
                    if (e.from.lane == e.to.lane) {
                        drawLine(stroke, a, b, sw)
                    } else {
                        val r = minOf(8.dp.toPx(), kotlin.math.abs(b.y - a.y), kotlin.math.abs(b.x - a.x))
                        val dir = if (b.y > a.y) 1f else -1f
                        val p = Path().apply {
                            moveTo(a.x, a.y)
                            lineTo(a.x, b.y - dir * r)
                            quadraticTo(a.x, b.y, a.x + r, b.y)
                            lineTo(b.x, b.y)
                        }
                        drawPath(p, stroke, style = Stroke(sw))
                    }
                }
                for (n in layout.nodes) {
                    val c = at(n)
                    val s = n.snapshot
                    if (s.id == selectedId) drawCircle(Color(palette.label), 9.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
                    when {
                        s.id == headId -> {
                            drawCircle(Color(palette.accentFill), 5.5.dp.toPx(), c)
                            drawCircle(Color(palette.background), 2.dp.toPx(), c)
                        }
                        !s.isComplete -> drawCircle(Color(palette.labelSecondary), 3.5.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
                        else -> drawCircle(Color(palette.labelSecondary), 3.5.dp.toPx(), c)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(state: HistoryState, palette: KeyboardPalette) {
    val (title, detail) = when (val status = state.status) {
        is CaptureStatus.Blocked -> Wording.blocked(status.reason) to Wording.blockedDetail(status.reason)
        CaptureStatus.Recording -> "Ainda não há versões" to "Escreva algo e faça uma pausa. Cada pausa vira uma versão."
        CaptureStatus.Inactive -> "Nenhum campo em edição" to "Toque em um campo de texto para começar."
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.status is CaptureStatus.Blocked) {
            GlyphIcon(Glyph.LOCK, Color(palette.labelSecondary), 28.dp)
            Spacer(Modifier.height(10.dp))
        }
        BasicText(title, style = TextStyle(color = Color(palette.label), fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(6.dp))
        BasicText(detail, style = TextStyle(color = Color(palette.labelSecondary), fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
    }
}

@Composable
private fun TextAction(text: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.height(48.dp).clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TextStyle(color = if (enabled) color else color.copy(alpha = 0.35f), fontSize = 15.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun PrimaryAction(text: String, enabled: Boolean, palette: KeyboardPalette, onClick: () -> Unit) {
    Box(
        Modifier.height(44.dp).clip(RoundedCornerShape(12.dp))
            .background(if (enabled) Color(palette.accentFill) else Color(palette.keyFunction))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = TextStyle(
                color = if (enabled) Color(palette.onAccent) else Color(palette.labelSecondary),
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, palette: KeyboardPalette, onSelect: (Int) -> Unit) {
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(palette.keyFunction)).padding(3.dp),
    ) {
        options.forEachIndexed { i, option ->
            val isSelected = i == selected
            Box(
                Modifier.fillMaxSizeHeight().clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color(palette.key) else Color.Transparent)
                    .semantics { this.selected = isSelected }
                    .clickable(role = Role.RadioButton) { onSelect(i) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(option, style = TextStyle(
                    color = if (isSelected) Color(palette.label) else Color(palette.labelSecondary),
                    fontSize = 13.sp, fontWeight = FontWeight.Medium,
                ))
            }
        }
    }
}

private fun Modifier.fillMaxSizeHeight(): Modifier = this.then(Modifier.height(30.dp))

private fun mono(color: Color, size: Int) = TextStyle(color = color, fontSize = size.sp, fontFamily = FontFamily.Monospace)

private const val CONTEXT = 160
private const val CONFIRM_MS = 3_000L
