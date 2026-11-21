package com.timemachine.keyboard.settings

import android.content.Context
import android.content.SharedPreferences
import com.timemachine.keyboard.history.SnapshotPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** Altura do teclado em porcentagem da altura padrão (fileiras de 56 dp). */
object KeyboardHeight {
    const val MIN = 80
    const val MAX = 130
    const val STEP = 5
    const val DEFAULT = 100
    const val BASE_ROW_DP = 56f

    fun clamp(percent: Int): Int = (percent.coerceIn(MIN, MAX) / STEP) * STEP
}

/** Disposição das teclas, como os "Modos" de outros teclados. */
enum class KeyboardMode { FULL, ONE_HANDED, THUMB }

enum class Side { LEFT, RIGHT }

/** Bordas ajustáveis na moldura de redimensionar, em dp. */
object KeyboardEdges {
    const val MAX_SIDE_DP = 120
    const val MAX_BOTTOM_DP = 120
}

/** Configurações do teclado. Só preferências; nenhum texto digitado é salvo aqui. */
data class KeyboardSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    /** Ver [KeyboardHeight]. */
    val keyboardHeight: Int = KeyboardHeight.DEFAULT,
    val mode: KeyboardMode = KeyboardMode.FULL,
    val oneHandedSide: Side = Side.RIGHT,
    /** Recuos laterais e elevação do teclado, ajustados na moldura (dp). */
    val insetLeftDp: Int = 0,
    val insetRightDp: Int = 0,
    val bottomOffsetDp: Int = 0,
    val numberRow: Boolean = false,
    val arrowKeys: Boolean = false,
    val longPressMs: Int = 350,
    /** Sugestões de palavras na faixa. */
    val predictions: Boolean = true,
    /** Aprender palavras digitadas para melhorar sugestões (só na memória). */
    val learnWords: Boolean = true,
    val haptics: Boolean = true,
    val sound: Boolean = false,
    val keyPreview: Boolean = true,
    val spaceHoldOpensHistory: Boolean = true,
    val debounceMs: Long = 500,
    val maxVersions: Int = 100,
    val retentionMinutes: Int = 60,
    val privateMode: Boolean = false,
    val excludedApps: Set<String> = emptySet(),
) {
    fun policy() = SnapshotPolicy(
        debounceMs = debounceMs,
        maxVersionsPerSession = maxVersions,
        retentionMs = retentionMinutes * 60_000L,
    )
}

