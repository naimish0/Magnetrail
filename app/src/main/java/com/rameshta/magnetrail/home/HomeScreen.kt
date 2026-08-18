package com.rameshta.magnetrail.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rameshta.magnetrail.game.GameUiState
import com.rameshta.magnetrail.ui.theme.LocalMagnetrailDimensions
import com.rameshta.magnetrail.ui.theme.LocalMagnetrailSpacing
import com.rameshta.magnetrail.ui.theme.MagnetrailMuted
import com.rameshta.magnetrail.ui.theme.MagnetrailPull
import com.rameshta.magnetrail.ui.theme.MagnetrailPullSoft

@Composable
fun HomeScreen(
    uiState: GameUiState,
    onPlay: () -> Unit,
    onOpenDaily: () -> Unit,
    onOpenLevels: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalMagnetrailSpacing.current
    val dimensions = LocalMagnetrailDimensions.current
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenHorizontal, vertical = spacing.screenTop),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${uiState.progress.coinBalance} coins",
                    modifier = Modifier.semantics {
                        contentDescription = "Coin balance: ${uiState.progress.coinBalance}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .height(dimensions.iconButtonSize)
                        .semantics { contentDescription = "Open settings" },
                ) {
                    Text("Settings")
                }
            }

            Spacer(Modifier.height(spacing.xl))
            Text(
                text = "Magnetrail",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Bend the path. Clear the board.",
                modifier = Modifier.padding(top = spacing.xs),
                style = MaterialTheme.typography.bodyLarge,
                color = MagnetrailMuted,
            )

            Spacer(Modifier.height(spacing.xl))
            if (uiState.hasProgress) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Continue Level ${uiState.currentLevel.number}, ${uiState.currentLevel.title}"
                        },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MagnetrailPullSoft),
                ) {
                    Column(modifier = Modifier.padding(spacing.lg)) {
                        Text(
                            "Continue",
                            style = MaterialTheme.typography.labelLarge,
                            color = MagnetrailPull,
                        )
                        Text(
                            "Level ${uiState.currentLevel.number.toString().padStart(2, '0')}",
                            modifier = Modifier.padding(top = spacing.xs),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            uiState.currentLevel.title,
                            modifier = Modifier.padding(top = spacing.xxs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MagnetrailMuted,
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(spacing.lg)) {
                        Text("Field basics", style = MaterialTheme.typography.labelLarge, color = MagnetrailPull)
                        Text(
                            "Start with one Rail Dart and discover how the field bends its path.",
                            modifier = Modifier.padding(top = spacing.xs),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            Spacer(Modifier.height(spacing.xl))
            Button(
                onClick = onPlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensions.primaryButtonHeight)
                    .semantics { contentDescription = "Play current level" },
                shape = MaterialTheme.shapes.small,
            ) {
                Text(if (uiState.hasProgress) "Continue" else "Play")
            }
            OutlinedButton(
                onClick = onOpenLevels,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.sm)
                    .height(dimensions.secondaryButtonHeight)
                    .semantics { contentDescription = "Open level selection" },
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Level select")
            }
            Card(
                onClick = onOpenDaily,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.md)
                    .semantics {
                        contentDescription = buildString {
                            append("Daily Challenge for device local date ${uiState.dailyDateLabel}. ")
                            append("Current streak ${uiState.progress.currentStreak}. ")
                            append(if (uiState.todayDailyCompleted) "Completed today" else "Not completed today")
                        }
                    },
                enabled = !uiState.isDailyLoading,
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(spacing.lg)) {
                    Text("DAILY CHALLENGE", style = MaterialTheme.typography.labelSmall, color = MagnetrailPull)
                    Text(
                        if (uiState.todayDailyCompleted) "Today’s board cleared" else "A field for ${uiState.dailyDateLabel}",
                        modifier = Modifier.padding(top = spacing.xs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (uiState.isDailyLoading) {
                            "Preparing a certified board…"
                        } else {
                            "Current streak ${uiState.progress.currentStreak} · Best ${uiState.progress.bestStreak}"
                        },
                        modifier = Modifier.padding(top = spacing.xxs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MagnetrailMuted,
                    )
                    Text(
                        "Uses this device’s local date. Playable offline.",
                        modifier = Modifier.padding(top = spacing.xs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MagnetrailMuted,
                    )
                }
            }
            uiState.dailyError?.let { message ->
                Text(
                    message,
                    modifier = Modifier.padding(top = spacing.xs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(spacing.screenBottom))
        }
    }
}
