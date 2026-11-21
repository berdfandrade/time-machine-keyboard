package com.timemachine.keyboard.timeline

import com.timemachine.keyboard.history.Snapshot

/** Posição de uma versão no trilho: [column] é a ordem temporal, [lane] a faixa do ramo. */
data class RailNode(val snapshot: Snapshot, val column: Int, val lane: Int)

data class RailEdge(val from: RailNode, val to: RailNode)

/**
 * Disposição do grafo de versões em faixas, no estilo de um `git log --graph` deitado.
 * A faixa 0 é o caminho até a versão atual; cada ramo fora dele ganha a próxima faixa livre, e
 * o primeiro filho de um ramo continua na faixa do pai.
 */
data class RailLayout(val nodes: List<RailNode>, val edges: List<RailEdge>, val laneCount: Int) {
    companion object {
        /** Faixas além desta são desenhadas na última, para o trilho caber no teclado. */
        const val MAX_LANES = 4

        fun of(versions: List<Snapshot>, headId: Long?): RailLayout {
            val byId = versions.associateBy { it.id }
            val main = HashSet<Long>()
            var cursor = headId?.let(byId::get)
            while (cursor != null && main.add(cursor.id)) cursor = cursor.parentId?.let(byId::get)

            val laneOf = HashMap<Long, Int>()
            val continued = HashSet<Long>() // pais cujo ramo já tem continuação
            var nextLane = 1
            val nodes = versions.mapIndexed { column, s ->
                val lane = if (s.id in main) 0 else {
                    val parentLane = s.parentId?.let(laneOf::get)
                    if (parentLane != null && parentLane != 0 && continued.add(s.parentId)) parentLane
                    else nextLane++
                }
                laneOf[s.id] = lane
                RailNode(s, column, minOf(lane, MAX_LANES - 1))
            }
            val nodeById = nodes.associateBy { it.snapshot.id }
            val edges = nodes.mapNotNull { n -> n.snapshot.parentId?.let(nodeById::get)?.let { RailEdge(it, n) } }
            return RailLayout(nodes, edges, (nodes.maxOfOrNull { it.lane } ?: 0) + 1)
        }
    }
}
