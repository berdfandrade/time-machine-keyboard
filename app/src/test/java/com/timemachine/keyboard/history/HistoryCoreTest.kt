package com.timemachine.keyboard.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun complete(text: String, cursor: Int = text.length) = FieldText(text, cursor, cursor, Completeness.COMPLETE)
private fun partial(text: String) = FieldText(text, text.length, text.length, Completeness.PARTIAL)

class VersionGraphTest {
    private var id = 0L
    private fun snap(parent: Long?, text: String) =
        Snapshot(++id, 1, parent, id, text, 0, 0, Completeness.COMPLETE, Operation.INSERT, null)

    @Test fun appendMovesHead() {
        val g = VersionGraph()
        val a = snap(null, "a").also(g::append)
        val b = snap(a.id, "ab").also(g::append)
        assertEquals(b.id, g.headId)
        assertEquals(listOf(b, a), g.headPath())
    }

    @Test(expected = IllegalArgumentException::class)
    fun appendRejectsNonHeadParent() {
        val g = VersionGraph()
        snap(null, "a").also(g::append)
        g.append(snap(999, "x"))
    }

    @Test fun removeReparentsChildren() {
        val g = VersionGraph()
        val a = snap(null, "a").also(g::append)
        val b = snap(a.id, "ab").also(g::append)
        val c = snap(b.id, "abc").also(g::append)
        assertTrue(g.remove(b.id))
        assertEquals(a.id, g[c.id]!!.parentId)
        assertEquals(listOf(c.id, a.id), g.headPath().map { it.id })
    }

    @Test fun removingHeadFallsBackToParent() {
        val g = VersionGraph()
        val a = snap(null, "a").also(g::append)
        val b = snap(a.id, "ab").also(g::append)
        g.remove(b.id)
        assertEquals(a.id, g.headId)
    }

    @Test fun branchesKeepOldVersions() {
        val g = VersionGraph()
        val a = snap(null, "A").also(g::append)
        val b = snap(a.id, "B").also(g::append)
        val c = snap(b.id, "C").also(g::append)
        g.moveHead(a.id)
        val d = snap(a.id, "D").also(g::append)
        assertEquals(4, g.size)
        assertEquals(listOf(b.id, d.id), g.children(a.id).map { it.id })
        assertEquals(c, g[c.id])
    }
}

class HistorySessionTest {
    private val key = FieldKey("app", 1, 1)
    private var next = 1L
    private val newId = { next++ }

    @Test fun deduplicatesIdenticalHead() {
        val s = HistorySession(100, key, 0)
        val p = SnapshotPolicy()
        assertTrue(s.record(complete("oi"), 1, p, 2, newId) is HistorySession.RecordResult.Added)
        assertEquals(HistorySession.RecordResult.Unchanged, s.record(complete("oi"), 2, p, 0, newId))
        assertEquals(1, s.graph.size)
    }

    @Test fun identicalToOlderVersionMovesHeadInsteadOfDuplicating() {
        val s = HistorySession(100, key, 0)
        val p = SnapshotPolicy()
        s.record(complete(""), 1, p, 0, newId)
        val first = (s.record(complete("abc"), 2, p, 3, newId) as HistorySession.RecordResult.Added).snapshot
        s.record(complete("abcdef"), 3, p, 3, newId)
        val result = s.record(complete("abc"), 4, p, 0, newId)
        assertEquals(HistorySession.RecordResult.HeadMoved(first), result)
        assertEquals(3, s.graph.size)
        assertEquals(first.id, s.graph.headId)
    }

    @Test fun enforcesVersionLimitWithoutDroppingHead() {
        val s = HistorySession(100, key, 0)
        val p = SnapshotPolicy(maxVersionsPerSession = 3)
        for (i in 1..10) s.record(complete("v$i"), i.toLong(), p, 2, newId)
        assertEquals(3, s.graph.size)
        assertEquals("v10", s.graph.head!!.text)
        assertEquals(listOf("v8", "v9", "v10"), s.graph.all().map { it.text })
    }

