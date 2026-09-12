package com.stailegrow.maxstrike.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Старые имена оставлены как есть, чтобы не трогать все экраны, которые уже
// на них ссылаются (MainActivity.kt, ServersScreen.kt), — но теперь это не
// константы, а вычисляются от активной палитры (LocalPalette, см. Theme.kt
// и Palette.kt). Смена темы в ThemePicker сразу перекрашивает всё, что через
// них раскрашено, без правок в самих экранах.
val HudBackground: Color @Composable get() = LocalPalette.current.background
val HudSurface: Color @Composable get() = LocalPalette.current.card
val HudTextPrimary: Color @Composable get() = LocalPalette.current.textPrimary
val HudTextSecondary: Color @Composable get() = LocalPalette.current.textSecondary
val AmberAccent: Color @Composable get() = LocalPalette.current.accentStart
val AmberAccentDim: Color @Composable get() = LocalPalette.current.accentEnd
