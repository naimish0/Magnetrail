package com.rameshta.magnetrail.playtest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rameshta.magnetrail.game.GameAction
import com.rameshta.magnetrail.game.GameUiState
import com.rameshta.magnetrail.ui.theme.LocalMagnetrailSpacing
import com.rameshta.magnetrail.ui.theme.MagnetrailMuted
import com.rameshta.magnetrail.ui.theme.MagnetrailPull

@Composable
fun HumanPlaytestScreen(
    uiState: GameUiState,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalMagnetrailSpacing.current
    val playtest = uiState.humanPlaytest
    val session = playtest.session
    var confirmNewSession by remember { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenHorizontal, vertical = spacing.screenTop),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onAction(GameAction.CloseHumanPlaytest) }) { Text("Back") }
                Text(
                    "HUMAN PLAYTEST",
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.weight(0.25f))
            }

            Text(
                "Blind difficulty calibration",
                modifier = Modifier.padding(top = spacing.md),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                "Difficulty labels stay hidden until each board has been played. " +
                    "Use an anonymous participant code—not a name or email address.",
                modifier = Modifier.padding(top = spacing.xs),
                style = MaterialTheme.typography.bodyMedium,
                color = MagnetrailMuted,
                textAlign = TextAlign.Center,
            )

            when {
                playtest.loading -> {
                    CircularProgressIndicator(modifier = Modifier.padding(spacing.lg))
                }
                session == null -> {
                    StartSessionCard(uiState, onAction)
                }
                else -> {
                    SessionCard(uiState, onAction, onStartNew = { confirmNewSession = true })
                }
            }

            playtest.message?.let { message ->
                Text(
                    message,
                    modifier = Modifier.padding(top = spacing.sm).semantics {
                        contentDescription = message
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.startsWith("Unable", ignoreCase = true)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MagnetrailPull
                    },
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(spacing.screenBottom))
        }
    }

    if (confirmNewSession) {
        AlertDialog(
            onDismissRequest = { confirmNewSession = false },
            title = { Text("Start a new session?") },
            text = { Text("Export the current results first. Starting over removes this device’s saved playtest session.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmNewSession = false
                        onAction(GameAction.ClearHumanPlaytest)
                    },
                ) { Text("Start new") }
            },
            dismissButton = {
                TextButton(onClick = { confirmNewSession = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun StartSessionCard(
    uiState: GameUiState,
    onAction: (GameAction) -> Unit,
) {
    val spacing = LocalMagnetrailSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = spacing.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(spacing.md)) {
            Text("New participant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = uiState.humanPlaytest.participantCodeInput,
                onValueChange = { onAction(GameAction.UpdateHumanPlaytestParticipant(it)) },
                modifier = Modifier.fillMaxWidth().padding(top = spacing.sm),
                label = { Text("Anonymous participant code") },
                supportingText = { Text("2–24 letters, numbers, _ or -") },
                singleLine = true,
            )
            Button(
                onClick = { onAction(GameAction.StartHumanPlaytest) },
                enabled = uiState.humanPlaytest.participantCodeInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = spacing.sm),
            ) { Text("Create blind session") }
        }
    }
}

@Composable
private fun SessionCard(
    uiState: GameUiState,
    onAction: (GameAction) -> Unit,
    onStartNew: () -> Unit,
) {
    val spacing = LocalMagnetrailSpacing.current
    val session = requireNotNull(uiState.humanPlaytest.session)
    val progress = if (session.assignments.isEmpty()) 0f else {
        session.completedCount.toFloat() / session.assignments.size
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = spacing.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(spacing.md)) {
            Text(
                "Participant ${session.participantCode}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${session.completedCount} of ${session.assignments.size} boards recorded",
                modifier = Modifier.padding(top = spacing.xs),
                style = MaterialTheme.typography.bodyMedium,
                color = MagnetrailMuted,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = spacing.sm).semantics {
                    contentDescription = "Playtest progress ${session.completedCount} of ${session.assignments.size}"
                },
            )
            Text(
                if (session.isComplete) {
                    "Session complete. Export the CSV for difficulty analysis."
                } else {
                    "The next board is identified only by a blind study number. Take breaks whenever needed."
                },
                modifier = Modifier.padding(top = spacing.sm),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!session.isComplete) {
                Button(
                    onClick = { onAction(GameAction.ResumeHumanPlaytest) },
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = spacing.md),
                ) {
                    Text(if (session.completedCount == 0) "Begin first board" else "Continue playtest")
                }
            }
            OutlinedButton(
                onClick = { onAction(GameAction.ExportHumanPlaytest) },
                enabled = session.observations.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = spacing.sm),
            ) { Text("Export results CSV") }
            TextButton(
                onClick = onStartNew,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = spacing.xs),
            ) { Text("Start another participant") }
        }
    }
}
