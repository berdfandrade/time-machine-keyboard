@file:OptIn(ExperimentalMaterial3Api::class)

package com.timemachine.keyboard.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

private enum class Screen(val title: String) {
    HOME("Time Machine"),
    APPEARANCE("Aparência"),
    HISTORY("Histórico"),
    PRIVACY("Privacidade"),
    EXCLUDED_APPS("Apps excluídos"),
    LIMITATIONS("Limitações técnicas"),
}

@Composable
fun SettingsApp(
    repository: SettingsRepository,
    eraseAll: () -> Unit,
    readStatus: () -> ImeStatus,
    openInputMethodSettings: () -> Unit,
    showKeyboardPicker: () -> Unit,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val settings by repository.settings.collectAsState()
    var confirmErase by remember { mutableStateOf(false) }
    BackHandler(enabled = screen != Screen.HOME) {
        screen = if (screen == Screen.EXCLUDED_APPS) Screen.PRIVACY else Screen.HOME
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screen.title) },
                navigationIcon = {
                    if (screen != Screen.HOME) {
                        IconButton(onClick = { screen = if (screen == Screen.EXCLUDED_APPS) Screen.PRIVACY else Screen.HOME }) {
                            Icon(BackArrow, contentDescription = "Voltar")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        when (screen) {
            Screen.HOME -> Home(modifier, readStatus, openInputMethodSettings, showKeyboardPicker, onOpen = { screen = it }, onErase = { confirmErase = true })
            Screen.APPEARANCE -> Appearance(modifier, settings, repository)
            Screen.HISTORY -> HistorySettings(modifier, settings, repository)
            Screen.PRIVACY -> Privacy(modifier, settings, repository, onExcluded = { screen = Screen.EXCLUDED_APPS }, onErase = { confirmErase = true })
            Screen.EXCLUDED_APPS -> ExcludedApps(modifier, settings, repository)
            Screen.LIMITATIONS -> Limitations(modifier)
        }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Apagar todo o histórico?") },
            text = { Text("Todas as versões e as palavras aprendidas, guardadas na memória, serão apagadas. Não é possível desfazer.") },
            confirmButton = {
                TextButton(onClick = { eraseAll(); confirmErase = false }) {
                    Text("Apagar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancelar") } },
        )
    }
}

// ---- Início ---------------------------------------------------------------------------------

