package com.timemachine.keyboard.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo

enum class KeyboardPage { LETTERS, SYMBOLS, SYMBOLS_ALT, NUMBER, PHONE }

/** Variação do campo que muda teclas da última fileira. */
enum class FieldVariant { TEXT, EMAIL, URI }

enum class KeyStyle { CHARACTER, FUNCTION, SPACE, ACTION }

sealed interface KeyAction {
    data class Text(val text: String) : KeyAction
    data object Shift : KeyAction
    data object Backspace : KeyAction
    data object Space : KeyAction
    data object Enter : KeyAction
    /** Abre o painel de emojis. */
    data object Emoji : KeyAction
    data class Page(val page: KeyboardPage) : KeyAction
    /** Tecla de seta: envia o KeyEvent DPAD correspondente. Repete ao segurar. */
    data class Arrow(val keyCode: Int) : KeyAction
    // Gerados por gestos, não por teclas
    data object OpenHistory : KeyAction
    data class MoveCursor(val steps: Int) : KeyAction
}

data class Key(
    val action: KeyAction,
    val label: String? = null,
    val glyph: Glyph? = null,
    /** Largura em unidades; cada fileira soma 10. */
    val width: Float = 1f,
    val style: KeyStyle = KeyStyle.CHARACTER,
    /** Variantes exibidas ao manter pressionado; a primeira é a própria tecla. */
    val variants: List<String> = emptyList(),
    /** Nome falado pelo leitor de tela, quando difere do rótulo. */
    val description: String? = null,
)

data class KeyboardLayout(val page: KeyboardPage, val rows: List<List<Key>>)

/** O que a tecla Enter faz neste campo (ver [EnterAction.resolve]). */
data class EnterAction(val kind: Kind, val actionId: Int = EditorInfo.IME_ACTION_NONE, val customLabel: String? = null) {
    enum class Kind { NEWLINE, ENTER_KEY, EDITOR_ACTION }

    val label: String?
        get() = customLabel ?: when (kind) {
            Kind.EDITOR_ACTION -> when (actionId) {
                EditorInfo.IME_ACTION_GO -> "Ir"
                EditorInfo.IME_ACTION_SEND -> "Enviar"
                EditorInfo.IME_ACTION_NEXT -> "Próximo"
                EditorInfo.IME_ACTION_PREVIOUS -> "Anterior"
                EditorInfo.IME_ACTION_DONE -> "OK"
                else -> null
            }
            else -> null
        }

    val glyph: Glyph?
        get() = when {
            label != null -> null
            kind == Kind.EDITOR_ACTION && actionId == EditorInfo.IME_ACTION_SEARCH -> Glyph.SEARCH
            else -> Glyph.RETURN
        }

    val description: String
        get() = label ?: when {
            kind == Kind.EDITOR_ACTION && actionId == EditorInfo.IME_ACTION_SEARCH -> "Buscar"
            kind == Kind.NEWLINE -> "Nova linha"
            else -> "Enter"
        }

    /** Só ações do editor (Enviar, Buscar…) ganham destaque; Enter comum é tecla de função. */
    val isProminent: Boolean get() = kind == Kind.EDITOR_ACTION

    companion object {
        fun resolve(inputType: Int, imeOptions: Int, actionLabel: CharSequence? = null): EnterAction {
            val inputClass = inputType and InputType.TYPE_MASK_CLASS
            val multiLine = inputClass == InputType.TYPE_CLASS_TEXT &&
                inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
            val action = imeOptions and EditorInfo.IME_MASK_ACTION
            val noEnterAction = imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
            if (noEnterAction || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
                return EnterAction(if (multiLine) Kind.NEWLINE else Kind.ENTER_KEY)
            }
            return EnterAction(Kind.EDITOR_ACTION, action, actionLabel?.toString()?.takeIf { it.isNotBlank() })
        }
    }
}

object Layouts {
    private val accents = mapOf(
        "a" to listOf("a", "á", "ã", "â", "à", "ä", "ª"),
        "e" to listOf("e", "é", "ê", "è", "ë"),
        "i" to listOf("i", "í", "î", "ì", "ï"),
        "o" to listOf("o", "ó", "õ", "ô", "ò", "ö", "º"),
        "u" to listOf("u", "ú", "ü", "ù", "û"),
        "c" to listOf("c", "ç"),
        "n" to listOf("n", "ñ"),
    )

