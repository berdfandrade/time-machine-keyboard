package com.timemachine.keyboard.history

/**
 * Regras de agrupamento, limite e expiração das versões. Os valores vêm das configurações do
 * usuário (ver `settings/SettingsRepository.kt`); os padrões são os do MVP.
 */
data class SnapshotPolicy(
    /** Pausa na digitação após a qual uma versão é registrada. */
    val debounceMs: Long = 500,
    val maxVersionsPerSession: Int = 100,
    /** Versões mais antigas que isso são descartadas; sessões inativas por esse tempo também. */
    val retentionMs: Long = 60 * 60_000L,
    /** Sessões (campos) guardadas ao mesmo tempo; as menos recentes saem primeiro. */
    val maxSessions: Int = 8,
    /** Inserção de pelo menos tantos caracteres além dos digitados conta como colagem. */
    val pasteThreshold: Int = 12,
) {
    init {
        require(debounceMs >= 0)
        require(maxVersionsPerSession >= 2)
        require(retentionMs > 0)
        require(maxSessions >= 1)
    }

    /**
     * Classifica a alteração de [previous] para [current]. [typedSinceLast] é quantos caracteres
     * este teclado digitou desde a versão anterior; distingue digitação de colagem e de mudanças
     * feitas pelo app.
     */
    fun classify(previous: Snapshot?, current: FieldText, typedSinceLast: Int): Operation {
        if (previous == null) return Operation.INITIAL
        if (!previous.isComplete || current.completeness != Completeness.COMPLETE) {
            val delta = current.text.length - previous.text.length
            return when {
                typedSinceLast == 0 -> Operation.EXTERNAL
                delta > 0 -> Operation.INSERT
                delta < 0 -> Operation.DELETE
                else -> Operation.REPLACE
            }
        }
        val diff = TextDiff.between(previous.text, current.text)
        val inserted = diff.inserted.length
        val removed = diff.removed.length
        return when {
            inserted >= typedSinceLast + pasteThreshold -> Operation.PASTE
            typedSinceLast == 0 -> Operation.EXTERNAL
            removed > 0 && inserted == 0 -> Operation.DELETE
            inserted > 0 && removed == 0 -> Operation.INSERT
            else -> Operation.REPLACE
        }
    }
}
