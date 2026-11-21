package com.timemachine.keyboard.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** As mesmas cores do teclado; o app acompanha o modo claro/escuro do sistema. */
private val Dark = darkColorScheme(
    primary = Color(0xFFF5B547),
    onPrimary = Color(0xFF1A1206),
    primaryContainer = Color(0xFF3A2A10),
    onPrimaryContainer = Color(0xFFFFDDA6),
    secondary = Color(0xFFA6A6AE),
    secondaryContainer = Color(0xFF3A2A10),
    onSecondaryContainer = Color(0xFFFFDDA6),
    background = Color(0xFF111113),
    onBackground = Color(0xFFF4F4F6),
    surface = Color(0xFF111113),
    onSurface = Color(0xFFF4F4F6),
    surfaceVariant = Color(0xFF1E1E22),
    onSurfaceVariant = Color(0xFFA6A6AE),
    surfaceContainer = Color(0xFF1A1A1D),
    surfaceContainerHigh = Color(0xFF222226),
    surfaceContainerHighest = Color(0xFF2B2B30),
    outline = Color(0xFF4A4A52),
    outlineVariant = Color(0xFF2A2A2F),
    error = Color(0xFFFF7A6B),
    onError = Color(0xFF1A0503),
)

private val Light = lightColorScheme(
    primary = Color(0xFF8A4F00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDA6),
    onPrimaryContainer = Color(0xFF2C1A00),
    secondary = Color(0xFF55555E),
    secondaryContainer = Color(0xFFFFDDA6),
    onSecondaryContainer = Color(0xFF2C1A00),
    background = Color(0xFFF7F7F9),
    onBackground = Color(0xFF111113),
    surface = Color(0xFFF7F7F9),
    onSurface = Color(0xFF111113),
    surfaceVariant = Color(0xFFE4E4E9),
    onSurfaceVariant = Color(0xFF55555E),
    surfaceContainer = Color(0xFFEDEDF1),
    surfaceContainerHigh = Color(0xFFE7E7EC),
    surfaceContainerHighest = Color(0xFFE1E1E7),
    outline = Color(0xFF8E8E96),
    outlineVariant = Color(0xFFCFCFD6),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
