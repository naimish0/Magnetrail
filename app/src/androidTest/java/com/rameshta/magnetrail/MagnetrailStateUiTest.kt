package com.rameshta.magnetrail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.semantics.SemanticsProperties
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.game.GameScreen
import com.rameshta.magnetrail.game.GameAction
import com.rameshta.magnetrail.game.GameUiState
import com.rameshta.magnetrail.home.HomeScreen
import com.rameshta.magnetrail.levels.LevelSelectionScreen
import com.rameshta.magnetrail.ui.theme.MagnetrailTheme
import com.rameshta.magnetrail.ads.RewardedOffer
import com.rameshta.magnetrail.ads.RewardedOfferStatus
import com.rameshta.magnetrail.data.PlayerSettings
import com.rameshta.magnetrail.data.LevelRecord
import com.rameshta.magnetrail.data.CompletionReceipt
import com.rameshta.magnetrail.data.PlayerProgress
import com.rameshta.magnetrail.data.InfiniteProgress
import com.rameshta.magnetrail.core.infinite.InfiniteDifficulty
import com.rameshta.magnetrail.game.GameMode
import com.rameshta.magnetrail.core.economy.RewardBreakdown
import com.rameshta.magnetrail.core.grading.AttemptGrade
import com.rameshta.magnetrail.settings.SettingsScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MagnetrailStateUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeKeepsOnePlayActionAndDailyChallengeOnly() {
        val level = level(1, "First release")
        val initial = level.initialState()
        composeRule.setContent {
            MagnetrailTheme {
                HomeScreen(
                    uiState = GameUiState(
                        levels = listOf(level),
                        currentLevelIndex = 0,
                        currentLevel = level,
                        initialState = initial,
                        boardState = initial,
                    ),
                    onPlay = {},
                    onOpenDaily = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("Magnetrail").assertIsDisplayed()
        composeRule.onNodeWithText("Bend the path. Clear the board.").assertIsDisplayed()
        composeRule.onNodeWithText("Play · Level 1").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Easy").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play Level 1, Easy difficulty")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithText("DAILY CHALLENGE").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Coin balance: 150").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open settings").assertIsDisplayed().assertHasClickAction()
        composeRule.onAllNodesWithText("Settings").assertCountEquals(0)
        composeRule.onAllNodesWithText("Continue").assertCountEquals(0)
        composeRule.onAllNodesWithText("Level select").assertCountEquals(0)
        composeRule.onAllNodesWithText("PROGRESSIVE JOURNEY").assertCountEquals(0)
    }

    @Test
    fun insufficientBalanceRoutesTheSingleHintButtonToRewardedAd() {
        val level = level(1, "First release")
        val initial = level.initialState()
        var rewardedSelected = false
        composeRule.setContent {
            MagnetrailTheme {
                GameScreen(
                    uiState = GameUiState(
                        levels = listOf(level),
                        currentLevelIndex = 0,
                        currentLevel = level,
                        initialState = initial,
                        boardState = initial,
                        progress = PlayerProgress(coinBalance = 0, lastSelectedLevelId = level.id),
                    ),
                    onAction = {},
                    rewardedOffer = RewardedOffer(
                        RewardedOfferStatus.AVAILABLE,
                        true,
                        "Watch an ad for one hint",
                        "Watch an ad to reveal one safe move.",
                    ),
                    onRewardedHint = { rewardedSelected = true },
                )
            }
        }

        composeRule.onNodeWithText("Hint · AD").assertIsDisplayed().assertIsEnabled().performClick()
        composeRule.runOnIdle { assertTrue(rewardedSelected) }
    }

    @Test
    fun deadlockRemainsUnannouncedWithRestartAvailableAndHintDisabled() {
        val level = level(1, "Keep thinking")
        val initial = level.initialState()
        composeRule.setContent {
            MagnetrailTheme {
                GameScreen(
                    uiState = GameUiState(
                        levels = listOf(level),
                        currentLevelIndex = 0,
                        currentLevel = level,
                        initialState = initial,
                        boardState = initial,
                        isDeadlocked = true,
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Restart").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("Hint · 30 coins").assertIsDisplayed().assertIsNotEnabled()
        composeRule.onAllNodesWithText("No clear route remains. Restart to try another sequence.")
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("No successful launches remain").assertCountEquals(0)
    }

    @Test
    fun rewardedSkipClearlyStatesAdAndTenCoinOutcome() {
        val level = level(1, "A skippable route")
        val initial = level.initialState()
        var skipSelected = false
        composeRule.setContent {
            MagnetrailTheme {
                GameScreen(
                    uiState = GameUiState(
                        levels = listOf(level),
                        currentLevelIndex = 0,
                        currentLevel = level,
                        initialState = initial,
                        boardState = initial,
                    ),
                    onAction = {},
                    rewardedSkipOffer = RewardedOffer(
                        RewardedOfferStatus.AVAILABLE,
                        true,
                        "Skip level with an ad",
                        "Watch an ad to skip this level and receive 10 coins.",
                    ),
                    onRewardedSkip = { skipSelected = true },
                )
            }
        }

        composeRule.onNodeWithText("Skip level · AD · +10 coins")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule.runOnIdle { assertTrue(skipSelected) }
    }

    @Test
    fun dailyChallengeDoesNotOfferSkip() {
        val level = level(1, "Daily integrity")
        val initial = level.initialState()
        composeRule.setContent {
            MagnetrailTheme {
                GameScreen(
                    uiState = GameUiState(
                        levels = listOf(level),
                        currentLevelIndex = -1,
                        currentLevel = level,
                        initialState = initial,
                        boardState = initial,
                        gameMode = GameMode.DAILY,
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onAllNodesWithText("Skip level · AD · +10 coins").assertCountEquals(0)
    }

    @Test
    fun strongSolveShowsBetweenGameConfettiAndDynamicPraise() {
        val level = level(1, "Celebrate skill")
        val initial = level.initialState()
        composeRule.setContent {
            MagnetrailTheme {
                GameScreen(
                    uiState = completedState(level, initial),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithTag("between_game_confetti").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Performance confetti animation").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Celebration", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Board cleared").assertIsDisplayed()
    }

    @Test
    fun reducedMotionKeepsPraiseButRemovesAnimatedBetweenGameConfetti() {
        val level = level(1, "Calm celebration")
        val initial = level.initialState()
        composeRule.setContent {
            MagnetrailTheme {
                GameScreen(
                    uiState = completedState(level, initial).copy(
                        settings = PlayerSettings(reducedMotion = true),
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onAllNodesWithTag("between_game_confetti").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Celebration", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Board cleared").assertIsDisplayed()
    }

    @Test
    fun firstProgressiveLevelShowsAnimatedTutorialAndBoardFocus() {
        val level = level(1, "Learn to launch")
        val initial = level.initialState()
        composeRule.setContent {
            MagnetrailTheme {
                GameScreen(
                    uiState = GameUiState(
                        levels = listOf(level),
                        currentLevelIndex = -1,
                        currentLevel = level,
                        initialState = initial,
                        boardState = initial,
                        gameMode = GameMode.INFINITE,
                        infinitePuzzleId = "infinite-v5-tutorial-test",
                        infiniteDifficulty = InfiniteDifficulty.PROGRESSIVE,
                        progress = PlayerProgress(
                            lastSelectedLevelId = level.id,
                            infinite = InfiniteProgress(selectionOrdinal = 0),
                        ),
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Tutorial 1 of 10", substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Arrow A, points east, tutorial focus")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun settingsExposeDiagnosticsPrivacyOptionsAndDebugPolicyPlaceholder() {
        composeRule.setContent {
            MagnetrailTheme {
                SettingsScreen(
                    settings = PlayerSettings(),
                    onBack = {},
                    onSettingChanged = { _, _ -> },
                    privacyOptionsRequired = true,
                    showPrivacyPolicyPlaceholder = true,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Usage & crash diagnostics").assertIsDisplayed()
        composeRule.onNodeWithText("Privacy options").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Privacy policy").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun delayedHintLoadingStateIsAccessible() {
        val level = level(1, "First release")
        val initial = level.initialState()
        composeRule.setContent {
            MagnetrailTheme {
                GameScreen(
                    uiState = GameUiState(
                        levels = listOf(level),
                        currentLevelIndex = 0,
                        currentLevel = level,
                        initialState = initial,
                        boardState = initial,
                        isHintLoading = true,
                        hintMessage = "Finding a clean move",
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Finding a clean move").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hint loading")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun releaseProgressionExposesSequentialLockSemantics() {
        val levels = listOf(level(1, "First release"), level(2, "Clear the blocker"))
        composeRule.setContent {
            MagnetrailTheme {
                LevelSelectionScreen(
                    levels = levels,
                    currentLevelIndex = 0,
                    highestUnlockedLevel = 1,
                    completedLevelIds = emptySet(),
                    debugUnlockAll = false,
                    onBack = {},
                    onLevelSelected = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Level 1: First release, available")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Level 2: Clear the blocker, locked")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun campaignSelectionExposesTheFullProgressiveJourney() {
        val levels = listOf(level(1, "First release"), level(2, "Clear the blocker"))
        var journeyOpened = false
        composeRule.setContent {
            MagnetrailTheme {
                LevelSelectionScreen(
                    levels = levels,
                    currentLevelIndex = 0,
                    highestUnlockedLevel = 1,
                    completedLevelIds = emptySet(),
                    debugUnlockAll = false,
                    infiniteLevelCount = 624,
                    onOpenInfinite = { journeyOpened = true },
                    onBack = {},
                    onLevelSelected = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open Progressive Journey with 624 certified puzzles")
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertTrue(journeyOpened) }
    }

    @Test
    fun expandedLevelSelectionLazilyScrollsThroughLevel200WithStableProgressSemantics() {
        val levels = (1..200).map { level(it, "Board $it") }
        composeRule.setContent {
            MagnetrailTheme {
                LevelSelectionScreen(
                    levels = levels,
                    currentLevelIndex = 150,
                    highestUnlockedLevel = 152,
                    completedLevelIds = setOf("test-151"),
                    debugUnlockAll = false,
                    recordsByLevel = mapOf("test-151" to LevelRecord(bestStars = 3)),
                    onBack = {},
                    onLevelSelected = {},
                )
            }
        }

        composeRule.onNodeWithTag("level_151").performScrollTo()
        composeRule.onNodeWithContentDescription("Level 151: Board 151, completed")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 stars"))
        composeRule.onNodeWithTag("level_200").performScrollTo()
        composeRule.onNodeWithContentDescription("Level 200: Board 200, locked")
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0 stars"))
    }

    private fun level(number: Int, title: String): LevelDefinition = LevelDefinition(
        id = "test-$number",
        number = number,
        title = title,
        width = 4,
        height = 4,
        arrows = listOf(Arrow("A", Position(2, 2), Direction.EAST)),
        magnets = emptyList(),
        walls = emptyList(),
        designedSolutions = listOf(listOf("A")),
    )

    private fun completedState(
        level: LevelDefinition,
        initial: com.rameshta.magnetrail.core.model.BoardState,
    ) = GameUiState(
        levels = listOf(level),
        currentLevelIndex = 0,
        currentLevel = level,
        initialState = initial,
        boardState = initial.copy(arrows = emptyList()),
        isComplete = true,
        inputEnabled = false,
        moves = 1,
        completionReceipt = CompletionReceipt(
            grade = AttemptGrade(stars = 3, actions = 1, overloads = 0, hintsUsed = 0),
            bestRecord = LevelRecord(bestStars = 3, lowestActions = 1, lowestOverloads = 0, lowestHints = 0),
            rewards = RewardBreakdown(resultingBalance = 150),
        ),
    )
}
