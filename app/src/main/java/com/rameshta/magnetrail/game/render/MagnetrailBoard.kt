package com.rameshta.magnetrail.game.render

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rameshta.magnetrail.core.engine.ResolutionResult
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.Magnet
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Wall
import com.rameshta.magnetrail.game.BoardGeometry
import com.rameshta.magnetrail.ui.theme.MagnetrailBorder
import com.rameshta.magnetrail.ui.theme.MagnetrailError
import com.rameshta.magnetrail.ui.theme.MagnetrailGrid
import com.rameshta.magnetrail.ui.theme.MagnetrailPrimary
import com.rameshta.magnetrail.ui.theme.MagnetrailPull
import com.rameshta.magnetrail.ui.theme.MagnetrailPush
import com.rameshta.magnetrail.ui.theme.MagnetrailSurface
import com.rameshta.magnetrail.ui.theme.MagnetrailWall
import kotlin.math.roundToInt

@Composable
fun MagnetrailBoard(
    boardState: BoardState,
    inFlightResult: ResolutionResult?,
    turnVisualState: TurnVisualState,
    inputEnabled: Boolean,
    onArrowTapped: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val exitGutterPx = with(density) { 12.dp.toPx() }
    var geometry by remember(boardState.width, boardState.height) {
        mutableStateOf<BoardGeometry?>(null)
    }

    Box(modifier = modifier.semantics {
        contentDescription = "Magnetrail board, ${boardState.arrows.size} arrows remaining"
    }) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .onSizeChanged { size ->
                    geometry = BoardGeometry.create(
                        canvasSize = Size(size.width.toFloat(), size.height.toFloat()),
                        boardWidth = boardState.width,
                        boardHeight = boardState.height,
                        exitGutterPx = exitGutterPx,
                    )
                }
                .pointerInput(geometry, boardState.arrows, inputEnabled) {
                    detectTapGestures { pointer ->
                        if (!inputEnabled) return@detectTapGestures
                        val position = geometry?.positionAt(pointer) ?: return@detectTapGestures
                        boardState.arrows.firstOrNull { it.position == position }?.let { arrow ->
                            onArrowTapped(arrow.id)
                        }
                    }
                },
        ) {
            val boardGeometry = geometry ?: return@Canvas
            drawBoardSurface(boardGeometry)

            val routePoints = inFlightResult?.let(boardGeometry::routePoints).orEmpty()
            if (routePoints.isNotEmpty()) {
                val trailColor = when (inFlightResult?.polarityChange?.from) {
                    Polarity.PULL -> MagnetrailPull
                    Polarity.PUSH -> MagnetrailPush
                    null -> MagnetrailPrimary
                }
                drawTrail(
                    boardGeometry.trailPoints(routePoints, turnVisualState.routeProgress),
                    trailColor,
                    boardGeometry.cellSize,
                )
            }

            boardState.walls.forEach { drawWall(it, boardGeometry) }
            val polarityChange = inFlightResult?.polarityChange
            boardState.magnets.forEach { magnet ->
                val renderedMagnet = if (
                    turnVisualState.applyPolarityChange &&
                    polarityChange?.magnetId == magnet.id
                ) {
                    magnet.copy(polarity = polarityChange.to)
                } else {
                    magnet
                }
                drawMagnet(renderedMagnet, boardGeometry)
            }

            boardState.arrows
                .filterNot { it.id == inFlightResult?.selectedArrowId }
                .forEach { drawArrow(it, boardGeometry.cellCenter(it.position), boardGeometry.cellSize) }

            if (inFlightResult != null && routePoints.isNotEmpty()) {
                val originalArrow = inFlightResult.originalState.arrow(inFlightResult.selectedArrowId)
                if (originalArrow != null) {
                    val movingCenter = boardGeometry.pointAlongRoute(
                        routePoints,
                        turnVisualState.routeProgress,
                    )
                    drawArrow(
                        arrow = originalArrow.copy(printedDirection = inFlightResult.effectiveDirection),
                        center = movingCenter,
                        cellSize = boardGeometry.cellSize,
                        moving = true,
                    )
                }
            }

            if (turnVisualState.showImpact) {
                inFlightResult?.collisionTarget?.let { target ->
                    drawImpact(boardGeometry.cellCenter(target.position), boardGeometry.cellSize)
                }
            }
        }

        geometry?.let { boardGeometry ->
            boardState.arrows.forEach { arrow ->
                val center = boardGeometry.cellCenter(arrow.position)
                val cellDp = with(density) { boardGeometry.cellSize.toDp() }
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (center.x - boardGeometry.cellSize / 2f).roundToInt(),
                                y = (center.y - boardGeometry.cellSize / 2f).roundToInt(),
                            )
                        }
                        .size(cellDp)
                        .semantics {
                            contentDescription =
                                "Arrow ${arrow.id}, points ${arrow.printedDirection.name.lowercase()}"
                            role = Role.Button
                        }
                        .clickable(
                            enabled = inputEnabled,
                            onClickLabel = "Launch arrow ${arrow.id}",
                        ) { onArrowTapped(arrow.id) },
                )
            }
        }
    }
}

