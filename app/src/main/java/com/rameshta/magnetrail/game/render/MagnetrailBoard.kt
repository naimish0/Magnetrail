package com.rameshta.magnetrail.game.render

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
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
import com.rameshta.magnetrail.game.MotionPolicy
import com.rameshta.magnetrail.ui.theme.MagnetrailBorder
import com.rameshta.magnetrail.ui.theme.MagnetrailError
import com.rameshta.magnetrail.ui.theme.MagnetrailGrid
import com.rameshta.magnetrail.ui.theme.MagnetrailPrimary
import com.rameshta.magnetrail.ui.theme.MagnetrailPrimaryStrong
import com.rameshta.magnetrail.ui.theme.MagnetrailPull
import com.rameshta.magnetrail.ui.theme.MagnetrailPush
import com.rameshta.magnetrail.ui.theme.MagnetrailSurface
import com.rameshta.magnetrail.ui.theme.MagnetrailWall
import kotlin.math.roundToInt

@Composable
fun MagnetrailBoard(
    boardState: BoardState,
    inFlightResult: ResolutionResult?,
    hintPreviewResult: ResolutionResult?,
    turnVisualState: TurnVisualState,
    motionPolicy: MotionPolicy,
    highContrastFields: Boolean,
    suggestedArrowId: String?,
    inputEnabled: Boolean,
    onArrowTapped: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val exitGutterPx = with(density) { 12.dp.toPx() }
    val fieldPhase = rememberFieldPhase(motionPolicy.animateFieldPulse)
    var geometry by remember(boardState.width, boardState.height) {
        mutableStateOf<BoardGeometry?>(null)
    }
    val polaritySummary = boardState.magnets.joinToString { magnet ->
        "Magnet ${magnet.id}, ${magnet.polarity.name}"
    }

    Box(modifier = modifier.semantics {
        contentDescription = buildString {
            append("Magnetrail board, ${boardState.arrows.size} arrows remaining")
            if (polaritySummary.isNotEmpty()) append(", $polaritySummary")
        }
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

            hintPreviewResult?.let { preview ->
                val previewPoints = boardGeometry.routePoints(preview).take(2)
                drawProjectedSegment(previewPoints, boardGeometry.cellSize)
            }

            val routePoints = inFlightResult?.let(boardGeometry::routePoints).orEmpty()
            if (routePoints.isNotEmpty()) {
                val trailColor = when (inFlightResult?.polarityChange?.from) {
                    Polarity.PULL -> MagnetrailPull
                    Polarity.PUSH -> MagnetrailPush
                    null -> MagnetrailPrimary
                }
                val visibleTrail = boardGeometry.trailPoints(routePoints, turnVisualState.routeProgress)
                    .let { points -> if (motionPolicy.showLongTrails) points else points.takeLast(2) }
                drawTrail(visibleTrail, trailColor, boardGeometry.cellSize)
            }

            boardState.walls.forEach { drawWall(it, boardGeometry) }
            val polarityChange = inFlightResult?.polarityChange
            boardState.magnets.forEach { magnet ->
                val isTransitioning = turnVisualState.applyPolarityChange &&
                    polarityChange?.magnetId == magnet.id
                val renderedMagnet = if (isTransitioning && turnVisualState.magnetTransitionProgress >= 0.5f) {
                    magnet.copy(polarity = requireNotNull(polarityChange).to)
                } else {
                    magnet
                }
                drawMagnet(
                    magnet = renderedMagnet,
                    geometry = boardGeometry,
                    fieldPhase = fieldPhase,
                    highContrast = highContrastFields,
                    transitionProgress = if (isTransitioning) {
                        turnVisualState.magnetTransitionProgress
                    } else {
                        0f
                    },
                )
            }

            boardState.arrows
                .filterNot { it.id == inFlightResult?.selectedArrowId }
                .forEach { arrow ->
                    drawRailDart(
                        arrow = arrow,
                        center = boardGeometry.cellCenter(arrow.position),
                        cellSize = boardGeometry.cellSize,
                        selected = arrow.id == suggestedArrowId,
                    )
                }

            if (inFlightResult != null && routePoints.isNotEmpty()) {
                val originalArrow = inFlightResult.originalState.arrow(inFlightResult.selectedArrowId)
                if (originalArrow != null) {
                    drawRailDart(
                        arrow = originalArrow.copy(printedDirection = inFlightResult.effectiveDirection),
                        center = boardGeometry.pointAlongRoute(
                            routePoints,
                            turnVisualState.routeProgress,
                        ),
                        cellSize = boardGeometry.cellSize,
                        selected = true,
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
                val suggested = arrow.id == suggestedArrowId
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
                            contentDescription = buildString {
                                append("Arrow ${arrow.id}, points ${arrow.printedDirection.name.lowercase()}")
                                if (suggested) append(", suggested hint")
                            }
                            role = Role.Button
                        }
                        .clickable(
                            enabled = inputEnabled,
                            onClickLabel = "Launch arrow ${arrow.id}",
                        ) { onArrowTapped(arrow.id) },
                )
            }
            boardState.magnets.forEach { magnet ->
                val center = boardGeometry.cellCenter(magnet.position)
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
                            contentDescription = "Magnet ${magnet.id}, ${magnet.polarity.name}, " +
                                if (magnet.polarity == Polarity.PULL) {
                                    "inward field"
                                } else {
                                    "outward field"
                                }
                        },
                )
            }
        }
    }
}

