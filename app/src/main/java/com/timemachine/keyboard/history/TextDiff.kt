package com.timemachine.keyboard.history

/**
 * Diferença entre dois textos como uma única região alterada: prefixo comum, trecho removido,
 * trecho inserido e sufixo comum. É O(n) e exata para o caso típico entre versões vizinhas
 * (uma edição contígua). Para várias edições distantes, mostra a região que as engloba.
 */
data class TextDiff(
    val prefix: String,
    val removed: String,
    val inserted: String,
    val suffix: String,
) {
    val isEmpty: Boolean get() = removed.isEmpty() && inserted.isEmpty()

    companion object {
        fun between(old: String, new: String): TextDiff {
            val max = minOf(old.length, new.length)
            var prefix = 0
            while (prefix < max && old[prefix] == new[prefix]) prefix++
            // não corta um par substituto (emoji) ao meio
            if (prefix > 0 && Character.isHighSurrogate(old[prefix - 1])) prefix--

            var suffix = 0
            while (suffix < max - prefix &&
                old[old.length - 1 - suffix] == new[new.length - 1 - suffix]
            ) suffix++
            if (suffix > 0 && Character.isLowSurrogate(old[old.length - suffix])) suffix--

            return TextDiff(
                prefix = old.substring(0, prefix),
                removed = old.substring(prefix, old.length - suffix),
                inserted = new.substring(prefix, new.length - suffix),
                suffix = old.substring(old.length - suffix),
            )
        }
    }
}