private fun DrawScope.drawBoardSurface(geometry: BoardGeometry) {
    val bounds = geometry.boardBounds
    val cornerRadius = CornerRadius(geometry.cellSize * 0.28f)
    drawRoundRect(
        color = MagnetrailSurface,
        topLeft = bounds.topLeft,
        size = bounds.size,
        cornerRadius = cornerRadius,
    )
    for (column in 1 until geometry.boardWidth) {
        val x = bounds.left + column * geometry.cellSize
        drawLine(
            color = MagnetrailGrid,
            start = Offset(x, bounds.top),
            end = Offset(x, bounds.bottom),
            strokeWidth = geometry.cellSize * 0.018f,
        )
    }
    for (row in 1 until geometry.boardHeight) {
        val y = bounds.top + row * geometry.cellSize
        drawLine(
            color = MagnetrailGrid,
            start = Offset(bounds.left, y),
            end = Offset(bounds.right, y),
            strokeWidth = geometry.cellSize * 0.018f,
        )
    }
    drawRoundRect(
        color = MagnetrailBorder,
        topLeft = bounds.topLeft,
        size = bounds.size,
        cornerRadius = cornerRadius,
        style = Stroke(width = geometry.cellSize * 0.025f),
    )
}

private fun DrawScope.drawTrail(points: List<Offset>, color: Color, cellSize: Float) {
    points.zipWithNext().forEach { (start, end) ->
        drawLine(
            color = color.copy(alpha = 0.42f),
            start = start,
            end = end,
            strokeWidth = cellSize * 0.11f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawWall(wall: Wall, geometry: BoardGeometry) {
    val center = geometry.cellCenter(wall.position)
    val size = geometry.cellSize * 0.58f
    drawRoundRect(
        color = MagnetrailWall,
        topLeft = Offset(center.x - size / 2f, center.y - size / 2f),
        size = Size(size, size),
        cornerRadius = CornerRadius(size * 0.14f),
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.14f),
        topLeft = Offset(center.x - size * 0.36f, center.y - size * 0.36f),
        size = Size(size * 0.72f, size * 0.72f),
        cornerRadius = CornerRadius(size * 0.1f),
        style = Stroke(width = geometry.cellSize * 0.025f),
    )
}

private fun DrawScope.drawMagnet(magnet: Magnet, geometry: BoardGeometry) {
    val center = geometry.cellCenter(magnet.position)
    val color = if (magnet.polarity == Polarity.PULL) MagnetrailPull else MagnetrailPush
    val radius = geometry.cellSize * 0.31f
    drawCircle(color.copy(alpha = 0.14f), radius = geometry.cellSize * 0.43f, center = center)
    drawCircle(color, radius = radius, center = center)
    drawCircle(MagnetrailSurface, radius = radius * 0.68f, center = center)

    val pointsInward = magnet.polarity == Polarity.PULL
    drawChevron(
        center = Offset(center.x - radius * 0.38f, center.y),
        pointsRight = pointsInward,
        color = color,
        size = radius * 0.32f,
    )
    drawChevron(
        center = Offset(center.x + radius * 0.38f, center.y),
        pointsRight = !pointsInward,
        color = color,
        size = radius * 0.32f,
    )

    drawContext.canvas.nativeCanvas.drawText(
        if (magnet.polarity == Polarity.PULL) "PULL" else "PUSH",
        center.x,
        center.y + radius * 1.34f,
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            textAlign = AndroidPaint.Align.CENTER
            textSize = geometry.cellSize * 0.12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        },
    )
}

private fun DrawScope.drawChevron(
    center: Offset,
    pointsRight: Boolean,
    color: Color,
    size: Float,
) {
    val direction = if (pointsRight) 1f else -1f
    val tip = Offset(center.x + direction * size * 0.5f, center.y)
    val upper = Offset(center.x - direction * size * 0.5f, center.y - size * 0.55f)
    val lower = Offset(center.x - direction * size * 0.5f, center.y + size * 0.55f)
    drawLine(color, upper, tip, strokeWidth = size * 0.24f, cap = StrokeCap.Round)
    drawLine(color, lower, tip, strokeWidth = size * 0.24f, cap = StrokeCap.Round)
}

private fun DrawScope.drawArrow(
    arrow: Arrow,
    center: Offset,
    cellSize: Float,
    moving: Boolean = false,
) {
    val vector = arrow.printedDirection.vector()
    val perpendicular = Offset(-vector.y, vector.x)
    val tail = center - vector * (cellSize * 0.27f)
    val tip = center + vector * (cellSize * 0.31f)
    val headBase = tip - vector * (cellSize * 0.22f)
    val headHalfWidth = cellSize * 0.17f
    val color = if (moving) MagnetrailPrimary.copy(alpha = 0.92f) else MagnetrailPrimary

    if (moving) {
        drawCircle(
            color = MagnetrailPrimary.copy(alpha = 0.12f),
            radius = cellSize * 0.38f,
            center = center,
        )
    }
    drawLine(
        color = color,
        start = tail,
        end = headBase + vector * (cellSize * 0.04f),
        strokeWidth = cellSize * 0.15f,
        cap = StrokeCap.Round,
    )
    drawPath(
        path = Path().apply {
            moveTo(tip.x, tip.y)
            val first = headBase + perpendicular * headHalfWidth
            val second = headBase - perpendicular * headHalfWidth
            lineTo(first.x, first.y)
            lineTo(second.x, second.y)
            close()
        },
        color = color,
    )
}

private fun DrawScope.drawImpact(center: Offset, cellSize: Float) {
    drawCircle(
        color = MagnetrailError.copy(alpha = 0.18f),
        radius = cellSize * 0.34f,
        center = center,
    )
    drawCircle(
        color = MagnetrailError,
        radius = cellSize * 0.22f,
        center = center,
        style = Stroke(width = cellSize * 0.055f),
    )
}

private fun Direction.vector(): Offset = when (this) {
    Direction.NORTH -> Offset(0f, -1f)
    Direction.EAST -> Offset(1f, 0f)
    Direction.SOUTH -> Offset(0f, 1f)
    Direction.WEST -> Offset(-1f, 0f)
}

private operator fun Offset.times(value: Float): Offset = Offset(x * value, y * value)

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).roundToInt(),
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt(),
)
