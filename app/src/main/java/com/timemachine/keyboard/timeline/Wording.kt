package com.timemachine.keyboard.timeline

import com.timemachine.keyboard.history.BlockReason
import com.timemachine.keyboard.history.Operation
import com.timemachine.keyboard.restoration.RestorationEngine.Failure
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Textos exibidos ao usuário. Diretos, sem jargão, e honestos sobre o que não foi feito. */
object Wording {
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun time(timestamp: Long): String = clock.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

    fun relative(timestamp: Long, now: Long): String {
        val s = ((now - timestamp) / 1000).coerceAtLeast(0)
        return when {
            s < 5 -> "agora"
            s < 60 -> "há $s s"
            s < 3600 -> "há ${s / 60} min"
            else -> "há ${s / 3600} h"
        }
    }

    fun operation(op: Operation): String = when (op) {
        Operation.INITIAL -> "Início"
        Operation.INSERT -> "Digitação"
        Operation.DELETE -> "Exclusão"
        Operation.REPLACE -> "Substituição"
        Operation.PASTE -> "Colagem"
        Operation.EXTERNAL -> "Alterado pelo app"
        Operation.BEFORE_RESTORE -> "Antes de restaurar"
    }

    fun versions(count: Int): String = if (count == 1) "1 versão" else "$count versões"

    fun blocked(reason: BlockReason): String = when (reason) {
        BlockReason.PRIVATE_MODE -> "Modo privado"
        BlockReason.EXCLUDED_APP -> "App excluído"
        BlockReason.PASSWORD -> "Senha · sem histórico"
        BlockReason.NO_PERSONALIZED_LEARNING -> "Campo privado"
        BlockReason.NUMERIC_FIELD -> "Campo numérico"
        BlockReason.UNSUPPORTED_FIELD -> "Campo sem suporte"
    }

    fun blockedDetail(reason: BlockReason): String = when (reason) {
        BlockReason.PRIVATE_MODE -> "O modo privado está ligado. Nada digitado é guardado."
        BlockReason.EXCLUDED_APP -> "Você excluiu este app do histórico."
        BlockReason.PASSWORD -> "Campos de senha nunca são gravados."
        BlockReason.NO_PERSONALIZED_LEARNING -> "Este app pediu que o teclado não guarde o que é digitado aqui."
        BlockReason.NUMERIC_FIELD -> "Números, telefones e datas não são gravados: costumam ser códigos e cartões."
        BlockReason.UNSUPPORTED_FIELD -> "Este campo não expõe texto ao teclado."
    }

    fun failure(f: Failure): String = when (f) {
        Failure.NO_CONNECTION -> "O campo não está mais conectado. Nada foi alterado."
        Failure.NO_SESSION, Failure.SESSION_MISMATCH -> "Esta versão é de outro campo. Nada foi alterado."
        Failure.NOT_FOUND -> "Esta versão não existe mais."
        Failure.TARGET_PARTIAL -> "Versão parcial: o app não deixou ler o campo inteiro, então ela não pode substituí-lo."
        Failure.CURRENT_UNREADABLE, Failure.CURRENT_PARTIAL ->
            "Não foi possível ler o campo inteiro agora. Nada foi alterado."
        Failure.SELECTION_UNSUPPORTED -> "Este campo não permite trocar o texto inteiro. Nada foi alterado."
        Failure.VERIFY_FAILED -> "O app não confirmou a troca. O texto anterior está salvo na linha do tempo."
    }
}
