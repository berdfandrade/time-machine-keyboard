package com.timemachine.keyboard.keyboard

enum class ShiftState { OFF, ONCE, LOCKED }

/**
 * Shift e Caps Lock: um toque liga para a próxima letra, dois toques rápidos travam, outro toque
 * desliga. A capitalização automática (início de frase) liga o shift sem sobrepor uma escolha
 * manual do usuário.
 */
class ShiftController(private val doubleTapMs: Long = 300) {
    var state: ShiftState = ShiftState.OFF
        private set

    private var lastTapAt = Long.MIN_VALUE / 2
    private var fromAutoCaps = false

    val isUpperCase: Boolean get() = state != ShiftState.OFF

    fun onShiftTap(now: Long) {
        state = when (state) {
            ShiftState.OFF -> ShiftState.ONCE
            ShiftState.ONCE -> if (now - lastTapAt <= doubleTapMs && !fromAutoCaps) ShiftState.LOCKED else ShiftState.OFF
            ShiftState.LOCKED -> ShiftState.OFF
        }
        fromAutoCaps = false
        lastTapAt = now
    }

    /** Uma letra foi digitada. */
    fun onCharacterTyped() {
        if (state == ShiftState.ONCE) {
            state = ShiftState.OFF
            fromAutoCaps = false
        }
    }

    /** [capsActive] vem de InputConnection.getCursorCapsMode. */
    fun applyAutoCaps(capsActive: Boolean) {
        when {
            state == ShiftState.LOCKED -> Unit
            capsActive && state == ShiftState.OFF -> { state = ShiftState.ONCE; fromAutoCaps = true }
            !capsActive && state == ShiftState.ONCE && fromAutoCaps -> { state = ShiftState.OFF; fromAutoCaps = false }
        }
    }

    fun reset() {
        state = ShiftState.OFF
        fromAutoCaps = false
    }
}
