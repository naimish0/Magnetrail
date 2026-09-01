package com.rameshta.magnetrail.infinite

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rameshta.magnetrail.core.infinite.InfiniteDifficulty
import com.rameshta.magnetrail.R
import com.rameshta.magnetrail.data.InfiniteProgress
import com.rameshta.magnetrail.localization.localizedExplanation
import com.rameshta.magnetrail.localization.localizedName
import com.rameshta.magnetrail.ui.theme.LocalMagnetrailSpacing
import com.rameshta.magnetrail.ui.theme.MagnetrailMuted
import com.rameshta.magnetrail.ui.theme.MagnetrailPull
import com.rameshta.magnetrail.ui.theme.MagnetrailPullSoft

@Composable
fun InfiniteModeScreen(
    progress: InfiniteProgress,
    catalogSize: Int,
    onBack: () -> Unit,
    onSelectDifficulty: (InfiniteDifficulty) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalMagnetrailSpacing.current
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
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(
                    stringResource(R.string.cleared_count, progress.completedCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MagnetrailPull,
                )
            }
            Spacer(Modifier.height(spacing.lg))
            Text(
                stringResource(R.string.progressive_journey),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.progressive_journey_summary, catalogSize),
                modifier = Modifier.padding(top = spacing.xs),
                style = MaterialTheme.typography.bodyLarge,
                color = MagnetrailMuted,
            )
            Text(
                stringResource(R.string.streak_and_best, progress.currentStreak, progress.bestStreak),
                modifier = Modifier.padding(top = spacing.md),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(spacing.lg))
            InfiniteDifficulty.entries.forEach { difficulty ->
                val difficultyName = difficulty.localizedName()
                val difficultyExplanation = difficulty.localizedExplanation()
                val canResume = progress.selectedDifficulty == difficulty.name &&
                    progress.selectedPuzzleId != null &&
                    progress.history.none { it.puzzleId == progress.selectedPuzzleId && it.completed }
                val difficultyDescription = stringResource(
                    R.string.infinite_difficulty_description,
                    difficultyName,
                    difficultyExplanation,
                    if (canResume) stringResource(R.string.resume_available) else "",
                )
                Card(
                    onClick = { onSelectDifficulty(difficulty) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = spacing.md)
                        .semantics {
                            contentDescription = difficultyDescription
                        },
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (canResume) MagnetrailPullSoft else MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(modifier = Modifier.padding(spacing.lg)) {
                        Text(
                            difficultyName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            difficultyExplanation,
                            modifier = Modifier.padding(top = spacing.xs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MagnetrailMuted,
                        )
                        if (canResume) {
                            Text(
                                stringResource(R.string.resume_current_board),
                                modifier = Modifier.padding(top = spacing.sm),
                                style = MaterialTheme.typography.labelLarge,
                                color = MagnetrailPull,
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.expert_fallback_note),
                style = MaterialTheme.typography.bodySmall,
                color = MagnetrailMuted,
            )
            Spacer(Modifier.height(spacing.screenBottom))
        }
    }
}
