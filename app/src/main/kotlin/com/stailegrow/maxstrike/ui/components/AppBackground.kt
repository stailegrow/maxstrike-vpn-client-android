package com.stailegrow.maxstrike.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.stailegrow.maxstrike.ui.theme.LocalPalette
import com.stailegrow.maxstrike.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * Живой фон — Android-аналог Views/HUD/HUDBackground.swift: дрейфующая
 * сетка, засечки вдоль левого края, проходящий скан и редкие глитч-
 * полосы, плюс виньетка по краям. Рисуется позади вообще всего
 * интерфейса, на всех трёх вкладках сразу.
 *
 * Дрейф обновляется 30 раз в секунду по таймеру, а не на каждом кадре
 * дисплея (было — через withFrameMillis, то есть 60/90/120 раз в секунду
 * без остановки, пока открыт хоть один экран приложения): для медленного
 * плавного движения разница на глаз не видна, а нагрузка на процессор и
 * расход батареи заметно ниже. Всё, что не зависит от времени (засечки,
 * виньетка), к тому же считается один раз на размер экрана через
 * remember, а сетка переиспользует один и тот же Path (reset() вместо
 * Path() на каждый тик) и собрана в один вызов drawPath вместо десятков
 * отдельных drawLine.
 *
 * Тумблер "Живой фон" (SettingsStore.liveBackground) управляет параметром
 * animated: выключен — только неподвижные сетка/засечки/виньетка, как в
 * macOS-версии при still=true.
 */
@Composable
fun AppBackground(animated: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val density = LocalDensity.current
    var elapsedMs by remember { mutableStateOf(0L) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    if (animated) {
        // Дрейф сетки медленный и плавный сам по себе — обновлять его на
        // каждом кадре дисплея (60/90/120 Гц) смысла нет. Раньше именно
        // так и было: цикл держал экран перерисовывающимся без остановки,
        // всё время, пока открыт хоть какой-то экран приложения (фон
        // рисуется позади всех вкладок сразу) — это и было главной
        // причиной общей подтормаженности интерфейса и повышенного
        // расхода батареи. 30 обновлений в секунду для медленного дрейфа
        // неотличимы на глаз от «родных» 90/120 Гц, а нагрузку снижают в
        // 2-4 раза в зависимости от экрана.
        LaunchedEffect(Unit) {
            val startMs = System.currentTimeMillis()
            while (true) {
                elapsedMs = System.currentTimeMillis() - startMs
                delay(33)
            }
        }
    }

    val tickPath = remember(canvasSize, density) { buildTickPath(canvasSize, density) }
    val vignetteBrush = remember(canvasSize, density, palette.background) {
        buildVignetteBrush(canvasSize, density, palette.background)
    }
    // Один объект Path на весь composable, а не по одному на тик — Path()
    // раньше пересоздавался при каждом обновлении дрейфа (до 30 раз в
    // секунду), reset() вместо этого почти ничего не стоит.
    val gridPath = remember { Path() }

    Canvas(modifier = modifier.onSizeChanged { canvasSize = it }) {
        drawHudBackground(
            palette = palette,
            timeSec = elapsedMs / 1000f,
            animated = animated,
            tickPath = tickPath,
            vignetteBrush = vignetteBrush,
            gridPath = gridPath,
        )
    }
}

/** Засечки не двигаются и не зависят от времени — строим один Path на
 *  размер экрана, а не заново на каждом кадре. */
private fun buildTickPath(canvasSize: IntSize, density: Density): Path {
    val path = Path()
    if (canvasSize.height <= 0) return path
    with(density) {
        val tickStep = 18.dp.toPx()
        val tickLong = 14.dp.toPx()
        val tickShort = 7.dp.toPx()
        var tickY = 20.dp.toPx()
        var index = 0
        while (tickY < canvasSize.height) {
            val long = index % 5 == 0
            path.moveTo(0f, tickY)
            path.lineTo(if (long) tickLong else tickShort, tickY)
            tickY += tickStep
            index += 1
        }
    }
    return path
}

/** Виньетка тоже не зависит от времени — раньше пересоздавалась на каждом
 *  кадре впустую, теперь только когда меняется размер экрана или палитра. */
private fun buildVignetteBrush(canvasSize: IntSize, density: Density, backgroundColor: Color): Brush {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) {
        return Brush.radialGradient(listOf(Color.Transparent, Color.Transparent))
    }
    return with(density) {
        val vignetteRadius = 620.dp.toPx()
        val innerStop = (120.dp.toPx() / vignetteRadius).coerceIn(0f, 1f)
        Brush.radialGradient(
            0f to Color.Transparent,
            innerStop to Color.Transparent,
            1f to backgroundColor.copy(alpha = 0.85f),
            center = Offset(canvasSize.width / 2f, canvasSize.height / 2f),
            radius = vignetteRadius,
        )
    }
}

