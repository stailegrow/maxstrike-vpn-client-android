package com.stailegrow.maxstrike.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Палитра приложения — Android-аналог Views/Theme.swift (struct Palette).
 * Экраны не знают конкретных цветов — только роли (см. LocalPalette в
 * Theme.kt и Color.kt), поэтому смена темы не требует правок в разметке.
 * Значения цветов — те же hex, что и на macOS: пять пресетов там уже
 * придуманы и утверждены, здесь только перенос на Compose.
 */
data class Palette(
    val id: String,
    val nameRU: String,
    val nameEN: String,

    val background: Color,
    val card: Color,
    val cardBorder: Color,

    val accentStart: Color,
    val accentEnd: Color,
    val idleRing: Color,

    val textPrimary: Color,
    val textSecondary: Color,

    val good: Color,
    val warn: Color,
    val bad: Color,
) {
    val name: String get() = nameRU

    /** Короткое имя для мест, где на маке было просто palette.accent
     *  (там это computed-алиас на accentStart). */
    val accent: Color get() = accentStart

    companion object {
        val amber = Palette(
            id = "amber", nameRU = "Янтарь", nameEN = "Amber",
            background = Color(0xFF0E0E10), card = Color(0xFF17171A), cardBorder = Color(0xFF2A2A30),
            accentStart = Color(0xFFFF8A1F), accentEnd = Color(0xFFFFC24B), idleRing = Color(0xFF3A3A40),
            textPrimary = Color(0xFFF2F2F5), textSecondary = Color(0xFF8E8E98),
            good = Color(0xFF35D07F), warn = Color(0xFFFFC24B), bad = Color(0xFFFF5C5C),
        )

        val emerald = Palette(
            id = "emerald", nameRU = "Изумруд", nameEN = "Emerald",
            background = Color(0xFF0A0C0B), card = Color(0xFF131715), cardBorder = Color(0xFF232B27),
            accentStart = Color(0xFF00E08A), accentEnd = Color(0xFF14D8C4), idleRing = Color(0xFF2E3633),
            textPrimary = Color(0xFFEFF4F1), textSecondary = Color(0xFF83918B),
            good = Color(0xFF14D8C4), warn = Color(0xFFFFC24B), bad = Color(0xFFFF6B5B),
        )

        val crimson = Palette(
            id = "crimson", nameRU = "Малина", nameEN = "Crimson",
            background = Color(0xFF100C0E), card = Color(0xFF1A1418), cardBorder = Color(0xFF2E2229),
            accentStart = Color(0xFFFF2E63), accentEnd = Color(0xFFFF6B4A), idleRing = Color(0xFF3A2F35),
            textPrimary = Color(0xFFF5EFF1), textSecondary = Color(0xFF9A8A90),
            good = Color(0xFF35D07F), warn = Color(0xFFFFC24B), bad = Color(0xFFFF2E63),
        )

        val steel = Palette(
            id = "steel", nameRU = "Сталь", nameEN = "Steel",
            background = Color(0xFF0D0D0F), card = Color(0xFF18181B), cardBorder = Color(0xFF2A2A2F),
            accentStart = Color(0xFFE8EAED), accentEnd = Color(0xFF9AA3AF), idleRing = Color(0xFF33333A),
            textPrimary = Color(0xFFF4F5F7), textSecondary = Color(0xFF8A8F98),
            good = Color(0xFF34D399), warn = Color(0xFFFBBF24), bad = Color(0xFFF87171),
        )

        /** Цвета исходной иконки: кислотно-жёлтый по чёрному. */
        val acid = Palette(
            id = "acid", nameRU = "Кислота", nameEN = "Acid",
            background = Color(0xFF0A0A09), card = Color(0xFF151510), cardBorder = Color(0xFF2E2E1C),
            accentStart = Color(0xFFF2DE12), accentEnd = Color(0xFFFFF35C), idleRing = Color(0xFF3B3B26),
            textPrimary = Color(0xFFF5F5EA), textSecondary = Color(0xFF91917D),
            good = Color(0xFF9BE31C), warn = Color(0xFFFFC24B), bad = Color(0xFFFF4B4B),
        )

        val all: List<Palette> = listOf(amber, acid, emerald, crimson, steel)

        fun named(id: String): Palette = all.firstOrNull { it.id == id } ?: amber
    }
}

/** Диагональный градиент start→end — Android-аналог Palette.accentGradient. */
fun Palette.accentBrush(): Brush = Brush.linearGradient(listOf(accentStart, accentEnd))
