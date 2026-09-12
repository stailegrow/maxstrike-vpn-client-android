package com.stailegrow.maxstrike.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Прямоугольник со срезанными углами — базовая форма всего интерфейса,
 * Android-аналог Views/HUD/HUDShapes.swift (CutRect). Срез — по умолчанию
 * сверху слева и снизу справа, как почти везде на маке.
 */
enum class CutCorner { TOP_LEADING, TOP_TRAILING, BOTTOM_TRAILING, BOTTOM_LEADING }

class CutRectShape(
    private val cut: Dp,
    private val corners: Set<CutCorner> = setOf(CutCorner.TOP_LEADING, CutCorner.BOTTOM_TRAILING),
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cutPx = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2).coerceAtLeast(0f)
        val tl = if (CutCorner.TOP_LEADING in corners) cutPx else 0f
        val tr = if (CutCorner.TOP_TRAILING in corners) cutPx else 0f
        val br = if (CutCorner.BOTTOM_TRAILING in corners) cutPx else 0f
        val bl = if (CutCorner.BOTTOM_LEADING in corners) cutPx else 0f

        val path = Path().apply {
            moveTo(tl, 0f)
            lineTo(size.width - tr, 0f)
            if (tr > 0f) lineTo(size.width, tr)
            lineTo(size.width, size.height - br)
            if (br > 0f) lineTo(size.width - br, size.height)
            lineTo(bl, size.height)
            if (bl > 0f) lineTo(0f, size.height - bl)
            lineTo(0f, tl)
            close()
        }
        return Outline.Generic(path)
    }
}

fun cutRect(cut: Dp): CutRectShape = CutRectShape(cut)
fun cutRectAll(cut: Dp): CutRectShape = CutRectShape(cut, CutCorner.values().toSet())

/**
 * Уголки-скобки по краям панели — декоративная деталь HUD, Android-аналог
 * Views/HUD/HUDShapes.swift (CornerTicks). Рисуется поверх содержимого,
 * клики не перехватывает.
 */
@Composable
fun CornerTicks(
    color: Color,
    modifier: Modifier = Modifier,
    length: Dp = 8.dp,
    strokeWidth: Dp = 1.2.dp,
    inset: Dp = 1.dp,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val lengthPx = length.toPx()
        val insetPx = inset.toPx()
        val strokePx = strokeWidth.toPx()
        val w = size.width - insetPx * 2
        val h = size.height - insetPx * 2
        if (w <= 0f || h <= 0f) return@Canvas

        val topLeading = Path().apply {
            moveTo(insetPx, insetPx + lengthPx)
            lineTo(insetPx, insetPx)
            lineTo(insetPx + lengthPx, insetPx)
        }
        val bottomTrailing = Path().apply {
            moveTo(insetPx + w, insetPx + h - lengthPx)
            lineTo(insetPx + w, insetPx + h)
            lineTo(insetPx + w - lengthPx, insetPx + h)
        }
        drawPath(topLeading, color = color, style = Stroke(width = strokePx))
        drawPath(bottomTrailing, color = color, style = Stroke(width = strokePx))
    }
}

/**
 * Диагональная штриховка — декоративная текстура из шапки Card
 * (Components.swift), Android-аналог Views/HUD/HUDShapes.swift (Hatch).
 * Используется маленьким кусочком (см. HudCard.kt), не на весь экран.
 */
@Composable
fun Hatch(
    color: Color,
    modifier: Modifier = Modifier,
    spacing: Dp = 5.dp,
    strokeWidth: Dp = 1.dp,
) {
    Canvas(modifier = modifier) {
        val spacingPx = spacing.toPx()
        val strokePx = strokeWidth.toPx()
        var x = -size.height
        while (x < size.width) {
            drawLine(
                color = color,
                start = Offset(x, size.height),
                end = Offset(x + size.height, 0f),
                strokeWidth = strokePx,
            )
            x += spacingPx
        }
    }
}
