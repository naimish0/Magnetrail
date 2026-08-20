package com.rameshta.magnetrail.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rameshta.magnetrail.R
import com.rameshta.magnetrail.core.engine.TerminalEvent
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.economy.EconomyConfig
import com.rameshta.magnetrail.game.render.MagnetrailBoard
import com.rameshta.magnetrail.game.render.rememberTurnVisualState
import com.rameshta.magnetrail.ui.theme.LocalMagnetrailDimensions
import com.rameshta.magnetrail.ui.theme.LocalMagnetrailSpacing
import com.rameshta.magnetrail.ui.theme.MagnetrailMuted
import com.rameshta.magnetrail.ui.theme.MagnetrailPull
import com.rameshta.magnetrail.ui.theme.MagnetrailPullSoft
import com.rameshta.magnetrail.ui.theme.MagnetrailPush
import com.rameshta.magnetrail.ui.theme.MagnetrailSuccess
import com.rameshta.magnetrail.ads.RewardedOffer
import com.rameshta.magnetrail.ads.RewardedOfferStatus
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun GameScreen(
    uiState: GameUiState,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
    rewardedOffer: RewardedOffer = RewardedOffer(
        RewardedOfferStatus.UNAVAILABLE,
        false,
        "Watch an ad for one hint",
        "No ad available right now",
    ),
    rewardedSkipOffer: RewardedOffer = RewardedOffer(
        RewardedOfferStatus.UNAVAILABLE,
        false,
        "Skip level with an ad",
        "No ad available right now",
    ),
    onRewardedHint: () -> Unit = {},
    onRewardedSkip: () -> Unit = {},
    onNextLevel: () -> Unit = { onAction(GameAction.NextLevel) },
) {
    val spacing = LocalMagnetrailSpacing.current
    val dimensions = LocalMagnetrailDimensions.current
    val motionPolicy = remember(uiState.settings.reducedMotion) {
        MotionPolicy.from(uiState.settings.reducedMotion)
    }
    val turnVisualState = rememberTurnVisualState(
        result = uiState.inFlightResult,
        motionPolicy = motionPolicy,
        onPhaseChanged = { onAction(GameAction.AnimationPhaseChanged(it)) },
        onAnimationCompleted = { onAction(GameAction.AnimationCompleted) },
    )
    val tutorialLesson = uiState.activeTutorialLesson()
    val journeyNumber = uiState.progress.infinite.selectionOrdinal + 1
    val headerEyebrow = when (uiState.gameMode) {
        GameMode.DAILY -> "Daily Challenge"
        GameMode.INFINITE -> if (
            uiState.infiniteDifficulty == com.rameshta.magnetrail.core.infinite.InfiniteDifficulty.PROGRESSIVE
        ) {
            "Level $journeyNumber · Progressive"
        } else {
            "Infinite Puzzle"
        }
        GameMode.CAMPAIGN -> "Level ${uiState.currentLevel.number.toString().padStart(2, '0')}"
    }
    val headerTitle = when (uiState.gameMode) {
        GameMode.DAILY -> uiState.dailyDateLabel ?: uiState.currentLevel.title
        GameMode.INFINITE -> uiState.currentLevel.title.removePrefix("Infinite ")
        GameMode.CAMPAIGN -> uiState.currentLevel.title
    }
    val completionCelebration = if (uiState.isComplete) {
        completionCelebrationStyle(
            levelIdentity = uiState.infinitePuzzleId ?: uiState.dailyId ?: uiState.currentLevel.id,
            stars = uiState.completionReceipt?.grade?.stars ?: 1,
            actions = uiState.moves,
            overloads = uiState.overloads,
            hintsUsed = uiState.hintsUsed,
        )
    } else {
        null
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            GameTopBar(
                eyebrow = headerEyebrow,
                title = headerTitle,
                enabled = uiState.inFlightResult == null,
                onHome = { onAction(GameAction.NavigateHome) },
                onSettings = { onAction(GameAction.OpenSettings) },
            )

            if (uiState.gameMode == GameMode.INFINITE && uiState.infiniteFallbackUsed) {
                Text(
                    "Using the strongest available certified difficulty",
                    style = MaterialTheme.typography.bodySmall,
                    color = MagnetrailMuted,
                )
            }
            GameplayMetrics(uiState)
            GameStatusRow(uiState)

            tutorialLesson?.let { lesson ->
                TutorialCoachCard(
                    lesson = lesson,
                    reducedMotion = motionPolicy.reduced,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
                )
            }

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                MagnetrailBoard(
                    boardState = uiState.boardState,
                    inFlightResult = uiState.inFlightResult,
                    hintPreviewResult = uiState.hintPreviewResult,
                    turnVisualState = turnVisualState,
                    motionPolicy = motionPolicy,
                    highContrastFields = uiState.settings.highContrastFields,
                    suggestedArrowId = uiState.suggestedArrowId,
                    tutorialArrowId = tutorialLesson?.focusArrowId,
                    inputEnabled = uiState.inputEnabled,
                    onArrowTapped = { onAction(GameAction.LaunchArrow(it)) },
                    modifier = Modifier
                        .widthIn(max = if (uiState.isComplete) 220.dp else dimensions.boardMaxSize)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(
                            elevation = dimensions.boardElevation,
                            shape = RoundedCornerShape(30.dp),
                            clip = false,
                        ),
                )
            }

                when {
                    uiState.isComplete -> CompletionCard(
                        uiState,
                        motionPolicy,
                        requireNotNull(completionCelebration),
                        onAction,
                        onNextLevel,
                    )
                }

                if (!uiState.isComplete) {
                    GameControls(
                        uiState = uiState,
                        rewardedOffer = rewardedOffer,
                        rewardedSkipOffer = rewardedSkipOffer,
                        onRewardedHint = onRewardedHint,
                        onRewardedSkip = onRewardedSkip,
                        onAction = onAction,
                    )
                } else {
                    Spacer(Modifier.height(spacing.screenBottom))
                }
            }
            completionCelebration?.let { celebration ->
                BetweenGameConfetti(motionPolicy, celebration)
            }
        }
    }
}