@Composable
private fun rememberFieldPhase(enabled: Boolean): Float {
    if (!enabled) return 0f
    val transition = rememberInfiniteTransition(label = "ambient magnetic field")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_600),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ambient field phase",
    )
    return phase
}

private fun DrawScope.drawBoardSurface(geometry: BoardGeometry) {
    val bounds = geometry.boardBounds
    val cornerRadius = CornerRadius(geometry.cellSize * 0.28f)
    drawRoundRect(MagnetrailSurface, bounds.topLeft, bounds.size, cornerRadius)
    for (column in 1 until geometry.boardWidth) {
        val x = bounds.left + column * geometry.cellSize
        drawLine(
            MagnetrailGrid,
            Offset(x, bounds.top),
            Offset(x, bounds.bottom),
            geometry.cellSize * 0.018f,
        )
    }
    for (row in 1 until geometry.boardHeight) {
        val y = bounds.top + row * geometry.cellSize
        drawLine(
            MagnetrailGrid,
            Offset(bounds.left, y),
            Offset(bounds.right, y),
            geometry.cellSize * 0.018f,
        )
    }
    drawRoundRect(
        MagnetrailBorder,
        bounds.topLeft,
        bounds.size,
        cornerRadius,
        style = Stroke(width = geometry.cellSize * 0.025f),
    )
}

