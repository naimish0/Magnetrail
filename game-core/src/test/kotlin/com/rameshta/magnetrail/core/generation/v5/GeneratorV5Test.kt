package com.rameshta.magnetrail.core.generation.v5

import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Magnet
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorV5Test {
    @Test
    fun rawGenerationIsDeterministicAndHonorsExplicitGridProfile() {
        val profile = GenerationProfilesV5.EASY.copy(gridSizes = listOf(8))
        val request = GenerationRequestV5("test-v5", 1, "test", 912_331L, profile, maxAttempts = 1)
        val generator = LevelGeneratorV5()

        val first = generator.generateRaw(request)
        val second = generator.generateRaw(request)

        assertEquals(first, second)
        assertEquals(8, first.width)
        assertEquals(8, first.height)
        assertTrue(first.arrows.size in profile.minArrows..profile.maxArrows)
        assertTrue(first.magnets.size in profile.minMagnets..profile.maxMagnets)
        assertTrue(first.walls.size in profile.minWalls..profile.maxWalls)
    }

    @Test
    fun certifiedCandidateSelectionIsDeterministic() {
        val request = GenerationRequestV5(
            stableId = "certified-v5",
            sequenceNumber = 1,
            title = "certified",
            seed = 5_200_001L,
            profile = GenerationProfilesV5.TUTORIAL,
            maxAttempts = 100,
        )
        val first = LevelGeneratorV5().generate(request) as GenerationResultV5.Generated
        val second = LevelGeneratorV5().generate(request) as GenerationResultV5.Generated

        assertEquals(first.level, second.level)
        assertEquals(first.diagnostics, second.diagnostics)
        assertEquals(first.attemptsUsed, second.attemptsUsed)
    }

    @Test
    fun interactionGraphUsesProductionControlAndCancellation() {
        val controlled = level(
            id = "controlled",
            arrows = listOf(Arrow("a1", Position(2, 1), Direction.NORTH)),
            magnets = listOf(Magnet("m1", Position(2, 3), Polarity.PULL)),
        )
        val cancellation = level(
            id = "cancelled",
            arrows = listOf(Arrow("a1", Position(2, 2), Direction.NORTH)),
            magnets = listOf(
                Magnet("m1", Position(2, 1), Polarity.PULL),
                Magnet("m2", Position(2, 3), Polarity.PUSH),
            ),
        )
        val analyzer = StructuralAnalyzerV5()
        val limits = StructuralAnalysisLimitsV5(maxStates = 1_000, maxActionResolutions = 10_000)

        val controlledResult = analyzer.analyze(controlled, StructuralDifficultyBandV5.TUTORIAL, limits)
        val cancellationResult = analyzer.analyze(cancellation, StructuralDifficultyBandV5.TUTORIAL, limits)

        assertTrue(controlledResult.interactionGraph.edges.any { it.type == InteractionTypeV5.MAGNET_CONTROL })
        assertTrue(cancellationResult.interactionGraph.edges.any { it.type == InteractionTypeV5.CANCELLATION })
        assertEquals(1, cancellationResult.magnetCancellationCount)
        assertFalse(controlledResult.truncated)
    }

    @Test
    fun migrationBlocksWithoutMappingAndPreservesNonCampaignStateWithApprovedMap() {
        val snapshot = LegacyPlayerSnapshotV1(
            completedLevelIds = setOf("old-1"),
            bestActionsByLevel = mapOf("old-1" to 4),
            starsByLevel = mapOf("old-1" to 3),
            claimedRewardLevelIds = setOf("old-1"),
            dailyStateFingerprint = "daily",
            settingsFingerprint = "settings",
            economyBalance = 420,
        )
        val blocked = D2MigrationSafety.assess(snapshot, setOf("old-1"), setOf("new-1"), null)
        assertEquals(D2MigrationStatusV1.BLOCKED, blocked.status)
        assertTrue("MIGRATION_MAPPING_NOT_APPROVED" in blocked.reasonCodes)

        val safe = D2MigrationSafety.assess(
            snapshot,
            setOf("old-1"),
            setOf("new-1"),
            mapOf("old-1" to "new-1"),
        )
        assertEquals(D2MigrationStatusV1.SAFE, safe.status)
        assertEquals(setOf("new-1"), safe.migratedSnapshot?.completedLevelIds)
        assertEquals(3, safe.migratedSnapshot?.starsByLevel?.get("new-1"))
        assertEquals("daily", safe.migratedSnapshot?.dailyStateFingerprint)
        assertEquals("settings", safe.migratedSnapshot?.settingsFingerprint)
        assertEquals(420, safe.migratedSnapshot?.economyBalance)
    }

    private fun level(
        id: String,
        arrows: List<Arrow>,
        magnets: List<Magnet>,
    ): LevelDefinition = LevelDefinition(
        id = id,
        number = 1,
        title = id,
        width = 3,
        height = 3,
        arrows = arrows,
        magnets = magnets,
        walls = emptyList(),
        designedSolutions = listOf(arrows.map { it.id }),
    )
}
