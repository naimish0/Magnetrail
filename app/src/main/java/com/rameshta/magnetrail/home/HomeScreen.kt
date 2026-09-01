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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rameshta.magnetrail.R
import com.rameshta.magnetrail.game.GameUiState
import com.rameshta.magnetrail.localization.localizedDifficultyName
import com.rameshta.magnetrail.localization.localizedDateLabel
import com.rameshta.magnetrail.localization.localizedRuntimeMessage
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
    showHumanPlaytest: Boolean = false,
    onOpenHumanPlaytest: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val spacing = LocalMagnetrailSpacing.current
    val dimensions = LocalMagnetrailDimensions.current
    val playLevelNumber = maxOf(
        uiState.progress.infinite.selectionOrdinal + 1,
        uiState.progress.infinite.completedCount + 1,
    )
    val playDifficulty = localizedDifficultyName(uiState.playDifficultyLabel)
    val dailyDate = localizedDateLabel(uiState.dailyDateLabel)
    val openSettingsDescription = stringResource(R.string.open_settings)
    val playDescription = stringResource(
        R.string.play_level_description,
        playLevelNumber,
        playDifficulty,
    )
    val dailyCompletionLabel = stringResource(
        if (uiState.todayDailyCompleted) R.string.completed_today else R.string.not_completed_today,
    )
    val dailyDescription = stringResource(
        R.string.daily_description,
        dailyDate,
        uiState.progress.currentStreak,
        dailyCompletionLabel,
    )
    val openHumanPlaytestDescription = stringResource(R.string.open_human_playtest)

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
                            .semantics { contentDescription = openSettingsDescription },
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
                    text = stringResource(R.string.app_name),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.home_tagline),
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
                            contentDescription = playDescription
                        },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.play_level, playLevelNumber))
                        Text(
                            playDifficulty,
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
                            contentDescription = dailyDescription
                        },
                    enabled = !uiState.isDailyLoading,
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md)) {
                        Text(
                            stringResource(R.string.daily_challenge_upper),
                            style = MaterialTheme.typography.labelSmall,
                            color = MagnetrailPull,
                        )
                        Text(
                            if (uiState.todayDailyCompleted) {
                                stringResource(R.string.todays_board_cleared)
                            } else {
                                stringResource(R.string.daily_field_for_date, dailyDate)
                            },
                            modifier = Modifier.padding(top = spacing.xs),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (uiState.isDailyLoading) {
                                stringResource(R.string.preparing_certified_board)
                            } else {
                                stringResource(
                                    R.string.current_and_best_streak,
                                    uiState.progress.currentStreak,
                                    uiState.progress.bestStreak,
                                )
                            },
                            modifier = Modifier.padding(top = spacing.xxs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MagnetrailMuted,
                        )
                    }
                }

                uiState.dailyError?.let { message ->
                    val localizedMessage = localizedRuntimeMessage(message).let { localized ->
                        if (localized == message) stringResource(R.string.runtime_daily_error) else localized
                    }
                    Text(
                        localizedMessage,
                        modifier = Modifier.widthIn(max = 420.dp).padding(top = spacing.xs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }

                if (showHumanPlaytest) {
                    OutlinedButton(
                        onClick = onOpenHumanPlaytest,
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth()
                            .padding(top = spacing.md)
                            .height(56.dp)
                            .semantics { contentDescription = openHumanPlaytestDescription },
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(stringResource(R.string.human_playtest))
                    }
                }
            }

            Spacer(Modifier.height(spacing.screenBottom))
        }
    }
}

@Composable
private fun CoinBalanceChip(balance: Int) {
    val spacing = LocalMagnetrailSpacing.current
    val balanceDescription = stringResource(R.string.coin_balance_description, balance)
    Surface(
        modifier = Modifier.semantics { contentDescription = balanceDescription },
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
