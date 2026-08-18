package com.rameshta.magnetrail.game.render

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import kotlinx.coroutines.delay

enum class MotionPolicy {
    STANDARD,
    REDUCED,
}

data class TurnVisualState(
    val routeProgress: Float = 0f,
    val showImpact: Boolean = false,
    val applyPolarityChange: Boolean = false,
)

@Composable
fun rememberTurnVisualState(
    result: ResolutionResult?,
    motionPolicy: MotionPolicy = MotionPolicy.STANDARD,
    onPhaseChanged: (TurnAnimationPhase) -> Unit,
    onAnimationCompleted: () -> Unit,
): TurnVisualState {
    val progress = remember(result) { Animatable(0f) }
    var showImpact by remember(result) { mutableStateOf(false) }
    var applyPolarityChange by remember(result) { mutableStateOf(false) }
    val currentOnPhaseChanged by rememberUpdatedState(onPhaseChanged)
    val currentOnAnimationCompleted by rememberUpdatedState(onAnimationCompleted)

    LaunchedEffect(result) {
        result ?: return@LaunchedEffect
        val exitsBoard = result.terminalEvent is TerminalEvent.Exit ||
            result.terminalEvent is TerminalEvent.InvalidPullExit
        val routeSteps = (result.traversedCells.size + if (exitsBoard) 1 else 0).coerceAtLeast(1)
        val routeDuration = when (motionPolicy) {
            MotionPolicy.STANDARD -> (routeSteps * MILLIS_PER_CELL).coerceAtMost(MAX_ROUTE_MILLIS)
            MotionPolicy.REDUCED -> REDUCED_ROUTE_MILLIS
        }

        currentOnPhaseChanged(TurnAnimationPhase.ROUTE)
        progress.snapTo(0f)
        progress.animateTo(1f, tween(routeDuration, easing = LinearEasing))

        if (!result.success) {
            if (result.terminalEvent is TerminalEvent.Collision) {
                currentOnPhaseChanged(TurnAnimationPhase.IMPACT)
                showImpact = true
                delay(if (motionPolicy == MotionPolicy.STANDARD) IMPACT_MILLIS else REDUCED_PAUSE_MILLIS)
            }
            currentOnPhaseChanged(TurnAnimationPhase.REWIND)
            showImpact = false
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = if (motionPolicy == MotionPolicy.STANDARD) REWIND_MILLIS else REDUCED_ROUTE_MILLIS,
                    easing = LinearEasing,
                ),
            )
        } else if (result.polarityChange != null) {
            currentOnPhaseChanged(TurnAnimationPhase.POLARITY_FLIP)
            applyPolarityChange = true
            delay(if (motionPolicy == MotionPolicy.STANDARD) POLARITY_FLIP_MILLIS else REDUCED_PAUSE_MILLIS)
        }

        currentOnAnimationCompleted()
    }

    return TurnVisualState(
        routeProgress = progress.value,
        showImpact = showImpact,
        applyPolarityChange = applyPolarityChange,
    )
}

private const val MILLIS_PER_CELL = 90
private const val MAX_ROUTE_MILLIS = 720
private const val IMPACT_MILLIS = 170L
private const val REWIND_MILLIS = 210
private const val POLARITY_FLIP_MILLIS = 260L
private const val REDUCED_ROUTE_MILLIS = 90
private const val REDUCED_PAUSE_MILLIS = 60L