@Composable
private fun GameTopBar(
    eyebrow: String,
    title: String,
    enabled: Boolean,
    onHome: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        GameIconButton(
            icon = R.drawable.ic_home,
            description = "Return home",
            enabled = enabled,
            onClick = onHome,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 58.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelSmall,
                color = MagnetrailPull,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
        }
        GameIconButton(
            icon = R.drawable.ic_settings,
            description = "Open settings",
            enabled = enabled,
            onClick = onSettings,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun GameIconButton(
    icon: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
                contentDescription = description
            },
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GameStatusRow(uiState: GameUiState) {
    val message = uiState.hintMessage ?: when {
        uiState.animationPhase == TurnAnimationPhase.IMPACT -> "Path blocked"
        uiState.animationPhase == TurnAnimationPhase.POLARITY_FLIP -> "The field flipped"
        uiState.inFlightResult?.terminalEvent is TerminalEvent.InvalidPullExit -> "Try another arrow"
        else -> "Find the sequence"
    }
    val polarities = uiState.boardState.magnets.map { it.polarity }.distinct()
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val content: @Composable () -> Unit = {
        Surface(
            modifier = Modifier.semantics {
                contentDescription = message
                liveRegion = LiveRegionMode.Polite
            },
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "●",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (uiState.suggestedArrowId != null) MagnetrailPull else MagnetrailSuccess,
                )
                Text(
                    message,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (uiState.suggestedArrowId != null) MagnetrailPull else MagnetrailMuted,
                    maxLines = 1,
                )
            }
        }
        polarities.forEach { polarity -> PolarityChip(polarity) }
    }
    if (largeText) {
        Column(
            modifier = Modifier.padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { content() }
    } else {
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

@Composable
private fun PolarityChip(polarity: Polarity) {
    val pull = polarity == Polarity.PULL
    Surface(
        modifier = Modifier.semantics {
            contentDescription = if (pull) "PULL, inward magnetic field" else "PUSH, outward magnetic field"
        },
        shape = RoundedCornerShape(999.dp),
        color = if (pull) MagnetrailPullSoft else com.rameshta.magnetrail.ui.theme.MagnetrailPushSoft,
    ) {
        Text(
            text = if (pull) "PULL  ›‹" else "PUSH  ‹›",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (pull) MagnetrailPull else MagnetrailPush,
        )
    }
}

@Composable
private fun GameplayMetrics(uiState: GameUiState) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val content: @Composable () -> Unit = {
        MetricItem(
            label = "Arrows",
            value = "${uiState.remainingArrowCount}/${uiState.initialArrowCount}",
            description = "Arrows remaining: ${uiState.remainingArrowCount}",
        )
        MetricItem("Actions", uiState.moves.toString(), "Actions: ${uiState.moves}")
        MetricItem("Overloads", uiState.overloads.toString(), "Overloads: ${uiState.overloads}")
    }
    if (largeText) {
        Column(
            modifier = Modifier.padding(top = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    } else {
        Surface(
            modifier = Modifier.padding(top = 2.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) { content() }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, description: String) {
    Column(
        modifier = Modifier.semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MagnetrailMuted,
        )
    }
}

@Composable
private fun GameControls(
    uiState: GameUiState,
    rewardedOffer: RewardedOffer,
    rewardedSkipOffer: RewardedOffer,
    onRewardedHint: () -> Unit,
    onRewardedSkip: () -> Unit,
    onAction: (GameAction) -> Unit,
) {
    val spacing = LocalMagnetrailSpacing.current
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val controls: @Composable (Modifier, Modifier) -> Unit = { restartModifier, hintModifier ->
        ActionButton(
            text = "Restart",
            icon = R.drawable.ic_restart,
            description = "Restart current level",
            enabled = uiState.canRestart,
            onClick = { onAction(GameAction.Restart) },
            modifier = restartModifier,
            primary = false,
        )
        ActionButton(
            text = when {
                uiState.isHintLoading || uiState.isHintPurchaseInProgress -> "Finding…"
                uiState.progress.coinBalance >= EconomyConfig.HINT_COST ->
                    "Hint · ${EconomyConfig.HINT_COST} coins"
                else -> "Hint · AD"
            },
            icon = R.drawable.ic_hint,
            description = when {
                uiState.isHintLoading || uiState.isHintPurchaseInProgress -> "Hint loading"
                uiState.progress.coinBalance >= EconomyConfig.HINT_COST ->
                    "Request a solver hint for ${EconomyConfig.HINT_COST} coins; balance ${uiState.progress.coinBalance}"
                else -> rewardedOffer.supportingText
            },
            enabled = uiState.canRequestHint &&
                (uiState.progress.coinBalance >= EconomyConfig.HINT_COST || rewardedOffer.enabled),
            onClick = {
                if (uiState.progress.coinBalance >= EconomyConfig.HINT_COST) {
                    onAction(GameAction.UseCoinHint)
                } else {
                    onRewardedHint()
                }
            },
            modifier = hintModifier,
            primary = true,
        )
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            if (largeText) {
                controls(Modifier.fillMaxWidth(), Modifier.fillMaxWidth())
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    controls(Modifier.weight(0.92f), Modifier.weight(1.08f))
                }
            }
            if (uiState.gameMode != GameMode.DAILY) {
                ActionButton(
                    text = "Skip level · AD · +${EconomyConfig.LEVEL_COMPLETION_REWARD} coins",
                    icon = R.drawable.ic_skip,
                    description = rewardedSkipOffer.supportingText,
                    enabled = uiState.canRequestSkip && rewardedSkipOffer.enabled,
                    onClick = onRewardedSkip,
                    modifier = Modifier.fillMaxWidth(),
                    primary = false,
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    primary: Boolean,
) {
    val content: @Composable () -> Unit = {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(text, modifier = Modifier.padding(start = 8.dp), maxLines = 1)
    }
    val buttonModifier = modifier.height(56.dp).semantics { contentDescription = description }
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = buttonModifier,
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = buttonModifier,
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) { content() }
    }
}

@Composable
private fun CompletionCard(
    uiState: GameUiState,
    motionPolicy: MotionPolicy,
    celebration: CompletionCelebrationStyle,
    onAction: (GameAction) -> Unit,
    onNextLevel: () -> Unit,
) {
    val spacing = LocalMagnetrailSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val receipt = uiState.completionReceipt
            CompletionCelebration(motionPolicy, celebration)
            if (celebration.celebratesStrongPlay) {
                Text(
                    celebration.message,
                    modifier = Modifier.semantics {
                        contentDescription = celebration.message
                        liveRegion = LiveRegionMode.Polite
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (celebration.intensity == CelebrationIntensity.EXCELLENT) {
                        MagnetrailPush
                    } else {
                        MagnetrailPull
                    },
                )
            }
            Text(
                "Board cleared",
                modifier = Modifier.semantics { heading() },
                style = if (celebration.celebratesStrongPlay) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.headlineSmall
                },
                fontWeight = FontWeight.Bold,
                color = MagnetrailSuccess,
            )
            Text(
                text = buildString {
                    repeat(3) { star -> append(if (star < (receipt?.grade?.stars ?: 1)) "★" else "☆") }
                },
                modifier = Modifier.padding(top = spacing.xs).semantics {
                    contentDescription = "${receipt?.grade?.stars ?: 1} stars earned"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MagnetrailPush,
            )
            val completionMetrics: @Composable () -> Unit = {
                Text(
                    "Actions ${uiState.moves}",
                    modifier = Modifier.semantics { contentDescription = "Moves: ${uiState.moves}" },
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    "Overloads ${uiState.overloads}",
                    modifier = Modifier.semantics { contentDescription = "Overloads: ${uiState.overloads}" },
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    "Hints ${uiState.hintsUsed}",
                    modifier = Modifier.semantics { contentDescription = "Hints: ${uiState.hintsUsed}" },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (LocalDensity.current.fontScale >= 1.3f) {
                Column(
                    modifier = Modifier.padding(top = spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { completionMetrics() }
            } else {
                Row(
                    modifier = Modifier.padding(top = spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.lg),
                ) { completionMetrics() }
            }
            receipt?.let { completion ->
                val best = completion.bestRecord
                Text(
                    "Best ${best.bestStars}★ · ${best.lowestActions ?: uiState.moves} actions",
                    modifier = Modifier.padding(top = spacing.xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MagnetrailMuted,
                )
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (completion.rewards.levelCompletionReward > 0) {
                        Text("Level complete +${completion.rewards.levelCompletionReward} coins")
                    }
                    if (completion.rewards.firstClearReward > 0) {
                        Text("First clear +${completion.rewards.firstClearReward} coins")
                    }
                    if (completion.rewards.dailyReward > 0) {
                        Text("Daily clear +${completion.rewards.dailyReward} coins")
                    }
                    Text(
                        "Balance ${completion.rewards.resultingBalance} coins",
                        modifier = Modifier.semantics {
                            contentDescription = "Resulting coin balance: ${completion.rewards.resultingBalance}"
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (uiState.gameMode == GameMode.DAILY) {
                        Text(
                            "Current streak ${uiState.progress.currentStreak} · Best ${uiState.progress.bestStreak}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MagnetrailMuted,
                        )
                    }
                    if (uiState.gameMode == GameMode.INFINITE) {
                        Text(
                            "Infinite streak ${uiState.progress.infinite.currentStreak} · Best ${uiState.progress.infinite.bestStreak}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MagnetrailMuted,
                        )
                        Text(
                            "Campaign progress remains separate",
                            style = MaterialTheme.typography.bodySmall,
                            color = MagnetrailMuted,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onAction(GameAction.Replay) }) { Text("Replay") }
                Button(onClick = onNextLevel) {
                    Text(
                        when {
                            uiState.gameMode == GameMode.DAILY -> "Home"
                            uiState.gameMode == GameMode.INFINITE -> "Next puzzle"
                            uiState.hasNextLevel -> "Next level"
                            else -> "Level selection"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BetweenGameConfetti(
    motionPolicy: MotionPolicy,
    style: CompletionCelebrationStyle,
) {
    if (motionPolicy.reduced || !style.celebratesStrongPlay) return
    val colors = listOf(
        MagnetrailPull,
        MagnetrailPush,
        MagnetrailSuccess,
        MaterialTheme.colorScheme.primary,
    )
    val progress = remember(style) { Animatable(0f) }
    LaunchedEffect(style) {
        progress.snapTo(0f)
        progress.animateTo(
            1f,
            tween(
                durationMillis = if (style.intensity == CelebrationIntensity.EXCELLENT) 1_600 else 1_100,
                easing = LinearEasing,
            ),
        )
    }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Performance confetti animation" }
            .testTag("between_game_confetti"),
    ) {
        val particleCount = if (style.intensity == CelebrationIntensity.EXCELLENT) {
            style.confettiCount * 2
        } else {
            style.confettiCount + 8
        }
        val globalFade = (1f - ((progress.value - 0.90f) / 0.10f).coerceIn(0f, 1f))
        repeat(particleCount) { index ->
            val seeded = index + style.variant * 19
            val delay = ((seeded * 17) % 35) / 100f
            val localProgress = ((progress.value - delay) / (1f - delay)).coerceIn(0f, 1f)
            if (localProgress <= 0f) return@repeat
            val baseX = (((seeded * 43) % 101) / 100f) * size.width
            val sway = sin((seeded * 0.61 + localProgress * PI * 2).toFloat()) * 18.dp.toPx()
            val position = Offset(
                x = (baseX + sway).coerceIn(4.dp.toPx(), size.width - 4.dp.toPx()),
                y = -12.dp.toPx() + localProgress * (size.height + 24.dp.toPx()),
            )
            val localAlpha = (localProgress * 6f).coerceAtMost(1f) * globalFade
            val halfWidth = if (seeded % 3 == 0) 3.dp.toPx() else 2.dp.toPx()
            val halfHeight = if (seeded % 2 == 0) 5.dp.toPx() else 3.dp.toPx()
            drawLine(
                color = colors[seeded % colors.size].copy(alpha = localAlpha),
                start = position + Offset(-halfWidth, -halfHeight),
                end = position + Offset(halfWidth, halfHeight),
                strokeWidth = 3.5.dp.toPx(),
            )
        }
    }
}

@Composable
private fun CompletionCelebration(
    motionPolicy: MotionPolicy,
    style: CompletionCelebrationStyle,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val progress = remember { Animatable(if (motionPolicy.reduced) 1f else 0f) }
    LaunchedEffect(motionPolicy.reduced, style) {
        if (!motionPolicy.reduced) {
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                tween(
                    durationMillis = if (style.celebratesStrongPlay) 760 else 420,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }
    val height = if (style.celebratesStrongPlay) 58.dp else if (motionPolicy.showCelebrationParticles) 36.dp else 18.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                if (style.celebratesStrongPlay) {
                    contentDescription = "${style.message} Celebration"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = MagnetrailPull.copy(alpha = (1f - progress.value) * 0.38f),
                radius = 8.dp.toPx() + progress.value * 24.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
            if (motionPolicy.showCelebrationParticles && style.confettiCount > 0) {
                val fade = (1f - ((progress.value - 0.72f) / 0.28f).coerceIn(0f, 1f))
                repeat(style.confettiCount) { index ->
                    val seeded = index + style.variant * 11
                    val horizontal = ((seeded * 37) % 101) / 100f - 0.5f
                    val vertical = 0.18f + ((seeded * 53) % 73) / 100f
                    val drift = sin((seeded * 0.73 + progress.value * PI).toFloat()) * 7.dp.toPx()
                    val particle = Offset(
                        x = center.x + horizontal * size.width * 0.9f * progress.value + drift,
                        y = center.y + (vertical * size.height - center.y) * progress.value,
                    )
                    val color = when (seeded % 4) {
                        0 -> MagnetrailPull
                        1 -> MagnetrailPush
                        2 -> MagnetrailSuccess
                        else -> primaryColor
                    }
                    drawLine(
                        color = color.copy(alpha = fade),
                        start = particle + Offset(-2.dp.toPx(), -3.dp.toPx()),
                        end = particle + Offset(2.dp.toPx(), 3.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
        }
        if (style.celebratesStrongPlay) {
            val animatedScale = if (motionPolicy.reduced) 1f else {
                val settled = (progress.value * 1.7f).coerceAtMost(1f)
                settled + sin((progress.value * PI).toFloat()) * 0.12f
            }
            Text(
                style.emoji,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = if (motionPolicy.reduced) 1f else (progress.value * 3f).coerceAtMost(1f)
                        scaleX = animatedScale
                        scaleY = animatedScale
                        rotationZ = if (motionPolicy.reduced) 0f else (1f - progress.value) *
                            if (style.variant % 2 == 0) -8f else 8f
                    }
                    .clearAndSetSemantics { },
                fontSize = 30.sp,
            )
        }
    }
}

private operator fun Offset.times(value: Float): Offset = Offset(x * value, y * value)
