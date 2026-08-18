package com.rameshta.magnetrail

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class MagnetrailUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun gameplayLaunchesAtLevelOneAndArrowIsAccessible() {
        composeRule.onNodeWithText("Level 01").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Arrow A, points east")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun undoEnablesAfterSuccessfulMoveCommits() {
        composeRule.onNodeWithContentDescription("Undo last successful move").assertIsNotEnabled()

        composeRule.onNodeWithContentDescription("Arrow A, points east").performClick()
        waitForText("Board cleared")

        composeRule.onNodeWithContentDescription("Undo last successful move").assertIsEnabled()
    }

    @Test
    fun restartRestoresInitialArrowCount() {
        openLevel(2, "Clear the blocker")
        composeRule.onNodeWithContentDescription("Arrows remaining: 2").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Arrow B, points north").performClick()
        waitForContentDescription("Arrows remaining: 1")
        composeRule.onNodeWithContentDescription("Restart current level").performClick()

        composeRule.onNodeWithContentDescription("Arrows remaining: 2").assertIsDisplayed()
    }

    @Test
    fun levelSelectionCanOpenLevelTwelve() {
        openLevel(12, "Prototype capstone")

        composeRule.onNodeWithText("Level 12").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Arrows remaining: 4").assertIsDisplayed()
    }

    @Test
    fun completionExposesReplayAndNextLevel() {
        composeRule.onNodeWithContentDescription("Arrow A, points east").performClick()
        waitForText("Board cleared")

        composeRule.onNodeWithText("Replay").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Next level").assertIsDisplayed().assertHasClickAction()
    }

    private fun openLevel(number: Int, title: String) {
        composeRule.onNodeWithContentDescription("Open level selection").performClick()
        composeRule.onNodeWithContentDescription("Open Level $number: $title")
            .performScrollTo()
            .performClick()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText(text).fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
    }

    private fun waitForContentDescription(description: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