/** Guarda as configurações em SharedPreferences (leitura síncrona, adequada ao IME). */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<KeyboardSettings> = _settings.asStateFlow()

    // Referência forte: SharedPreferences guarda listeners com WeakReference.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = load()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun update(transform: (KeyboardSettings) -> KeyboardSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString(K_THEME, next.theme.name)
            .putInt(K_KEYBOARD_HEIGHT, KeyboardHeight.clamp(next.keyboardHeight))
            .putString(K_MODE, next.mode.name)
            .putString(K_ONE_HANDED_SIDE, next.oneHandedSide.name)
            .putInt(K_INSET_LEFT, next.insetLeftDp.coerceIn(0, KeyboardEdges.MAX_SIDE_DP))
            .putInt(K_INSET_RIGHT, next.insetRightDp.coerceIn(0, KeyboardEdges.MAX_SIDE_DP))
            .putInt(K_BOTTOM_OFFSET, next.bottomOffsetDp.coerceIn(0, KeyboardEdges.MAX_BOTTOM_DP))
            .putBoolean(K_NUMBER_ROW, next.numberRow)
            .putBoolean(K_ARROW_KEYS, next.arrowKeys)
            .putInt(K_LONG_PRESS, next.longPressMs.coerceIn(200, 800))
            .putBoolean(K_PREDICTIONS, next.predictions)
            .putBoolean(K_LEARN, next.learnWords)
            .putBoolean(K_HAPTICS, next.haptics)
            .putBoolean(K_SOUND, next.sound)
            .putBoolean(K_PREVIEW, next.keyPreview)
            .putBoolean(K_SPACE_HOLD, next.spaceHoldOpensHistory)
            .putLong(K_DEBOUNCE, next.debounceMs)
            .putInt(K_MAX_VERSIONS, next.maxVersions)
            .putInt(K_RETENTION, next.retentionMinutes)
            .putBoolean(K_PRIVATE, next.privateMode)
            .putStringSet(K_EXCLUDED, next.excludedApps)
            .apply()
        _settings.value = next
    }

    private fun load(): KeyboardSettings {
        val d = KeyboardSettings()
        return KeyboardSettings(
            theme = enumOrDefault(prefs.getString(K_THEME, null), d.theme),
            keyboardHeight = loadKeyboardHeight(d.keyboardHeight),
            mode = enumOrDefault(prefs.getString(K_MODE, null), d.mode),
            oneHandedSide = enumOrDefault(prefs.getString(K_ONE_HANDED_SIDE, null), d.oneHandedSide),
            insetLeftDp = prefs.getInt(K_INSET_LEFT, d.insetLeftDp).coerceIn(0, KeyboardEdges.MAX_SIDE_DP),
            insetRightDp = prefs.getInt(K_INSET_RIGHT, d.insetRightDp).coerceIn(0, KeyboardEdges.MAX_SIDE_DP),
            bottomOffsetDp = prefs.getInt(K_BOTTOM_OFFSET, d.bottomOffsetDp).coerceIn(0, KeyboardEdges.MAX_BOTTOM_DP),
            numberRow = prefs.getBoolean(K_NUMBER_ROW, d.numberRow),
            arrowKeys = prefs.getBoolean(K_ARROW_KEYS, d.arrowKeys),
            longPressMs = prefs.getInt(K_LONG_PRESS, d.longPressMs).coerceIn(200, 800),
            predictions = prefs.getBoolean(K_PREDICTIONS, d.predictions),
            learnWords = prefs.getBoolean(K_LEARN, d.learnWords),
            haptics = prefs.getBoolean(K_HAPTICS, d.haptics),
            sound = prefs.getBoolean(K_SOUND, d.sound),
            keyPreview = prefs.getBoolean(K_PREVIEW, d.keyPreview),
            spaceHoldOpensHistory = prefs.getBoolean(K_SPACE_HOLD, d.spaceHoldOpensHistory),
            debounceMs = prefs.getLong(K_DEBOUNCE, d.debounceMs).coerceIn(100, 5_000),
            maxVersions = prefs.getInt(K_MAX_VERSIONS, d.maxVersions).coerceIn(10, 500),
            retentionMinutes = prefs.getInt(K_RETENTION, d.retentionMinutes).coerceIn(1, 24 * 60),
            privateMode = prefs.getBoolean(K_PRIVATE, d.privateMode),
            // cópia: o Set devolvido pelo SharedPreferences não deve ser modificado
            excludedApps = prefs.getStringSet(K_EXCLUDED, null)?.toSet() ?: d.excludedApps,
        )
    }

    /** Converte a opção antiga (Compacta/Padrão/Alta) na primeira leitura. */
    private fun loadKeyboardHeight(default: Int): Int {
        if (prefs.contains(K_KEYBOARD_HEIGHT)) return KeyboardHeight.clamp(prefs.getInt(K_KEYBOARD_HEIGHT, default))
        return when (prefs.getString(K_LEGACY_KEY_HEIGHT, null)) {
            "COMPACT" -> 90
            "TALL" -> 110
            else -> default
        }
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val K_THEME = "theme"
        const val K_LEGACY_KEY_HEIGHT = "key_height"
        const val K_KEYBOARD_HEIGHT = "keyboard_height_percent"
        const val K_MODE = "keyboard_mode"
        const val K_ONE_HANDED_SIDE = "one_handed_side"
        const val K_INSET_LEFT = "inset_left_dp"
        const val K_INSET_RIGHT = "inset_right_dp"
        const val K_BOTTOM_OFFSET = "bottom_offset_dp"
        const val K_NUMBER_ROW = "number_row"
        const val K_ARROW_KEYS = "arrow_keys"
        const val K_LONG_PRESS = "long_press_ms"
        const val K_PREDICTIONS = "predictions"
        const val K_LEARN = "learn_words"
        const val K_HAPTICS = "haptics"
        const val K_SOUND = "sound"
        const val K_PREVIEW = "key_preview"
        const val K_SPACE_HOLD = "space_hold_history"
        const val K_DEBOUNCE = "debounce_ms"
        const val K_MAX_VERSIONS = "max_versions"
        const val K_RETENTION = "retention_minutes"
        const val K_PRIVATE = "private_mode"
        const val K_EXCLUDED = "excluded_apps"
    }
}
