package com.rameshta.magnetrail.levels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.ui.theme.LocalMagnetrailSpacing
import com.rameshta.magnetrail.ui.theme.MagnetrailBorder
import com.rameshta.magnetrail.ui.theme.MagnetrailMuted
import com.rameshta.magnetrail.ui.theme.MagnetrailPull
import com.rameshta.magnetrail.ui.theme.MagnetrailPullSoft

@Composable
fun LevelSelectionScreen(
    levels: List<LevelDefinition>,
    currentLevelIndex: Int,
    highestUnlockedLevel: Int,
    completedLevelIds: Set<String>,
    debugUnlockAll: Boolean,
    onBack: () -> Unit,
    onLevelSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalMagnetrailSpacing.current
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = spacing.sm),
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                        .semantics { contentDescription = "Close level selection" },
                ) { Text("Back") }
                Text(
                    "Field basics",
                    modifier = Modifier.align(Alignment.Center).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "${completedLevelIds.size} of ${levels.size} boards cleared",
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                style = MaterialTheme.typography.bodyMedium,
                color = MagnetrailMuted,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                itemsIndexed(levels, key = { _, level -> level.id }) { index, level ->
                    val completed = level.id in completedLevelIds
                    val progressionUnlocked = index < highestUnlockedLevel
                    val available = progressionUnlocked || debugUnlockAll
                    val stateLabel = when {
                        completed -> "completed"
                        progressionUnlocked -> "available"
                        debugUnlockAll -> "available for debug"
                        else -> "locked"
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                            .testTag("level_${level.number}")
                            .semantics {
                                contentDescription =
                                    "Level ${level.number}: ${level.title}, $stateLabel"
                                role = Role.Button
                                if (!available) disabled()
                            }
                            .clickable(enabled = available) { onLevelSelected(index) },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(
                            width = if (completed) 2.dp else 1.dp,
                            color = if (completed) MagnetrailPull else MagnetrailBorder,
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                completed -> MagnetrailPullSoft
                                index == currentLevelIndex -> MaterialTheme.colorScheme.surface
                                else -> MaterialTheme.colorScheme.surface
                            },
                        ),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(spacing.sm),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = when {
                                    completed -> "✓ ${level.number.toString().padStart(2, '0')}"
                                    !available -> "LOCK ${level.number.toString().padStart(2, '0')}"
                                    else -> level.number.toString().padStart(2, '0')
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (available) MaterialTheme.colorScheme.primary else MagnetrailMuted,
                            )
                            Text(
                                text = level.title,
                                modifier = Modifier.padding(top = spacing.xxs),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (available) MaterialTheme.colorScheme.onSurface else MagnetrailMuted,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        }
                    }
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    if (debugUnlockAll && highestUnlockedLevel < levels.size) {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = spacing.xs)) {
                            Text(
                                "Debug build: locked boards remain open for QA.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MagnetrailMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}
