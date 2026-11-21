package com.timemachine.keyboard.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.timemachine.keyboard.TimeMachineApp
import com.timemachine.keyboard.ime.TimeMachineIME

/** Configurações, instruções de ativação e área de teste. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as TimeMachineApp
        setContent {
            AppTheme {
                SettingsApp(
                    repository = app.settings,
                    eraseAll = app::eraseTypingData,
                    readStatus = { ImeStatus.read(this) },
                    openInputMethodSettings = {
                        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    showKeyboardPicker = { getSystemService(InputMethodManager::class.java)?.showInputMethodPicker() },
                )
            }
        }
    }
}

/** Se o teclado está habilitado e se é o teclado atual. O Android exige que o usuário faça ambos. */
data class ImeStatus(val enabled: Boolean, val selected: Boolean) {
    companion object {
        fun read(context: Context): ImeStatus {
            val component = ComponentName(context, TimeMachineIME::class.java)
            val imm = context.getSystemService(InputMethodManager::class.java)
            val enabled = imm?.enabledInputMethodList.orEmpty().any { it.component == component }
            val current = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            } catch (_: SecurityException) {
                null
            }
            val selected = current?.let(ComponentName::unflattenFromString) == component
            return ImeStatus(enabled, enabled && selected)
        }
    }
}
