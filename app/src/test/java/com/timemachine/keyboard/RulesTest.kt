package com.timemachine.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.timemachine.keyboard.history.BlockReason
import com.timemachine.keyboard.history.Completeness
import com.timemachine.keyboard.history.FieldKey
import com.timemachine.keyboard.history.FieldText
import com.timemachine.keyboard.history.HistorySession
import com.timemachine.keyboard.history.SnapshotPolicy
import com.timemachine.keyboard.ime.FieldReader
import com.timemachine.keyboard.keyboard.EnterAction
import com.timemachine.keyboard.keyboard.FieldVariant
import com.timemachine.keyboard.keyboard.KeyAction
import com.timemachine.keyboard.keyboard.KeyboardPage
import com.timemachine.keyboard.keyboard.Layouts
import com.timemachine.keyboard.keyboard.ShiftController
import com.timemachine.keyboard.keyboard.ShiftState
import com.timemachine.keyboard.privacy.PrivacyPolicy
import com.timemachine.keyboard.restoration.RestorationEngine
import com.timemachine.keyboard.restoration.RestorationEngine.Failure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPolicyTest {
    private val text = InputType.TYPE_CLASS_TEXT
    private fun eval(type: Int, options: Int = 0, pkg: String = "app", private: Boolean = false, excluded: Set<String> = emptySet()) =
        PrivacyPolicy.evaluate(type, options, pkg, private, excluded)

    @Test fun plainTextIsAllowed() = assertNull(eval(text or InputType.TYPE_TEXT_FLAG_MULTI_LINE))

    @Test fun passwordsAreBlocked() {
        listOf(
            text or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
        ).forEach { assertEquals(BlockReason.PASSWORD, eval(it)) }
    }

    @Test fun appPrivacyFlagIsRespected() =
        assertEquals(BlockReason.NO_PERSONALIZED_LEARNING, eval(text, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))

    @Test fun numericFieldsAreBlocked() {
        assertEquals(BlockReason.NUMERIC_FIELD, eval(InputType.TYPE_CLASS_NUMBER))
        assertEquals(BlockReason.NUMERIC_FIELD, eval(InputType.TYPE_CLASS_PHONE))
        assertEquals(BlockReason.NUMERIC_FIELD, eval(InputType.TYPE_CLASS_DATETIME))
        assertEquals(BlockReason.UNSUPPORTED_FIELD, eval(InputType.TYPE_NULL))
    }

    @Test fun userControlsWin() {
        assertEquals(BlockReason.PRIVATE_MODE, eval(text, private = true))
        assertEquals(BlockReason.EXCLUDED_APP, eval(text, pkg = "banco", excluded = setOf("banco")))
    }
}

class FieldReaderTest {
    @Test fun completeWhenSourcesAgree() {
        val r = FieldReader.assess("ola mundo", 0, 3, 3, "ola", null, " mundo")!!
        assertEquals(Completeness.COMPLETE, r.completeness)
        assertEquals("ola mundo", r.text)
        assertEquals(3, r.selectionStart)
    }

    @Test fun completeWithSelection() {
        val r = FieldReader.assess("ola mundo", 0, 4, 9, "ola ", "mundo", "")!!
        assertEquals(Completeness.COMPLETE, r.completeness)
        assertEquals(4 to 9, r.selectionStart to r.selectionEnd)
    }

    @Test fun partialWhenExtractedTextIsMissing() {
        val r = FieldReader.assess(null, -1, -1, -1, "antes", null, "depois")!!
        assertEquals(Completeness.PARTIAL, r.completeness)
        assertEquals("antesdepois", r.text)
    }

    @Test fun partialWhenExtractedDoesNotStartAtZero() {
        val r = FieldReader.assess("mundo", 4, 5, 5, "ola mundo", null, "")!!
        assertEquals(Completeness.PARTIAL, r.completeness)
    }

    @Test fun partialWhenSourcesDisagree() {
        // o app truncou o texto antes do cursor
        val r = FieldReader.assess("um texto longo", 0, 14, 14, "longo", null, "")!!
        assertEquals(Completeness.PARTIAL, r.completeness)
    }

