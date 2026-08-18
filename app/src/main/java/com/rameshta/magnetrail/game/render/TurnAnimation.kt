package com.rameshta.magnetrail.game.render

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.rameshta.magnetrail.core.engine.ResolutionResult
import com.rameshta.magnetrail.core.engine.TerminalEvent
import com.rameshta.magnetrail.game.TurnAnimationPhase
import com.rameshta.magnetrail.game.MotionPolicy
import kotlinx.coroutines.delay

data class TurnVisualState(
    val routeProgress: Float = 0f,
    val showImpact: Boolean = false,
    val applyPolarityChange: Boolean = false,
    val magnetTransitionProgress: Float = 0f,
)

@Composable
fun rememberTurnVisualState(
    result: ResolutionResult?,
    motionPolicy: MotionPolicy = MotionPolicy.Normal,
    onPhaseChanged: (TurnAnimationPhase) -> Unit,
    onAnimationCompleted: () -> Unit,
): TurnVisualState {
    val progress = remember(result) { Animatable(0f) }
    val magnetProgress = remember(result) { Animatable(0f) }
    var showImpact by remember(result) { mutableStateOf(false) }
    var applyPolarityChange by remember(result) { mutableStateOf(false) }
    val currentOnPhaseChanged by rememberUpdatedState(onPhaseChanged)
    val currentOnAnimationCompleted by rememberUpdatedState(onAnimationCompleted)

    LaunchedEffect(result) {
        result ?: return@LaunchedEffect
        val exitsBoard = result.terminalEvent is TerminalEvent.Exit ||
            result.terminalEvent is TerminalEvent.InvalidPullExit
        val routeSteps = (result.traversedCells.size + if (exitsBoard) 1 else 0).coerceAtLeast(1)
        val routeDuration = (routeSteps * motionPolicy.millisPerCell)
            .coerceAtMost(motionPolicy.maxRouteMillis)
        val routeEasing = when (result.terminalEvent) {
            is TerminalEvent.PullCapture -> LinearOutSlowInEasing
            is TerminalEvent.Exit -> if (result.polarityChange != null) {
                FastOutLinearInEasing
            } else {
                FastOutSlowInEasing
            }
            else -> LinearEasing
        }

        currentOnPhaseChanged(TurnAnimationPhase.ROUTE)
        progress.snapTo(0f)
        progress.animateTo(1f, tween(routeDuration, easing = routeEasing))

        if (!result.success) {
            if (result.terminalEvent is TerminalEvent.Collision) {
                currentOnPhaseChanged(TurnAnimationPhase.IMPACT)
                showImpact = true
                delay(motionPolicy.impactMillis)
            }
            currentOnPhaseChanged(TurnAnimationPhase.REWIND)
            showImpact = false
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = motionPolicy.rewindMillis,
                    easing = LinearEasing,
                ),
            )
        } else if (result.polarityChange != null) {
            currentOnPhaseChanged(TurnAnimationPhase.POLARITY_FLIP)
            applyPolarityChange = true
            if (motionPolicy.polarityFlipMillis == 0L) {
                magnetProgress.snapTo(1f)
            } else {
                magnetProgress.animateTo(
                    1f,
                    tween(motionPolicy.polarityFlipMillis.toInt(), easing = FastOutSlowInEasing),
                )
            }
        }

        if (result.isWin) {
            val elapsedAfterRoute = if (result.polarityChange != null) {
                motionPolicy.polarityFlipMillis
            } else {
                0L
            }
            delay((motionPolicy.completionDelayMillis - elapsedAfterRoute).coerceAtLeast(0L))
        }

        currentOnAnimationCompleted()
    }

    return TurnVisualState(
        routeProgress = progress.value,
        showImpact = showImpact,
        applyPolarityChange = applyPolarityChange,
        magnetTransitionProgress = magnetProgress.value,
    )
}
