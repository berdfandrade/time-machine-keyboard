package com.timemachine.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.timemachine.keyboard.history.Completeness
import com.timemachine.keyboard.history.FieldKey
import com.timemachine.keyboard.history.HistoryEngine
import com.timemachine.keyboard.history.Operation
import com.timemachine.keyboard.history.SnapshotPolicy
import com.timemachine.keyboard.ime.FieldReader
import com.timemachine.keyboard.restoration.RestorationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integração com campos de texto reais: EditText e o InputConnection que o próprio Android
 * entrega a um teclado. Cobre leitura completa, cursor, seleção, colagem/alteração externa,
 * troca de campo, conexão inválida e restauração.
 */
@RunWith(AndroidJUnit4::class)
class InputConnectionIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After fun tearDown() = scope.cancel()

    private class Field(val view: EditText, val ic: InputConnection, val key: FieldKey)

    private fun field(id: Int, text: String = ""): Field {
        val view = EditText(context).apply {
            this.id = id
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(text)
            setSelection(text.length)
        }
        val ic = view.onCreateInputConnection(EditorInfo())!!
        return Field(view, ic, FieldKey(context.packageName, id, InputType.TYPE_CLASS_TEXT))
    }

    private fun <T> main(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private fun engine() = HistoryEngine(scope, policy = SnapshotPolicy(debounceMs = 50))

    @Test fun readsWholeFieldWithCursorInTheMiddle() = main {
        val f = field(1, "ola mundo cruel")
        f.view.setSelection(3)
        val r = FieldReader.read(f.ic)!!
        assertEquals(Completeness.COMPLETE, r.completeness)
        assertEquals("ola mundo cruel", r.text)
        assertEquals(3, r.selectionStart)
        assertEquals(3, r.selectionEnd)
    }

    @Test fun readsSelection() = main {
        val f = field(1, "ola mundo cruel")
        f.view.setSelection(4, 9)
        val r = FieldReader.read(f.ic)!!
        assertEquals(Completeness.COMPLETE, r.completeness)
        assertEquals(4 to 9, r.selectionStart to r.selectionEnd)
    }

    @Test fun pasteAndExternalChangesAreCaptured() = main {
        val f = field(1, "oi")
        val e = engine()
        e.begin(f.key) { FieldReader.read(f.ic) }
        // o app (não o teclado) insere um trecho grande: colagem
        f.view.text.insert(2, ", aqui vai um texto colado")
        e.captureNow()
        assertEquals(Operation.PASTE, e.state.value.versions.last().operation)
        // o app limpa o campo, como ao enviar uma mensagem
        f.view.setText("")
        e.captureNow()
        assertEquals(Operation.EXTERNAL, e.state.value.versions.last().operation)
        assertEquals(3, e.state.value.versions.size)
    }

    @Test fun cursorMovementAloneDoesNotCreateVersions() = main {
        val f = field(1, "texto fixo")
        val e = engine()
        e.begin(f.key) { FieldReader.read(f.ic) }
        f.view.setSelection(2); e.captureNow()
        f.view.setSelection(5, 8); e.captureNow()
        assertEquals(1, e.state.value.versions.size)
    }

    @Test fun restoresCompleteVersionWithoutDuplicatingText() = main {
        val f = field(1)
        val e = engine()
        val restoration = RestorationEngine(e)
        e.begin(f.key) { FieldReader.read(f.ic) }
        f.ic.commitText("primeira frase. ", 1); e.captureNow()
        val target = e.state.value.headId!!
        f.ic.commitText("segunda frase.", 1); e.captureNow()
        f.ic.deleteSurroundingText(100, 0); e.captureNow() // apagou tudo
        f.ic.commitText("outra coisa", 1)

        val result = restoration.restore(f.ic, f.key, target)

        assertTrue("$result", result is RestorationEngine.Result.Restored)
        assertEquals("primeira frase. ", f.view.text.toString())
        assertEquals(16, f.view.selectionStart)
        // o texto substituído foi guardado e pode ser restaurado de volta
        val before = (result as RestorationEngine.Result.Restored).beforeId
        assertEquals("outra coisa", e.state.value.versions.first { it.id == before }.text)
        assertEquals(target, e.state.value.headId)

        val undo = restoration.restore(f.ic, f.key, before)
        assertTrue("$undo", undo is RestorationEngine.Result.Restored)
        assertEquals("outra coisa", f.view.text.toString())
    }

    @Test fun restoresWhenCurrentTextHasSelectionAndCursorInside() = main {
        val f = field(1)
        val e = engine()
        val restoration = RestorationEngine(e)
        e.begin(f.key) { FieldReader.read(f.ic) }
        f.ic.commitText("abc", 1); e.captureNow()
        val target = e.state.value.headId!!
        f.ic.commitText(" def ghi", 1); e.captureNow()
        f.view.setSelection(4, 7) // "def" selecionado no meio

        val result = restoration.restore(f.ic, f.key, target)

        assertTrue("$result", result is RestorationEngine.Result.Restored)
        assertEquals("abc", f.view.text.toString())
    }

    @Test fun refusesSnapshotFromAnotherField() = main {
        val a = field(1, "campo A")
        val b = field(2, "campo B")
        val e = engine()
        val restoration = RestorationEngine(e)
        e.begin(a.key) { FieldReader.read(a.ic) }
        val fromA = e.state.value.headId!!
        a.ic.commitText(" editado", 1); e.captureNow()

        // troca de campo: nova sessão
        e.begin(b.key) { FieldReader.read(b.ic) }
        assertEquals(listOf("campo B"), e.state.value.versions.map { it.text })

        val result = restoration.restore(b.ic, b.key, fromA)
        assertEquals(RestorationEngine.Result.Failed(RestorationEngine.Failure.NOT_FOUND), result)
        assertEquals("campo B", b.view.text.toString())

        val mismatch = restoration.restore(b.ic, a.key, e.state.value.headId!!)
        assertEquals(RestorationEngine.Result.Failed(RestorationEngine.Failure.SESSION_MISMATCH), mismatch)
        assertEquals("campo B", b.view.text.toString())
    }

    @Test fun refusesWithoutConnection() = main {
        val f = field(1, "x")
        val e = engine()
        e.begin(f.key) { FieldReader.read(f.ic) }
        val result = RestorationEngine(e).restore(null, f.key, e.state.value.headId!!)
        assertEquals(RestorationEngine.Result.Failed(RestorationEngine.Failure.NO_CONNECTION), result)
    }

    @Test fun refusesWhenCurrentFieldCannotBeReadInFull() = main {
        val f = field(1)
        val e = engine()
        e.begin(f.key) { FieldReader.read(f.ic) }
        f.ic.commitText("versao", 1); e.captureNow()
        val target = e.state.value.headId!!
        f.ic.commitText(" atual", 1)
        // leitor que simula um app que só expõe o trecho perto do cursor
        val restoration = RestorationEngine(e) { ic ->
            FieldReader.read(ic)?.copy(completeness = Completeness.PARTIAL)
        }
        val result = restoration.restore(f.ic, f.key, target)
        assertEquals(RestorationEngine.Result.Failed(RestorationEngine.Failure.CURRENT_PARTIAL), result)
        assertEquals("versao atual", f.view.text.toString())
    }

    @Test fun clearAllRemovesEverything() = main {
        val f = field(1, "algo")
        val e = engine()
        e.begin(f.key) { FieldReader.read(f.ic) }
        f.ic.commitText(" mais", 1); e.captureNow()
        assertNotNull(e.state.value.headId)
        e.clearAll()
        assertEquals(0, e.totalVersions())
    }
}
