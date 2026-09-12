package com.stailegrow.maxstrike.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Мини-график истории пинга — карточка "// ЗАДЕРЖКА" на экране "Сервера".
 * Шкала своя у каждой строки (как подписано на маке: "своя шкала у
 * строки") — то есть min/max берутся из значений именно этого сервера,
 * не общие на всю карточку.
 */
@Composable
fun Sparkline(values: List<Int>, color: Color, modifier: Modifier = Modifier, strokeWidth: Dp = 1.5.dp) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val minV = values.min().toFloat()
        val maxV = values.max().toFloat()
        val range = (maxV - minV).coerceAtLeast(1f)
        val stepX = if (values.size > 1) size.width / (values.size - 1) else 0f

        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = (value - minV) / range
            // Выше = быстрее рисуем внизу — большой пинг ближе к низу.
            val y = normalized * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
