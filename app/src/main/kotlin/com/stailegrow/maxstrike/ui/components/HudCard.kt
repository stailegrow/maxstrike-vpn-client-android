package com.stailegrow.maxstrike.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import com.stailegrow.maxstrike.ui.theme.CornerTicks
import com.stailegrow.maxstrike.ui.theme.Hatch
import com.stailegrow.maxstrike.ui.theme.HudType
import com.stailegrow.maxstrike.ui.theme.LocalPalette
import com.stailegrow.maxstrike.ui.theme.cutRect

/**
 * HudCard — Android-аналог macOS Card (Components.swift): панель со
 * срезанным углом (CutRect), тонкой рамкой и уголками (CornerTicks),
 * с опциональным заголовком в духе "// TITLE" и разделителем под ним.
 *
 * Карточка сама никогда не задаёт себе фиксированную ширину — снаружи
 * всегда fillMaxWidth (или weight в Row), высота считается по контенту.
 * Это и есть устойчивость к разным разрешениям/плотностям экранов: карточка
 * просто занимает то место, что ей выделил родитель.
 */
@Composable
fun HudCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    innerPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    val palette = LocalPalette.current
    val shape = cutRect(14.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(palette.card)
            .border(BorderStroke(1.dp, palette.cardBorder), shape),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (title != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "// ${title.uppercase()}",
                        style = HudType.label(11.sp),
                        color = palette.accent,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Hatch(
                        color = palette.cardBorder,
                        modifier = Modifier.weight(1f).height(14.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(palette.cardBorder),
                )
            }
            Column(modifier = Modifier.fillMaxWidth().padding(innerPadding)) {
                content()
            }
        }
        CornerTicks(
            color = palette.cardBorder,
            modifier = Modifier.matchParentSize(),
        )
    }
}