@Composable
private fun Home(
    modifier: Modifier,
    readStatus: () -> ImeStatus,
    openInputMethodSettings: () -> Unit,
    showKeyboardPicker: () -> Unit,
    onOpen: (Screen) -> Unit,
    onErase: () -> Unit,
) {
    var status by remember { mutableStateOf(readStatus()) }
    // O seletor de teclado é um diálogo do sistema: recarrega ao voltar o foco e ao retomar.
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { status = readStatus() }
    LifecycleResumeEffect(Unit) {
        status = readStatus()
        onPauseOrDispose { }
    }
    var tryText by rememberSaveable { mutableStateOf("") }

    Column(modifier.verticalScroll(rememberScrollState()).imePadding().padding(bottom = 24.dp)) {
        Hero()

        SectionTitle(if (status.selected) "Pronto para usar" else "Configurar")
        SetupStep(
            number = 1,
            title = "Ativar o teclado",
            detail = "Em Configurações, ligue “Time Machine Keyboard”. O Android mostra um aviso padrão sobre teclados; o Time Machine não pede acesso à Internet.",
            done = status.enabled,
            action = "Abrir configurações",
            onAction = openInputMethodSettings,
        )
        SetupStep(
            number = 2,
            title = "Escolher como teclado atual",
            detail = "Escolha o Time Machine na lista. Para trocar de teclado depois, use o ícone de teclado na barra de navegação do Android.",
            done = status.selected,
            action = "Escolher teclado",
            enabled = status.enabled,
            onAction = showKeyboardPicker,
        )
        SetupStep(
            number = 3,
            title = "Experimentar",
            detail = "Escreva, apague um trecho e segure a barra de espaço (ou toque no relógio) para abrir a linha do tempo.",
            done = false,
            action = null,
            onAction = {},
        )
        OutlinedTextField(
            value = tryText,
            onValueChange = { tryText = it },
            label = { Text("Área de teste") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        SectionTitle("Configurações")
        NavRow("Aparência", "Tema, altura do teclado, vibração e som") { onOpen(Screen.APPEARANCE) }
        NavRow("Histórico", "Agrupamento, limite de versões e retenção") { onOpen(Screen.HISTORY) }
        NavRow("Privacidade", "Modo privado e apps excluídos") { onOpen(Screen.PRIVACY) }
        NavRow("Limitações técnicas", "O que o teclado consegue e não consegue recuperar") { onOpen(Screen.LIMITATIONS) }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        ListItem(
            headlineContent = { Text("Apagar todo o histórico", color = MaterialTheme.colorScheme.error) },
            supportingContent = { Text("Remove agora todas as versões da memória") },
            modifier = Modifier.clickableRow(onErase),
        )
    }
}

@Composable
private fun Hero() {
    val accent = MaterialTheme.colorScheme.primary
    val dot = MaterialTheme.colorScheme.onSurfaceVariant
    val line = MaterialTheme.colorScheme.outline
    val bg = MaterialTheme.colorScheme.background
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            // Um trilho de versões: o símbolo do produto, desenhado com as mesmas regras da linha do tempo.
            Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                val y = size.height * 0.38f
                val gap = size.width / 7f
                val xs = (0..5).map { gap * (it + 0.6f) }
                drawLine(line, Offset(xs.first(), y), Offset(xs.last(), y), 2.dp.toPx())
                val branchY = size.height * 0.82f
                val r = 10.dp.toPx()
                val p = Path().apply {
                    moveTo(xs[2], y); lineTo(xs[2], branchY - r); quadraticTo(xs[2], branchY, xs[2] + r, branchY); lineTo(xs[4], branchY)
                }
                drawPath(p, line, style = Stroke(2.dp.toPx()))
                xs.dropLast(1).forEach { drawCircle(dot, 4.dp.toPx(), Offset(it, y)) }
                drawCircle(dot, 3.5.dp.toPx(), Offset(xs[3], branchY), style = Stroke(1.5.dp.toPx()))
                drawCircle(dot, 4.dp.toPx(), Offset(xs[4], branchY))
                drawCircle(accent, 7.dp.toPx(), Offset(xs.last(), y))
                drawCircle(bg, 2.5.dp.toPx(), Offset(xs.last(), y))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Volte a qualquer versão do que você escreveu.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Enquanto você digita, o teclado guarda versões do texto. Apagou um parágrafo ou mudou de ideia? Abra a linha do tempo, compare e restaure. Tudo fica neste aparelho, só na memória.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    detail: String,
    done: Boolean,
    action: String?,
    enabled: Boolean = true,
    onAction: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Surface(
            shape = CircleShape,
            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(32.dp),
        ) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (done) Icon(Check, contentDescription = "Concluído", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                else Text("$number", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null && !done) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onAction, enabled = enabled) { Text(action) }
            }
        }
    }
}

// ---- Aparência ------------------------------------------------------------------------------

