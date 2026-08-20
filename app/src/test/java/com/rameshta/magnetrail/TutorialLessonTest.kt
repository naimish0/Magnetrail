package com.rameshta.magnetrail

import com.rameshta.magnetrail.core.infinite.InfiniteDifficulty
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.data.InfiniteProgress
import com.rameshta.magnetrail.data.PlayerProgress
import com.rameshta.magnetrail.game.GameMode
import com.rameshta.magnetrail.game.GameUiState
import com.rameshta.magnetrail.game.activeTutorialLesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialLessonTest {
    private val level = LevelDefinition(
        id = "tutorial-test",
        number = 1,
        title = "Tutorial",
        width = 4,
        height = 4,
        arrows = listOf(
            Arrow("A", Position(2, 2), Direction.EAST),
            Arrow("B", Position(1, 1), Direction.NORTH),
        ),
        magnets = emptyList(),
        walls = emptyList(),
        designedSolutions = listOf(listOf("A", "B")),
    )

    @Test
    fun `progressive levels one through ten expose ordered animated lessons`() {
        val lessons = (0..9).map { ordinal -> state(ordinal).activeTutorialLesson() }

        assertEquals((1..10).toList(), lessons.map { it?.number })
        assertTrue(lessons.all { it?.focusArrowId == "A" })
        assertTrue(lessons.all { it?.prompt?.isNotBlank() == true })
        assertTrue(lessons.first()?.prompt?.contains("hand") == true)
    }

    @Test
    fun `tutorial ends after level ten and never appears in fixed difficulty`() {
        assertNull(state(10).activeTutorialLesson())
        assertNull(state(0).copy(infiniteDifficulty = InfiniteDifficulty.RELAXED).activeTutorialLesson())
        assertNull(state(0).copy(isComplete = true).activeTutorialLesson())
    }

    @Test
    fun `numbered campaign exposes the same ten onboarding lessons`() {
        val lessons = (1..10).map { number ->
            state(0).copy(
                gameMode = GameMode.CAMPAIGN,
                currentLevel = level.copy(number = number),
            ).activeTutorialLesson()
        }

        assertEquals((1..10).toList(), lessons.map { it?.number })
        assertNull(
            state(0).copy(
                gameMode = GameMode.CAMPAIGN,
                currentLevel = level.copy(number = 11),
            ).activeTutorialLesson(),
        )
    }

    @Test
    fun `finger advances to the next authored arrow after a successful removal`() {
        val lesson = state(1).copy(
            moves = 1,
            boardState = level.initialState().copy(arrows = listOf(level.arrows[1])),
        ).activeTutorialLesson()

        assertEquals("B", lesson?.focusArrowId)
        assertTrue(lesson?.prompt?.startsWith("Nice.") == true)
    }

    @Test
    fun `failed tap keeps the finger on the current authored arrow`() {
        val lesson = state(1).copy(moves = 1).activeTutorialLesson()

        assertEquals("A", lesson?.focusArrowId)
    }

    private fun state(ordinal: Int) = GameUiState(
        levels = listOf(level),
        currentLevelIndex = -1,
        currentLevel = level,
        initialState = level.initialState(),
        boardState = level.initialState(),
        gameMode = GameMode.INFINITE,
        infinitePuzzleId = "infinite-v5-tutorial-test",
        infiniteDifficulty = InfiniteDifficulty.PROGRESSIVE,
        progress = PlayerProgress(
            lastSelectedLevelId = level.id,
            infinite = InfiniteProgress(selectionOrdinal = ordinal),
        ),
    )
}
