package com.timemachine.keyboard.keyboard

import android.content.res.Configuration
import com.timemachine.keyboard.settings.ThemeMode

/**
 * Cores do teclado e da linha do tempo. O âmbar é reservado ao tempo: versão atual, botão de
 * histórico ativo e "Restaurar". A tecla de ação (Enviar, Buscar) usa o contraste invertido,
 * não o âmbar, para que a cor mantenha um único significado.
 *
 * Contraste (WCAG) medido nos pares de texto:
 *  escuro  label/key 12.8:1, label/função 15.1:1, secundário/fundo 7.8:1, âmbar/fundo 10.4:1,
 *          texto sobre âmbar 10.2:1, perigo/fundo 7.4:1
 *  claro   label/key 18.9:1, label/função 11.5:1, secundário/fundo 5.8:1, âmbar-tinta/fundo 5.2:1,
 *          texto sobre âmbar 9.2:1, perigo/fundo 5.2:1
 */
data class KeyboardPalette(
    val isDark: Boolean,
    val background: Int,
    val key: Int,
    val keyFunction: Int,
    val keyPressed: Int,
    val keyShadow: Int,
    val label: Int,
    val labelSecondary: Int,
    /** Só para traços decorativos (linhas do trilho), nunca para texto. */
    val stroke: Int,
    /** Âmbar para preenchimentos (botão Restaurar, ponto da versão atual no escuro). */
    val accentFill: Int,
    val onAccent: Int,
    /** Âmbar para traços e texto sobre o fundo. */
    val accentInk: Int,
    val action: Int,
    val onAction: Int,
    val danger: Int,
    val surface: Int,
    val separator: Int,
) {
    companion object {
        val Dark = KeyboardPalette(
            isDark = true,
            background = 0xFF111113.toInt(),
            key = 0xFF2B2B30.toInt(),
            keyFunction = 0xFF1E1E22.toInt(),
            keyPressed = 0xFF45454C.toInt(),
            keyShadow = 0x99000000.toInt(),
            label = 0xFFF4F4F6.toInt(),
            labelSecondary = 0xFFA6A6AE.toInt(),
            stroke = 0xFF4A4A52.toInt(),
            accentFill = 0xFFF5B547.toInt(),
            onAccent = 0xFF1A1206.toInt(),
            accentInk = 0xFFF5B547.toInt(),
            action = 0xFFF4F4F6.toInt(),
            onAction = 0xFF111113.toInt(),
            danger = 0xFFFF7A6B.toInt(),
            surface = 0xFF1A1A1D.toInt(),
            separator = 0xFF2A2A2F.toInt(),
        )

        val Light = KeyboardPalette(
            isDark = false,
            background = 0xFFE4E4E9.toInt(),
            key = 0xFFFFFFFF.toInt(),
            keyFunction = 0xFFC9C9D1.toInt(),
            keyPressed = 0xFFB4B4BD.toInt(),
            keyShadow = 0x2E000000,
            label = 0xFF111113.toInt(),
            labelSecondary = 0xFF55555E.toInt(),
            stroke = 0xFFA9A9B2.toInt(),
            accentFill = 0xFFF0A93A.toInt(),
            onAccent = 0xFF1A1206.toInt(),
            accentInk = 0xFF8A4F00.toInt(),
            action = 0xFF111113.toInt(),
            onAction = 0xFFFFFFFF.toInt(),
            danger = 0xFFB3261E.toInt(),
            surface = 0xFFF4F4F7.toInt(),
            separator = 0xFFCFCFD6.toInt(),
        )

        fun resolve(mode: ThemeMode, configuration: Configuration): KeyboardPalette = when (mode) {
            ThemeMode.DARK -> Dark
            ThemeMode.LIGHT -> Light
            ThemeMode.SYSTEM ->
                if (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) Dark else Light
        }
    }
}
