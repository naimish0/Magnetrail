package com.rameshta.magnetrail.core.generation.v5

import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4Analyzer
import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4Config
import com.rameshta.magnetrail.core.difficulty.v4.defaultDifficultyV4Seeds
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProceduralTopologyV5Test {
    @Test
    fun `purposeful-space ordered weave certifies against unchanged structural gates`() {
        listOf(GenerationProfilesD21.EXPERT, GenerationProfilesD21.MASTER).forEachIndexed { index, profile ->
            val result = LevelGeneratorV5().generate(
                GenerationRequestV5(
                    stableId = "weave-certification-${profile.id}",
                    sequenceNumber = index + 1,
                    title = "Weave certification",
                    seed = 11_510_013L + index,
                    profile = profile,
                    maxAttempts = 1,
                    topologyFamily = TopologyFamilyV5.ORDERED_LONG_RANGE_WEAVE_V4,
                ),
            )
            println("WEAVE_CERTIFICATION profile=${profile.id} result=$result")
            assertTrue(result.toString(), result is GenerationResultV5.Generated)
            result as GenerationResultV5.Generated
            assertTrue(result.diagnostics.searchComplete)
            assertFalse(result.diagnostics.truncated)
            assertTrue(result.diagnostics.spatialDensity.emptyCells > 0)
            assertTrue(result.diagnostics.mandatoryOrderingDepth > 0)
        }
    }

    @Test
    fun `ordered long range weave stays inside unchanged V4 bounds`() {
        listOf(GenerationProfilesD21.EXPERT, GenerationProfilesD21.MASTER).forEachIndexed { index, profile ->
            val request = GenerationRequestV5(
                stableId = "ordered-long-range-weave-${profile.id}",
                sequenceNumber = index + 1,
                title = "Ordered long range weave",
                seed = 11_510_013L + index,
                profile = profile,
                maxAttempts = 1,
                topologyFamily = TopologyFamilyV5.ORDERED_LONG_RANGE_WEAVE_V4,
            )
            val candidate = SolutionFirstConstructorV5().construct(request, request.seed)
            val v4 = DifficultyV4Analyzer(
                config = DifficultyV4Config(
                    maxExpandedStates = profile.analysisStateCap,
                    maxActionResolutions = profile.analysisStateCap * 12,
                    maxCounterfactualStates = profile.analysisStateCap,
                    maxCounterfactualActionResolutions = profile.analysisStateCap * 16,
                    maxObjectCounterfactuals = profile.counterfactualCap,
                    randomPolicySeeds = defaultDifficultyV4Seeds(32),
                ),
            ).analyze(candidate.level)
            println(
                "ORDERED_LONG_RANGE_WEAVE profile=${profile.id} score=${v4.score} complete=${v4.searchComplete && !v4.searchTruncated} " +
                    "safe=${v4.metrics.safeChoiceRatio} orderRate=${v4.metrics.ordering.mandatoryOrderingRatio} " +
                    "orderDepth=${v4.metrics.ordering.mandatoryOrderingChainDepth} " +
                    "states=${v4.metrics.searchStateCount} reasons=${v4.truncationReasons}",
            )
            assertTrue(v4.truncationReasons.toString(), v4.searchComplete && !v4.searchTruncated)
            assertTrue(candidate.canonicalReplayVerified)
        }
    }

    @Test
    fun `causal fingerprint and candidate diversity are deterministic`() {
        val nodes = listOf(
            SolutionContractNodeV5("a", "a", "arrow:a", SolutionObjectRoleV5.ENTRY),
            SolutionContractNodeV5("b", "b", "arrow:b", SolutionObjectRoleV5.TRAP),
        )
        val graph = CausalGraphV5(
            TopologyFamilyV5.ORDERING_CHAIN,
            nodes,
            listOf(
                SolutionContractEdgeV5(
                    "a", "b", ConstructedRelationshipV5.ORDER_DEPENDENCY, "a unlocks b",
                ),
            ),
        )
        assertEquals(graph.deterministicFingerprint, graph.copy().deterministicFingerprint)

        val identity = LogicalLevelIdentityV5(10_000, "v5-d2.1-expert", seed = 42L,
            topologyFamily = TopologyFamilyV5.ORDERING_CHAIN)
        val record = ProceduralCandidateRecordV5(identity, graph.deterministicFingerprint, "exact-a", true)
        val pool = CandidatePoolV5()
        assertTrue(pool.offer(record))
        assertFalse(pool.offer(record.copy(exactFingerprint = "exact-b")))
        assertEquals(1, pool.snapshot().size)
        assertTrue(identity.stableId.contains("010000"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cyclic causal graph is rejected before board realization`() {
        val nodes = listOf(
            SolutionContractNodeV5("a", "a", "arrow:a", SolutionObjectRoleV5.ENTRY),
            SolutionContractNodeV5("b", "b", "arrow:b", SolutionObjectRoleV5.TRAP),
        )
        CausalGraphV5(
            TopologyFamilyV5.ORDERING_CHAIN,
            nodes,
            listOf(
                SolutionContractEdgeV5("a", "b", ConstructedRelationshipV5.ORDER_DEPENDENCY, "a-b"),
                SolutionContractEdgeV5("b", "a", ConstructedRelationshipV5.ORDER_DEPENDENCY, "b-a"),
            ),
        )
    }

    @Test
    fun `empty cell policy requires exact causal purposes and enforces configured ceiling`() {
        val occupied = listOf(
            Position(1, 1), Position(1, 2), Position(1, 3), Position(2, 1),
            Position(2, 2), Position(2, 3), Position(3, 1), Position(3, 2),
        )
        val level = LevelDefinition(
            id = "empty-policy", number = 1, title = "Empty policy", width = 3, height = 3,
            arrows = listOf(Arrow("a", occupied.first(), Direction.NORTH)),
            magnets = emptyList(),
            walls = occupied.drop(1).map(::Wall),
            designedSolutions = listOf(listOf("a")),
        )
        val purpose = PurposefulEmptyCellV5(
            Position(3, 3), EmptyCellPurposeTypeV5.MOVEMENT_CORRIDOR, "arrow:a>edge",
        )

        assertFalse(EmptyCellPolicyV5.evaluate(level, listOf(purpose), 0.10).accepted)
        val fourByFour = level.copy(
            width = 4,
            height = 4,
            walls = (1..4).flatMap { row -> (1..4).map { column -> Position(row, column) } }
                .filterNot { it == Position(1, 1) || it == Position(4, 4) }
                .map(::Wall),
        )
        assertTrue(
            EmptyCellPolicyV5.evaluate(
                fourByFour,
                listOf(purpose.copy(position = Position(4, 4))),
                0.10,
            ).accepted,
        )
        assertFalse(EmptyCellPolicyV5.evaluate(fourByFour, emptyList(), 0.10).accepted)
    }

    @Test
    fun `high band registry exposes physically different deterministic families`() {
        val request = GenerationRequestV5(
            stableId = "topology-family-test",
            sequenceNumber = 1,
            title = "Topology family test",
            seed = 11_510_013L,
            profile = GenerationProfilesD21.EXPERT,
            maxAttempts = 1,
        )
        val realizer = SolutionFirstBoardRealizerV5()
        val ordered = realizer.realize(request, request.seed, TopologyFamilyV5.ORDERED_POLARITY_V1)
        val tail = realizer.realize(request, request.seed, TopologyFamilyV5.CAUSAL_POLARITY_TAIL_V2)
        val staircase = realizer.realize(
            request,
            request.seed,
            TopologyFamilyV5.ORDERED_POLARITY_STAIRCASE_V3,
        )
        val weave = realizer.realize(
            request,
            request.seed,
            TopologyFamilyV5.ORDERED_LONG_RANGE_WEAVE_V4,
        )

        assertEquals(TopologyFamilyV5.ORDERED_POLARITY_V1, ordered.topologyFamily)
        assertEquals(TopologyFamilyV5.CAUSAL_POLARITY_TAIL_V2, tail.topologyFamily)
        assertEquals(TopologyFamilyV5.ORDERED_POLARITY_STAIRCASE_V3, staircase.topologyFamily)
        assertEquals(TopologyFamilyV5.ORDERED_LONG_RANGE_WEAVE_V4, weave.topologyFamily)
        assertNotEquals(ordered.level, tail.level)
        assertNotEquals(ordered.level, staircase.level)
        assertNotEquals(tail.level, staircase.level)
        assertNotEquals(ordered.level, weave.level)
        assertNotEquals(staircase.level, weave.level)
        assertNotEquals(ordered.causalGraph().deterministicFingerprint, tail.causalGraph().deterministicFingerprint)
        assertTrue(ordered.canonicalReplayVerified)
        assertTrue(tail.canonicalReplayVerified)
        assertTrue(staircase.canonicalReplayVerified)
        assertTrue(weave.canonicalReplayVerified)
    }

    @Test
    fun `physical verifier rejects a declared relationship that geometry does not realize`() {
        val request = expertRequest(TopologyFamilyV5.ORDERED_POLARITY_V1)
        val constructor = SolutionFirstConstructorV5()
        val candidate = constructor.construct(request, request.seed)
        val longEdgeIndex = candidate.contract.edges.indexOfFirst {
            it.relationship == ConstructedRelationshipV5.LONG_RANGE_MAGNET_CONTROL
        }
        val invalidEdges = candidate.contract.edges.toMutableList().apply {
            this[longEdgeIndex] = this[longEdgeIndex].copy(fromNodeId = "magnet:controllerA")
        }
        val invalid = candidate.contract.copy(edges = invalidEdges)

        assertFalse(constructor.verifyPhysicalContract(candidate.level, invalid, 150_000).passed)
    }

    @Test
    fun `legacy noncompliant topology remains fail closed`() {
        val result = LevelGeneratorV5().generate(expertRequest(TopologyFamilyV5.CAUSAL_POLARITY_TAIL_V2))

        assertTrue(result is GenerationResultV5.Exhausted)
        result as GenerationResultV5.Exhausted
        assertTrue(result.rejectedReasons.isNotEmpty())
        assertEquals(0, result.telemetry.certifiedCandidates)
    }

    @Test
    fun `automatic high band attempts use only certified purposeful-space weave`() {
        repeat(8) { attempt ->
            val family = HighBandTopologyRegistryV5.familyForAttempt(GenerationProfilesD21.EXPERT, attempt)
            assertNotEquals(TopologyFamilyV5.CAUSAL_POLARITY_TAIL_V2, family)
            assertEquals(TopologyFamilyV5.ORDERED_LONG_RANGE_WEAVE_V4, family)
        }
    }

    private fun expertRequest(family: TopologyFamilyV5) = GenerationRequestV5(
        stableId = "procedural-topology-test",
        sequenceNumber = 1,
        title = "Procedural topology test",
        seed = 11_510_013L,
        profile = GenerationProfilesD21.EXPERT,
        maxAttempts = 1,
        topologyFamily = family,
    )
}
