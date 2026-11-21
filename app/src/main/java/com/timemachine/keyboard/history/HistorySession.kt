package com.timemachine.keyboard.history

/** O histórico de um campo de texto. */
class HistorySession(
    val id: Long,
    val key: FieldKey,
    createdAt: Long,
) {
    val graph = VersionGraph()

    var lastActivity: Long = createdAt
        private set

    sealed interface RecordResult {
        data class Added(val snapshot: Snapshot) : RecordResult
        /** O texto é idêntico a uma versão existente: o head passa a ser ela, sem duplicar. */
        data class HeadMoved(val snapshot: Snapshot) : RecordResult
        data object Unchanged : RecordResult
    }

    fun record(
        reading: FieldText,
        now: Long,
        policy: SnapshotPolicy,
        typedSinceLast: Int,
        newId: () -> Long,
        operation: Operation? = null,
    ): RecordResult {
        lastActivity = now
        val head = graph.head
        if (head != null && head.text == reading.text && head.completeness == reading.completeness) {
            return RecordResult.Unchanged
        }
        graph.findIdentical(reading.text, reading.completeness)?.let {
            graph.moveHead(it.id)
            return RecordResult.HeadMoved(it)
        }
        val snapshot = Snapshot(
            id = newId(),
            sessionId = id,
            parentId = graph.headId,
            timestamp = now,
            text = reading.text,
            selectionStart = reading.selectionStart,
            selectionEnd = reading.selectionEnd,
            completeness = reading.completeness,
            operation = operation ?: policy.classify(head, reading, typedSinceLast),
            origin = key.packageName,
        )
        graph.append(snapshot)
        prune(now, policy)
        return RecordResult.Added(snapshot)
    }

    /** Aplica expiração e limite de versões. O head (estado atual) nunca é descartado aqui. */
    fun prune(now: Long, policy: SnapshotPolicy) {
        graph.all()
            .filter { it.id != graph.headId && now - it.timestamp > policy.retentionMs }
            .forEach { graph.remove(it.id) }
        while (graph.size > policy.maxVersionsPerSession) {
            val oldest = graph.all().first { it.id != graph.headId }
            graph.remove(oldest.id)
        }
    }

    fun touch(now: Long) {
        lastActivity = now
    }
}
