package com.stailegrow.maxstrike.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Три значка нижней навигации — рисуются вручную на Canvas (как и глиф
 * питания в ConnectSlab), чтобы не тащить material-icons-extended, которой
 * нет в зависимостях проекта.
 */
@Composable
fun HomeIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        val roof = Path().apply {
            moveTo(w * 0.5f, h * 0.05f)
            lineTo(w * 0.92f, h * 0.42f)
            moveTo(w * 0.5f, h * 0.05f)
            lineTo(w * 0.08f, h * 0.42f)
        }
        val base = Path().apply {
            moveTo(w * 0.2f, h * 0.4f)
            lineTo(w * 0.2f, h * 0.95f)
            lineTo(w * 0.8f, h * 0.95f)
            lineTo(w * 0.8f, h * 0.4f)
        }
        drawPath(roof, color = color, style = stroke)
        drawPath(base, color = color, style = stroke)
    }
}

@Composable
fun ServersIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        val w = size.width
        val rowHeights = listOf(0.12f, 0.42f, 0.72f)
        for (top in rowHeights) {
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.08f, size.height * top),
                size = androidx.compose.ui.geometry.Size(w * 0.84f, size.height * 0.18f),
                style = stroke,
            )
        }
    }
}

@Composable
fun SettingsIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension * 0.42f
        val innerRadius = size.minDimension * 0.16f
        drawCircle(color = color, radius = innerRadius, center = center, style = stroke)
        val teeth = 8
        for (i in 0 until teeth) {
            val angle = (2 * Math.PI * i / teeth).toFloat()
            val x1 = center.x + outerRadius * 0.7f * kotlin.math.cos(angle)
            val y1 = center.y + outerRadius * 0.7f * kotlin.math.sin(angle)
            val x2 = center.x + outerRadius * kotlin.math.cos(angle)
            val y2 = center.y + outerRadius * kotlin.math.sin(angle)
            drawLine(color = color, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = stroke.width)
        }
    }
}
