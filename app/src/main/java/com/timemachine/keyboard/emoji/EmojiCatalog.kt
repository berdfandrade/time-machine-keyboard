package com.timemachine.keyboard.emoji

import android.content.Context
import android.graphics.Paint
import java.io.InputStream

/** Uma categoria de emojis; cada item é a forma base seguida das variações de tom de pele. */
data class EmojiCategory(val id: String, val label: String, val icon: String, val items: List<List<String>>)

/**
 * Catálogo completo do Unicode (assets/emoji.txt, gerado por tools/build_assets.py), filtrado
 * pelo que a fonte do aparelho consegue desenhar: emojis mais novos que o sistema não aparecem
 * como quadrados vazios.
 */
object EmojiCatalog {
    private val names = mapOf(
        "smileys" to ("Carinhas" to "😀"),
        "people" to ("Pessoas" to "👋"),
        "animals" to ("Animais e natureza" to "🐻"),
        "food" to ("Comida" to "🍔"),
        "travel" to ("Viagem e lugares" to "🚗"),
        "activities" to ("Atividades" to "⚽"),
        "objects" to ("Objetos" to "💡"),
        "symbols" to ("Símbolos" to "❤️"),
        "flags" to ("Bandeiras" to "🏁"),
    )

    fun parse(input: InputStream, isDrawable: (String) -> Boolean = { true }): List<EmojiCategory> {
        val result = ArrayList<EmojiCategory>()
        var id: String? = null
        var items = ArrayList<List<String>>()
        fun flush() {
            val cat = id ?: return
            val (label, icon) = names[cat] ?: (cat to "•")
            if (items.isNotEmpty()) result += EmojiCategory(cat, label, icon, items)
        }
        input.bufferedReader(Charsets.UTF_8).forEachLine { line ->
            when {
                line.startsWith("@") -> { flush(); id = line.substring(1); items = ArrayList() }
                line.isNotBlank() -> {
                    val forms = line.split(' ').filter(isDrawable)
                    if (forms.isNotEmpty() && isDrawable(line.substringBefore(' '))) items += forms
                }
            }
        }
        flush()
        return result
    }

    fun load(context: Context): List<EmojiCategory> {
        val paint = Paint()
        return context.assets.open("emoji.txt").use { parse(it) { e -> paint.hasGlyph(e) } }
    }
}

/** Emojis usados recentemente (só os emojis, nenhum texto), guardados entre sessões. */
class EmojiRecents(context: Context) {
    private val prefs = context.getSharedPreferences("emoji_recents", Context.MODE_PRIVATE)

    fun get(): List<String> = prefs.getString(KEY, "").orEmpty().split(' ').filter { it.isNotEmpty() }

    fun add(emoji: String): List<String> {
        val next = (listOf(emoji) + get().filter { it != emoji }).take(MAX)
        prefs.edit().putString(KEY, next.joinToString(" ")).apply()
        return next
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    private companion object {
        const val KEY = "recent"
        const val MAX = 32
    }
}