private fun DrawScope.drawHudBackground(
    palette: Palette,
    timeSec: Float,
    animated: Boolean,
    tickPath: Path,
    vignetteBrush: Brush,
    gridPath: Path,
) {
    val lineColor = palette.accent.copy(alpha = 0.055f)
    val step = 46.dp.toPx()

    val driftX = if (!animated) 0f else (timeSec * 6f) % step
    val driftY = if (!animated) 0f else (timeSec * 3f) % step

    // Сетка дрейфует, поэтому геометрию нельзя закешировать целиком — но
    // reset() переиспользуемого Path всё равно намного дешевле, чем
    // Path() заново на каждый тик, и уж тем более чем десятки отдельных
    // drawLine.
    gridPath.reset()
    var x = -step + driftX
    while (x < size.width + step) {
        gridPath.moveTo(x, 0f)
        gridPath.lineTo(x, size.height)
        x += step
    }
    var y = -step + driftY
    while (y < size.height + step) {
        gridPath.moveTo(0f, y)
        gridPath.lineTo(size.width, y)
        y += step
    }
    drawPath(gridPath, color = lineColor, style = Stroke(width = 1f))

    // Засечки вдоль левого края — геометрия уже посчитана и закеширована.
    drawPath(tickPath, color = palette.accent.copy(alpha = 0.13f), style = Stroke(width = 1f))

    if (animated) {
        // Скан — мягкая полоса, идущая сверху вниз примерно за 8 секунд.
        val period = 8f
        val progress = (timeSec % period) / period
        val bandHeightPx = 120.dp.toPx()
        val scanY = progress * (size.height + 160.dp.toPx()) - 80.dp.toPx()
        val bandTop = scanY - bandHeightPx / 2f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, palette.accent.copy(alpha = 0.07f), Color.Transparent),
                startY = bandTop,
                endY = bandTop + bandHeightPx,
            ),
            topLeft = Offset(0f, bandTop),
            size = Size(size.width, bandHeightPx),
        )

        // Глитч-полосы. Псевдослучайность считается от номера кадра, поэтому
        // картинка воспроизводима и не требует хранить состояние.
        val frame = (timeSec * 30f).toInt()
        val burst = frame / 26
        if (burst % 7 == 0) {
            for (slot in 0 until 3) {
                val seed = pseudoHash(burst * 31 + slot)
                val bandY = (seed % 1000) / 1000f * size.height
                val bandX = ((seed / 7) % 1000) / 1000f * size.width
                val glitchHeight = (1 + seed % 3).dp.toPx()
                val glitchWidth = (30 + seed % 140).dp.toPx()
                drawRect(
                    color = palette.accent.copy(alpha = 0.22f),
                    topLeft = Offset(bandX, bandY),
                    size = Size(glitchWidth, glitchHeight),
                )
            }
        }
    }

    // Виньетка — прижимает фон к краям, чтобы контент в центре читался.
    // Рисуется всегда, вне зависимости от animated (как в macOS-версии).
    drawRect(brush = vignetteBrush)
}

private fun pseudoHash(value: Int): Int {
    var x = value.toLong() * 2654435761L
    x = x xor (x ushr 13)
    x *= 1274126177L
    return kotlin.math.abs((x % 100000L)).toInt()
}