@Composable
private fun Appearance(modifier: Modifier, s: KeyboardSettings, repo: SettingsRepository) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        SectionTitle("Tema do teclado")
        Segments(
            options = listOf(ThemeMode.DARK to "Escuro", ThemeMode.LIGHT to "Claro", ThemeMode.SYSTEM to "Sistema"),
            selected = s.theme,
        ) { v -> repo.update { it.copy(theme = v) } }
        Note("Escuro é o padrão. “Sistema” acompanha o modo claro ou escuro do Android.")

        SliderRow(
            title = "Altura do teclado",
            value = s.keyboardHeight.toFloat(),
            range = KeyboardHeight.MIN.toFloat()..KeyboardHeight.MAX.toFloat(),
            steps = (KeyboardHeight.MAX - KeyboardHeight.MIN) / KeyboardHeight.STEP - 1,
            format = { "${it.toInt()}%" },
        ) { v -> repo.update { it.copy(keyboardHeight = KeyboardHeight.clamp(v.toInt())) } }
        SliderRow(
            title = "Recuo à esquerda",
            value = s.insetLeftDp.toFloat(),
            range = 0f..KeyboardEdges.MAX_SIDE_DP.toFloat(),
            steps = KeyboardEdges.MAX_SIDE_DP / 10 - 1,
            format = { "${it.toInt()} dp" },
        ) { v -> repo.update { it.copy(insetLeftDp = v.toInt()) } }
        SliderRow(
            title = "Recuo à direita",
            value = s.insetRightDp.toFloat(),
            range = 0f..KeyboardEdges.MAX_SIDE_DP.toFloat(),
            steps = KeyboardEdges.MAX_SIDE_DP / 10 - 1,
            format = { "${it.toInt()} dp" },
        ) { v -> repo.update { it.copy(insetRightDp = v.toInt()) } }
        SliderRow(
            title = "Elevação",
            value = s.bottomOffsetDp.toFloat(),
            range = 0f..KeyboardEdges.MAX_BOTTOM_DP.toFloat(),
            steps = KeyboardEdges.MAX_BOTTOM_DP / 10 - 1,
            format = { "${it.toInt()} dp" },
        ) { v -> repo.update { it.copy(bottomOffsetDp = v.toInt()) } }
        Note("No próprio teclado: engrenagem › Redimensionar, e arraste as bordas da moldura.")

        SectionTitle("Modo")
        Segments(
            options = listOf(KeyboardMode.FULL to "Completo", KeyboardMode.ONE_HANDED to "Uma mão", KeyboardMode.THUMB to "Polegar"),
            selected = s.mode,
        ) { v -> repo.update { it.copy(mode = v) } }
        if (s.mode == KeyboardMode.ONE_HANDED) {
            Spacer(Modifier.height(8.dp))
            Segments(
                options = listOf(Side.LEFT to "Mão esquerda", Side.RIGHT to "Mão direita"),
                selected = s.oneHandedSide,
            ) { v -> repo.update { it.copy(oneHandedSide = v) } }
        }

        SectionTitle("Previsão")
        SwitchRow("Sugestões de palavras", "Três sugestões acima das teclas enquanto você digita", s.predictions) { v -> repo.update { it.copy(predictions = v) } }
        SwitchRow(
            "Aprender minhas palavras",
            "Melhora as sugestões com o que você escreve. Fica só na memória; nunca em senhas ou no modo privado.",
            s.learnWords,
        ) { v -> repo.update { it.copy(learnWords = v) } }

        SectionTitle("Layout e teclas")
        SwitchRow("Fileira de números", "Números de 1 a 0 acima das letras", s.numberRow) { v -> repo.update { it.copy(numberRow = v) } }
        SwitchRow("Teclas de seta", "Uma fileira com ← ↓ ↑ → abaixo do teclado", s.arrowKeys) { v -> repo.update { it.copy(arrowKeys = v) } }
        SliderRow(
            title = "Manter pressionado",
            value = s.longPressMs.toFloat(),
            range = 200f..800f,
            steps = 11,
            format = { "${it.toInt()} ms" },
        ) { v -> repo.update { it.copy(longPressMs = v.toInt()) } }
        Note("Tempo para abrir acentos e variantes ao segurar uma tecla.")

        SectionTitle("Ao tocar")
        SwitchRow("Vibração", "Um toque leve a cada tecla", s.haptics) { v -> repo.update { it.copy(haptics = v) } }
        SwitchRow("Som", "O clique padrão do sistema", s.sound) { v -> repo.update { it.copy(sound = v) } }
        SwitchRow("Prévia da tecla", "Mostra a letra ampliada acima do dedo", s.keyPreview) { v -> repo.update { it.copy(keyPreview = v) } }
    }
}

