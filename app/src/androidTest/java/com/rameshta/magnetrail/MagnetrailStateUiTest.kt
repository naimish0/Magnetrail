package com.rameshta.magnetrail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.game.GameScreen
import com.rameshta.magnetrail.game.GameUiState
import com.rameshta.magnetrail.levels.LevelSelectionScreen
import com.rameshta.magnetrail.ui.theme.MagnetrailTheme
import org.junit.Rule
import org.junit.Test

class MagnetrailStateUiTest {
    @get:Rule
    val composeRule = createComposeRule()

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
}