private fun DrawScope.drawTrail(points: List<Offset>, color: Color, cellSize: Float) {
    points.zipWithNext().forEachIndexed { index, (start, end) ->
        drawLine(
            color = color.copy(alpha = (0.46f - index * 0.035f).coerceAtLeast(0.24f)),
            start = start,
            end = end,
            strokeWidth = cellSize * 0.10f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawProjectedSegment(points: List<Offset>, cellSize: Float) {
    points.zipWithNext().forEach { (start, end) ->
        drawLine(
            color = MagnetrailPull.copy(alpha = 0.72f),
            start = start,
            end = end,
            strokeWidth = cellSize * 0.055f,
            cap = StrokeCap.Round,
        )
        drawCircle(MagnetrailPull, cellSize * 0.055f, end)
    }
}

private fun DrawScope.drawWall(wall: Wall, geometry: BoardGeometry) {
    val center = geometry.cellCenter(wall.position)
    val size = geometry.cellSize * 0.58f
    drawRoundRect(
        MagnetrailWall,
        Offset(center.x - size / 2f, center.y - size / 2f),
        Size(size, size),
        CornerRadius(size * 0.14f),
    )
    drawRoundRect(
        Color.White.copy(alpha = 0.14f),
        Offset(center.x - size * 0.36f, center.y - size * 0.36f),
        Size(size * 0.72f, size * 0.72f),
        CornerRadius(size * 0.1f),
        style = Stroke(width = geometry.cellSize * 0.025f),
    )
}

private fun DrawScope.drawMagnet(
    magnet: Magnet,
    geometry: BoardGeometry,
    fieldPhase: Float,
    highContrast: Boolean,
    transitionProgress: Float,
) {
    val center = geometry.cellCenter(magnet.position)
    val color = if (magnet.polarity == Polarity.PULL) MagnetrailPull else MagnetrailPush
    val radius = geometry.cellSize * 0.30f
    val fieldAlpha = if (highContrast) 0.24f else 0.14f
    repeat(2) { ring ->
        val travel = if (magnet.polarity == Polarity.PULL) 1f - fieldPhase else fieldPhase
        val fieldRadius = geometry.cellSize * (0.39f + ring * 0.10f + travel * 0.05f)
        drawCircle(
            color = color.copy(alpha = fieldAlpha * (1f - ring * 0.28f)),
            radius = fieldRadius,
            center = center,
            style = Stroke(width = geometry.cellSize * if (highContrast) 0.035f else 0.025f),
        )
    }

    val scaleFactor = 1f - (1f - kotlin.math.abs(transitionProgress * 2f - 1f)) * 0.12f
    scale(scaleFactor, pivot = center) {
        rotate(transitionProgress * 90f, pivot = center) {
            drawCircle(MagnetrailSurface, radius * 0.98f, center)
            drawArc(
                color = color,
                startAngle = -68f,
                sweepAngle = 136f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = radius * 0.30f, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = 112f,
                sweepAngle = 136f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = radius * 0.30f, cap = StrokeCap.Round),
            )
            drawCircle(
                color = MagnetrailPrimaryStrong.copy(alpha = 0.10f),
                radius = radius * 0.48f,
                center = center,
            )
            drawLine(
                color,
                Offset(center.x - radius * 0.50f, center.y),
                Offset(center.x + radius * 0.50f, center.y),
                radius * 0.10f,
                StrokeCap.Round,
            )
        }
    }

    val inward = magnet.polarity == Polarity.PULL
    drawChevron(
        Offset(center.x - radius * 0.42f, center.y),
        pointsRight = inward,
        color = color,
        size = radius * 0.28f,
    )
    drawChevron(
        Offset(center.x + radius * 0.42f, center.y),
        pointsRight = !inward,
        color = color,
        size = radius * 0.28f,
    )
    drawContext.canvas.nativeCanvas.drawText(
        magnet.polarity.name,
        center.x,
        center.y + radius * 1.43f,
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            textAlign = AndroidPaint.Align.CENTER
            textSize = geometry.cellSize * 0.12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        },
    )
}

private fun DrawScope.drawChevron(center: Offset, pointsRight: Boolean, color: Color, size: Float) {
    val direction = if (pointsRight) 1f else -1f
    val tip = Offset(center.x + direction * size * 0.5f, center.y)
    val upper = Offset(center.x - direction * size * 0.5f, center.y - size * 0.55f)
    val lower = Offset(center.x - direction * size * 0.5f, center.y + size * 0.55f)
    drawLine(color, upper, tip, size * 0.24f, StrokeCap.Round)
    drawLine(color, lower, tip, size * 0.24f, StrokeCap.Round)
}

private fun DrawScope.drawRailDart(
    arrow: Arrow,
    center: Offset,
    cellSize: Float,
    selected: Boolean,
) {
    val angle = when (arrow.printedDirection) {
        Direction.EAST -> 0f
        Direction.SOUTH -> 90f
        Direction.WEST -> 180f
        Direction.NORTH -> 270f
    }
    rotate(angle, pivot = center) {
        if (selected) {
            drawCircle(
                color = MagnetrailPull.copy(alpha = 0.16f),
                radius = cellSize * 0.39f,
                center = center,
            )
            drawCircle(
                color = MagnetrailPull,
                radius = cellSize * 0.34f,
                center = center,
                style = Stroke(width = cellSize * 0.035f),
            )
        }

        val tailX = center.x - cellSize * 0.29f
        val headBaseX = center.x + cellSize * 0.10f
        val tipX = center.x + cellSize * 0.33f
        val stemHeight = cellSize * 0.15f
        drawRoundRect(
            color = MagnetrailPrimary,
            topLeft = Offset(tailX, center.y - stemHeight / 2f),
            size = Size(headBaseX - tailX + cellSize * 0.05f, stemHeight),
            cornerRadius = CornerRadius(stemHeight / 2f),
        )
        drawPath(
            path = Path().apply {
                moveTo(tipX, center.y)
                quadraticTo(
                    center.x + cellSize * 0.25f,
                    center.y - cellSize * 0.15f,
                    headBaseX,
                    center.y - cellSize * 0.19f,
                )
                quadraticTo(
                    center.x + cellSize * 0.05f,
                    center.y,
                    headBaseX,
                    center.y + cellSize * 0.19f,
                )
                quadraticTo(
                    center.x + cellSize * 0.25f,
                    center.y + cellSize * 0.15f,
                    tipX,
                    center.y,
                )
                close()
            },
            color = MagnetrailPrimary,
        )

        // Two surface cuts form the Rail Dart's original split-tail rail detail.
        val railStart = tailX + cellSize * 0.035f
        val railEnd = tailX + cellSize * 0.15f
        drawLine(
            MagnetrailSurface.copy(alpha = 0.86f),
            Offset(railStart, center.y - cellSize * 0.035f),
            Offset(railEnd, center.y - cellSize * 0.035f),
            cellSize * 0.025f,
            StrokeCap.Round,
        )
        drawLine(
            MagnetrailSurface.copy(alpha = 0.86f),
            Offset(railStart, center.y + cellSize * 0.035f),
            Offset(railEnd, center.y + cellSize * 0.035f),
            cellSize * 0.025f,
            StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawImpact(center: Offset, cellSize: Float) {
    drawCircle(MagnetrailError.copy(alpha = 0.18f), cellSize * 0.34f, center)
    drawCircle(
        MagnetrailError,
        cellSize * 0.22f,
        center,
        style = Stroke(width = cellSize * 0.055f),
    )
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).roundToInt(),
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt(),
)
