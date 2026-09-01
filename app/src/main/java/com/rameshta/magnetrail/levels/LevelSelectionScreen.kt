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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.R
import com.rameshta.magnetrail.data.LevelRecord
import com.rameshta.magnetrail.localization.localizedDifficultyName
import com.rameshta.magnetrail.localization.localizedLevelTitle
import com.rameshta.magnetrail.localization.localizedPackName
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
    recordsByLevel: Map<String, LevelRecord> = emptyMap(),
    infiniteLevelCount: Int = 0,
    onOpenInfinite: () -> Unit = {},
) {
    val spacing = LocalMagnetrailSpacing.current
    val initialLevelNumber = maxOf(currentLevelIndex + 1, highestUnlockedLevel).coerceAtMost(levels.size.coerceAtLeast(1))
    var pageIndex by rememberSaveable(levels.size, currentLevelIndex) {
        mutableStateOf(LevelRangeNavigator.pageForLevel(initialLevelNumber, levels.size))
    }
    var goToText by rememberSaveable { mutableStateOf("") }
    val range = LevelRangeNavigator.window(pageIndex, levels.size)
    val visibleLevels = levels.subList(range.startIndex, range.endIndexExclusive)
    val closeDescription = stringResource(R.string.close_level_selection)
    val openProgressiveDescription = stringResource(R.string.open_progressive_journey, infiniteLevelCount)
    val visibleRangeDescription = stringResource(R.string.visible_level_range)
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = spacing.sm),
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                        .semantics { contentDescription = closeDescription },
                ) { Text(stringResource(R.string.back)) }
                Text(
                    stringResource(R.string.campaign),
                    modifier = Modifier.align(Alignment.Center).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = stringResource(
                    R.string.campaign_progress,
                    completedLevelIds.size,
                    levels.size,
                    recordsByLevel.values.sumOf { it.bestStars },
                    levels.size * 3,
                ),
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                style = MaterialTheme.typography.bodyMedium,
                color = MagnetrailMuted,
            )
            if (infiniteLevelCount > 0) {
                Card(
                    onClick = onOpenInfinite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm)
                        .testTag("open_progressive_journey")
                        .semantics {
                            contentDescription = openProgressiveDescription
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MagnetrailPullSoft),
                ) {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        Text(
                            stringResource(R.string.progressive_journey_puzzles, infiniteLevelCount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MagnetrailPull,
                        )
                        Text(
                            stringResource(R.string.progressive_journey_card_detail),
                            modifier = Modifier.padding(top = spacing.xxs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MagnetrailMuted,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                    enabled = range.hasPrevious,
                ) { Text(stringResource(R.string.previous)) }
                Text(
                    text = if (levels.isEmpty()) {
                        stringResource(R.string.no_levels)
                    } else {
                        stringResource(R.string.levels_range, range.startLevelNumber, range.endLevelNumber)
                    },
                    modifier = Modifier.semantics { contentDescription = visibleRangeDescription },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    onClick = { pageIndex = (pageIndex + 1).coerceAtMost((range.pageCount - 1).coerceAtLeast(0)) },
                    enabled = range.hasNext,
                ) { Text(stringResource(R.string.next)) }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = goToText,
                    onValueChange = { value -> goToText = value.filter(Char::isDigit).take(6) },
                    modifier = Modifier.weight(1f).testTag("go_to_level_input"),
                    label = { Text(stringResource(R.string.go_to_level)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                TextButton(
                    onClick = {
                        goToText.toIntOrNull()?.takeIf { it in 1..levels.size }?.let { levelNumber ->
                            pageIndex = LevelRangeNavigator.pageForLevel(levelNumber, levels.size)
                        }
                    },
                    modifier = Modifier.width(64.dp).testTag("go_to_level_action"),
                ) { Text(stringResource(R.string.go)) }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                visibleLevels.forEachIndexed { visibleIndex, level ->
                    val index = range.startIndex + visibleIndex
                    val packId = level.metadata?.packId ?: "field-basics"
                    val previousPack = levels.getOrNull(index - 1)?.metadata?.packId ?: "field-basics"
                    if (visibleIndex == 0 || packId != previousPack) {
                        val packDifficultyBands = levels.asSequence()
                            .filter { it.metadata?.packId == packId }
                            .mapNotNull { it.metadata?.difficultyBand?.name }
                            .distinct()
                            .toList()
                        item(
                            key = "pack_$packId",
                            span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) },
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = spacing.sm)) {
                                Text(
                                    localizedPackName(packId),
                                    modifier = Modifier.semantics { heading() },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    if (packDifficultyBands.size > 1) {
                                        stringResource(R.string.difficulty_mixed)
                                    } else {
                                        packDifficultyBands.singleOrNull()?.let { localizedDifficultyName(it) }
                                            ?: stringResource(R.string.difficulty_intro)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MagnetrailMuted,
                                )
                            }
                        }
                    }
                    val completed = level.id in completedLevelIds
                    val stars = recordsByLevel[level.id]?.bestStars ?: 0
                    val progressionUnlocked = index < highestUnlockedLevel
                    val available = progressionUnlocked || debugUnlockAll
                    item(key = level.id) {
                    val levelTitle = localizedLevelTitle(level)
                    val stateLabel = stringResource(
                        when {
                            completed -> R.string.level_state_completed
                            progressionUnlocked -> R.string.level_state_available
                            debugUnlockAll -> R.string.level_state_debug
                            else -> R.string.level_state_locked
                        },
                    )
                    val levelDescription = stringResource(
                        R.string.level_description,
                        level.number,
                        levelTitle,
                        stateLabel,
                    )
                    val starsDescription = stringResource(R.string.stars_description, stars)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                            .testTag("level_${level.number}")
                            .semantics {
                                contentDescription = levelDescription
                                stateDescription = starsDescription
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
                                    !available -> stringResource(
                                        R.string.lock_level,
                                        level.number.toString().padStart(2, '0'),
                                    )
                                    else -> level.number.toString().padStart(2, '0')
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (available) MaterialTheme.colorScheme.primary else MagnetrailMuted,
                            )
                            Text(
                                text = levelTitle,
                                modifier = Modifier.padding(top = spacing.xxs),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (available) MaterialTheme.colorScheme.onSurface else MagnetrailMuted,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                            Text(
                                text = buildString {
                                    repeat(3) { star -> append(if (star < stars) "★" else "☆") }
                                },
                                modifier = Modifier.padding(top = spacing.xxs),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (stars > 0) com.rameshta.magnetrail.ui.theme.MagnetrailPush else MagnetrailMuted,
                            )
                        }
                    }
                    }
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    if (debugUnlockAll && highestUnlockedLevel < levels.size) {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = spacing.xs)) {
                            Text(
                                stringResource(R.string.debug_levels_note),
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
