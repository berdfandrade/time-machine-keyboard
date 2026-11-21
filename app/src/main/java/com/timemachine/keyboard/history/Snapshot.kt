package com.timemachine.keyboard.history

/**
 * Se o texto capturado é o conteúdo integral do campo.
 *
 * Só [COMPLETE] pode ser restaurado por substituição integral. [PARTIAL] significa que o app
 * hospedeiro não deixou confirmar o início e o fim do texto (ver `ime/FieldReader.kt`).
 */
enum class Completeness { COMPLETE, PARTIAL }

/** Tipo de alteração detectada em relação à versão anterior. */
enum class Operation {
    INITIAL,
    INSERT,
    DELETE,
    REPLACE,
    /** Inserção grande que não veio do teclado (normalmente colagem). */
    PASTE,
    /** Alteração feita pelo app ou por outra fonte, sem digitação deste teclado. */
    EXTERNAL,
    /** Estado do campo salvo imediatamente antes de uma restauração (permite desfazer). */
    BEFORE_RESTORE,
}

/** Estado do campo lido do InputConnection. A seleção é relativa a [text]. */
data class FieldText(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val completeness: Completeness,
)

/**
 * Uma versão imutável do texto. [parentId] liga as versões num grafo (ver [VersionGraph]):
 * restaurar uma versão antiga e continuar escrevendo cria um ramo, sem apagar os demais.
 *
 * [origin] é o pacote do app hospedeiro, guardado só para exibição local.
 */
data class Snapshot(
    val id: Long,
    val sessionId: Long,
    val parentId: Long?,
    val timestamp: Long,
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val completeness: Completeness,
    val operation: Operation,
    val origin: String?,
) {
    val isComplete: Boolean get() = completeness == Completeness.COMPLETE
}

/** Identidade de um campo de texto, derivada do EditorInfo. */
data class FieldKey(
    val packageName: String,
    val fieldId: Int,
    /** Classe + variação do inputType; flags não fazem parte da identidade. */
    val inputKind: Int,
)
