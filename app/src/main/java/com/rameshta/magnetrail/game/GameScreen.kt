package com.rameshta.magnetrail.game

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rameshta.magnetrail.core.engine.TerminalEvent
import com.rameshta.magnetrail.game.render.MagnetrailBoard
import com.rameshta.magnetrail.game.render.rememberTurnVisualState
import com.rameshta.magnetrail.ui.theme.MagnetrailMuted

@Composable
fun GameScreen(
    uiState: GameUiState,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val turnVisualState = rememberTurnVisualState(
        result = uiState.inFlightResult,
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
                onOpenLevels = { onAction(GameAction.OpenLevelSelection) },
            )

            Text(
                text = uiState.currentLevel.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Arrows ${uiState.remainingArrowCount}/${uiState.initialArrowCount}",
                modifier = Modifier
                    .padding(top = 4.dp)
                    .semantics {
                        contentDescription = "Arrows remaining: ${uiState.remainingArrowCount}"
                    },
                style = MaterialTheme.typography.labelLarge,
                color = MagnetrailMuted,
            )

            StatusLine(uiState)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                MagnetrailBoard(
                    boardState = uiState.boardState,
                    inFlightResult = uiState.inFlightResult,
                    turnVisualState = turnVisualState,
                    inputEnabled = uiState.inputEnabled,
                    onArrowTapped = { onAction(GameAction.LaunchArrow(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 380.dp)
                        .aspectRatio(1f)
                        .shadow(
                            elevation = 10.dp,
                            shape = RoundedCornerShape(30.dp),
                            clip = false,
                        ),
                )
            }

            when {
                uiState.isComplete -> CompletionCard(uiState, onAction)
                uiState.isDeadlocked -> DeadlockCard()
            }

            GameControls(uiState = uiState, onAction = onAction)
        }
    }
}

@Composable
private fun GameTopBar(
    levelNumber: Int,
    enabled: Boolean,
    onOpenLevels: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 12.dp),
    ) {
        TextButton(
            onClick = onOpenLevels,
            enabled = enabled,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = "Open level selection" },
        ) {
            Text("Levels")
        }
        Text(
            text = "Level ${levelNumber.toString().padStart(2, '0')}",
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StatusLine(uiState: GameUiState) {
    val message = when {
        uiState.animationPhase == TurnAnimationPhase.IMPACT -> "Path blocked"
        uiState.animationPhase == TurnAnimationPhase.POLARITY_FLIP -> "The field flipped"
        uiState.isDeadlocked -> "No successful launches remain"
        uiState.inFlightResult?.terminalEvent is TerminalEvent.InvalidPullExit -> "Try another arrow"
        else -> "Find the sequence"
    }
    Text(
        text = message,
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MagnetrailMuted,
    )
}

@Composable
private fun GameControls(uiState: GameUiState, onAction: (GameAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = { onAction(GameAction.Undo) },
            enabled = uiState.canUndo,
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .semantics { contentDescription = "Undo last successful move" },
        ) {
            Text("Undo")
        }
        OutlinedButton(
            onClick = { onAction(GameAction.Restart) },
            enabled = uiState.canRestart,
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .semantics { contentDescription = "Restart current level" },
        ) {
            Text("Restart")
        }
    }
}

@Composable
private fun CompletionCard(uiState: GameUiState, onAction: (GameAction) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Board cleared",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { onAction(GameAction.Replay) }) {
                    Text("Replay")
                }
                Button(onClick = { onAction(GameAction.NextLevel) }) {
                    Text(if (uiState.hasNextLevel) "Next level" else "Level selection")
                }
            }
        }
    }
}

@Composable
private fun DeadlockCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
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
