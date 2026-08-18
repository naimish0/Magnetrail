package com.rameshta.magnetrail.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rameshta.magnetrail.core.engine.TerminalEvent
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.game.render.MagnetrailBoard
import com.rameshta.magnetrail.game.render.rememberTurnVisualState
import com.rameshta.magnetrail.ui.theme.LocalMagnetrailDimensions
import com.rameshta.magnetrail.ui.theme.LocalMagnetrailSpacing
import com.rameshta.magnetrail.ui.theme.MagnetrailMuted
import com.rameshta.magnetrail.ui.theme.MagnetrailPull
import com.rameshta.magnetrail.ui.theme.MagnetrailPullSoft
import com.rameshta.magnetrail.ui.theme.MagnetrailPush
import com.rameshta.magnetrail.ui.theme.MagnetrailSuccess

@Composable
fun GameScreen(
    uiState: GameUiState,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalMagnetrailSpacing.current
    val dimensions = LocalMagnetrailDimensions.current
    val motionPolicy = remember(uiState.settings.reducedMotion) {
        MotionPolicy.from(uiState.settings.reducedMotion)
    }
    val turnVisualState = rememberTurnVisualState(
        result = uiState.inFlightResult,
        motionPolicy = motionPolicy,
        onPhaseChanged = { onAction(GameAction.AnimationPhaseChanged(it)) },
        onAnimationCompleted = { onAction(GameAction.AnimationCompleted) },
    )

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GameTopBar(
                levelNumber = uiState.currentLevel.number,
                enabled = uiState.inFlightResult == null,
                onHome = { onAction(GameAction.NavigateHome) },
                onSettings = { onAction(GameAction.OpenSettings) },
            )

            Text(
                text = uiState.currentLevel.title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(
                modifier = Modifier.padding(top = spacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = "Arrows ${uiState.remainingArrowCount}/${uiState.initialArrowCount}",
                    modifier = Modifier.semantics {
                        contentDescription = "Arrows remaining: ${uiState.remainingArrowCount}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MagnetrailMuted,
                )
                Text(
                    text = "Moves ${uiState.moves}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MagnetrailMuted,
                )
            }

            PolarityLegend(uiState)
            StatusLine(uiState)

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                MagnetrailBoard(
                    boardState = uiState.boardState,
                    inFlightResult = uiState.inFlightResult,
                    hintPreviewResult = uiState.hintPreviewResult,
                    turnVisualState = turnVisualState,
                    motionPolicy = motionPolicy,
                    highContrastFields = uiState.settings.highContrastFields,
                    suggestedArrowId = uiState.suggestedArrowId,
                    inputEnabled = uiState.inputEnabled,
                    onArrowTapped = { onAction(GameAction.LaunchArrow(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = dimensions.boardMaxSize)
                        .aspectRatio(1f)
                        .shadow(
                            elevation = dimensions.boardElevation,
                            shape = RoundedCornerShape(30.dp),
                            clip = false,
                        ),
                )
            }

            when {
                uiState.isComplete -> CompletionCard(uiState, motionPolicy, onAction)
                uiState.isDeadlocked -> DeadlockCard()
            }

            if (!uiState.isComplete) {
                GameControls(uiState = uiState, onAction = onAction)
            } else {
                Spacer(Modifier.height(spacing.screenBottom))
            }
        }
    }
}

@Composable
private fun GameTopBar(
    levelNumber: Int,
    enabled: Boolean,
    onHome: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp),
    ) {
        TextButton(
            onClick = onHome,
            enabled = enabled,
            modifier = Modifier.align(Alignment.CenterStart).sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = "Return home" },
        ) { Text("Home") }
        Text(
            text = "Level ${levelNumber.toString().padStart(2, '0')}",
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        TextButton(
            onClick = onSettings,
            enabled = enabled,
            modifier = Modifier.align(Alignment.CenterEnd).sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = "Open settings" },
        ) { Text("Settings") }
    }
}

@Composable
private fun PolarityLegend(uiState: GameUiState) {
    val magnet = uiState.boardState.magnets.firstOrNull() ?: return
    val pull = magnet.polarity == Polarity.PULL
    Surface(
        modifier = Modifier.padding(top = 8.dp).semantics {
            contentDescription = if (pull) "PULL, inward magnetic field" else "PUSH, outward magnetic field"
        },
        shape = RoundedCornerShape(999.dp),
        color = if (pull) MagnetrailPullSoft else com.rameshta.magnetrail.ui.theme.MagnetrailPushSoft,
    ) {
        Text(
            text = if (pull) "PULL  ›‹" else "PUSH  ‹›",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (pull) MagnetrailPull else MagnetrailPush,
        )
    }
}