// ---- Histórico ------------------------------------------------------------------------------

@Composable
private fun HistorySettings(modifier: Modifier, s: KeyboardSettings, repo: SettingsRepository) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        Note("O histórico fica só na memória do aparelho. Reiniciar o aparelho ou desativar o teclado apaga tudo.")

        SliderRow(
            title = "Nova versão após uma pausa de",
            value = s.debounceMs.toFloat(),
            range = 250f..2000f,
            steps = 6,
            format = { "${it.toInt()} ms" },
        ) { v -> repo.update { it.copy(debounceMs = v.toLong()) } }
        Note("Pausas mais curtas guardam mais versões. Antes de apagar, de enviar e de restaurar, uma versão é guardada na hora.")

        SliderRow(
            title = "Versões por campo",
            value = s.maxVersions.toFloat(),
            range = 20f..200f,
            steps = 17,
            format = { "${it.toInt()}" },
        ) { v -> repo.update { it.copy(maxVersions = v.toInt()) } }
        Note("Ao passar do limite, as versões mais antigas saem primeiro. A versão atual nunca é descartada.")

        SectionTitle("Guardar por")
        Segments(
            options = listOf(15 to "15 min", 60 to "1 h", 360 to "6 h", 1440 to "24 h"),
            selected = s.retentionMinutes,
        ) { v -> repo.update { it.copy(retentionMinutes = v) } }
        Note("Versões mais antigas que isso são apagadas automaticamente.")

        SectionTitle("Gestos")
        SwitchRow(
            "Segurar a barra de espaço",
            "Abre a linha do tempo. Arrastar a barra continua movendo o cursor.",
            s.spaceHoldOpensHistory,
        ) { v -> repo.update { it.copy(spaceHoldOpensHistory = v) } }
    }
}

// ---- Privacidade ----------------------------------------------------------------------------

@Composable
private fun Privacy(modifier: Modifier, s: KeyboardSettings, repo: SettingsRepository, onExcluded: () -> Unit, onErase: () -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        SwitchRow("Modo privado", "Nada do que você digitar é guardado enquanto estiver ligado", s.privateMode) { v ->
            repo.update { it.copy(privateMode = v) }
        }
        NavRow("Apps excluídos", if (s.excludedApps.isEmpty()) "Nenhum" else "${s.excludedApps.size} app(s)", onExcluded)

        SectionTitle("Nunca gravados")
        Bullet("Campos de senha.")
        Bullet("Campos em que o app pede privacidade, como abas anônimas.")
        Bullet("Números, telefones e datas, que costumam ser códigos de verificação e cartões.")
        Note("Essa proteção depende de como cada app identifica os próprios campos. Um campo de senha que o app não marca como senha não é detectado: nesses casos use o modo privado ou exclua o app.")

        SectionTitle("Seus dados")
        Bullet("O histórico fica só na memória deste aparelho e nunca é salvo em disco.")
        Bullet("O app não tem permissão de acesso à Internet e não tem telemetria.")
        Bullet("Nada digitado é escrito nos logs do sistema.")
        Bullet("Palavras aprendidas para as sugestões ficam só na memória e são apagadas junto com o histórico.")
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        ListItem(
            headlineContent = { Text("Apagar todo o histórico", color = MaterialTheme.colorScheme.error) },
            modifier = Modifier.clickableRow(onErase),
        )
    }
}

private data class InstalledApp(val packageName: String, val label: String)

