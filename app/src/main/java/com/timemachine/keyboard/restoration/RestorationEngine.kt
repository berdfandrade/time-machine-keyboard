package com.timemachine.keyboard.restoration

import android.view.inputmethod.InputConnection
import com.timemachine.keyboard.history.Completeness
import com.timemachine.keyboard.history.FieldKey
import com.timemachine.keyboard.history.FieldText
import com.timemachine.keyboard.history.HistoryEngine
import com.timemachine.keyboard.history.HistorySession
import com.timemachine.keyboard.history.Snapshot
import com.timemachine.keyboard.ime.FieldReader

/**
 * Restaura uma versão completa substituindo o texto inteiro do campo.
 *
 * Invariante: nunca apaga o que não conseguiu ler por inteiro. O texto atual é lido e
 * confirmado como completo, guardado como versão ("Antes de restaurar") e só então substituído.
 * A substituição apaga exatamente os trechos lidos antes e depois do cursor, em lote, e o
 * resultado é conferido; se não bater, o texto anterior continua salvo como versão.
 */
class RestorationEngine(
    private val history: HistoryEngine,
    private val read: (InputConnection) -> FieldText? = FieldReader::read,
) {
    enum class Failure {
        NO_CONNECTION,
        NO_SESSION,
        SESSION_MISMATCH,
        NOT_FOUND,
        TARGET_PARTIAL,
        CURRENT_UNREADABLE,
        CURRENT_PARTIAL,
        SELECTION_UNSUPPORTED,
        VERIFY_FAILED,
    }

    sealed interface Result {
        data class Restored(val target: Snapshot, val beforeId: Long) : Result
        /** Comandos enviados, mas o campo ainda não mostra o resultado (pode aplicar depois). */
        data class Unconfirmed(val target: Snapshot, val beforeId: Long) : Result
        /** O campo já mostra essa versão. */
        data object AlreadyCurrent : Result
        data class Failed(val reason: Failure) : Result
    }

    fun restore(ic: InputConnection?, currentKey: FieldKey?, snapshotId: Long): Result {
        if (ic == null) return Result.Failed(Failure.NO_CONNECTION)
        val session = history.activeSession
        val target = session?.graph?.get(snapshotId)
        val current = read(ic)
        validate(session, currentKey, target, current)?.let { return Result.Failed(it) }
        target!!
        current!!
        if (current.text == target.text) {
            history.completeRestore(target.id, target.id)
            return Result.AlreadyCurrent
        }

        val before = history.recordBeforeRestore(current) ?: return Result.Failed(Failure.NO_SESSION)

        // Apaga exatamente o que a leitura completa encontrou antes e depois da seleção, e o
        // commitText troca a própria seleção. Não depende de ler a seleção de volta: editores
        // como o TextField do Compose só aplicam edições no endBatchEdit.
        val textBefore = current.selectionStart
        val textAfter = current.text.length - current.selectionEnd
        ic.beginBatchEdit()
        try {
            if (!ic.deleteSurroundingText(textBefore, textAfter)) {
                return Result.Failed(Failure.SELECTION_UNSUPPORTED)
            }
            ic.commitText(target.text, 1)
            val start = target.selectionStart.coerceIn(0, target.text.length)
            val end = target.selectionEnd.coerceIn(start, target.text.length)
            ic.setSelection(start, end)
        } finally {
            ic.endBatchEdit()
        }
        history.completeRestore(target.id, before.id)
        // Alguns editores aplicam a mudança depois; quem chama confere de novo com confirm().
        return if (confirm(ic, target)) Result.Restored(target, before.id) else Result.Unconfirmed(target, before.id)
    }

    /** O campo mostra exatamente o texto de [target]? */
    fun confirm(ic: InputConnection?, target: Snapshot): Boolean =
        ic != null && read(ic)?.text == target.text

    companion object {
        /** Pré-condições, separadas para teste na JVM. */
        fun validate(
            session: HistorySession?,
            currentKey: FieldKey?,
            target: Snapshot?,
            current: FieldText?,
        ): Failure? = when {
            session == null || currentKey == null -> Failure.NO_SESSION
            session.key != currentKey -> Failure.SESSION_MISMATCH
            target == null || target.sessionId != session.id -> Failure.NOT_FOUND
            target.completeness != Completeness.COMPLETE -> Failure.TARGET_PARTIAL
            current == null -> Failure.CURRENT_UNREADABLE
            current.completeness != Completeness.COMPLETE -> Failure.CURRENT_PARTIAL
            else -> null
        }
    }
}
