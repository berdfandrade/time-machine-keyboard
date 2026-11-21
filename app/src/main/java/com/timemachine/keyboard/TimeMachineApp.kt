package com.timemachine.keyboard

import android.app.Application
import com.timemachine.keyboard.emoji.EmojiCatalog
import com.timemachine.keyboard.emoji.EmojiCategory
import com.timemachine.keyboard.emoji.EmojiRecents
import com.timemachine.keyboard.history.HistoryEngine
import com.timemachine.keyboard.predict.WordDictionary
import com.timemachine.keyboard.predict.WordPredictor
import com.timemachine.keyboard.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * O teclado (serviço) e o app de configurações rodam no mesmo processo e compartilham estas
 * instâncias; assim "Apagar histórico" na Activity limpa a memória do teclado diretamente.
 */
class TimeMachineApp : Application() {
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    val history: HistoryEngine by lazy {
        HistoryEngine(scope = appScope, policy = settings.settings.value.policy()).also { engine ->
            engine.startExpiryTimer()
            appScope.launch { settings.settings.collect { engine.policy = it.policy() } }
        }
    }

    /** Sugestões de palavras. O que aprende fica só na memória. */
    val predictor = WordPredictor()

    val emojiRecents: EmojiRecents by lazy { EmojiRecents(this) }

    private val _emojis = MutableStateFlow<List<EmojiCategory>>(emptyList())
    val emojis: StateFlow<List<EmojiCategory>> = _emojis

    private var languageDataRequested = false

    /** Carrega dicionário e emojis em segundo plano, uma vez por processo. */
    fun loadLanguageData() {
        if (languageDataRequested) return
        languageDataRequested = true
        appScope.launch(Dispatchers.IO) {
            predictor.dictionary = assets.open("words_pt_br.txt").use(WordDictionary::load)
            _emojis.value = EmojiCatalog.load(this@TimeMachineApp)
        }
    }

    /** "Apagar todo o histórico": versões e palavras aprendidas. */
    fun eraseTypingData() {
        history.clearAll()
        predictor.clear()
    }

}
