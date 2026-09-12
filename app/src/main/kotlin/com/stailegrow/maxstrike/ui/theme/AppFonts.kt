package com.stailegrow.maxstrike.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.stailegrow.maxstrike.R

/**
 * Фирменные шрифты — те же файлы, что зарегистрированы в бандле
 * macOS-версии (Sources/MaxStrike/Resources/Fonts), просто лежат в
 * res/font вместо системы шрифтов приложения (там регистрировались на
 * старте, здесь Compose подхватывает их сам по ресурсу).
 *
 *  - Chakra Petch — основной интерфейсный шрифт (см. HudType: hero/
 *    heading/label/body/wide), аналог System на маке.
 *  - Share Tech Mono — цифры/адреса/логи (HudType.code), раньше был
 *    системный FontFamily.Monospace.
 *  - Saira Stencil One — только вордмарк "MAX STRIKE" (HudType.wordmark).
 *  - Syncopate — только подпись "SECURE TUNNEL" под вордмарком, держит
 *    её и в HudType.wide (там же и только там используется).
 */
val ChakraPetch = FontFamily(
    Font(R.font.chakra_petch_regular, FontWeight.Normal),
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

val ShareTechMono = FontFamily(
    Font(R.font.share_tech_mono_regular, FontWeight.Normal),
)

val SairaStencilOne = FontFamily(
    Font(R.font.saira_stencil_one_regular, FontWeight.Normal),
)

val Syncopate = FontFamily(
    Font(R.font.syncopate_regular, FontWeight.Normal),
    Font(R.font.syncopate_bold, FontWeight.Bold),
)
