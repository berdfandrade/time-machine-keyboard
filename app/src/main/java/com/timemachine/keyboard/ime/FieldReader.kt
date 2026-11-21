package com.timemachine.keyboard.ime

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.timemachine.keyboard.history.Completeness
import com.timemachine.keyboard.history.FieldText

/**
 * Lê o estado de um campo pelo InputConnection e decide se a leitura é integral.
 *
 * Nenhuma das APIs garante o texto inteiro: `getExtractedText` pode vir truncado ou nulo, e
 * `getTextBefore/AfterCursor` podem ser limitados pelo app. Por isso a leitura só é
 * [Completeness.COMPLETE] quando as duas fontes concordam: o texto extraído começa no offset 0
 * e o texto antes/depois do cursor bate exatamente com ele. Qualquer dúvida vira PARTIAL.
 */
object FieldReader {
    /** Campos maiores que isso não são gravados como completos (limita memória). */
    const val MAX_CHARS = 10_000

    fun read(ic: InputConnection?): FieldText? {
        if (ic == null) return null
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        val before = ic.getTextBeforeCursor(MAX_CHARS + 1, 0)?.toString()
        val selected = ic.getSelectedText(0)?.toString()
        val after = ic.getTextAfterCursor(MAX_CHARS + 1, 0)?.toString()
        return assess(
            extractedText = extracted?.text?.toString(),
            extractedStartOffset = extracted?.startOffset ?: -1,
            extractedSelectionStart = extracted?.selectionStart ?: -1,
            extractedSelectionEnd = extracted?.selectionEnd ?: -1,
            before = before,
            selected = selected,
            after = after,
        )
    }

    /** Regra de completude, separada da leitura para teste na JVM. */
    fun assess(
        extractedText: String?,
        extractedStartOffset: Int,
        extractedSelectionStart: Int,
        extractedSelectionEnd: Int,
        before: String?,
        selected: String?,
        after: String?,
    ): FieldText? {
        if (extractedText != null && extractedStartOffset == 0 && extractedText.length <= MAX_CHARS) {
            val start = minOf(extractedSelectionStart, extractedSelectionEnd)
            val end = maxOf(extractedSelectionStart, extractedSelectionEnd)
            val selectionValid = start >= 0 && end <= extractedText.length
            if (selectionValid && before != null && after != null &&
                before == extractedText.substring(0, start) &&
                after == extractedText.substring(end) &&
                (selected == null || selected == extractedText.substring(start, end))
            ) {
                return FieldText(extractedText, start, end, Completeness.COMPLETE)
            }
        }
        // Sem confirmação: guarda só a janela ao redor do cursor, marcada como parcial.
        if (before == null && after == null) return null
        val head = before.orEmpty().takeLast(MAX_CHARS / 2)
        val sel = selected.orEmpty().take(MAX_CHARS / 4)
        val tail = after.orEmpty().take(MAX_CHARS / 2)
        return FieldText(head + sel + tail, head.length, head.length + sel.length, Completeness.PARTIAL)
    }
}