    @Test fun partialWhenTooLarge() {
        val big = "a".repeat(FieldReader.MAX_CHARS + 1)
        val r = FieldReader.assess(big, 0, big.length, big.length, big.take(FieldReader.MAX_CHARS + 1), null, "")!!
        assertEquals(Completeness.PARTIAL, r.completeness)
        assertTrue(r.text.length <= FieldReader.MAX_CHARS)
    }

    @Test fun nullWhenNothingReadable() = assertNull(FieldReader.assess(null, -1, -1, -1, null, null, null))
}

class RestorationValidationTest {
    private val key = FieldKey("app", 1, 1)
    private val session = HistorySession(7, key, 0)
    private var next = 1L

    init {
        session.record(FieldText("completo", 8, 8, Completeness.COMPLETE), 1, SnapshotPolicy(), 8, { next++ })
        session.record(FieldText("janela", 6, 6, Completeness.PARTIAL), 2, SnapshotPolicy(), 0, { next++ })
    }

    private val completeTarget get() = session.graph.all().first { it.isComplete }
    private val partialTarget get() = session.graph.all().first { !it.isComplete }
    private val readable = FieldText("atual", 5, 5, Completeness.COMPLETE)

    @Test fun okWhenEverythingMatches() =
        assertNull(RestorationEngine.validate(session, key, completeTarget, readable))

    @Test fun rejectsOtherField() =
        assertEquals(Failure.SESSION_MISMATCH, RestorationEngine.validate(session, FieldKey("app", 2, 1), completeTarget, readable))

    @Test fun rejectsNoSession() =
        assertEquals(Failure.NO_SESSION, RestorationEngine.validate(null, key, completeTarget, readable))

    @Test fun rejectsPartialTarget() =
        assertEquals(Failure.TARGET_PARTIAL, RestorationEngine.validate(session, key, partialTarget, readable))

    @Test fun neverReplacesWhatCannotBeReadInFull() {
        assertEquals(Failure.CURRENT_UNREADABLE, RestorationEngine.validate(session, key, completeTarget, null))
        assertEquals(
            Failure.CURRENT_PARTIAL,
            RestorationEngine.validate(session, key, completeTarget, FieldText("x", 1, 1, Completeness.PARTIAL)),
        )
    }

    @Test fun rejectsSnapshotFromAnotherSession() {
        val foreign = completeTarget.copy(sessionId = 99)
        assertEquals(Failure.NOT_FOUND, RestorationEngine.validate(session, key, foreign, readable))
    }
}

class KeyboardRulesTest {
    @Test fun enterFollowsEditorInfo() {
        val text = InputType.TYPE_CLASS_TEXT
        assertEquals(EnterAction.Kind.NEWLINE, EnterAction.resolve(text or InputType.TYPE_TEXT_FLAG_MULTI_LINE, EditorInfo.IME_FLAG_NO_ENTER_ACTION).kind)
        assertEquals(EnterAction.Kind.ENTER_KEY, EnterAction.resolve(text, EditorInfo.IME_ACTION_UNSPECIFIED).kind)
        val send = EnterAction.resolve(text, EditorInfo.IME_ACTION_SEND)
        assertEquals(EnterAction.Kind.EDITOR_ACTION, send.kind)
        assertEquals("Enviar", send.label)
        assertTrue(send.isProminent)
        val search = EnterAction.resolve(text, EditorInfo.IME_ACTION_SEARCH)
        assertEquals("Buscar", search.description)
        assertNull(search.label)
        assertEquals("Ir agora", EnterAction.resolve(text, EditorInfo.IME_ACTION_GO, "Ir agora").label)
        assertEquals("Próximo", EnterAction.resolve(text, EditorInfo.IME_ACTION_NEXT).label)
        assertEquals("OK", EnterAction.resolve(text, EditorInfo.IME_ACTION_DONE).label)
    }

    @Test fun pageAndVariantFollowInputType() {
        assertEquals(KeyboardPage.NUMBER, Layouts.pageFor(InputType.TYPE_CLASS_NUMBER))
        assertEquals(KeyboardPage.PHONE, Layouts.pageFor(InputType.TYPE_CLASS_PHONE))
        assertEquals(KeyboardPage.LETTERS, Layouts.pageFor(InputType.TYPE_CLASS_TEXT))
        assertEquals(FieldVariant.EMAIL, Layouts.variantFor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertEquals(FieldVariant.URI, Layouts.variantFor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI))
    }

