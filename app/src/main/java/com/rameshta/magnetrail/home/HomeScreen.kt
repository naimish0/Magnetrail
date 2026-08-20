package com.rameshta.magnetrail.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rameshta.magnetrail.R
import com.rameshta.magnetrail.game.GameUiState
import com.rameshta.magnetrail.ui.theme.LocalMagnetrailDimensions
import com.rameshta.magnetrail.ui.theme.LocalMagnetrailSpacing
import com.rameshta.magnetrail.ui.theme.MagnetrailMuted
import com.rameshta.magnetrail.ui.theme.MagnetrailPull

@Composable
fun HomeScreen(
    uiState: GameUiState,
    onPlay: () -> Unit,
    onOpenDaily: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalMagnetrailSpacing.current
    val dimensions = LocalMagnetrailDimensions.current
    val playLevelNumber = maxOf(
        uiState.progress.infinite.selectionOrdinal + 1,
        uiState.progress.infinite.completedCount + 1,
    )

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = spacing.screenHorizontal, vertical = spacing.screenTop),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(dimensions.iconButtonSize),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoinBalanceChip(uiState.progress.coinBalance)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(dimensions.iconButtonSize)
                            .semantics { contentDescription = "Open settings" },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Magnetrail",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Bend the path. Clear the board.",
                    modifier = Modifier.padding(top = spacing.xxs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MagnetrailMuted,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(spacing.lg))

                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .height(68.dp)
                        .semantics {
                            contentDescription = "Play Level $playLevelNumber, ${uiState.playDifficultyLabel} difficulty"
                        },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Play · Level $playLevelNumber")
                        Text(
                            uiState.playDifficultyLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                        )
                    }
                }

                Spacer(Modifier.height(spacing.md))

                Card(
                    onClick = onOpenDaily,
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = buildString {
                                append("Daily Challenge for device local date ${uiState.dailyDateLabel}. ")
                                append("Current streak ${uiState.progress.currentStreak}. ")
                                append(if (uiState.todayDailyCompleted) "Completed today" else "Not completed today")
                            }
                        },
                    enabled = !uiState.isDailyLoading,
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md)) {
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
                    }
                }

                uiState.dailyError?.let { message ->
                    Text(
                        message,
                        modifier = Modifier.widthIn(max = 420.dp).padding(top = spacing.xs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(spacing.screenBottom))
        }
    }
}

@Composable
private fun CoinBalanceChip(balance: Int) {
    val spacing = LocalMagnetrailSpacing.current
    Surface(
        modifier = Modifier.semantics { contentDescription = "Coin balance: $balance" },
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "C",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = balance.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
