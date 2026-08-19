package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.generation.v5.CompletionTimeBucketV1
import com.rameshta.magnetrail.core.generation.v5.DifficultySelectionV1
import com.rameshta.magnetrail.core.generation.v5.MechanicExposureStateV1
import com.rameshta.magnetrail.core.generation.v5.PlayerPerformanceV1
import com.rameshta.magnetrail.core.generation.v5.PlayerSkillModelV1
import com.rameshta.magnetrail.core.generation.v5.PlayerSkillStateV1
import com.rameshta.magnetrail.core.generation.v5.ProgressionStateV1
import com.rameshta.magnetrail.core.generation.v5.SelectableLevelV1
import com.rameshta.magnetrail.core.generation.v5.SelectionStateV1
import com.rameshta.magnetrail.core.generation.v5.StructuralDifficultyBandV5
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultySelectionV1Test {
    @Test
    fun selectionIsDeterministicAndRespectsDifficultyJumpAndCurriculum() {
        val selector = DifficultySelectionV1()
        val progression = ProgressionStateV1(emptySet(), 40, 40, MechanicExposureStateV1(mapOf("PULL" to 3)))
        val skill = PlayerSkillStateV1(skillScore = 95)
        val selection = SelectionStateV1(
            recentDifficultyHistory = listOf(StructuralDifficultyBandV5.MEDIUM),
            selectionOrdinal = 12,
        )
        val pool = listOf(
            level("hard", StructuralDifficultyBandV5.HARD, 6),
            level("expert", StructuralDifficultyBandV5.EXPERT, 8),
            level("master", StructuralDifficultyBandV5.MASTER, 8),
        )

        val first = selector.selectNextLevel(progression, skill, pool, selection)
        val second = selector.selectNextLevel(progression, skill, pool, selection)

        assertEquals(first, second)
        assertEquals("hard", first.selectedLevelId)
        assertTrue(first.selectedDifficultyBand.rank <= StructuralDifficultyBandV5.HARD.rank)
    }

    @Test
    fun antiRepetitionChangesTheSelectedStructure() {
        val selector = DifficultySelectionV1()
        val progression = ProgressionStateV1(emptySet(), 90, 90, MechanicExposureStateV1(mapOf("PULL" to 3)))
        val skill = PlayerSkillStateV1(skillScore = 60)
        val repeated = level("hard-a", StructuralDifficultyBandV5.HARD, 6, interaction = "same")
        val fresh = level("hard-b", StructuralDifficultyBandV5.HARD, 7, interaction = "fresh")
        val baseline = selector.selectNextLevel(progression, skill, listOf(repeated, fresh), SelectionStateV1())
        val withHistory = selector.selectNextLevel(
            progression,
            skill,
            listOf(repeated, fresh),
            SelectionStateV1(recentInteractionFingerprints = listOf("same"), recentGridSizes = listOf(6, 6)),
        )

        assertEquals("hard-b", withHistory.selectedLevelId)
        assertNotEquals(baseline.selectionReason, "")
    }

    @Test
    fun skillChangesAreBoundedAndRecoveryDoesNotOverreactToOneAttempt() {
        val original = PlayerSkillStateV1(skillScore = 50)
        val oneDifficultResult = PlayerSkillModelV1.update(
            original,
            PlayerPerformanceV1(
                levelId = "x",
                difficultyBand = StructuralDifficultyBandV5.EXPERT,
                completed = false,
                actions = 8,
                parActions = 6,
                failedLaunches = 3,
                restarts = 1,
                hintsUsed = 0,
                undosUsed = 1,
                deadlocks = 1,
                completionTimeBucket = CompletionTimeBucketV1.VERY_SLOW,
            ),
        )

        assertTrue(oneDifficultResult.skillScore in 45..55)
        assertTrue(original.skillScore - oneDifficultResult.skillScore <= 5)
    }

    @Test
    fun mechanicExposureAndDeterministicFallbackAreEnforced() {
        val selector = DifficultySelectionV1()
        val locked = level("locked", StructuralDifficultyBandV5.MEDIUM, 5, required = setOf("CANCELLATION"))
        val available = level("available", StructuralDifficultyBandV5.EASY, 4)
        val progression = ProgressionStateV1(emptySet(), 20, 20, MechanicExposureStateV1(mapOf("PULL" to 2)))

        val result = selector.selectNextLevel(
            progression,
            PlayerSkillStateV1(40),
            listOf(locked, available),
            SelectionStateV1(),
        )

        assertEquals("available", result.selectedLevelId)
    }

    private fun level(
        id: String,
        band: StructuralDifficultyBandV5,
        grid: Int,
        interaction: String = "interaction-$id",
        required: Set<String> = emptySet(),
    ) = SelectableLevelV1(
        levelId = id,
        difficultyBand = band,
        gridSize = grid,
        mechanicTags = setOf("PULL"),
        requiredPriorMechanics = required,
        exactFingerprint = "exact-$id",
        symmetryFingerprint = "symmetry-$id",
        interactionFingerprint = interaction,
        dependencyFingerprint = "dependency-$id",
        strategyFingerprint = "strategy-$id",
        mechanicCombinationFingerprint = "mechanics-$id",
    )
}