    @Test fun everyRowFillsTenUnits() {
        val enter = EnterAction(EnterAction.Kind.NEWLINE)
        for (page in KeyboardPage.entries) for (variant in FieldVariant.entries) {
            val layout = Layouts.build(page, variant, enter)
            layout.rows.forEachIndexed { i, row ->
                assertEquals("$page/$variant row $i", 10.0, row.sumOf { it.width.toDouble() }, 0.001)
            }
        }
    }

    @Test fun bottomRowHasEmojiKeyAndNoKeyboardSwitch() {
        val bottom = Layouts.build(KeyboardPage.LETTERS, FieldVariant.TEXT, EnterAction(EnterAction.Kind.NEWLINE)).rows.last()
        assertEquals(listOf(KeyAction.Page(KeyboardPage.SYMBOLS), KeyAction.Emoji, KeyAction.Text(","), KeyAction.Space, KeyAction.Text("."), KeyAction.Enter),
            bottom.map { it.action })
    }

    @Test fun numberRowAndArrowKeysAreOptional() {
        val enter = EnterAction(EnterAction.Kind.NEWLINE)
        val plain = Layouts.build(KeyboardPage.LETTERS, FieldVariant.TEXT, enter)
        val full = Layouts.build(KeyboardPage.LETTERS, FieldVariant.TEXT, enter, numberRow = true, arrowKeys = true)
        assertEquals(plain.rows.size + 2, full.rows.size)
        assertEquals((1..9).map { "$it" } + "0", full.rows.first().map { it.label })
        assertTrue(full.rows.last().all { it.action is KeyAction.Arrow })
        full.rows.forEach { assertEquals(10.0, it.sumOf { k -> k.width.toDouble() }, 0.001) }
        // números e telefone não ganham fileira de números nem setas
        val number = Layouts.build(KeyboardPage.NUMBER, FieldVariant.TEXT, enter, numberRow = true, arrowKeys = true)
        assertEquals(4, number.rows.size)
    }

    @Test fun lettersHavePortugueseAccentsAndCedilla() {
        val layout = Layouts.build(KeyboardPage.LETTERS, FieldVariant.TEXT, EnterAction(EnterAction.Kind.NEWLINE))
        val keys = layout.rows.flatten()
        assertTrue(keys.any { it.action == KeyAction.Text("ç") })
        assertTrue(keys.first { it.label == "a" }.variants.containsAll(listOf("á", "ã", "â", "à")))
        assertTrue(keys.first { it.label == "o" }.variants.containsAll(listOf("ó", "õ", "ô")))
        assertEquals("@", Layouts.build(KeyboardPage.LETTERS, FieldVariant.EMAIL, EnterAction(EnterAction.Kind.NEWLINE))
            .rows.last()[2].label)
    }

    @Test fun shiftAndCapsLock() {
        val s = ShiftController(doubleTapMs = 300)
        s.onShiftTap(0); assertEquals(ShiftState.ONCE, s.state)
        s.onCharacterTyped(); assertEquals(ShiftState.OFF, s.state)
        s.onShiftTap(1_000); s.onShiftTap(1_100); assertEquals(ShiftState.LOCKED, s.state)
        s.onCharacterTyped(); assertEquals(ShiftState.LOCKED, s.state)
        s.applyAutoCaps(false); assertEquals(ShiftState.LOCKED, s.state)
        s.onShiftTap(2_000); assertEquals(ShiftState.OFF, s.state)
    }

    @Test fun autoCapsDoesNotOverrideManualChoice() {
        val s = ShiftController()
        s.applyAutoCaps(true); assertEquals(ShiftState.ONCE, s.state)
        s.applyAutoCaps(false); assertEquals(ShiftState.OFF, s.state)
        s.onShiftTap(0); s.applyAutoCaps(false); assertEquals(ShiftState.ONCE, s.state)
    }
}