    /** Página inicial para o tipo de campo. */
    fun pageFor(inputType: Int): KeyboardPage = when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_NUMBER -> KeyboardPage.NUMBER
        InputType.TYPE_CLASS_PHONE -> KeyboardPage.PHONE
        InputType.TYPE_CLASS_DATETIME -> KeyboardPage.SYMBOLS
        else -> KeyboardPage.LETTERS
    }

    fun variantFor(inputType: Int): FieldVariant {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return FieldVariant.TEXT
        return when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> FieldVariant.EMAIL
            InputType.TYPE_TEXT_VARIATION_URI -> FieldVariant.URI
            else -> FieldVariant.TEXT
        }
    }

    fun build(
        page: KeyboardPage,
        variant: FieldVariant,
        enter: EnterAction,
        numberRow: Boolean = false,
        arrowKeys: Boolean = false,
    ): KeyboardLayout {
        val base = when (page) {
            KeyboardPage.LETTERS -> listOf(
                chars("qwertyuiop"),
                chars("asdfghjklç"),
                listOf(shiftKey()) + chars("zxcvbnm") + backspace(),
                bottomRow(pageKey("?123", KeyboardPage.SYMBOLS, "Números e símbolos"), variant, enter),
            )
            KeyboardPage.SYMBOLS -> listOf(
                symbols("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                symbols("@", "#", "R$", "_", "&", "-", "+", "(", ")", "/"),
                listOf(pageKey("=\\<", KeyboardPage.SYMBOLS_ALT, "Mais símbolos", 1.5f)) +
                    symbols("*", "\"", "'", ":", ";", "!", "?") + backspace(),
                bottomRow(pageKey("ABC", KeyboardPage.LETTERS, "Letras"), variant, enter),
            )
            KeyboardPage.SYMBOLS_ALT -> listOf(
                symbols("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆"),
                symbols("€", "£", "¥", "$", "¢", "^", "°", "=", "{", "}"),
                listOf(pageKey("?123", KeyboardPage.SYMBOLS, "Números e símbolos", 1.5f)) +
                    symbols("\\", "©", "®", "™", "%", "[", "]") + backspace(),
                bottomRow(pageKey("ABC", KeyboardPage.LETTERS, "Letras"), variant, enter),
            )
            KeyboardPage.NUMBER -> listOf(
                pad("1", "2", "3", "-"),
                pad("4", "5", "6", ","),
                pad("7", "8", "9") + backspace(2.5f),
                listOf(pageKey("?123", KeyboardPage.SYMBOLS, "Símbolos", 2.5f)) + pad("0", ".") + enterKey(enter, 2.5f),
            )
            KeyboardPage.PHONE -> listOf(
                pad("1", "2", "3", "-"),
                pad("4", "5", "6", "(").let { it.dropLast(1) + it.last().copy(variants = listOf("(", ")")) },
                pad("7", "8", "9") + backspace(2.5f),
                listOf(
                    Key(KeyAction.Text("+"), "+", width = 2.5f, variants = listOf("+", "*")),
                    Key(KeyAction.Text("0"), "0", width = 2.5f),
                    Key(KeyAction.Text("#"), "#", width = 2.5f, variants = listOf("#", ";", ",")),
                ) + enterKey(enter, 2.5f),
            )
        }
        val textPage = page == KeyboardPage.LETTERS || page == KeyboardPage.SYMBOLS || page == KeyboardPage.SYMBOLS_ALT
        val rows = buildList {
            if (numberRow && page == KeyboardPage.LETTERS) add(symbols("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"))
            addAll(base)
            if (arrowKeys && textPage) add(arrowRow())
        }
        return KeyboardLayout(page, rows)
    }

    private fun arrowRow(): List<Key> = listOf(
        Key(KeyAction.Arrow(android.view.KeyEvent.KEYCODE_DPAD_LEFT), glyph = Glyph.CHEVRON_LEFT, width = 2.5f, style = KeyStyle.FUNCTION, description = "Cursor para a esquerda"),
        Key(KeyAction.Arrow(android.view.KeyEvent.KEYCODE_DPAD_DOWN), glyph = Glyph.CHEVRON_DOWN, width = 2.5f, style = KeyStyle.FUNCTION, description = "Cursor para baixo"),
        Key(KeyAction.Arrow(android.view.KeyEvent.KEYCODE_DPAD_UP), glyph = Glyph.CHEVRON_UP, width = 2.5f, style = KeyStyle.FUNCTION, description = "Cursor para cima"),
        Key(KeyAction.Arrow(android.view.KeyEvent.KEYCODE_DPAD_RIGHT), glyph = Glyph.CHEVRON_RIGHT, width = 2.5f, style = KeyStyle.FUNCTION, description = "Cursor para a direita"),
    )

    private fun chars(letters: String) = letters.map { c ->
        val s = c.toString()
        Key(KeyAction.Text(s), s, variants = accents[s].orEmpty())
    }

    private fun symbols(vararg s: String) = s.map { sym ->
        val variants = when (sym) {
            "-" -> listOf("-", "–", "—")
            "\"" -> listOf("\"", "“", "”", "«", "»")
            "'" -> listOf("'", "‘", "’")
            "?" -> listOf("?", "¿")
            "!" -> listOf("!", "¡")
            "1" -> listOf("1", "¹", "½", "¼")
            "2" -> listOf("2", "²")
            "3" -> listOf("3", "³", "¾")
            "0" -> listOf("0", "°")
            else -> emptyList()
        }
        Key(KeyAction.Text(sym), sym, variants = variants, description = if (sym == "R$") "Real" else null)
    }

    private fun pad(vararg s: String) = s.map { Key(KeyAction.Text(it), it, width = 2.5f) }

    private fun shiftKey() = Key(KeyAction.Shift, glyph = Glyph.SHIFT, width = 1.5f, style = KeyStyle.FUNCTION, description = "Maiúsculas")

    private fun backspace(width: Float = 1.5f) =
        Key(KeyAction.Backspace, glyph = Glyph.BACKSPACE, width = width, style = KeyStyle.FUNCTION, description = "Apagar")

    private fun pageKey(label: String, page: KeyboardPage, description: String, width: Float = 1.5f) =
        Key(KeyAction.Page(page), label, width = width, style = KeyStyle.FUNCTION, description = description)

    private fun enterKey(enter: EnterAction, width: Float) = Key(
        KeyAction.Enter, enter.label, enter.glyph, width,
        if (enter.isProminent) KeyStyle.ACTION else KeyStyle.FUNCTION,
        description = enter.description,
    )

    /**
     * Última fileira: modo, emoji, vírgula, espaço, ponto, Enter. Sem tecla de trocar teclado:
     * o Android já mostra esse botão na barra de navegação, e a tecla fazia trocar sem querer.
     */
    private fun bottomRow(mode: Key, variant: FieldVariant, enter: EnterAction): List<Key> {
        val punctuation = when (variant) {
            FieldVariant.EMAIL -> Key(KeyAction.Text("@"), "@")
            FieldVariant.URI -> Key(KeyAction.Text("/"), "/")
            FieldVariant.TEXT -> Key(KeyAction.Text(","), ",", variants = listOf(",", ";", ":"), description = "Vírgula")
        }
        val period = when (variant) {
            FieldVariant.URI, FieldVariant.EMAIL ->
                Key(KeyAction.Text("."), ".", variants = listOf(".", ".com", ".com.br", ".br", ".org"), description = "Ponto")
            FieldVariant.TEXT ->
                Key(KeyAction.Text("."), ".", variants = listOf(".", "…", "?", "!", "-", "'", "\""), description = "Ponto")
        }
        val emoji = Key(KeyAction.Emoji, glyph = Glyph.SMILE, style = KeyStyle.FUNCTION, description = "Emojis")
        val enterKey = enterKey(enter, 1.5f)
        val fixed = listOf(mode, emoji, punctuation, period, enterKey)
        val spaceWidth = 10f - fixed.sumOf { it.width.toDouble() }.toFloat()
        val space = Key(KeyAction.Space, "português", width = spaceWidth, style = KeyStyle.SPACE, description = "Espaço")
        return listOf(mode, emoji, punctuation, space, period, enterKey)
    }
}
