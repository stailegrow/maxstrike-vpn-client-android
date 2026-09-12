package com.stailegrow.maxstrike.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.stailegrow.maxstrike.core.ThemeStore

/**
 * Текущая палитра — читается из composition local, а не передаётся вручную
 * по всей иерархии экранов (аналог EnvironmentValues.palette на macOS).
 * Дефолт нужен только для превью/раннего доступа до первого MaxStrikeTheme —
 * в реальном дереве экрана значение всегда приходит от него.
 */
val LocalPalette = staticCompositionLocalOf { Palette.amber }

/**
 * Пять пресетов вместо одной HUD-схемы — этап 5 плана. ThemeStore хранит
 * только id (см. core/ThemeStore.kt), сама палитра резолвится уже здесь —
 * смена темы в ThemePicker (MainActivity.kt) сразу перекрашивает всё дерево.
 */
@Composable
fun MaxStrikeTheme(content: @Composable () -> Unit) {
    val paletteId by ThemeStore.paletteId.collectAsState()
    val palette = Palette.named(paletteId)

    val colorScheme = darkColorScheme(
        primary = palette.accentStart,
        onPrimary = palette.background,
        secondary = palette.accentEnd,
        onSecondary = palette.background,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.card,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.card,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.cardBorder,
        error = palette.bad,
        onError = palette.background,
    )

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
