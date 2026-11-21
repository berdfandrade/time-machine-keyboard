package com.timemachine.keyboard.history

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Por que o teclado não está gravando o campo atual. Exibido ao usuário, nunca silencioso. */
enum class BlockReason {
    PRIVATE_MODE,
    EXCLUDED_APP,
    PASSWORD,
    /** O app pediu IME_FLAG_NO_PERSONALIZED_LEARNING (ex.: aba anônima). */
    NO_PERSONALIZED_LEARNING,
    /** Número, telefone ou data: costumam ser PIN, código de verificação ou cartão. */
    NUMERIC_FIELD,
    /** Campo sem texto editável (TYPE_NULL), como terminais. */
    UNSUPPORTED_FIELD,
}

sealed interface CaptureStatus {
    /** Nenhum campo em edição com o teclado visível. */
    data object Inactive : CaptureStatus
    data object Recording : CaptureStatus
    data class Blocked(val reason: BlockReason) : CaptureStatus
}

/** Uma restauração recente, para oferecer "Desfazer". */
data class RestoreMarker(val beforeId: Long, val targetId: Long, val at: Long)

data class HistoryState(
    val status: CaptureStatus = CaptureStatus.Inactive,
    val sessionId: Long? = null,
    val origin: String? = null,
    /** Versões da sessão ativa, em ordem temporal. */
    val versions: List<Snapshot> = emptyList(),
    val headId: Long? = null,
    val lastRestore: RestoreMarker? = null,
)

/**
 * Coordena sessões e captura de versões. Toda chamada acontece na thread principal (a mesma do
 * InputMethodService); [scope] deve usar um dispatcher de thread principal.
 *
 * O histórico vive só em memória. Nada aqui registra texto em log.
 */
class HistoryEngine(
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    policy: SnapshotPolicy = SnapshotPolicy(),
) {
    var policy: SnapshotPolicy = policy
        set(value) {
            field = value
            pruneExpired()
        }

    private val sessions = LinkedHashMap<Long, HistorySession>()
    private var active: HistorySession? = null
    private var reader: (() -> FieldText?)? = null
    private var pending: Job? = null
    private var typedSinceLast = 0
    private var lastRestore: RestoreMarker? = null
    private var nextId = 1L

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    val activeKey: FieldKey? get() = active?.key
    val activeSession: HistorySession? get() = active

    /**
     * Começa (ou retoma) a gravação de um campo. Retoma a sessão do mesmo campo se ela teve
     * atividade dentro do período de retenção. [reader] lê o estado atual do campo, ou null se
     * a conexão não estiver disponível.
     */
    fun begin(key: FieldKey, reader: () -> FieldText?) {
        end()
        pruneExpired()
        val now = clock()
        val session = sessions.values.lastOrNull { it.key == key && now - it.lastActivity <= policy.retentionMs }
            ?: HistorySession(newId(), key, now)
        session.touch(now)
        sessions.remove(session.id)
        sessions[session.id] = session // fim = mais recente
        while (sessions.size > policy.maxSessions) sessions.remove(sessions.keys.first())

        active = session
        this.reader = reader
        typedSinceLast = 0
        lastRestore = null
        capture(status = CaptureStatus.Recording)
    }

    /** O campo atual não pode ser gravado. Encerra qualquer sessão ativa. */
    fun block(reason: BlockReason) {
        end()
        publish(CaptureStatus.Blocked(reason))
    }

    /** Fim da edição: grava o que estiver pendente e para de ler o campo. */
    fun end() {
        if (pending != null) captureNow()
        pending?.cancel()
        pending = null
        active = null
        reader = null
        publish(CaptureStatus.Inactive)
    }

    /** O teclado inseriu ou apagou [chars] caracteres. */
    fun onOwnEdit(chars: Int) {
        if (active == null) return
        typedSinceLast += chars
        lastRestore = null
        schedule()
    }

    /** O texto ou a seleção mudou (por este teclado ou não). */
    fun onFieldChanged() {
        if (active == null) return
        schedule()
    }

    /** Grava agora, sem esperar a pausa. Usado antes de alterações destrutivas. */
    fun captureNow(): Snapshot? {
        pending?.cancel()
        pending = null
        return capture()
    }

    /**
     * Guarda o estado do campo imediatamente antes de uma restauração.
     * Retorna a versão que representa esse estado (nova ou idêntica a uma existente).
     */
    fun recordBeforeRestore(reading: FieldText): Snapshot? {
        val session = active ?: return null
        pending?.cancel()
        pending = null
        session.record(reading, clock(), policy, typedSinceLast, ::newId, Operation.BEFORE_RESTORE)
        typedSinceLast = 0
        publish()
        return session.graph.head
    }

    /** O campo agora mostra [targetId]; edições seguintes descendem dele (podem formar um ramo). */
    fun completeRestore(targetId: Long, beforeId: Long) {
        val session = active ?: return
        if (targetId !in session.graph) return
        session.graph.moveHead(targetId)
        lastRestore = RestoreMarker(beforeId, targetId, clock())
        publish()
    }

    fun deleteSnapshot(id: Long): Boolean {
        val session = active ?: return false
        val removed = session.graph.remove(id)
        if (removed) publish()
        return removed
    }

    /** Apaga o histórico do campo atual. A próxima alteração recomeça a gravação. */
    fun clearSession() {
        val session = active ?: return
        pending?.cancel()
        pending = null
        session.graph.clear()
        lastRestore = null
        publish()
    }

    /** Apaga todo o histórico de todos os campos. */
    fun clearAll() {
        pending?.cancel()
        pending = null
        sessions.values.forEach { it.graph.clear() }
        sessions.keys.retainAll(setOfNotNull(active?.id))
        lastRestore = null
        publish()
    }

    /** Descarta versões e sessões fora do período de retenção. */
    fun pruneExpired() {
        val now = clock()
        val activeId = active?.id
        sessions.values.removeAll { it.id != activeId && now - it.lastActivity > policy.retentionMs }
        sessions.values.forEach { it.prune(now, policy) }
        publish()
    }

    /** Roda [pruneExpired] periodicamente enquanto [scope] existir. */
    fun startExpiryTimer(periodMs: Long = 60_000L): Job = scope.launch {
        while (true) {
            delay(periodMs)
            pruneExpired()
        }
    }

    /** Quantas versões estão em memória no total (todas as sessões). */
    fun totalVersions(): Int = sessions.values.sumOf { it.graph.size }

    private fun schedule() {
        pending?.cancel()
        pending = scope.launch {
            delay(policy.debounceMs)
            pending = null
            capture()
        }
    }

    private fun capture(status: CaptureStatus? = null): Snapshot? {
        val session = active
        val reading = session?.let { reader?.invoke() }
        if (session != null && reading != null) {
            session.record(reading, clock(), policy, typedSinceLast, ::newId)
            typedSinceLast = 0
        }
        publish(status)
        return session?.graph?.head
    }

    private fun publish(status: CaptureStatus? = null) {
        val session = active
        _state.value = HistoryState(
            status = status ?: _state.value.status,
            sessionId = session?.id,
            origin = session?.key?.packageName,
            versions = session?.graph?.all().orEmpty(),
            headId = session?.graph?.headId,
            lastRestore = lastRestore,
        )
    }

    private fun newId(): Long = nextId++
}