@Composable
private fun ExcludedApps(modifier: Modifier, s: KeyboardSettings, repo: SettingsRepository) {
    val context = LocalContext.current
    val apps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
    var filter by rememberSaveable { mutableStateOf("") }
    val shown = apps.filter { filter.isBlank() || it.label.contains(filter, ignoreCase = true) }
    Column(modifier) {
        Note("O teclado continua funcionando nesses apps, mas não guarda versões.")
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("Buscar app") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(shown, key = { it.packageName }) { app ->
                val checked = app.packageName in s.excludedApps
                val toggle = {
                    repo.update { cur ->
                        cur.copy(excludedApps = if (checked) cur.excludedApps - app.packageName else cur.excludedApps + app.packageName)
                    }
                }
                ListItem(
                    headlineContent = { Text(app.label) },
                    supportingContent = { Text(app.packageName, style = MaterialTheme.typography.bodySmall) },
                    trailingContent = { Checkbox(checked = checked, onCheckedChange = { toggle() }) },
                    modifier = Modifier.clickableRow { toggle() },
                )
            }
        }
    }
}

// ---- Limitações -----------------------------------------------------------------------------

@Composable
private fun Limitations(modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Note("O Time Machine só recupera o que conseguiu ler. Ele depende do que cada app deixa um teclado ver.")
        SectionTitle("Versões completas e parciais")
        Paragraph("Uma versão é completa quando duas leituras independentes do campo concordam sobre o texto inteiro. Quando o app só mostra o trecho perto do cursor, a versão é marcada como parcial: dá para ler, mas ela não substitui o campo, para que nada seja apagado por engano.")
        SectionTitle("Restauração")
        Paragraph("Antes de restaurar, o texto atual é lido por inteiro e guardado como “Antes de restaurar”. Se o campo não puder ser lido por inteiro, ou não permitir selecionar e trocar todo o texto, nada é alterado.")
        Paragraph("Alguns apps reformatam o texto, limitam o tamanho ou trocam o campo depois de enviar. Nesses casos a restauração pode não ser confirmada, e o texto anterior continua salvo na linha do tempo.")
        SectionTitle("O que não é gravado")
        Paragraph("Texto digitado com o teclado fechado, mudanças feitas em outros campos, e qualquer campo protegido (senha, privado, numérico, apps excluídos, modo privado).")
        SectionTitle("Memória")
        Paragraph("Cada campo guarda até o limite de versões definido, por até o tempo de retenção escolhido. Textos acima de 10.000 caracteres são guardados só como parciais. Reiniciar o aparelho ou trocar de teclado por muito tempo pode apagar o histórico.")
    }
}

// ---- componentes ----------------------------------------------------------------------------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp).semantics { heading() },
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun Paragraph(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
        Text("•", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NavRow(title: String, detail: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        trailingContent = { Icon(Chevron, contentDescription = null) },
        colors = ListItemDefaults.colors(),
        modifier = Modifier.clickableRow(onClick),
    )
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleableRow(checked, onChange),
    )
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, format: (Float) -> String, onDone: (Float) -> Unit) {
    var current by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(format(current), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onDone(current) },
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
private fun <T> Segments(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

private fun Modifier.toggleableRow(checked: Boolean, onChange: (Boolean) -> Unit): Modifier =
    this.then(Modifier.toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange))

// Ícones mínimos, para não depender da biblioteca de ícones inteira.
private val BackArrow: ImageVector = ImageVector.Builder("Back", 24.dp, 24.dp, 24f, 24f).path(
    stroke = androidx.compose.ui.graphics.SolidColor(Color.Black), strokeLineWidth = 2f,
    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
) { moveTo(19f, 12f); lineTo(5f, 12f); moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f) }.build()

private val Chevron: ImageVector = ImageVector.Builder("Chevron", 24.dp, 24.dp, 24f, 24f).path(
    stroke = androidx.compose.ui.graphics.SolidColor(Color.Black), strokeLineWidth = 2f,
    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
) { moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f) }.build()

private val Check: ImageVector = ImageVector.Builder("Check", 24.dp, 24.dp, 24f, 24f).path(
    stroke = androidx.compose.ui.graphics.SolidColor(Color.Black), strokeLineWidth = 2.5f,
    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
) { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }.build()
