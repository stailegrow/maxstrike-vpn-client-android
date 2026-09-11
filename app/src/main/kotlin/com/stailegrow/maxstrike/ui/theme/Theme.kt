package com.stailegrow.maxstrike.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Пока один тёмный HUD-вариант на встроенной Material3-схеме. Когда дойдём
// до этапа 5 (перенос тем с macOS), MaxStrikeTheme научится переключаться
// между пятью пресетами так же, как на маке.
private val HudColorScheme = darkColorScheme(
    primary = AmberAccent,
    onPrimary = HudBackground,
    secondary = AmberAccentDim,
    background = HudBackground,
    surface = HudSurface,
    onBackground = HudTextPrimary,
    onSurface = HudTextPrimary,
)

@Composable
fun MaxStrikeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HudColorScheme,
        content = content,
    )
}
