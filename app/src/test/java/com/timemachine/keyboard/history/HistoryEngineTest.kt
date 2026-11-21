package com.timemachine.keyboard.history

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryEngineTest {
    private val keyA = FieldKey("app", 1, 1)
    private val keyB = FieldKey("app", 2, 1)

    /** Campo falso: o "app" muda [text]; o motor lê pelo reader. */
    private class FakeField(var text: String = "", var complete: Boolean = true) {
        var reads = 0
        fun read(): FieldText {
            reads++
            return FieldText(text, text.length, text.length, if (complete) Completeness.COMPLETE else Completeness.PARTIAL)
        }
    }

    private fun TestScope.engine(policy: SnapshotPolicy = SnapshotPolicy(debounceMs = 500)) =
        HistoryEngine(this, clock = { testScheduler.currentTime }, policy = policy)

    @Test fun beginCapturesInitialState() = runTest {
        val field = FakeField("rascunho")
        val e = engine()
        e.begin(keyA, field::read)
        assertEquals(CaptureStatus.Recording, e.state.value.status)
        assertEquals(listOf("rascunho"), e.state.value.versions.map { it.text })
        assertEquals(Operation.INITIAL, e.state.value.versions.single().operation)
    }

    @Test fun debounceGroupsTypingIntoOneVersion() = runTest {
        val field = FakeField()
        val e = engine()
        e.begin(keyA, field::read)
        for (c in "ola") {
            field.text += c
            e.onOwnEdit(1)
            advanceTimeBy(200)
        }
        assertEquals(1, e.state.value.versions.size) // ainda digitando
        advanceTimeBy(501)
        runCurrent()
        assertEquals(listOf("", "ola"), e.state.value.versions.map { it.text })
        assertEquals(Operation.INSERT, e.state.value.versions.last().operation)
    }

    @Test fun captureNowFlushesBeforeDestructiveEdit() = runTest {
        val field = FakeField()
        val e = engine()
        e.begin(keyA, field::read)
        field.text = "frase importante"
        e.onOwnEdit(16)
        e.captureNow() // antes do primeiro apagar
        field.text = ""
        e.onOwnEdit(1)
        advanceTimeBy(600)
        assertEquals(listOf("", "frase importante"), e.state.value.versions.map { it.text }.take(2))
        // "" é idêntico à versão inicial: o head volta para ela, sem duplicar
        assertEquals(2, e.state.value.versions.size)
        assertEquals(e.state.value.versions.first().id, e.state.value.headId)
    }

    @Test fun externalChangeIsCapturedAndClassified() = runTest {
        val field = FakeField("oi")
        val e = engine()
        e.begin(keyA, field::read)
        field.text = "oi, texto colado de outro lugar"
        e.onFieldChanged()
        advanceTimeBy(600)
        assertEquals(Operation.PASTE, e.state.value.versions.last().operation)
    }

    @Test fun blockedFieldIsNeverRead() = runTest {
        val field = FakeField("segredo")
        val e = engine()
        e.block(BlockReason.PASSWORD)
        e.onOwnEdit(1)
        e.onFieldChanged()
        e.captureNow()
        advanceTimeBy(1_000)
        assertEquals(0, field.reads)
        assertEquals(CaptureStatus.Blocked(BlockReason.PASSWORD), e.state.value.status)
        assertTrue(e.state.value.versions.isEmpty())
    }

    @Test fun endFlushesPendingAndStopsReading() = runTest {
        val field = FakeField()
        val e = engine()
        e.begin(keyA, field::read)
        field.text = "quase"
        e.onOwnEdit(5)
        e.end()
        assertEquals(CaptureStatus.Inactive, e.state.value.status)
        val readsAfterEnd = field.reads
        e.onOwnEdit(1)
        advanceTimeBy(1_000)
        assertEquals(readsAfterEnd, field.reads)
        // retomando o mesmo campo, a versão pendente está lá
        e.begin(keyA, field::read)
        assertEquals(listOf("", "quase"), e.state.value.versions.map { it.text })
    }

    @Test fun differentFieldsHaveSeparateSessions() = runTest {
        val a = FakeField("campo A")
        val b = FakeField("campo B")
        val e = engine()
        e.begin(keyA, a::read)
        val sessionA = e.state.value.sessionId
        e.begin(keyB, b::read)
        assertNotEquals(sessionA, e.state.value.sessionId)
        assertEquals(listOf("campo B"), e.state.value.versions.map { it.text })
    }

    @Test fun inactiveSessionExpires() = runTest {
        val field = FakeField("x")
        val e = engine(SnapshotPolicy(retentionMs = 1_000))
        e.begin(keyA, field::read)
        val first = e.state.value.sessionId
        e.end()
        advanceTimeBy(2_000)
        e.pruneExpired()
        assertEquals(0, e.totalVersions())
        e.begin(keyA, field::read)
        assertNotEquals(first, e.state.value.sessionId)
    }

    @Test fun maxSessionsDropsLeastRecent() = runTest {
        val e = engine(SnapshotPolicy(maxSessions = 2))
        for (i in 1..3) e.begin(FieldKey("app", i, 1)) { FieldText("t$i", 0, 0, Completeness.COMPLETE) }
        e.end()
        assertEquals(2, e.totalVersions())
    }

    @Test fun deleteAndClear() = runTest {
        val field = FakeField()
        val e = engine()
        e.begin(keyA, field::read)
        field.text = "um"; e.captureNow()
        field.text = "um dois"; e.captureNow()
        val middle = e.state.value.versions[1].id
        assertTrue(e.deleteSnapshot(middle))
        assertEquals(listOf("", "um dois"), e.state.value.versions.map { it.text })
        e.clearSession()
        assertTrue(e.state.value.versions.isEmpty())
        field.text = "tres"; e.captureNow()
        e.clearAll()
        assertEquals(0, e.totalVersions())
        assertNull(e.state.value.lastRestore)
    }

    @Test fun restoreThenEditCreatesBranch() = runTest {
        val field = FakeField()
        val e = engine()
        e.begin(keyA, field::read)
        field.text = "A"; e.captureNow()
        val a = e.state.value.headId!!
        field.text = "AB"; e.captureNow()
        field.text = "ABC"; e.captureNow()
        val before = e.recordBeforeRestore(field.read())!!
        field.text = "A"
        e.completeRestore(a, before.id)
        assertEquals(a, e.state.value.headId)
        assertEquals(before.id, e.state.value.lastRestore!!.beforeId)
        field.text = "AD"; e.captureNow()
        val d = e.state.value.versions.last()
        assertEquals(a, d.parentId)
        assertEquals(listOf("", "A", "AB", "ABC", "AD"), e.state.value.versions.map { it.text })
    }

    @Test fun partialReadingIsMarked() = runTest {
        val field = FakeField("janela", complete = false)
        val e = engine()
        e.begin(keyA, field::read)
        assertEquals(Completeness.PARTIAL, e.state.value.versions.single().completeness)
    }
}