@Composable
private fun StatusLine(uiState: GameUiState) {
    val message = uiState.hintMessage ?: when {
        uiState.animationPhase == TurnAnimationPhase.IMPACT -> "Path blocked"
        uiState.animationPhase == TurnAnimationPhase.POLARITY_FLIP -> "The field flipped"
        uiState.isDeadlocked -> "No successful launches remain"
        uiState.inFlightResult?.terminalEvent is TerminalEvent.InvalidPullExit -> "Try another arrow"
        else -> "Find the sequence"
    }
    Text(
        text = message,
        modifier = Modifier.padding(top = 8.dp).semantics {
            contentDescription = message
            liveRegion = LiveRegionMode.Polite
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (uiState.suggestedArrowId != null) MagnetrailPull else MagnetrailMuted,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun GameControls(uiState: GameUiState, onAction: (GameAction) -> Unit) {
    val spacing = LocalMagnetrailSpacing.current
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val controls: @Composable (Modifier) -> Unit = { rowModifier ->
        ActionButton(
            text = "Undo",
            description = "Undo last successful move",
            enabled = uiState.canUndo,
            onClick = { onAction(GameAction.Undo) },
            modifier = rowModifier,
        )
        ActionButton(
            text = "Restart",
            description = "Restart current level",
            enabled = uiState.canRestart,
            onClick = { onAction(GameAction.Restart) },
            modifier = rowModifier,
        )
        ActionButton(
            text = if (uiState.isHintLoading) "Finding…" else "Hint",
            description = if (uiState.isHintLoading) "Hint loading" else "Request a solver hint",
            enabled = uiState.canRequestHint,
            onClick = { onAction(GameAction.RequestHint) },
            modifier = rowModifier,
        )
    }
    if (largeText) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            controls(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            controls(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp).semantics { contentDescription = description },
        shape = MaterialTheme.shapes.small,
    ) { Text(text, maxLines = 1) }
}

@Composable
private fun CompletionCard(
    uiState: GameUiState,
    motionPolicy: MotionPolicy,
    onAction: (GameAction) -> Unit,
) {
    val spacing = LocalMagnetrailSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompletionCelebration(motionPolicy)
            Text(
                "Board cleared",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MagnetrailSuccess,
            )
            Row(
                modifier = Modifier.padding(top = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                Text(
                    "Moves ${uiState.moves}",
                    modifier = Modifier.semantics { contentDescription = "Moves: ${uiState.moves}" },
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    "Hints ${uiState.hintsUsed}",
                    modifier = Modifier.semantics { contentDescription = "Hints: ${uiState.hintsUsed}" },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Row(
                modifier = Modifier.padding(top = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onAction(GameAction.Replay) }) { Text("Replay") }
                Button(onClick = { onAction(GameAction.NextLevel) }) {
                    Text(if (uiState.hasNextLevel) "Next level" else "Level selection")
                }
            }
        }
    }
}

@Composable
private fun CompletionCelebration(motionPolicy: MotionPolicy) {
    val progress = remember { Animatable(if (motionPolicy.reduced) 1f else 0f) }
    LaunchedEffect(motionPolicy.reduced) {
        if (!motionPolicy.reduced) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(if (motionPolicy.showCelebrationParticles) 36.dp else 18.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = MagnetrailPull.copy(alpha = (1f - progress.value) * 0.38f),
            radius = 8.dp.toPx() + progress.value * 24.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
        if (motionPolicy.showCelebrationParticles) {
            val offsets = listOf(-42f to -5f, -26f to 10f, 28f to -8f, 44f to 8f)
            offsets.forEachIndexed { index, (x, y) ->
                drawCircle(
                    color = if (index % 2 == 0) MagnetrailPull else MagnetrailPush,
                    radius = 2.5.dp.toPx(),
                    center = center + Offset(x.dp.toPx(), y.dp.toPx()) * progress.value,
                )
            }
        }
    }
}

@Composable
private fun DeadlockCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(
            text = "No clear route remains. Undo or restart to try another sequence.",
            modifier = Modifier.padding(16.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private operator fun Offset.times(value: Float): Offset = Offset(x * value, y * value)
