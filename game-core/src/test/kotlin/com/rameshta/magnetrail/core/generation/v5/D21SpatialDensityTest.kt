package com.rameshta.magnetrail.core.generation.v5

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Magnet
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall
import com.rameshta.magnetrail.core.solver.Solver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class D21SpatialDensityTest {
    @Test
    fun occupancyAndIndependentObjectRatiosAreCalculatedFromAuthoredCells() {
        val board = level(
            size = 4,
            arrows = listOf(Arrow("a1", Position(1, 1), Direction.NORTH), Arrow("a2", Position(4, 4), Direction.SOUTH)),
            magnets = listOf(Magnet("m1", Position(1, 4), Polarity.PULL)),
            walls = listOf(Wall(Position(2, 2)), Wall(Position(3, 3))),
        )

        val metrics = SpatialDensityAnalyzerV5.analyze(board)

        assertEquals(16, metrics.boardCells)
        assertEquals(5, metrics.occupiedCells)
        assertEquals(11, metrics.emptyCells)
        assertEquals(0.3125, metrics.occupancyRatio, 0.0001)
        assertEquals(0.125, metrics.arrowRatio, 0.0001)
        assertEquals(0.0625, metrics.magnetRatio, 0.0001)
        assertEquals(0.125, metrics.wallRatio, 0.0001)
        assertEquals(0, metrics.overlappingAuthoredCellCount)
    }

    @Test
    fun everyMediumOrHigherRawBoardMeetsConfiguredOccupancyAndObjectCounts() {
        GenerationProfilesD21.benchmarkProfiles.drop(2).forEachIndexed { profileIndex, profile ->
            repeat(12) { seedOffset ->
                val request = GenerationRequestV5(
                    "density-${profile.id}-$seedOffset", 1, "density",
                    91_000L + profileIndex * 10_000L + seedOffset, profile, maxAttempts = 1,
                )
                val board = LevelGeneratorV5().generateRaw(request)
                val spatial = requireNotNull(profile.spatialDensityProfile)
                val metrics = SpatialDensityAnalyzerV5.analyze(board, spatial.longRangeDistance)
                assertTrue("${profile.id}: ${metrics.occupancyRatio}", metrics.occupancyRatio >= spatial.minimumOccupancyRatio)
                assertTrue("${profile.id}: ${metrics.occupancyRatio}", metrics.occupancyRatio <= spatial.maximumOccupancyRatio)
                assertTrue(board.arrows.size in spatial.arrowCount)
                assertTrue(board.magnets.size in spatial.magnetCount)
                assertTrue(board.walls.size in spatial.wallCount)
                assertEquals(0, metrics.overlappingAuthoredCellCount)
            }
        }
    }

    @Test
    fun minimumAndMaximumOccupancyAreEnforcedBeforeSolverWork() {
        val profile = requireNotNull(GenerationProfilesD21.MEDIUM.spatialDensityProfile)
        val sparse = level(size = 5, arrows = listOf(Arrow("a1", Position(1, 1), Direction.NORTH)))
        val full = level(
            size = 5,
            arrows = (1..5).map { Arrow("a$it", Position(1, it), Direction.NORTH) },
            magnets = (1..4).map { Magnet("m$it", Position(2, it), Polarity.PULL) },
            walls = listOf(Wall(Position(2, 5))) +
                (1..15).map { index -> Wall(Position(3 + (index - 1) / 5, 1 + (index - 1) % 5)) },
        )

        assertTrue("occupancy-below-profile" in SpatialDensityGateV5.evaluateAuthored(sparse, profile))
        assertFalse("occupancy-above-profile" in SpatialDensityGateV5.evaluateAuthored(full, profile))
    }

    @Test
    fun authoredSpatialRelationshipsDetectLongRangeLosArrowBlockingAndWallOcclusion() {
        val board = level(
            size = 8,
            arrows = listOf(
                Arrow("target", Position(4, 1), Direction.WEST),
                Arrow("blocker", Position(4, 3), Direction.NORTH),
                Arrow("multi", Position(1, 7), Direction.NORTH),
            ),
            magnets = listOf(
                Magnet("far", Position(4, 8), Polarity.PULL),
                Magnet("vertical", Position(8, 7), Polarity.PUSH),
                Magnet("horizontal", Position(1, 1), Polarity.PULL),
            ),
            walls = listOf(Wall(Position(4, 5))),
        )

        val metrics = SpatialDensityAnalyzerV5.analyze(board, longRangeDistance = 4)

        assertTrue(metrics.authoredLongRangeMagneticRelationshipCount >= 3)
        assertTrue(metrics.arrowsWithMultipleAlignedMagnets >= 1)
        assertTrue(metrics.arrowBlockerCandidateRelationshipCount >= 1)
        assertTrue(metrics.wallOcclusionCandidateRelationshipCount >= 1)
    }

    @Test
    fun sparseTutorialIsAllowedButTheSameBoardFailsMedium() {
        val board = level(size = 3, arrows = listOf(Arrow("a1", Position(1, 1), Direction.NORTH)))
        val tutorial = requireNotNull(GenerationProfilesD21.TUTORIAL.spatialDensityProfile)
        val medium = requireNotNull(GenerationProfilesD21.MEDIUM.spatialDensityProfile)

        assertFalse("occupancy-below-profile" in SpatialDensityGateV5.evaluateAuthored(board, tutorial))
        assertTrue("occupancy-below-profile" in SpatialDensityGateV5.evaluateAuthored(board, medium))
    }

    @Test
    fun denseButTrivialSignalRejectsVisualDensityWithoutConsequences() {
        assertTrue(
            SpatialDensityGateV5.isDenseButTrivial(
                occupancyRatio = 0.70,
                targetOccupancyRatio = 0.60,
                greedySolveRate = 1.0,
                safeChoiceRatio = 0.99,
                meaningfulFailureRate = 0.01,
            ),
        )
        assertFalse(
            SpatialDensityGateV5.isDenseButTrivial(
                occupancyRatio = 0.70,
                targetOccupancyRatio = 0.60,
                greedySolveRate = 0.67,
                safeChoiceRatio = 0.75,
                meaningfulFailureRate = 0.25,
            ),
        )
    }

    @Test
    fun generationIsDeterministicAcrossEveryD21ProfileAndSeedsSeparateCandidates() {
        GenerationProfilesD21.benchmarkProfiles.forEachIndexed { index, profile ->
            val request = GenerationRequestV5("det-$index", 1, "det", 7_210_000L + index, profile, maxAttempts = 1)
            val first = LevelGeneratorV5().generateRaw(request)
            val second = LevelGeneratorV5().generateRaw(request)
            val other = LevelGeneratorV5().generateRaw(request, request.seed + 1)
            assertEquals(first, second)
            assertNotEquals(ContentFingerprint.of(first), ContentFingerprint.of(other))
        }
    }

    @Test
    fun profileMatrixSeparatesDensityFromDifficultyAndKeepsNineByNineExperimental() {
        assertEquals(7, GenerationProfilesD21.benchmarkProfiles.size)
        assertEquals(listOf(5, 6), GenerationProfilesD21.MEDIUM.gridSizes)
        assertEquals(listOf(8), GenerationProfilesD21.EXPERT.gridSizes)
        assertEquals(listOf(8), GenerationProfilesD21.MASTER.gridSizes)
        assertTrue(GenerationProfilesD21.MASTER_9X9_EXPERIMENTAL.experimental)
        assertTrue(GenerationProfilesD21.MEDIUM.spatialDensityProfile!!.minimumOccupancyRatio >= 0.36)
        assertEquals(0.40, GenerationProfilesD21.EXPERT.spatialDensityProfile!!.minimumOccupancyRatio, 0.0001)
        assertEquals(0.45, GenerationProfilesD21.MASTER.spatialDensityProfile!!.minimumOccupancyRatio, 0.0001)
        assertTrue(GenerationProfilesD21.EXPERT.maximumPurposefulEmptyCellRatio > 0.0)
        assertTrue(GenerationProfilesD21.MASTER.maximumPurposefulEmptyCellRatio > 0.0)
    }

    @Test
    fun duplicateFingerprintDetectionRemainsStable() {
        val request = GenerationRequestV5("duplicate", 1, "duplicate", 991L, GenerationProfilesD21.EASY, maxAttempts = 1)
        val generator = LevelGeneratorV5()
        val first = generator.generateRaw(request)
        val duplicate = generator.generateRaw(request)
        val different = generator.generateRaw(request, 992L)

        assertEquals(ContentFingerprint.of(first), ContentFingerprint.of(duplicate))
        assertNotEquals(ContentFingerprint.of(first), ContentFingerprint.of(different))
    }

    @Test
    fun productionStructuralAnalysisReportsCancellationPolarityAndParticipationEvidence() {
        val cancellation = level(
            size = 3,
            arrows = listOf(Arrow("a1", Position(2, 2), Direction.NORTH)),
            magnets = listOf(
                Magnet("m1", Position(2, 1), Polarity.PULL),
                Magnet("m2", Position(2, 3), Polarity.PUSH),
            ),
        )
        val diagnostics = StructuralAnalyzerV5().analyze(
            cancellation,
            StructuralDifficultyBandV5.TUTORIAL,
            StructuralAnalysisLimitsV5(maxStates = 1_000, maxActionResolutions = 10_000),
        )

        assertEquals(1, diagnostics.magnetCancellationCount)
        assertTrue(diagnostics.meaningfulLineOfSightInteractionCount > 0)
        assertTrue(diagnostics.objectRelevance.relevantObjectCountByType.isNotEmpty())
        assertFalse(diagnostics.truncated)
    }

    @Test
    fun polarityTrapCreatesAProductionEngineVerifiedHarmfulOrderingDecision() {
        val trap = level(
            size = 6,
            arrows = listOf(
                Arrow("a1", Position(4, 2), Direction.NORTH),
                Arrow("a2", Position(2, 4), Direction.EAST),
            ),
            magnets = listOf(Magnet("m1", Position(4, 4), Polarity.PULL)),
            walls = listOf(Wall(Position(4, 1))),
        )
        val diagnostics = StructuralAnalyzerV5().analyze(
            trap,
            StructuralDifficultyBandV5.HARD,
            StructuralAnalysisLimitsV5(maxStates = 2_000, maxActionResolutions = 20_000),
        )

        assertTrue(diagnostics.toString(), diagnostics.meaningfulFailureRate > 0.0)
        assertTrue(diagnostics.toString(), diagnostics.orderingConstraintCount > 0)
        assertTrue(diagnostics.toString(), diagnostics.polarityDependentDecisionCount > 0)
        assertTrue(diagnostics.toString(), diagnostics.consequenceDepth >= 1)
    }

    @Test
    fun generatedPolarityTrapHasAWinningOrderAndAChangedFollowUpAction() {
        val trap = level(
            size = 6,
            arrows = listOf(
                Arrow("reveal", Position(3, 2), Direction.NORTH),
                Arrow("must-first", Position(3, 5), Direction.NORTH),
                Arrow("safe-last", Position(5, 1), Direction.SOUTH),
            ),
            magnets = listOf(Magnet("m1", Position(3, 1), Polarity.PULL)),
            walls = listOf(Wall(Position(3, 6))),
        )
        val engine = DefaultGameEngine()
        val fatal = engine.resolve(trap.initialState(), PlayerAction("reveal"))

        assertTrue(fatal.success)
        assertFalse(engine.resolve(fatal.resultingState, PlayerAction("must-first")).success)
        val solved = Solver(engine).solve(trap.initialState())
        assertTrue(solved.solvable)
        var intended = trap.initialState()
        listOf("must-first", "reveal", "safe-last").forEach { arrowId ->
            val result = engine.resolve(intended, PlayerAction(arrowId))
            assertTrue("$arrowId should succeed in the intended order: $result", result.success)
            intended = result.resultingState
        }
        assertTrue(intended.arrows.isEmpty())
    }

    @Test
    fun generatedHardBoardContainsPolarityAndOrderingConsequencesBeforeCertification() {
        val profile = GenerationProfilesD21.HARD.copy(gridSizes = listOf(6))
        val board = LevelGeneratorV5().generateRaw(
            GenerationRequestV5("hard-structure", 1, "hard", 6_210_001L, profile, maxAttempts = 1),
        )
        val diagnostics = StructuralAnalyzerV5().analyze(
            board,
            StructuralDifficultyBandV5.HARD,
            StructuralAnalysisLimitsV5(maxStates = 20_000, maxActionResolutions = 200_000),
        )

        val evidence = "$board\n$diagnostics"
        assertTrue(evidence, Solver().solve(board.initialState(), maxExploredStates = 100_000).solvable)
        assertTrue(evidence, diagnostics.polarityDependentDecisionCount > 0)
        assertTrue(evidence, diagnostics.spatialDensity.authoredLongRangeMagneticRelationshipCount > 0)
        assertTrue(evidence, diagnostics.spatialDensity.arrowBlockerCandidateRelationshipCount > 0)
    }

    private fun level(
        size: Int,
        arrows: List<Arrow>,
        magnets: List<Magnet> = emptyList(),
        walls: List<Wall> = emptyList(),
    ) = LevelDefinition(
        id = "d21-test",
        number = 1,
        title = "D2.1 test",
        width = size,
        height = size,
        arrows = arrows,
        magnets = magnets,
        walls = walls,
        designedSolutions = listOf(arrows.map { it.id }),
    )
}
