package com.stailegrow.maxstrike.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stailegrow.maxstrike.core.ConnectionState
import com.stailegrow.maxstrike.core.L
import com.stailegrow.maxstrike.ui.theme.CornerTicks
import com.stailegrow.maxstrike.ui.theme.HudType
import com.stailegrow.maxstrike.ui.theme.LocalPalette
import com.stailegrow.maxstrike.ui.theme.accentBrush
import com.stailegrow.maxstrike.ui.theme.cutRect
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * ConnectSlab — Android-аналог Views/ConnectSlab.swift: главный элемент
 * управления, панель-переключатель на всю ширину экрана (а не круглая
 * кнопка). Тап в любом месте панели — это toggle() выбранного сервера.
 *
 * Состояния:
 *  - Disconnected: серый глиф, "ОТКЛЮЧЕНО", подпись — имя выбранного
 *    сервера или "Выберите сервер".
 *  - Connecting: пульсирующий глиф, "ПОДКЛЮЧЕНИЕ…", бегущая полоса прогресса
 *    сверху и анимированный эквалайзер вместо таймера.
 *  - Connected: акцентный градиент на заголовке, полоска-акцент слева,
 *    таймер аптайма и внешний IP.
 *  - Failed: заголовок цветом bad, текст ошибки в подписи.
 */
@Composable
fun ConnectSlab(
    state: ConnectionState,
    serverName: String?,
    externalIP: String?,
    connectedSinceMillis: Long?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val shape = cutRect(18.dp)
    val connected = state is ConnectionState.Connected
    val connecting = state is ConnectionState.Connecting
    val failed = state is ConnectionState.Failed
    val enabled = serverName != null

    val headlineColor = when {
        failed -> palette.bad
        connected -> palette.accent
        else -> palette.textPrimary
    }
    val headlineText = when (state) {
        is ConnectionState.Connected -> L.t("ВКЛЮЧЕНО", "CONNECTED")
        is ConnectionState.Connecting -> L.t("ПОДКЛЮЧЕНИЕ…", "CONNECTING…")
        is ConnectionState.Failed -> L.t("ОШИБКА", "ERROR")
        is ConnectionState.Disconnected -> L.t("ВЫКЛЮЧЕНО", "DISCONNECTED")
    }
    val captionText = when (state) {
        is ConnectionState.Failed -> state.message
        else -> serverName ?: L.t("Выберите сервер в списке ниже", "Select a server from the list below")
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (connected) {
                    Modifier.shadow(
                        elevation = 14.dp,
                        shape = shape,
                        ambientColor = palette.accent.copy(alpha = 0.6f),
                        spotColor = palette.accent.copy(alpha = 0.6f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(palette.card)
            .border(BorderStroke(1.dp, if (connected) palette.accent else palette.cardBorder), shape)
            .clickable(enabled = enabled) { onToggle() },
    ) {
        if (connected) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(palette.accentBrush()),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            PowerGlyph(connected = connected, connecting = connecting, failed = failed)

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headlineText,
                    style = HudType.hero(20.sp),
                    color = headlineColor,
                )
                Text(
                    text = captionText,
                    style = HudType.body(13.sp),
                    color = palette.textSecondary,
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                when {
                    connecting -> EqualizerBars(color = palette.accent)
                    connected -> {
                        UptimeText(sinceMillis = connectedSinceMillis, color = palette.textPrimary)
                        externalIP?.let {
                            Text(text = it, style = HudType.code(11.sp), color = palette.textSecondary)
                        }
                    }
                }
            }
        }

        if (connecting) {
            ProgressSweep(color = palette.accent)
        }

        CornerTicks(color = palette.cardBorder, modifier = Modifier.matchParentSize())
    }
}

@Composable
private fun PowerGlyph(connected: Boolean, connecting: Boolean, failed: Boolean) {
    val palette = LocalPalette.current
    val ringColor = when {
        failed -> palette.bad
        connected -> palette.accent
        else -> palette.idleRing
    }
    val glyphColor = if (connected) palette.accent else palette.textSecondary

    // Пульс нужен только пока идёт подключение — rememberInfiniteTransition
    // раньше создавался всегда, даже когда !connecting и его значение никуда
    // не шло (alpha всё равно была 1f), и крутился вхолостую бесконечно на
    // "Главной" каждый кадр. Теперь анимация вообще не создаётся, если
    // подключение не идёт — Compose сам останавливает её, когда мы уходим
    // из этой ветки.
    val pulse = if (connecting) {
        val transition = rememberInfiniteTransition(label = "power-glyph")
        val animated by transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
            label = "power-glyph-pulse",
        )
        animated
    } else {
        1f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .alpha(pulse)
            .clip(CircleShape)
            .border(BorderStroke(1.5.dp, ringColor), CircleShape),
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val strokeWidth = 2.dp.toPx()
            // Вертикальная чёрточка сверху.
            drawLine(
                color = glyphColor,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height * 0.55f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            // Дуга окружности с разрывом сверху — классический глиф "питание".
            drawArc(
                color = glyphColor,
                startAngle = -235f,
                sweepAngle = 290f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun EqualizerBars(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "equalizer-phase",
    )

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier.height(20.dp),
    ) {
        repeat(5) { index ->
            val local = phase + index * 0.9f
            val amplitude = (sin(local.toDouble()).toFloat() + 1f) / 2f
            val h = (6.dp + (14.dp * amplitude))
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h)
                    .background(color),
            )
        }
    }
}

@Composable
private fun UptimeText(sinceMillis: Long?, color: Color) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sinceMillis) {
        while (sinceMillis != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val text = sinceMillis?.let { formatUptime(now - it) } ?: "--:--"
    Text(text = text, style = HudType.code(13.sp), color = color)
}

private fun formatUptime(elapsedMillis: Long): String {
    val totalSeconds = (elapsedMillis / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%02d:%02d".format(m, s)
    }
}

@Composable
private fun ProgressSweep(color: Color) {
    val transition = rememberInfiniteTransition(label = "progress-sweep")
    val position by transition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "progress-sweep-position",
    )

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(2.dp),
    ) {
        val barWidth = maxWidth * 0.28f
        val offsetX = (maxWidth - barWidth) * position
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(2.dp)
                .offset(x = offsetX)
                .background(
                    Brush.horizontalGradient(
                        listOf(color.copy(alpha = 0f), color, color.copy(alpha = 0f)),
                    ),
                ),
        )
    }
}
