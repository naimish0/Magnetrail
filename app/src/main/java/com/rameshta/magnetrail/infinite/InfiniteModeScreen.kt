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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rameshta.magnetrail.core.infinite.InfiniteDifficulty
import com.rameshta.magnetrail.data.InfiniteProgress
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
                TextButton(onClick = onBack) { Text("Back") }
                Text(
                    "${progress.completedCount} cleared",
                    style = MaterialTheme.typography.labelLarge,
                    color = MagnetrailPull,
                )
            }
            Spacer(Modifier.height(spacing.lg))
            Text(
                "Progressive Journey",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "$catalogSize certified puzzles in rotation. Start gently, then face every difficulty.",
                modifier = Modifier.padding(top = spacing.xs),
                style = MaterialTheme.typography.bodyLarge,
                color = MagnetrailMuted,
            )
            Text(
                "Streak ${progress.currentStreak} · Best ${progress.bestStreak}",
                modifier = Modifier.padding(top = spacing.md),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(spacing.lg))
            InfiniteDifficulty.entries.forEach { difficulty ->
                val canResume = progress.selectedDifficulty == difficulty.name &&
                    progress.selectedPuzzleId != null &&
                    progress.history.none { it.puzzleId == progress.selectedPuzzleId && it.completed }
                Card(
                    onClick = { onSelectDifficulty(difficulty) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = spacing.md)
                        .semantics {
                            contentDescription = buildString {
                                append("${difficulty.displayName} Infinite difficulty. ")
                                append(difficulty.explanation)
                                if (canResume) append(" Resume available.")
                            }
                        },
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (canResume) MagnetrailPullSoft else MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(modifier = Modifier.padding(spacing.lg)) {
                        Text(
                            difficulty.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            difficulty.explanation,
                            modifier = Modifier.padding(top = spacing.xs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MagnetrailMuted,
                        )
                        if (canResume) {
                            Text(
                                "Resume current board",
                                modifier = Modifier.padding(top = spacing.sm),
                                style = MaterialTheme.typography.labelLarge,
                                color = MagnetrailPull,
                            )
                        }
                    }
                }
            }
            Text(
                "Expert always falls back to the strongest fully certified band until Expert certification passes unchanged gates.",
                style = MaterialTheme.typography.bodySmall,
                color = MagnetrailMuted,
            )
            Spacer(Modifier.height(spacing.screenBottom))
        }
    }
}
