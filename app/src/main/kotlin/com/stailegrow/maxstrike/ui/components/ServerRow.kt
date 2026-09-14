package com.stailegrow.maxstrike.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stailegrow.maxstrike.core.L
import com.stailegrow.maxstrike.core.PingQuality
import com.stailegrow.maxstrike.model.ProxyConfig
import com.stailegrow.maxstrike.model.Security
import com.stailegrow.maxstrike.model.Subscription
import com.stailegrow.maxstrike.ui.theme.HudType
import com.stailegrow.maxstrike.ui.theme.LocalPalette
import com.stailegrow.maxstrike.ui.theme.cutRect

/**
 * Разбор "флаг + имя" — Android-порт ServerRow.title(of:)/flag(of:) из
 * macOS Components.swift. Имена серверов на панелях обычно выглядят как
 * "🇳🇱 Netherlands #1": ведущие символы, которые не буква и не цифра,
 * считаются флагом, всё остальное — заголовком.
 */
fun extractFlag(displayName: String): String {
    val sb = StringBuilder()
    for (ch in displayName) {
        if (ch.isLetterOrDigit()) break
        if (ch.isWhitespace()) {
            if (sb.isEmpty()) continue else break
        }
        sb.append(ch)
    }
    return sb.toString()
}

fun extractTitle(displayName: String): String {
    val stripped = displayName.dropWhile { !it.isLetterOrDigit() }.trim()
    return stripped.ifEmpty { displayName }
}

/** Маленькая уголковая пилюля-лейбл — протокол/транспорт/security теги.
 *  color — переопределение акцентного цвета (по умолчанию — акцент темы):
 *  нужно для предупреждающих чипов вроде "INSECURE", которым не подходит
 *  обычный акцентный цвет темы. */
@Composable
fun Chip(text: String, filled: Boolean = false, color: Color? = null, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val accent = color ?: palette.accent
    val background = if (filled) accent.copy(alpha = 0.16f) else Color.Transparent
    val border = if (filled) accent else palette.cardBorder
    val textColor = if (filled) accent else palette.textSecondary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text = text.uppercase(), style = HudType.label(9.sp), color = textColor)
    }
}

/** Флаг страны (или заглушка) в маленькой рамке со срезанным углом. */
@Composable
fun FlagBadge(flag: String, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val shape = cutRect(6.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(30.dp)
            .clip(shape)
            .background(palette.background)
            .border(BorderStroke(1.dp, palette.cardBorder), shape),
    ) {
        Text(text = flag.ifBlank { "•" }, fontSize = 15.sp)
    }
}

/**
 * Индикатор пинга с тремя состояниями (Core/ServerStore.swift держит
 * `[String: Int?]`, здесь то же самое через два параметра):
 *  - measured=false             → ещё не мерили, рисуем пусто;
 *  - measured=true, ms=null     → мерили, ответа нет — пустые бары + N/A;
 *  - measured=true, ms=значение → цветные бары по PingQuality + значение.
 */
@Composable
fun PingBadge(measured: Boolean, pingMs: Int?, modifier: Modifier = Modifier) {
    if (!measured) return
    val palette = LocalPalette.current

    val activeBars: Int
    val color: Color
    val label: String
    if (pingMs == null) {
        activeBars = 0
        color = palette.textSecondary
        label = L.t("Н/Д", "N/A")
    } else {
        val quality = PingQuality.of(pingMs)
        color = when (quality) {
            PingQuality.GOOD -> palette.good
            PingQuality.FAIR -> palette.warn
            PingQuality.POOR -> palette.bad
        }
        activeBars = when (quality) {
            PingQuality.GOOD -> 3
            PingQuality.FAIR -> 2
            PingQuality.POOR -> 1
        }
        label = "$pingMs ${L.t("мс", "ms")}"
    }

    Row(verticalAlignment = Alignment.Bottom, modifier = modifier) {
        val heights = listOf(3.dp, 5.dp, 7.dp)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 0..2) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(heights[i])
                        .background(if (i < activeBars) color else palette.cardBorder),
                )
            }
        }
        Spacer(Modifier.width(5.dp))
        Text(text = label, color = color, style = HudType.code(10.sp))
    }
}

/**
 * Полоса трафика подписки — Android-аналог TrafficMeter из Components.swift.
 * С лимитом: цветная полоса заполнения + использовано/всего + процент
 * (красный, если больше 90%). Без лимита: точка-маркер + использовано + "∞".
 */
@Composable
fun TrafficMeter(subscription: Subscription, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val summary = subscription.trafficSummary ?: return
    val fraction = subscription.trafficFraction

    Column(modifier = modifier.fillMaxWidth()) {
        if (fraction != null) {
            val percent = (fraction * 100).toInt()
            val barColor = if (percent > 90) palette.bad else palette.accent
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = summary, color = palette.textSecondary, style = HudType.code(11.sp))
                Text(text = "$percent%", color = barColor, style = HudType.code(11.sp))
            }
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.cardBorder),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(barColor),
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(palette.accent),
                )
                Spacer(Modifier.width(6.dp))
                Text(text = summary, color = palette.textSecondary, style = HudType.code(11.sp))
                Spacer(Modifier.width(4.dp))
                Text(text = "∞", color = palette.accent, style = HudType.code(11.sp))
            }
        }
    }
}

/**
 * Строка сервера в списке. Тап — только select() (Core/ServerStore.swift +
 * ConnectSlab разделены и на macOS: строка выбирает, отдельная панель
 * подключает/отключает) — саму connect/disconnect-логику дергает только
 * ConnectSlab, эта строка про неё вообще не знает.
 */
@Composable
fun ServerRow(
    config: ProxyConfig,
    index: Int,
    isSelected: Boolean,
    isActive: Boolean,
    measured: Boolean,
    pingMs: Int?,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val shape = cutRect(10.dp)
    val flag = remember(config.displayName) { extractFlag(config.displayName) }
    val title = remember(config.displayName) { extractTitle(config.displayName) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) palette.accent.copy(alpha = 0.10f) else Color.Transparent)
            .border(BorderStroke(1.dp, if (isSelected) palette.accent else Color.Transparent), shape)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = "%02d".format(index + 1),
            style = HudType.code(11.sp),
            color = palette.textSecondary,
            modifier = Modifier.width(20.dp),
        )
        FlagBadge(flag = flag)
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = HudType.body(14.sp),
                color = if (isActive) palette.accent else palette.textPrimary,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 3.dp),
            ) {
                Chip(text = config.kind.rawValue, filled = true)
                Chip(text = config.transport.rawValue)
                if (config.security != Security.NONE) {
                    Chip(text = config.security.rawValue)
                }
                // allowInsecure=1 в ссылке узла отключает проверку TLS-сертификата
                // сервера на стороне Xray — само подключение ломать нельзя (часть
                // самописных узлов сознательно использует self-signed сертификаты),
                // но пользователь должен видеть, что здесь МОЖЕТ быть MITM-риск.
                if (config.allowInsecure) {
                    Chip(text = L.t("небезопасно", "insecure"), filled = true, color = palette.bad)
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            PingBadge(measured = measured, pingMs = pingMs)
            if (isActive) {
                Text(
                    text = "LINK",
                    style = HudType.label(9.sp),
                    color = palette.accent,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        if (onDelete != null) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDelete) {
                Text(text = "✕", color = palette.textSecondary, style = HudType.body(14.sp))
            }
        }
    }
}
