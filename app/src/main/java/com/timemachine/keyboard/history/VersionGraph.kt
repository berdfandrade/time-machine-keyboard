package com.timemachine.keyboard.history

/**
 * Grafo de versões de uma sessão: cada versão aponta para a anterior ([Snapshot.parentId]) e
 * [headId] marca a versão atual. Novas versões são filhas do head; mover o head para uma versão
 * antiga e seguir editando cria um ramo.
 *
 * Remover uma versão religa seus filhos ao pai dela, então o histórico continua conectado.
 * A ordem de inserção é a ordem temporal. Não é thread-safe: usado só na thread principal.
 */
class VersionGraph {
    private val nodes = LinkedHashMap<Long, Snapshot>()

    var headId: Long? = null
        private set

    val head: Snapshot? get() = headId?.let(nodes::get)
    val size: Int get() = nodes.size

    fun all(): List<Snapshot> = nodes.values.toList()
    operator fun get(id: Long): Snapshot? = nodes[id]
    operator fun contains(id: Long): Boolean = id in nodes

    /** Adiciona [snapshot] como filho do head atual e o torna head. */
    fun append(snapshot: Snapshot) {
        require(snapshot.id !in nodes) { "duplicate snapshot id" }
        require(snapshot.parentId == headId) { "a new version must descend from the head" }
        nodes[snapshot.id] = snapshot
        headId = snapshot.id
    }

    fun moveHead(id: Long) {
        require(id in nodes) { "unknown snapshot" }
        headId = id
    }

    /** Versão com exatamente esse conteúdo, se existir (a mais recente). */
    fun findIdentical(text: String, completeness: Completeness): Snapshot? =
        nodes.values.lastOrNull { it.text == text && it.completeness == completeness }

    fun children(id: Long): List<Snapshot> = nodes.values.filter { it.parentId == id }

    /** Caminho do head até a raiz, do mais recente para o mais antigo. */
    fun headPath(): List<Snapshot> {
        val path = ArrayList<Snapshot>()
        var cursor = head
        while (cursor != null) {
            path += cursor
            cursor = cursor.parentId?.let(nodes::get)
        }
        return path
    }

    /**
     * Remove a versão [id]. Os filhos passam a apontar para o pai dela. Se era o head, o head passa
     * para o pai ou, sem pai, para a versão mais recente que restar.
     */
    fun remove(id: Long): Boolean {
        val removed = nodes[id] ?: return false
        for (child in children(id)) {
            // put em chave existente preserva a ordem do LinkedHashMap
            nodes[child.id] = child.copy(parentId = removed.parentId)
        }
        nodes.remove(id)
        if (headId == id) {
            headId = removed.parentId?.takeIf { it in nodes } ?: nodes.keys.lastOrNull()
        }
        return true
    }

    fun clear() {
        nodes.clear()
        headId = null
    }
}
