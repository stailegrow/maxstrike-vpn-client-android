package com.stailegrow.maxstrike.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stailegrow.maxstrike.core.L
import com.stailegrow.maxstrike.core.PingQuality
import com.stailegrow.maxstrike.model.ProxyConfig
import com.stailegrow.maxstrike.ui.theme.HudType
import com.stailegrow.maxstrike.ui.theme.LocalPalette

/**
 * Карточка "// ЗАДЕРЖКА" — история пинга по каждому серверу (вкладка
 * "Сервера"). Шкала своя у строки: Sparkline сам берёт min/max из значений
 * именно этого сервера, как подписано на маке.
 */
@Composable
fun LatencyCard(
    servers: List<ProxyConfig>,
    pingHistory: Map<String, List<Int>>,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current

    HudCard(title = L.t("Задержка", "Latency"), modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = L.t("опрос каждые 5 с", "polled every 5 s"),
                style = HudType.body(10.sp),
                color = palette.textSecondary,
            )
            Text(
                text = L.t("своя шкала у строки", "own scale per row"),
                style = HudType.body(10.sp),
                color = palette.textSecondary,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (config in servers) {
                val history = pingHistory[config.id] ?: emptyList()
                LatencyRow(name = extractTitle(config.displayName), history = history)
            }
        }
    }
}

@Composable
private fun LatencyRow(name: String, history: List<Int>) {
    val palette = LocalPalette.current
    val last = history.lastOrNull()
    val previous = if (history.size >= 2) history[history.size - 2] else null

    val color = if (last == null) {
        palette.textSecondary
    } else {
        when (PingQuality.of(last)) {
            PingQuality.GOOD -> palette.good
            PingQuality.FAIR -> palette.warn
            PingQuality.POOR -> palette.bad
        }
    }
    val trend = when {
        last == null || previous == null -> ""
        last < previous -> "▼"
        last > previous -> "▲"
        else -> ""
    }
    val valueText = last?.let { "$trend$it" } ?: "—"

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = name,
            style = HudType.body(13.sp),
            color = palette.textPrimary,
            modifier = Modifier.width(84.dp),
        )
        Sparkline(
            values = history,
            color = color,
            modifier = Modifier.weight(1f).height(24.dp).padding(horizontal = 8.dp),
        )
        Text(
            text = valueText,
            style = HudType.code(12.sp),
            color = color,
            modifier = Modifier.width(50.dp),
        )
    }
}
