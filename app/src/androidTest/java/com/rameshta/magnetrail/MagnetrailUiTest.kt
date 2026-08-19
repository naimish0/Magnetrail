package com.rameshta.magnetrail

import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.hasScrollAction
import org.junit.Rule
import org.junit.Test

class MagnetrailUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShowsPrimaryActions() {
        composeRule.onNodeWithContentDescription("Play current level")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("Open level selection")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("Open settings").assertIsDisplayed()
        composeRule.onNodeWithText("DAILY CHALLENGE").performScrollTo().assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun gameplayLaunchesAtLevelOneAndExposesAllControls() {
        openLevel(1)

        composeRule.onNodeWithText("Level 01").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Arrow A, points east")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("Undo last successful move").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Restart current level").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Request a solver hint").assertIsDisplayed()
    }

    @Test
    fun undoEnablesAfterSuccessfulMoveCommits() {
        openLevel(2)
        composeRule.onNodeWithContentDescription("Undo last successful move").assertIsNotEnabled()

        composeRule.onNodeWithContentDescription("Arrow B, points north").performClick()
        waitForContentDescription("Arrows remaining: 1")

        composeRule.onNodeWithContentDescription("Undo last successful move").assertIsEnabled()
    }

    @Test
    fun restartRestoresInitialArrowCount() {
        openLevel(2)
        composeRule.onNodeWithContentDescription("Arrows remaining: 2").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Arrow B, points north").performClick()
        waitForContentDescription("Arrows remaining: 1")
        composeRule.onNodeWithContentDescription("Restart current level").performClick()

        composeRule.onNodeWithContentDescription("Arrows remaining: 2").assertIsDisplayed()
    }

    @Test
    fun levelSelectionRepresentsCompletionUnlockAndDebugAccess() {
        openLevel(1)
        composeRule.onNodeWithContentDescription("Arrow A, points east").performClick()
        waitForText("Board cleared")
        composeRule.onNodeWithContentDescription("Return home").performClick()
        composeRule.onNodeWithContentDescription("Open level selection").performClick()

        composeRule.onNodeWithContentDescription("Level 1: First release, completed")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("level_2")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithTag("level_12").performScrollTo().assertHasClickAction()
        composeRule.onNode(hasScrollAction()).performScrollToIndex(105)
        composeRule.onNodeWithTag("level_100").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun solverHintIsAnnouncedAndDoesNotLaunchArrow() {
        openLevel(6)
        composeRule.onNodeWithContentDescription("Arrows remaining: 2").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Request a solver hint").performClick()
        composeRule.onNodeWithText("Use 30 coins").performClick()
        waitForContentDescription("Hint: Try arrow B")

        composeRule.onNodeWithContentDescription("Arrow B, points south, suggested hint")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("Arrows remaining: 2").assertIsDisplayed()
    }

    @Test
    fun settingsTogglePersistsAcrossActivityRecreation() {
        composeRule.onNodeWithContentDescription("Open settings").performClick()
        val soundNode = composeRule.onNodeWithContentDescription("Sound")
        soundNode.performClick()
        val expectedState = soundNode.fetchSemanticsNode().config[SemanticsProperties.StateDescription]

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithContentDescription("Sound").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expectedState),
        )
    }

    @Test
    fun reducedMotionStillProducesTheSameCompletionOutcome() {
        composeRule.onNodeWithContentDescription("Open settings").performClick()
        val reducedMotion = composeRule.onNodeWithContentDescription("Reduced motion")
        val current = reducedMotion.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        if (current != "On") reducedMotion.performClick()
        composeRule.onNodeWithContentDescription("Close settings").performClick()

        openLevel(1)
        composeRule.onNodeWithContentDescription("Arrow A, points east").performClick()
        waitForText("Board cleared")

        composeRule.onNodeWithContentDescription("Moves: 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hints: 0").assertIsDisplayed()
        composeRule.onNodeWithText("First clear", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("New stars", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Daily clear", substring = true).assertIsDisplayed()
    }

    @Test
    fun completionExposesMetricsReplayAndNextLevel() {
        openLevel(1)
        composeRule.onNodeWithContentDescription("Arrow A, points east").performClick()
        waitForText("Board cleared")

        composeRule.onNodeWithContentDescription("Moves: 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hints: 0").assertIsDisplayed()
        composeRule.onNodeWithText("Replay").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Next level").assertIsDisplayed().assertHasClickAction()
    }

    @Suppress("DEPRECATION")
    @Test
    fun enlargedFontKeepsHomePrimaryActionsReachable() {
        composeRule.activityRule.scenario.onActivity { activity ->
            val configuration = Configuration(activity.resources.configuration).apply {
                fontScale = 1.5f
            }
            activity.resources.updateConfiguration(configuration, activity.resources.displayMetrics)
        }
        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithContentDescription("Play current level").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open level selection").assertIsDisplayed()
    }

    private fun openLevel(number: Int) {
        composeRule.onNodeWithContentDescription("Open level selection").performClick()
        composeRule.onNodeWithTag("level_$number").performScrollTo().performClick()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 7_000) {
            runCatching {
                composeRule.onNodeWithText(text).fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
    }

    private fun waitForContentDescription(description: String) {
        composeRule.waitUntil(timeoutMillis = 7_000) {
            composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
