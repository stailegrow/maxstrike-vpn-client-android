package com.stailegrow.maxstrike.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stailegrow.maxstrike.ui.theme.HudType
import com.stailegrow.maxstrike.ui.theme.LocalPalette
import com.stailegrow.maxstrike.ui.theme.accentBrush
import com.stailegrow.maxstrike.ui.theme.cutRect

/**
 * Верхняя панель, общая для всех трёх вкладок — Views/Wordmark.swift
 * (компактный вариант) + статус-плашка "SECURE"/"OFFLINE" + кнопка
 * быстрого добавления сервера. Раньше вордмарк был отдельным большим
 * блоком на весь экран только на главной — теперь как на маке: узкая
 * панель сверху везде, а место, которое она освобождает, отдано контенту.
 */
@Composable
fun AppTopBar(connected: Boolean, onAddClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val wordmarkShape = cutRect(10.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .clip(wordmarkShape)
                .border(BorderStroke(1.dp, palette.cardBorder), wordmarkShape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val title = buildAnnotatedString {
                withStyle(SpanStyle(color = palette.textPrimary)) { append("MAX ") }
                withStyle(SpanStyle(brush = palette.accentBrush())) { append("STRIKE") }
            }
            Text(text = title, style = HudType.wordmark(17.sp))
            Text(
                text = "SECURE TUNNEL",
                style = HudType.wide(8.sp),
                color = palette.textSecondary,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        SecureBadge(connected = connected)
        Spacer(modifier = Modifier.width(8.dp))
        AddButton(onClick = onAddClick)
    }
}

@Composable
private fun SecureBadge(connected: Boolean) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(50)
    val dotColor = if (connected) palette.good else palette.textSecondary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(palette.card)
            .border(BorderStroke(1.dp, palette.cardBorder), shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (connected) "SECURE" else "OFFLINE",
            style = HudType.label(9.sp),
            color = palette.textSecondary,
        )
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val palette = LocalPalette.current
    val shape = cutRect(7.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .border(BorderStroke(1.dp, palette.accent), shape)
            .clickable(onClick = onClick),
    ) {
        Text(text = "+", color = palette.accent, style = HudType.hero(18.sp))
    }
}