    @Test fun expiresOldVersionsButKeepsHead() {
        val s = HistorySession(100, key, 0)
        val p = SnapshotPolicy(retentionMs = 1_000)
        s.record(complete("a"), 0, p, 1, newId)
        s.record(complete("ab"), 100, p, 1, newId)
        s.prune(5_000, p)
        assertEquals(listOf("ab"), s.graph.all().map { it.text })
    }

    @Test fun partialAndCompleteWithSameTextAreDistinct() {
        val s = HistorySession(100, key, 0)
        val p = SnapshotPolicy()
        s.record(complete("x"), 1, p, 1, newId)
        assertTrue(s.record(partial("x"), 2, p, 0, newId) is HistorySession.RecordResult.Added)
        assertFalse(s.graph.head!!.isComplete)
    }
}

class SnapshotPolicyTest {
    private val p = SnapshotPolicy(pasteThreshold = 12)
    private fun prev(text: String, complete: Boolean = true) = Snapshot(
        1, 1, null, 0, text, 0, 0,
        if (complete) Completeness.COMPLETE else Completeness.PARTIAL, Operation.INSERT, null,
    )

    @Test fun classifiesOperations() {
        assertEquals(Operation.INITIAL, p.classify(null, complete("a"), 0))
        assertEquals(Operation.INSERT, p.classify(prev("ola"), complete("ola mundo"), 6))
        assertEquals(Operation.DELETE, p.classify(prev("ola mundo"), complete("ola"), 6))
        assertEquals(Operation.REPLACE, p.classify(prev("ola mundo"), complete("ola gente"), 5))
        assertEquals(Operation.PASTE, p.classify(prev("a"), complete("a texto colado bem grande"), 0))
        assertEquals(Operation.EXTERNAL, p.classify(prev("mensagem"), complete(""), 0))
    }

    @Test fun partialReadingsUseLengthOnly() {
        assertEquals(Operation.INSERT, p.classify(prev("ab", complete = false), partial("abc"), 1))
        assertEquals(Operation.EXTERNAL, p.classify(prev("ab"), partial("abc"), 0))
    }
}

class TextDiffTest {
    @Test fun singleRegion() {
        val d = TextDiff.between("ola mundo cruel", "ola gente cruel")
        assertEquals("ola ", d.prefix)
        assertEquals("mundo", d.removed)
        assertEquals("gente", d.inserted)
        assertEquals(" cruel", d.suffix)
    }

    @Test fun identicalIsEmpty() = assertTrue(TextDiff.between("abc", "abc").isEmpty)

    @Test fun doesNotSplitSurrogatePairs() {
        val d = TextDiff.between("a😀", "a😁")
        assertEquals("a", d.prefix)
        assertEquals("😀", d.removed)
        assertEquals("😁", d.inserted)
    }
}

class RailLayoutTest {
    private fun s(id: Long, parent: Long?) = Snapshot(id, 1, parent, id, "$id", 0, 0, Completeness.COMPLETE, Operation.INSERT, null)

    @Test fun headPathIsLaneZeroAndBranchGetsNewLane() {
        // A → B → C, restaura A, A → D (head)
        val versions = listOf(s(1, null), s(2, 1), s(3, 2), s(4, 1))
        val layout = com.timemachine.keyboard.timeline.RailLayout.of(versions, headId = 4)
        val lane = layout.nodes.associate { it.snapshot.id to it.lane }
        assertEquals(0, lane[1]); assertEquals(0, lane[4])
        assertEquals(1, lane[2]); assertEquals(1, lane[3])
        assertEquals(2, layout.laneCount)
        assertEquals(3, layout.edges.size)
    }

    @Test fun emptyHistory() {
        val layout = com.timemachine.keyboard.timeline.RailLayout.of(emptyList(), null)
        assertTrue(layout.nodes.isEmpty())
        assertNull(layout.nodes.firstOrNull())
    }
}
