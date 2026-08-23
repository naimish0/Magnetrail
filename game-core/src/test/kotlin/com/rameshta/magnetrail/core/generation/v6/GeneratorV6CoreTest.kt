package com.rameshta.magnetrail.core.generation.v6

import com.rameshta.magnetrail.core.content.BoardSymmetry
import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Magnet
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorV6CoreTest {
    private val engine = DefaultGameEngine()

    @Test
    fun `all six grammars are valid and structurally distinct`() {
        val constructor = ReverseLogicalConstructorV6()
        val fingerprints = CausalGrammarFamilyV6.entries.mapIndexed { index, family ->
            val spec = constructor.construct(GeneratorV6Identity(6_001L + index, 4, family, index))
            assertTrue(spec.solutionPartialOrder.deterministicTopologicalOrder().isNotEmpty())
            assertTrue(spec.hyperedges.isNotEmpty())
            assertTrue(spec.actions.all { action -> spec.entities.any { it.key == action.arrowRoleKey } })
            SemanticFingerprintBuilderV6(
                ExactColoredGraphCanonicalizerV6(100_000),
            ).causalSignature(spec)
        }
        assertEquals(6, fingerprints.toSet().size)
    }

    @Test
    fun `logical graph instances change dependency structure rather than coordinates`() {
        val constructor = ReverseLogicalConstructorV6()
        val first = constructor.construct(
            GeneratorV6Identity(7_001L, 5, CausalGrammarFamilyV6.FORK_JOIN_COUPLED, 0),
        )
        val second = constructor.construct(
            GeneratorV6Identity(7_001L, 5, CausalGrammarFamilyV6.FORK_JOIN_COUPLED, 2),
        )
        val builder = SemanticFingerprintBuilderV6(ExactColoredGraphCanonicalizerV6(100_000))
        assertNotEquals(builder.causalSignature(first), builder.causalSignature(second))
    }

    @Test
    fun `constraint realizer is deterministic and obeys alignment`() {
        val arrow = LogicalEntityRole("a", LogicalEntityKindV6.ARROW)
        val magnet = LogicalEntityRole("m", LogicalEntityKindV6.MAGNET)
        val positions = listOf(Position(1, 1), Position(1, 2), Position(2, 1), Position(2, 2))
        val problem = V6RealizationProblem(
            2,
            2,
            listOf(
                V6RealizationVariable(arrow, positions.map { V6EntityValue(it, Direction.EAST) }),
                V6RealizationVariable(magnet, positions.map { V6EntityValue(it, polarity = Polarity.PULL) }),
            ),
            listOf(V6Constraints.aligned("a", "m"), V6Constraints.minimumDistance("a", "m", 1)),
        )
        val first = DeterministicConstraintRealizerV6(91L, 100, 100, 5_000).realize(problem)
        val second = DeterministicConstraintRealizerV6(91L, 100, 100, 5_000).realize(problem)
        val firstRealized = first as V6RealizationResult.Realized
        val secondRealized = second as V6RealizationResult.Realized
        assertEquals(firstRealized.assignment, secondRealized.assignment)
        assertEquals(firstRealized.exploredStates, secondRealized.exploredStates)
        assertEquals(firstRealized.recordedNogoods, secondRealized.recordedNogoods)
        assertEquals(firstRealized.elapsedMillis, secondRealized.elapsedMillis)
        val assignment = firstRealized.assignment
        val a = requireNotNull(assignment["a"]).position
        val m = requireNotNull(assignment["m"]).position
        assertTrue(a.row == m.row || a.column == m.column)
        assertNotEquals(a, m)
    }

    @Test
    fun `realizer reports state cap rather than returning partial geometry`() {
        val role = LogicalEntityRole("a", LogicalEntityKindV6.ARROW)
        val problem = V6RealizationProblem(
            2,
            2,
            listOf(V6RealizationVariable(role, listOf(V6EntityValue(Position(1, 1), Direction.NORTH)))),
            listOf(V6RealizationConstraint { false }),
        )
        val result = DeterministicConstraintRealizerV6(1L, 1, 10, 5_000).realize(problem)
        assertTrue(result is V6RealizationResult.Rejected)
    }

    @Test
    fun `complete decision DAG separates failed taps from successful persistent trap`() {
        val level = delayedTrapBoard()
        val analysis = CompleteDecisionDagAnalyzerV6(engine, 1_000, 10_000).analyze(level.initialState())
        val metrics = requireNotNull(analysis.metrics)
        assertTrue(analysis.complete)
        assertTrue(metrics.failedActionAnnotationCount > 0)
        assertTrue(metrics.successfulLosingBranchCount > 0)
        val root = requireNotNull(analysis.nodes[analysis.rootStateKey])
        val trap = root.transitions.single { it.arrowId == "A" }
        assertTrue(trap.successful)
        assertFalse(trap.futureSolvable == true)
        assertEquals(1, trap.delayedDeadlockDepth)
        assertTrue(metrics.meaningfulNonCommutingDecisionCount > 0)
        assertEquals(1, metrics.meaningfulDecisionCount)
        assertTrue(metrics.mandatoryPrecedence.contains("B" to "A"))
    }

    @Test
    fun `human policies and easiest-policy grading ignore entity ids`() {
        val original = delayedTrapBoard()
        val arrowIds = mapOf("A" to "zeta", "B" to "alpha", "C" to "middle")
        val renamed = original.copy(
            id = "renamed-policy-board",
            arrows = original.arrows.map { it.copy(id = requireNotNull(arrowIds[it.id])) },
            magnets = original.magnets.map { it.copy(id = "renamed-magnet") },
            designedSolutions = original.designedSolutions.map { solution -> solution.map { requireNotNull(arrowIds[it]) } },
        )
        val originalAnalysis = CompleteDecisionDagAnalyzerV6(engine, 1_000, 10_000).analyze(original.initialState())
        val renamedAnalysis = CompleteDecisionDagAnalyzerV6(engine, 1_000, 10_000).analyze(renamed.initialState())
        assertEquals(originalAnalysis.metrics?.meaningfulDecisionCount, renamedAnalysis.metrics?.meaningfulDecisionCount)
        val ensemble = HumanPolicyEnsembleV1()
        assertEquals(
            ensemble.evaluate(originalAnalysis).map { Triple(it.policy, it.budget, it.solvedRuns) },
            ensemble.evaluate(renamedAnalysis).map { Triple(it.policy, it.budget, it.solvedRuns) },
        )
    }

    @Test
    fun `geometry compiler derives a contract compliant rectangle`() {
        val spec = ReverseLogicalConstructorV6().construct(
            GeneratorV6Identity(8_001L, 1, CausalGrammarFamilyV6.POLARITY_LOCK_RELEASE, 0),
        )
        val profile = V6Profiles.forBucket(1)
        val problem = V6GeometryCompiler().problem(spec, profile)
        val occupiedRatio = spec.entities.size.toDouble() / (problem.width * problem.height)
        assertTrue(occupiedRatio in profile.occupancy)
        assertTrue(problem.width <= 8 && problem.height <= 8)
        spec.entities.filter { it.kind != LogicalEntityKindV6.WALL }.groupBy { it.chain }.values.forEach { roles ->
            val rows = roles.flatMap { role -> problem.variables.single { it.role == role }.domain.map { it.position.row } }.toSet()
            assertEquals(1, rows.size)
        }
    }

    @Test
    fun `decision analysis fails closed at a search cap`() {
        val analysis = CompleteDecisionDagAnalyzerV6(engine, 1, 2).analyze(delayedTrapBoard().initialState())
        assertFalse(analysis.complete)
        assertTrue(analysis.truncationReasons.any { it.startsWith("DECISION_DAG_STATE_CAP") })
        assertEquals(null, analysis.metrics)
    }

    @Test
    fun `production causal verifier proves polarity effect and delayed trap`() {
        val level = delayedTrapBoard()
        val analysis = CompleteDecisionDagAnalyzerV6(engine, 1_000, 10_000).analyze(level.initialState())
        val entities = listOf(
            LogicalEntityRole("arrow-a", LogicalEntityKindV6.ARROW),
            LogicalEntityRole("arrow-b", LogicalEntityKindV6.ARROW),
            LogicalEntityRole("arrow-c", LogicalEntityKindV6.ARROW),
            LogicalEntityRole("magnet", LogicalEntityKindV6.MAGNET),
            LogicalEntityRole("wall", LogicalEntityKindV6.WALL),
        )
        val actions = listOf(
            LogicalActionRole("act-a", "arrow-a", LogicalActionKindV6.SUCCESSFUL_TRAP),
            LogicalActionRole("act-b", "arrow-b", LogicalActionKindV6.REQUIRED),
            LogicalActionRole("act-c", "arrow-c", LogicalActionKindV6.REQUIRED),
        )
        val spec = CausalHypergraphSpec(
            CausalGrammarFamilyV6.INTERACTING_CHAINS_DELAYED_TRAPS,
            entities,
            actions,
            listOf(
                CausalHyperedge(
                    "flip-changes-b",
                    listOf(StateCondition(StateConditionTypeV6.MAGNET_PULL, "magnet")),
                    "act-a",
                    CausalEffectV6.FLIPS_REQUIRED_POLARITY,
                    listOf("magnet", "act-b"),
                    requiredCounterfactual = false,
                ),
                CausalHyperedge(
                    "a-trap",
                    listOf(StateCondition(StateConditionTypeV6.STATE_SOLVABLE, "arrow-a")),
                    "act-a",
                    CausalEffectV6.SUCCESSFUL_TRAP,
                    listOf("arrow-a"),
                ),
            ),
            SolutionPartialOrder(actions.mapTo(linkedSetOf()) { it.key }, setOf("act-b" to "act-a")),
            listOf(SuccessfulTrapContract("act-a", 1)),
            2,
        )
        val result = ProductionCausalVerifierV6(engine).verify(
            spec,
            mapOf(
                "arrow-a" to "A", "arrow-b" to "B", "arrow-c" to "C", "magnet" to "M",
                "wall" to "4,3", "act-a" to "A", "act-b" to "B", "act-c" to "C",
            ),
            analysis,
        )
        assertTrue(result.rejections.joinToString(), result.complete)
        assertEquals(2, result.witnesses.size)
        assertNotNull(result.witnesses.first().resolution)
    }

    @Test
    fun `purposeful occupancy proves wall and magnet while rejecting filler`() {
        val level = delayedTrapBoard()
        val analysis = CompleteDecisionDagAnalyzerV6(engine, 1_000, 10_000).analyze(level.initialState())
        val permissive = testProfile(0.25..0.40)
        val occupancy = PurposefulOccupancyAnalyzerV6(engine).analyze(level, analysis, permissive)
        assertTrue(occupancy.objectWitnesses["magnet:M"].orEmpty().isNotEmpty())
        assertTrue(occupancy.objectWitnesses["wall:4,3"].orEmpty().isNotEmpty())
        assertEquals(1.0, occupancy.purposefulOccupiedRatio, 0.0)

        val filler = level.copy(walls = level.walls + Wall(Position(4, 4)))
        val fillerAnalysis = CompleteDecisionDagAnalyzerV6(engine, 1_000, 10_000).analyze(filler.initialState())
        val fillerOccupancy = PurposefulOccupancyAnalyzerV6(engine).analyze(
            filler,
            fillerAnalysis,
            testProfile(0.25..0.50).copy(minimumPurposefulOccupiedRatio = 0.95, maximumInertOccupiedRatio = 0.05),
        )
        assertTrue(fillerOccupancy.objectWitnesses["wall:4,4"].isNullOrEmpty())
        assertTrue(fillerOccupancy.rejectionReasons.any { it.startsWith("wall-without-semantic-witness") })
    }

    @Test
    fun `rectangular D4 and id renaming preserve fingerprints`() {
        val source = LevelDefinition(
            "rect", 1, "Rect", 3, 5,
            listOf(Arrow("A", Position(1, 2), Direction.SOUTH), Arrow("B", Position(4, 3), Direction.WEST)),
            listOf(Magnet("M", Position(3, 2), Polarity.PUSH)),
            listOf(Wall(Position(5, 1))),
            emptyList(),
        )
        val renamed = source.copy(
            id = "renamed",
            arrows = source.arrows.mapIndexed { index, arrow -> arrow.copy(id = "x$index") },
            magnets = source.magnets.map { it.copy(id = "renamed-magnet") },
        )
        assertEquals(ContentFingerprint.symmetryNormalized(source), ContentFingerprint.symmetryNormalized(renamed))
        BoardSymmetry.entries.forEach { symmetry ->
            val transformed = transform(source, symmetry)
            assertEquals(
                symmetry.name,
                ContentFingerprint.symmetryNormalized(source),
                ContentFingerprint.symmetryNormalized(transformed),
            )
        }
    }

    @Test
    fun `MAP Elites merge is independent of evaluation order`() {
        val analysis = CompleteDecisionDagAnalyzerV6(engine, 1_000, 10_000).analyze(delayedTrapBoard().initialState())
        val profile = testProfile(0.25..0.40)
        val occupancy = PurposefulOccupancyAnalyzerV6(engine).analyze(delayedTrapBoard(), analysis, profile)
        val spec = ReverseLogicalConstructorV6().construct(
            GeneratorV6Identity(41L, 3, CausalGrammarFamilyV6.POLARITY_LOCK_RELEASE, 0),
        )
        fun candidate(seed: Long, exact: String) = V6Candidate(
            GeneratorV6Identity(seed, 3, CausalGrammarFamilyV6.POLARITY_LOCK_RELEASE, 0),
            profile,
            spec,
            delayedTrapBoard(),
            emptyMap(),
            decisionAnalysis = analysis,
            occupancy = occupancy,
            fingerprints = fingerprintBundle(exact),
        )
        val first = candidate(2L, "b")
        val second = candidate(1L, "a")
        val forward = DeterministicMapElitesV6().apply { merge(listOf(first, second)) }.entries()
        val reverse = DeterministicMapElitesV6().apply { merge(listOf(second, first)) }.entries()
        assertEquals(forward.map { it.candidate.identity }, reverse.map { it.candidate.identity })
        assertEquals("a", forward.single().candidate.fingerprints?.exactLayout)
        assertTrue(DeterministicMapElitesV6().saturated(2, 10, 10))
    }

    @Test
    fun `cosmetic filler does not create relevance novelty while polarity changes do`() {
        val profile = testProfile(0.20..0.50)
        val source = delayedTrapBoard()
        val sourceDag = CompleteDecisionDagAnalyzerV6(engine, 1_000, 10_000).analyze(source.initialState())
        val sourceOccupancy = PurposefulOccupancyAnalyzerV6(engine).analyze(source, sourceDag, profile)
        val filler = source.copy(walls = source.walls + Wall(Position(4, 4)))
        val fillerDag = CompleteDecisionDagAnalyzerV6(engine, 1_000, 10_000).analyze(filler.initialState())
        val fillerOccupancy = PurposefulOccupancyAnalyzerV6(engine).analyze(filler, fillerDag, profile)
        val spec = ReverseLogicalConstructorV6().construct(
            GeneratorV6Identity(71L, 3, CausalGrammarFamilyV6.POLARITY_LOCK_RELEASE, 0),
        )
        val builder = SemanticFingerprintBuilderV6(ExactColoredGraphCanonicalizerV6(100_000))
        val sourceBundle = builder.build(source, spec, sourceDag, sourceOccupancy, emptyList()).getOrThrow()
        val fillerBundle = builder.build(filler, spec, fillerDag, fillerOccupancy, emptyList()).getOrThrow()
        assertEquals(sourceBundle.relevancePrunedD4Layout, fillerBundle.relevancePrunedD4Layout)
        val polarityChanged = source.copy(magnets = source.magnets.map { it.copy(polarity = it.polarity.flipped()) })
        assertNotEquals(
            ContentFingerprint.symmetryNormalized(source),
            ContentFingerprint.symmetryNormalized(polarityChanged),
        )
    }

    private fun delayedTrapBoard() = LevelDefinition(
        id = "delayed-trap",
        number = 1,
        title = "Delayed trap",
        width = 4,
        height = 4,
        arrows = listOf(
            Arrow("A", Position(1, 1), Direction.EAST),
            Arrow("B", Position(3, 3), Direction.NORTH),
            Arrow("C", Position(4, 1), Direction.WEST),
        ),
        magnets = listOf(Magnet("M", Position(1, 3), Polarity.PULL)),
        walls = listOf(Wall(Position(4, 3))),
        designedSolutions = listOf(listOf("B", "A", "C")),
    )

    private fun testProfile(occupancy: ClosedFloatingPointRange<Double>) = V6Profile(
        bucket = 3,
        occupancy = occupancy,
        minimumPurposefulOccupiedRatio = 0.80,
        meaningfulDecisionRange = 0..10,
        minimumPersistentTraps = 0,
        minimumLookahead = 0,
        maximumHardestWinningShare = null,
        minimumInteractingChains = 0,
    )

    private fun fingerprintBundle(exact: String) = V6FingerprintBundle(
        exact, exact, exact, exact, exact, exact,
        "causal", "dag", "policy", "trace", "rhythm", listOf(1.0),
        nearestSemanticSimilarity = 0.0,
    )

    private fun transform(level: LevelDefinition, symmetry: BoardSymmetry): LevelDefinition = level.copy(
        id = "${level.id}-${symmetry.name}",
        width = symmetry.outputWidth(level.width, level.height),
        height = symmetry.outputHeight(level.width, level.height),
        arrows = level.arrows.map { arrow ->
            arrow.copy(
                position = symmetry.transform(arrow.position, level.width, level.height),
                printedDirection = symmetry.transform(arrow.printedDirection),
            )
        },
        magnets = level.magnets.map { magnet ->
            magnet.copy(position = symmetry.transform(magnet.position, level.width, level.height))
        },
        walls = level.walls.map { wall ->
            wall.copy(position = symmetry.transform(wall.position, level.width, level.height))
        },
    )
}
