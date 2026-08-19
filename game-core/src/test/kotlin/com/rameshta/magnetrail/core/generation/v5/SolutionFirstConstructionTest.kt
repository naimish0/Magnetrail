package com.rameshta.magnetrail.core.generation.v5

import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4Analyzer
import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4Config
import com.rameshta.magnetrail.core.difficulty.v4.defaultDifficultyV4Seeds
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.solver.Solver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SolutionFirstConstructionTest {
    private val constructor = SolutionFirstConstructorV5()

    @Test
    fun everySolutionFirstProfileConstructsWithinItsBounds() {
        val profiles = listOf(
            GenerationProfilesD21.MEDIUM,
            GenerationProfilesD21.HARD,
            GenerationProfilesD21.VERY_HARD,
            GenerationProfilesD21.EXPERT,
            GenerationProfilesD21.MASTER,
        )
        profiles.forEachIndexed { index, profile ->
            val request = GenerationRequestV5(
                stableId = "profile-$index", sequenceNumber = index + 1, title = profile.id,
                seed = 10_000L + index, profile = profile, maxAttempts = 1,
            )
            val candidate = constructor.construct(request, request.seed)
            assertTrue(candidate.level.width in profile.gridSizes)
            assertTrue(candidate.level.arrows.size in profile.minArrows..profile.maxArrows)
            assertEquals(
                candidate.level.width * candidate.level.height,
                candidate.level.arrows.size + candidate.level.magnets.size + candidate.level.walls.size,
            )
        }
    }

    @Test
    fun sameSeedBuildsSameFullDependencyContract() {
        val request = expertRequest()
        val first = constructor.construct(request, request.seed)
        val second = constructor.construct(request, request.seed)

        assertEquals(first.level, second.level)
        assertEquals(first.contract, second.contract)
        assertTrue(first.canonicalReplayVerified)
        assertEquals(64, first.level.arrows.size + first.level.magnets.size + first.level.walls.size)
        assertEquals(64, SpatialDensityAnalyzerV5.analyze(first.level).occupiedCells)
        assertFalse(first.level.magnets.any { it.id.startsWith("shielded-filler-") })
        assertTrue(first.contract.edges.any { it.relationship == ConstructedRelationshipV5.EXPOSURE })
        assertEquals(
            3,
            first.contract.edges.count { it.relationship == ConstructedRelationshipV5.LONG_RANGE_MAGNET_CONTROL },
        )
        assertTrue(first.contract.edges.any { it.relationship == ConstructedRelationshipV5.POLARITY_DEPENDENCY })
        val verification = constructor.verifyPhysicalContract(first.level, first.contract, request.profile.analysisStateCap)
        assertTrue(verification.missingEdges.toString(), verification.passed)
        assertEquals(verification.declaredEdgeCount, verification.verifiedEdgeCount)
    }

    @Test
    fun canonicalSolutionReplaysAndSolverProvesConstruction() {
        val candidate = constructor.construct(expertRequest(), 9_001L)
        val engine = DefaultGameEngine()
        var state = candidate.level.initialState()
        candidate.contract.canonicalActionIds.forEach { actionId ->
            val result = engine.resolve(state, PlayerAction(actionId))
            assertTrue("$actionId must replay: $result", result.success)
            state = result.resultingState
        }
        assertTrue(state.arrows.isEmpty())

        val solved = Solver().solve(candidate.level.initialState(), 100_000, 150_000)
        assertTrue(solved.toString(), solved.searchComplete)
        assertTrue(solved.toString(), solved.solvable)
        assertNotNull(solved.oneCleanSolution)
    }

    @Test
    fun reachableSuccessfulWrongChoiceProducesARealFutureDeadEnd() {
        val candidate = constructor.construct(expertRequest(), 9_002L)
        val score = DifficultyV4Analyzer(
            config = DifficultyV4Config(
                maxExpandedStates = 150_000,
                maxActionResolutions = 1_800_000,
                maxCounterfactualStates = 150_000,
                maxCounterfactualActionResolutions = 2_400_000,
                maxObjectCounterfactuals = 160,
                randomPolicySeeds = defaultDifficultyV4Seeds(32),
            ),
        ).analyze(candidate.level)
        assertTrue(score.truncationReasons.toString(), score.searchComplete)
        assertFalse(score.truncationReasons.toString(), score.searchTruncated)
        assertTrue(score.metrics.meaningfulFailureRate > 0.0)
    }

    @Test
    fun realV4AnalysisIsCompleteAndNotBypassed() {
        val candidate = constructor.construct(expertRequest(), 9_003L)
        val score = DifficultyV4Analyzer(
            config = DifficultyV4Config(
                maxExpandedStates = 150_000,
                maxActionResolutions = 1_800_000,
                maxCounterfactualStates = 150_000,
                maxCounterfactualActionResolutions = 2_400_000,
                maxObjectCounterfactuals = 160,
                randomPolicySeeds = defaultDifficultyV4Seeds(32),
            ),
        ).analyze(candidate.level)
        println("SOLUTION_FIRST_V4 safe=${score.metrics.safeChoiceRatio} failure=${score.metrics.meaningfulFailureRate} ordering=${score.metrics.ordering.mandatoryOrderingRatio} depth=${score.metrics.ordering.mandatoryOrderingChainDepth}")
        assertTrue(score.truncationReasons.toString(), score.searchComplete)
        assertFalse(score.truncationReasons.toString(), score.searchTruncated)
        assertTrue(score.metrics.meaningfulFailureRate > 0.0)
    }

    @Test
    fun structuralAnalysisUsesTheRealCounterfactualAnalyzer() {
        val candidate = constructor.construct(expertRequest(), 9_003L)
        val diagnostics = StructuralAnalyzerV5().analyze(
            candidate.level,
            StructuralDifficultyBandV5.EXPERT,
            StructuralAnalysisLimitsV5(150_000, 1_800_000, 160, 150_000),
        )
        println(
            "SOLUTION_FIRST_STRUCTURAL interaction=${diagnostics.interactionGraph.interactionDensity} " +
                "relevance=${diagnostics.objectRelevance.relevantObjectRatio} average=${diagnostics.objectRelevance.averageScore} " +
                "long=${diagnostics.longRangeMagneticRelationshipCount} ordering=${diagnostics.meaningfulOrderingRate} " +
                "walls=${diagnostics.meaningfulWallOcclusionCount} commutation=${diagnostics.viablePairCommutationRatio}",
        )
        assertTrue(diagnostics.truncationReasons.toString(), diagnostics.searchComplete)
        assertFalse(diagnostics.truncationReasons.toString(), diagnostics.truncated)
    }

    @Test
    fun physicalDependencyGraphContainsThreeVerifiedLongRangeRelationships() {
        val request = expertRequest().copy(seed = 11_510_013L)
        val candidate = constructor.construct(request, request.seed)
        val physical = StructuralAnalyzerV5().analyzePhysicalSemantics(
            candidate.level,
            StructuralAnalysisLimitsV5(150_000, 1_800_000, 0, 150_000),
        )
        val graph = requireNotNull(physical.interactionGraph)
        val verification = constructor.verifyPhysicalContract(
            candidate.level,
            candidate.contract,
            request.profile.analysisStateCap,
        )
        println(
            "SOLUTION_FIRST_PHYSICAL declared=${verification.declaredEdgeCount} " +
                "verified=${verification.verifiedEdgeCount} edges=${graph.totalInteractionEdges} " +
                "density=${graph.interactionDensity} components=${graph.connectedComponents} " +
                "largest=${graph.largestConnectedComponent} isolated=${graph.isolatedObjects} " +
                "long=${physical.longRangeRelationships.size}",
        )
        assertTrue(physical.truncationReasons.toString(), physical.complete)
        assertTrue(verification.missingEdges.toString(), verification.passed)
        assertEquals(3, physical.longRangeRelationships.size)
    }

    @Test
    fun knownExpertSeedReportsItsRemainingCertificationBottleneck() {
        val request = expertRequest().copy(seed = 11_510_013L, maxAttempts = 1)
        val result = LevelGeneratorV5().generate(request)
        assertTrue(result is GenerationResultV5.Exhausted)
        result as GenerationResultV5.Exhausted
        println("SOLUTION_FIRST_REJECTIONS ${result.rejectedReasons}")
        assertEquals(
            setOf("safe-choice-ratio-above-profile", "ordering-depth-below-profile"),
            result.rejectedReasons.keys,
        )
    }

    @Test
    fun dependencyCompleteCandidateHasNoFillerRepairOperator() {
        val request = expertRequest()
        val original = constructor.construct(request, request.seed)
        val outcome = constructor.repair(
            original,
            listOf("wall-participation-below-profile"),
            1L,
            request.profile.solverStateCap,
        )
        assertFalse(outcome.applied)
        assertFalse(outcome.rolledBack)
        assertEquals("no-structurally-safe-operator", outcome.operator)
        assertEquals(original.level, outcome.level)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cyclicDependencyContractIsRejected() {
        val nodes = listOf(
            SolutionContractNodeV5("a", "a", "arrow:a", SolutionObjectRoleV5.BLOCKER),
            SolutionContractNodeV5("b", "b", "arrow:b", SolutionObjectRoleV5.BLOCKER),
        )
        SolutionContractV5(
            nodes = nodes,
            edges = listOf(
                SolutionContractEdgeV5("a", "b", ConstructedRelationshipV5.ORDER_DEPENDENCY, "test"),
                SolutionContractEdgeV5("b", "a", ConstructedRelationshipV5.ORDER_DEPENDENCY, "test"),
            ),
            canonicalActionIds = listOf("a", "b"),
        )
    }

    private fun expertRequest() = GenerationRequestV5(
        stableId = "v5-solution-first-test",
        sequenceNumber = 1,
        title = "Solution First Test",
        seed = 9_001L,
        profile = GenerationProfilesD21.EXPERT,
        maxAttempts = 1,
    )
}
