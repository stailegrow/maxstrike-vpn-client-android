package com.stailegrow.maxstrike.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em

/**
 * Роли шрифтов — Android-аналог Views/HUD/Typography.swift. Теперь на
 * фирменных шрифтах (ChakraPetch/ShareTechMono/SairaStencilOne/Syncopate
 * — см. AppFonts.kt), как и на маке, а не на системных заглушках.
 * Характер шрифта здесь по-прежнему держат и трекинг (letterSpacing), и
 * насыщенность начертания — гарнитура просто довершает то же самое.
 */
object HudType {
    fun hero(size: TextUnit): TextStyle = TextStyle(
        fontSize = size,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.12.em,
        fontFamily = ChakraPetch,
    )

    fun heading(size: TextUnit): TextStyle = TextStyle(
        fontSize = size,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.02.em,
        fontFamily = ChakraPetch,
    )

    fun label(size: TextUnit): TextStyle = TextStyle(
        fontSize = size,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.02.em,
        fontFamily = ChakraPetch,
    )

    fun body(size: TextUnit): TextStyle = TextStyle(
        fontSize = size,
        fontWeight = FontWeight.Normal,
        fontFamily = ChakraPetch,
    )

    /** Цифры, адреса, логи — всё, что должно стоять в колонку. */
    fun code(size: TextUnit): TextStyle = TextStyle(
        fontSize = size,
        fontWeight = FontWeight.Normal,
        fontFamily = ShareTechMono,
    )

    /** Широкая разрядка для подписей вроде "SECURE TUNNEL" — единственное
     *  место, где используется Syncopate. */
    fun wide(size: TextUnit): TextStyle = TextStyle(
        fontSize = size,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.3.em,
        fontFamily = Syncopate,
    )

    /** Только для вордмарка "MAX STRIKE" в шапке (AppTopBar). */
    fun wordmark(size: TextUnit): TextStyle = TextStyle(
        fontSize = size,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.04.em,
        fontFamily = SairaStencilOne,
    )
}
