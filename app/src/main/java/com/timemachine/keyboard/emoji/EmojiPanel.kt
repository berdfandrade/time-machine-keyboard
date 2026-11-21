package com.timemachine.keyboard.emoji

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timemachine.keyboard.keyboard.Glyph
import com.timemachine.keyboard.keyboard.KeyboardPalette
import com.timemachine.keyboard.timeline.GlyphIcon

private const val RECENT = "recent"

/**
 * Painel de emojis no lugar das teclas: abas de categoria no topo, grade no meio e, embaixo,
 * ABC, espaço e apagar. Segurar um emoji com tons de pele abre a barra de tons.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmojiPanel(
    categories: List<EmojiCategory>,
    recents: List<String>,
    palette: KeyboardPalette,
    onEmoji: (String) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onAbc: () -> Unit,
) {
    var tab by remember { mutableStateOf(if (recents.isNotEmpty()) RECENT else categories.firstOrNull()?.id ?: RECENT) }
    LaunchedEffect(categories.isNotEmpty()) {
        if (tab == RECENT && recents.isEmpty()) categories.firstOrNull()?.let { tab = it.id }
    }
    var tones by remember { mutableStateOf<List<String>?>(null) }
    val label = Color(palette.label)
    val secondary = Color(palette.labelSecondary)

    Column(Modifier.fillMaxSize().background(Color(palette.background))) {
        // Abas
        Row(
            Modifier.fillMaxWidth().height(44.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab(selected = tab == RECENT, description = "Recentes", palette = palette, onClick = { tab = RECENT; tones = null }) {
                GlyphIcon(Glyph.HISTORY, if (tab == RECENT) label else secondary, 20.dp)
            }
            for (c in categories) {
                Tab(selected = tab == c.id, description = c.label, palette = palette, onClick = { tab = c.id; tones = null }) {
                    BasicText(c.icon, style = TextStyle(fontSize = 20.sp))
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val items: List<List<String>> = if (tab == RECENT) recents.map { listOf(it) } else categories.firstOrNull { it.id == tab }?.items.orEmpty()
            val grid = rememberLazyGridState()
            LaunchedEffect(tab) { grid.scrollToItem(0) }
            when {
                categories.isEmpty() && tab != RECENT -> Message("Carregando emojis…", secondary)
                items.isEmpty() -> Message("Os emojis que você usar aparecem aqui.", secondary)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(44.dp),
                    state = grid,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.first() }) { forms ->
                        Box(
                            Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    role = Role.Button,
                                    onClick = { tones = null; onEmoji(forms.first()) },
                                    onLongClick = if (forms.size > 1) ({ tones = forms }) else null,
                                )
                                .semantics { contentDescription = forms.first() },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(forms.first(), style = TextStyle(fontSize = 26.sp, textAlign = TextAlign.Center))
                            if (forms.size > 1) {
                                // marca discreta: este emoji tem tons de pele
                                Box(
                                    Modifier.align(Alignment.BottomEnd).padding(5.dp).size(4.dp)
                                        .clip(RoundedCornerShape(50)).background(secondary),
                                )
                            }
                        }
                    }
                }
            }
            // Barra de tons de pele
            val toneAlpha by animateFloatAsState(if (tones != null) 1f else 0f, tween(150), label = "tones")
            if (tones != null || toneAlpha > 0f) {
                Row(
                    Modifier.align(Alignment.TopCenter).padding(top = 6.dp).alpha(toneAlpha)
                        .clip(RoundedCornerShape(14.dp)).background(Color(palette.key)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    tones.orEmpty().forEach { form ->
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                                .clickable(role = Role.Button) { tones = null; onEmoji(form) }
                                .semantics { contentDescription = form },
                            contentAlignment = Alignment.Center,
                        ) { BasicText(form, style = TextStyle(fontSize = 28.sp)) }
                    }
                }
            }
        }

        // ABC, espaço, apagar
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 3.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BottomKey(Modifier.width(76.dp), palette.keyFunction, "Letras", onAbc) {
                BasicText("ABC", style = TextStyle(color = label, fontSize = 15.sp, fontWeight = FontWeight.Medium))
            }
            BottomKey(Modifier.weight(1f), palette.key, "Espaço", onSpace) {
                BasicText("espaço", style = TextStyle(color = secondary, fontSize = 13.sp))
            }
            BottomKey(Modifier.width(76.dp), palette.keyFunction, "Apagar", onBackspace) {
                GlyphIcon(Glyph.BACKSPACE, label, 22.dp)
            }
        }
    }
}

@Composable
private fun Tab(selected: Boolean, description: String, palette: KeyboardPalette, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.width(44.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Tab, onClickLabel = description, onClick = onClick)
            .semantics { this.selected = selected; contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.width(18.dp).height(2.dp).clip(RoundedCornerShape(50))
                .background(if (selected) Color(palette.label) else Color.Transparent),
        )
    }
}

@Composable
private fun BottomKey(modifier: Modifier, color: Int, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(Color(color))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Message(text: String, color: Color) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        BasicText(text, style = TextStyle(color = color, fontSize = 14.sp, textAlign = TextAlign.Center))
    }
}
