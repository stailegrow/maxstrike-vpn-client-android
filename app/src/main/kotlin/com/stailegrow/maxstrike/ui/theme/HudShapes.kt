package com.stailegrow.maxstrike.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
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

    // Без equals/hashCode каждый новый CutRectShape(cut, corners) — даже с
    // теми же значениями — Compose считает "другим" объектом (сравнение по
    // ссылке по умолчанию). А cutRect() ниже раньше был обычной функцией,
    // вызываемой заново при каждой рекомпозиции HudCard/ServerRow/ConnectSlab
    // и т.д. — то есть Modifier.clip(shape)/.border(..., shape) каждый раз
    // видели "изменившийся" параметр и заново прогоняли clip/border/redraw,
    // хотя визуально ничего не менялось. Это одна из главных причин
    // подтормаживания при скролле на всех экранах: cutRect() используется
    // почти в каждой карточке.
    override fun equals(other: Any?): Boolean =
        other is CutRectShape && other.cut == cut && other.corners == corners

    override fun hashCode(): Int = cut.hashCode() * 31 + corners.hashCode()
}

/**
 * cutRect()/cutRectAll() теперь @Composable и кешируют форму через
 * remember(cut, corners) — форма пересоздаётся только когда реально
 * меняются её параметры, а не на каждую рекомпозицию вызывающей карточки.
 * Вместе с equals/hashCode выше это позволяет Compose пропускать лишний
 * clip/border/redraw там, где ничего не изменилось.
 */
@Composable
fun cutRect(
    cut: Dp,
    corners: Set<CutCorner> = setOf(CutCorner.TOP_LEADING, CutCorner.BOTTOM_TRAILING),
): CutRectShape = remember(cut, corners) { CutRectShape(cut, corners) }

@Composable
fun cutRectAll(cut: Dp): CutRectShape {
    val allCorners = remember { CutCorner.values().toSet() }
    return remember(cut, allCorners) { CutRectShape(cut, allCorners) }
}

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
    val density = LocalDensity.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Геометрия уголков зависит только от размера панели и параметров
    // length/inset — не от времени и не от содержимого. Раньше два Path()
    // строились заново в draw-лямбде Canvas на каждый redraw (в том числе
    // при первом появлении карточки на экране во время скролла LazyColumn,
    // а CornerTicks вызывается почти в каждой карточке через HudCard) — на
    // экране с десятками карточек это заметная лишняя работа. Теперь
    // геометрия считается один раз на размер/параметры через remember, как
    // уже сделано в AppBackground для сетки и засечек фона.
    val corners = remember(canvasSize, density, length, inset) {
        buildCornerTickPaths(canvasSize, density, length, inset)
    }

    Canvas(modifier = modifier.fillMaxSize().onSizeChanged { canvasSize = it }) {
        val strokePx = with(density) { strokeWidth.toPx() }
        drawPath(corners.first, color = color, style = Stroke(width = strokePx))
        drawPath(corners.second, color = color, style = Stroke(width = strokePx))
    }
}

private fun buildCornerTickPaths(canvasSize: IntSize, density: Density, length: Dp, inset: Dp): Pair<Path, Path> {
    val topLeading = Path()
    val bottomTrailing = Path()
    with(density) {
        val lengthPx = length.toPx()
        val insetPx = inset.toPx()
        val w = canvasSize.width - insetPx * 2
        val h = canvasSize.height - insetPx * 2
        if (w <= 0f || h <= 0f) return topLeading to bottomTrailing

        topLeading.moveTo(insetPx, insetPx + lengthPx)
        topLeading.lineTo(insetPx, insetPx)
        topLeading.lineTo(insetPx + lengthPx, insetPx)

        bottomTrailing.moveTo(insetPx + w, insetPx + h - lengthPx)
        bottomTrailing.lineTo(insetPx + w, insetPx + h)
        bottomTrailing.lineTo(insetPx + w - lengthPx, insetPx + h)
    }
    return topLeading to bottomTrailing
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
    val density = LocalDensity.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Раньше отдельный drawLine на каждую диагональную чёрточку пересчитывался
    // в draw-лямбде на каждый redraw. Теперь вся штриховка — один Path,
    // посчитанный один раз на размер/интервал через remember и нарисованный
    // одним drawPath — то же ускорение, что уже применено к сетке фона в
    // AppBackground.
    val hatchPath = remember(canvasSize, density, spacing) {
        buildHatchPath(canvasSize, density, spacing)
    }

    Canvas(modifier = modifier.onSizeChanged { canvasSize = it }) {
        val strokePx = with(density) { strokeWidth.toPx() }
        drawPath(hatchPath, color = color, style = Stroke(width = strokePx))
    }
}

private fun buildHatchPath(canvasSize: IntSize, density: Density, spacing: Dp): Path {
    val path = Path()
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return path
    with(density) {
        val spacingPx = spacing.toPx()
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        var x = -h
        while (x < w) {
            path.moveTo(x, h)
            path.lineTo(x + h, 0f)
            x += spacingPx
        }
    }
    return path
}
