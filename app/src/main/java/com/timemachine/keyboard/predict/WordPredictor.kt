package com.timemachine.keyboard.predict

import java.io.InputStream
import java.text.Normalizer
import kotlin.math.ln

/**
 * Dicionário de palavras com frequência, ordenado pela forma normalizada (minúsculas, sem acento)
 * para buscar por prefixo com busca binária: "nao" encontra "não", "acao" encontra "ação".
 */
class WordDictionary(entries: List<Pair<String, Long>>) {
    class Entry(val key: String, val word: String, val frequency: Long)

    private val sorted: Array<Entry> = entries
        .map { (w, f) -> Entry(normalize(w), w, f) }
        .groupBy { it.key }
        .values
        // Grafias que só diferem por acento: descarta as raras ("nao", "nâo" ao lado de "não"),
        // que na lista de origem são erros de digitação. "e" e "é" continuam, ambas são comuns.
        .flatMap { group ->
            val top = group.maxOf { it.frequency }
            group.filter { it.frequency * VARIANT_MIN_SHARE >= top }
        }
        .sortedWith(compareBy<Entry> { it.key }.thenByDescending { it.frequency })
        .toTypedArray()

    private val byWord = sorted.associateBy { it.word }

    val size: Int get() = sorted.size

    fun frequency(word: String): Long = byWord[word]?.frequency ?: 0

    /** Palavras cuja forma normalizada começa com [prefixKey], da mais à menos frequente. */
    fun completions(prefixKey: String, limit: Int): List<Entry> {
        if (prefixKey.isEmpty()) return emptyList()
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid].key < prefixKey) lo = mid + 1 else hi = mid
        }
        val best = ArrayList<Entry>(limit + 1)
        var i = lo
        var scanned = 0
        while (i < sorted.size && sorted[i].key.startsWith(prefixKey) && scanned < MAX_SCAN) {
            val e = sorted[i]
            if (best.size < limit || e.frequency > best.last().frequency) {
                best += e
                best.sortByDescending { it.frequency }
                if (best.size > limit) best.removeAt(best.lastIndex)
            }
            i++
            scanned++
        }
        return best
    }

    companion object {
        private const val MAX_SCAN = 20_000
        /** Uma grafia precisa de ao menos 1/20 da frequência da mais comum com o mesmo "esqueleto". */
        private const val VARIANT_MIN_SHARE = 20
        private val marks = Regex("\\p{Mn}+")

        fun normalize(s: String): String =
            marks.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")

        /** Lê "palavra<TAB>frequência" por linha. */
        fun load(input: InputStream): WordDictionary {
            val entries = ArrayList<Pair<String, Long>>(40_000)
            input.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) entries += line.substring(0, tab) to (line.substring(tab + 1).toLongOrNull() ?: 0)
            }
            return WordDictionary(entries)
        }
    }
}

/**
 * Sugestões de palavras, sem rede e sem IA: completa a palavra atual pelo dicionário e pelo que
 * a pessoa costuma digitar, e prevê a próxima palavra pelas sequências já usadas.
 *
 * O que é aprendido fica só na memória, com limite de tamanho, e é apagado junto com o histórico.
 * Quem chama decide quando é permitido aprender (nunca em senha, modo privado etc.).
 */
class WordPredictor(@Volatile var dictionary: WordDictionary? = null) {
    private val learned = lruMap<String, Int>(MAX_LEARNED)
    private val bigrams = lruMap<String, MutableMap<String, Int>>(MAX_LEARNED)

    /** Até [limit] sugestões, a melhor primeiro, no mesmo padrão de maiúsculas que [current]. */
    fun suggest(current: String, previous: String?, limit: Int = 3): List<String> {
        val prev = previous?.lowercase()
        if (current.isEmpty()) {
            // próxima palavra: só pelo que a pessoa já escreveu depois de [previous]
            val next = prev?.let { bigrams[it] } ?: return emptyList()
            return next.entries.sortedByDescending { it.value }.take(limit).map { it.key }
        }
        val key = WordDictionary.normalize(current)
        val scores = HashMap<String, Double>()
        dictionary?.completions(key, 24)?.forEach { scores[it.word] = ln(1.0 + it.frequency) }
        for ((word, count) in learned) {
            if (WordDictionary.normalize(word).startsWith(key)) {
                scores[word] = (scores[word] ?: ln(1.0 + 1_000)) + 3.0 * count
            }
        }
        prev?.let { bigrams[it] }?.forEach { (word, count) -> scores.computeIfPresent(word) { _, s -> s + 6.0 * count } }
        return scores.entries.sortedByDescending { it.value }.take(limit).map { matchCase(it.key, current) }
    }

    /** Registra que [word] foi escrita (depois de [previous]). */
    fun learn(word: String, previous: String?) {
        val w = word.lowercase()
        if (w.length < 2 || w.length > 32 || !w.all { it.isLetter() || it == '-' }) return
        learned[w] = (learned[w] ?: 0) + 1
        val prev = previous?.lowercase()?.takeIf { it.isNotEmpty() } ?: return
        val next = bigrams.getOrPut(prev) { lruMap(MAX_NEXT) }
        next[w] = (next[w] ?: 0) + 1
    }

    fun clear() {
        learned.clear()
        bigrams.clear()
    }

    companion object {
        private const val MAX_LEARNED = 5_000
        private const val MAX_NEXT = 16

        private fun <K, V> lruMap(max: Int): MutableMap<K, V> =
            object : LinkedHashMap<K, V>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > max
            }

        /** "Nao" → "Não"; "NAO" → "NÃO"; "nao" → "não". */
        fun matchCase(word: String, typed: String): String = when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
            typed.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercase() }
            else -> word
        }

        /** Palavra que termina no cursor e a palavra anterior, a partir do texto antes do cursor. */
        fun splitContext(before: String): Pair<String, String?> {
            var end = before.length
            var start = end
            while (start > 0 && isWordChar(before[start - 1])) start--
            val current = before.substring(start, end)
            end = start
            while (end > 0 && before[end - 1] == ' ') end--
            // a palavra anterior só conta se estiver na mesma frase (separada só por espaços)
            if (end == start && start > 0) return current to null
            start = end
            while (start > 0 && isWordChar(before[start - 1])) start--
            val previous = before.substring(start, end).takeIf { it.isNotEmpty() }
            return current to previous
        }

        fun isWordChar(c: Char) = c.isLetter() || c == '-' || c == '\''
    }
}
