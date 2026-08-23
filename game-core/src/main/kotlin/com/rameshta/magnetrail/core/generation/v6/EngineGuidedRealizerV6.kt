package com.rameshta.magnetrail.core.generation.v6

import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.DeterministicRouteTracer
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.engine.ResolutionResult
import com.rameshta.magnetrail.core.generation.SeededRandom
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Magnet
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall
import com.rameshta.magnetrail.core.solver.Solver

data class EngineGuidedRealizationV6(
    val level: LevelDefinition,
    val spec: CausalHypergraphSpec,
    val bindings: Map<String, String>,
    val examinedBoards: Int,
)

data class EngineGuidedSearchFailureV6(
    val examinedBoards: Int,
    val rejectionCounts: Map<V6RejectionCode, Int>,
) {
    fun detail(): String = "engine-guided boards=$examinedBoards;" +
        rejectionCounts.toSortedMap(compareBy { it.name }).entries.joinToString(",") { "${it.key}=${it.value}" }
}

data class EngineGuidedShapeV6(
    val width: Int,
    val height: Int,
    val arrows: Int,
    val magnets: Int,
    val walls: Int,
) {
    init {
        require(width in 1..8 && height in 1..8)
        require(arrows > 0 && magnets >= 0 && walls >= 0)
        require(arrows + magnets + walls <= width * height)
    }
}

/**
 * Deterministic CEGIS realizer. A grammar family is fixed before search. Every proposed geometry is
 * fully expanded by the production engine; only then are logical roles bound to witnessed state
 * transitions. The checked-in elites are starting states for causal mutations, never output
 * templates: D4, decision-DAG and solution-policy uniqueness remain downstream hard gates.
 */
class EngineGuidedSpatialRealizerV6(
    private val engine: GameEngine = DefaultGameEngine(),
) {
    fun realize(
        request: V6GenerationRequest,
        identity: GeneratorV6Identity,
        profile: V6Profile,
        mutationRound: Int,
        shapeOverride: EngineGuidedShapeV6? = null,
        maximumBoardCandidates: Int? = null,
        candidateAcceptance: ((LevelDefinition, DecisionDagAnalysisV6, CausalHypergraphSpec) -> Boolean)? = null,
    ): Pair<EngineGuidedRealizationV6?, EngineGuidedSearchFailureV6> {
        val random = SeededRandom(
            identity.seed xor (identity.graphInstance.toLong() shl 17) xor
                (identity.grammarFamily.ordinal.toLong() shl 41) xor mutationRound.toLong(),
        )
        val rejectionCounts = linkedMapOf<V6RejectionCode, Int>()
        fun reject(code: V6RejectionCode) {
            rejectionCounts[code] = rejectionCounts.getOrDefault(code, 0) + 1
        }
        val cap = maximumBoardCandidates ?: if (shapeOverride != null) 24_000 else when (profile.bucket) {
            1 -> 4_000
            2 -> 8_000
            3 -> 12_000
            4 -> 18_000
            5 -> 6_000
            else -> error("Unsupported V6 difficulty bucket ${profile.bucket}")
        }
        require(cap > 0)
        repeat(cap) { boardIndex ->
            if (Thread.currentThread().isInterrupted) throw InterruptedException("cancelled speculative V6.1 board")
            val raw = proposedLevel(
                request,
                profile.bucket,
                identity.grammarFamily,
                random,
                boardIndex + mutationRound * cap,
                shapeOverride,
            )
            val dag = request.analysisCache?.decisionDag(
                raw,
                profile.budgets.decisionDagStates,
                profile.budgets.decisionDagResolutions,
            ) {
                CompleteDecisionDagAnalyzerV6(
                    engine,
                    profile.budgets.decisionDagStates,
                    profile.budgets.decisionDagResolutions,
                ).analyze(raw.initialState())
            } ?: CompleteDecisionDagAnalyzerV6(
                engine,
                profile.budgets.decisionDagStates,
                profile.budgets.decisionDagResolutions,
            ).analyze(raw.initialState())
            val metrics = dag.metrics
            if (!dag.complete || metrics == null) {
                reject(V6RejectionCode.DECISION_ANALYSIS_TRUNCATED)
                return@repeat
            }
            if (metrics.shortestCompletion == null) {
                reject(V6RejectionCode.SOLVER_DISAGREEMENT)
                return@repeat
            }
            if (!behaviourPasses(profile, metrics)) {
                reject(V6RejectionCode.BEHAVIOURAL_GATE_FAILED)
                return@repeat
            }
            val policies = HumanPolicyEnsembleV1().evaluate(dag)
            val minimumBudget = HumanPolicyEnsembleV1().minimumReliableBudget(policies)
            if (profile.bucket >= 5 && minimumBudget != null && minimumBudget.lookaheadDepth <= 2) {
                reject(V6RejectionCode.SIMPLE_POLICY_SOLVES_HIGH_BUCKET)
                return@repeat
            }
            val occupancy = request.analysisCache?.occupancy(
                raw,
                profile,
                profile.budgets.counterfactualChecks,
            ) {
                PurposefulOccupancyAnalyzerV6(engine, profile.budgets.counterfactualChecks)
                    .analyze(raw, dag, profile)
            } ?: PurposefulOccupancyAnalyzerV6(engine, profile.budgets.counterfactualChecks)
                .analyze(raw, dag, profile)
            if (occupancy.rejectionReasons.isNotEmpty()) {
                occupancy.rejectionReasons.forEach { reason ->
                    reject(
                        when {
                            reason.startsWith("occupancy") -> V6RejectionCode.OCCUPANCY_OUT_OF_RANGE
                            reason.startsWith("inert") -> V6RejectionCode.INERT_RATIO_HIGH
                            reason.startsWith("magnet") -> V6RejectionCode.MAGNET_WITHOUT_WITNESS
                            reason.startsWith("wall") -> V6RejectionCode.WALL_WITHOUT_WITNESS
                            else -> V6RejectionCode.PURPOSEFUL_RATIO_LOW
                        },
                    )
                }
                return@repeat
            }
            val extracted = request.analysisCache?.extractedSpec(raw, identity, profile) {
                ProductionCausalSpecExtractorV6(engine).extract(identity, profile, raw, dag)
            } ?: ProductionCausalSpecExtractorV6(engine).extract(identity, profile, raw, dag)
            if (extracted == null) {
                reject(V6RejectionCode.CAUSAL_WITNESS_MISSING)
                return@repeat
            }
            val solver = request.analysisCache?.solver(raw, profile.budgets.decisionDagStates) {
                Solver(engine).solve(
                    raw.initialState(), solutionLimit = 100_000,
                    maxExploredStates = profile.budgets.decisionDagStates,
                )
            } ?: Solver(engine).solve(
                raw.initialState(), solutionLimit = 100_000,
                maxExploredStates = profile.budgets.decisionDagStates,
            )
            if (!solver.searchComplete || !solver.solvable || solver.oneCleanSolution == null) {
                reject(V6RejectionCode.SOLVER_DISAGREEMENT)
                return@repeat
            }
            val solution = requireNotNull(solver.oneCleanSolution)
            var solverReplay = raw.initialState()
            for (action in solution) {
                val resolution = engine.resolve(solverReplay, action)
                if (!resolution.success) {
                    reject(V6RejectionCode.SOLVER_DISAGREEMENT)
                    return@repeat
                }
                solverReplay = resolution.resultingState
            }
            if (solverReplay.arrows.isNotEmpty()) {
                reject(V6RejectionCode.SOLVER_DISAGREEMENT)
                return@repeat
            }
            val level = raw.copy(designedSolutions = listOf(solution.map { it.arrowId }))
            val verification = request.analysisCache?.causalVerification(raw, extracted.spec) {
                ProductionCausalVerifierV6(engine).verify(extracted.spec, extracted.bindings, dag)
            } ?: ProductionCausalVerifierV6(engine).verify(extracted.spec, extracted.bindings, dag)
            if (!verification.complete) {
                reject(V6RejectionCode.CAUSAL_WITNESS_MISSING)
                return@repeat
            }
            if (candidateAcceptance != null && !candidateAcceptance(level, dag, extracted.spec)) {
                reject(V6RejectionCode.BEHAVIOURAL_GATE_FAILED)
                return@repeat
            }
            val fingerprintBase = runCatching {
                request.analysisCache?.fingerprint(raw, extracted.spec) {
                    SemanticFingerprintBuilderV6(
                        ExactColoredGraphCanonicalizerV6(profile.budgets.canonicalBacktrackingStates),
                    ).build(raw, extracted.spec, dag, occupancy).getOrThrow()
                } ?: SemanticFingerprintBuilderV6(
                    ExactColoredGraphCanonicalizerV6(profile.budgets.canonicalBacktrackingStates),
                ).build(
                    raw,
                    extracted.spec,
                    dag,
                    occupancy,
                    if (request.knownFingerprintIndex == null) request.knownFingerprints else emptyList(),
                ).getOrThrow()
            }.getOrElse {
                reject(V6RejectionCode.CANONICALIZATION_CAP)
                return@repeat
            }
            val fingerprints = request.knownFingerprintIndex?.attachExactNearest(fingerprintBase) ?: fingerprintBase
            val indexedDuplicate = request.knownFingerprintIndex?.duplicateReason(fingerprints)
            val duplicateCode = indexedDuplicate?.let { reason ->
                when (reason) {
                    "REJECT_EXACT_DUPLICATE" -> V6RejectionCode.EXACT_DUPLICATE
                    "REJECT_D4_DUPLICATE" -> V6RejectionCode.D4_DUPLICATE
                    "REJECT_CAUSAL_DUPLICATE" -> V6RejectionCode.CAUSAL_DUPLICATE
                    "REJECT_DECISION_DAG_DUPLICATE" -> V6RejectionCode.DECISION_DAG_DUPLICATE
                    "REJECT_SOLUTION_POLICY_DUPLICATE" -> V6RejectionCode.SOLUTION_POLICY_DUPLICATE
                    "REJECT_NEAR_SEMANTIC_DUPLICATE" -> V6RejectionCode.NEAR_SEMANTIC_CLONE
                    else -> V6RejectionCode.D4_DUPLICATE
                }
            } ?: request.knownFingerprints.firstNotNullOfOrNull { known ->
                when {
                    known.exactLayout == fingerprints.exactLayout -> V6RejectionCode.EXACT_DUPLICATE
                    known.d4Layout == fingerprints.d4Layout -> V6RejectionCode.D4_DUPLICATE
                    known.causalHypergraph == fingerprints.causalHypergraph -> V6RejectionCode.CAUSAL_DUPLICATE
                    known.quotientDecisionDag == fingerprints.quotientDecisionDag -> V6RejectionCode.DECISION_DAG_DUPLICATE
                    known.solutionPolicy == fingerprints.solutionPolicy -> V6RejectionCode.SOLUTION_POLICY_DUPLICATE
                    else -> null
                }
            }
            if (duplicateCode != null) {
                reject(duplicateCode)
                return@repeat
            }
            if ((fingerprints.nearestSemanticSimilarity ?: 0.0) > 0.92) {
                reject(V6RejectionCode.NEAR_SEMANTIC_CLONE)
                return@repeat
            }
            return EngineGuidedRealizationV6(level, extracted.spec, extracted.bindings, boardIndex + 1) to
                EngineGuidedSearchFailureV6(boardIndex + 1, rejectionCounts)
        }
        return null to EngineGuidedSearchFailureV6(cap, rejectionCounts)
    }

    private fun behaviourPasses(profile: V6Profile, metrics: DecisionDagMetricsV6): Boolean =
        metrics.meaningfulDecisionCount in profile.meaningfulDecisionRange &&
            metrics.successfulLosingBranchCount >= profile.minimumPersistentTraps &&
            metrics.minimumLookaheadProofDepth >= profile.minimumLookahead &&
            (profile.maximumHardestWinningShare == null ||
                metrics.hardestWinningChoiceShare <= profile.maximumHardestWinningShare) &&
            !(profile.bucket >= 5 && (metrics.forcedRunLengths.maxOrNull() ?: 0) >=
                (metrics.shortestCompletion ?: Int.MAX_VALUE) && metrics.meaningfulDecisionCount < profile.minimumPersistentTraps)

    private fun proposedLevel(
        request: V6GenerationRequest,
        bucket: Int,
        family: CausalGrammarFamilyV6,
        random: SeededRandom,
        boardIndex: Int,
        shapeOverride: EngineGuidedShapeV6?,
    ): LevelDefinition = if (shapeOverride != null) {
        randomLevel(request, shapeOverride, random)
    } else when (bucket) {
        5 -> when (family) {
            CausalGrammarFamilyV6.COMPETING_CONTROLLER_HANDOFF ->
                mutateElite(expertHandoffElite(request), random, boardIndex + 1)
            CausalGrammarFamilyV6.CANCELLATION_RELEASE -> if (boardIndex % 3 == 0) {
                randomCancellationExpertLevel(request, random)
            } else {
                mutateElite(expertElite(request), random, boardIndex + 1)
            }
            else -> mutateElite(expertElite(request), random, boardIndex + 1)
        }
        1 -> if (family == CausalGrammarFamilyV6.COMPETING_CONTROLLER_HANDOFF) {
            mutateElite(beginnerHandoffElite(request), random, boardIndex + 1)
        } else {
            randomLevel(request, bucket, family, random)
        }
        3, 4 -> if (family == CausalGrammarFamilyV6.COMPETING_CONTROLLER_HANDOFF) {
            randomHandoffLevel(request, random, bucket)
        } else {
            randomLevel(request, bucket, family, random)
        }
        2 -> if (
            family == CausalGrammarFamilyV6.COMPETING_CONTROLLER_HANDOFF &&
            request.knownFingerprints.any { !it.causalHypergraph.startsWith("unavailable:") } &&
            boardIndex % 2 == 1
        ) {
            randomExpandedHandoffLevel(request, random)
        } else {
            randomLevel(request, bucket, family, random)
        }
        else -> randomLevel(request, bucket, family, random)
    }

    private fun randomLevel(
        request: V6GenerationRequest,
        bucket: Int,
        family: CausalGrammarFamilyV6,
        random: SeededRandom,
    ): LevelDefinition {
        val shape = when (bucket) {
            1 -> if (family in setOf(
                    CausalGrammarFamilyV6.CANCELLATION_RELEASE,
                    CausalGrammarFamilyV6.COMPETING_CONTROLLER_HANDOFF,
                )
            ) Shape(3, 3, 3, 2, 0) else Shape(3, 3, 3, 1, 1)
            2 -> if (family == CausalGrammarFamilyV6.COMPETING_CONTROLLER_HANDOFF) {
                Shape(4, 3, 4, 3, 0)
            } else {
                Shape(4, 3, 4, 2, 1)
            }
            3 -> Shape(4, 4, 5, 2, 2)
            else -> if (family == CausalGrammarFamilyV6.COMPETING_CONTROLLER_HANDOFF) {
                Shape(4, 4, 6, 3, 1)
            } else {
                Shape(4, 4, 6, 2, 2)
            }
        }
        val positions = shuffledCells(shape.width, shape.height, random)
        return LevelDefinition(
            request.stableId,
            request.number,
            request.title,
            shape.width,
            shape.height,
            (0 until shape.arrows).map { index ->
                Arrow(arrowId(index), positions[index], Direction.entries[random.nextInt(Direction.entries.size)])
            },
            (0 until shape.magnets).map { index ->
                Magnet(
                    magnetId(index),
                    positions[shape.arrows + index],
                    Polarity.entries[random.nextInt(Polarity.entries.size)],
                )
            },
            (0 until shape.walls).map { index -> Wall(positions[shape.arrows + shape.magnets + index]) },
            emptyList(),
        )
    }

    private fun randomLevel(
        request: V6GenerationRequest,
        shape: EngineGuidedShapeV6,
        random: SeededRandom,
    ): LevelDefinition {
        val positions = shuffledCells(shape.width, shape.height, random)
        return LevelDefinition(
            request.stableId,
            request.number,
            request.title,
            shape.width,
            shape.height,
            (0 until shape.arrows).map { index ->
                Arrow(arrowId(index), positions[index], Direction.entries[random.nextInt(Direction.entries.size)])
            },
            (0 until shape.magnets).map { index ->
                Magnet(
                    magnetId(index),
                    positions[shape.arrows + index],
                    Polarity.entries[random.nextInt(Polarity.entries.size)],
                )
            },
            (0 until shape.walls).map { index ->
                Wall(positions[shape.arrows + shape.magnets + index])
            },
            emptyList(),
        )
    }

    private fun mutateElite(base: LevelDefinition, random: SeededRandom, boardIndex: Int): LevelDefinition {
        if (boardIndex == 0) return base
        var arrows = base.arrows
        var magnets = base.magnets
        var walls = base.walls
        val edits = 1 + random.nextInt(7)
        repeat(edits) {
            when (random.nextInt(8)) {
                0 -> {
                    val index = random.nextInt(arrows.size)
                    arrows = arrows.toMutableList().also { values ->
                        values[index] = values[index].copy(printedDirection = Direction.entries[random.nextInt(4)])
                    }
                }
                1 -> {
                    val index = random.nextInt(magnets.size)
                    magnets = magnets.toMutableList().also { values ->
                        values[index] = values[index].copy(polarity = values[index].polarity.flipped())
                    }
                }
                2 -> {
                    val first = random.nextInt(arrows.size)
                    val second = random.nextInt(arrows.size)
                    if (first != second) arrows = arrows.toMutableList().also { values ->
                        val firstPosition = values[first].position
                        values[first] = values[first].copy(position = values[second].position)
                        values[second] = values[second].copy(position = firstPosition)
                    }
                }
                3 -> {
                    val arrowIndex = random.nextInt(arrows.size)
                    val magnetIndex = random.nextInt(magnets.size)
                    val arrowPosition = arrows[arrowIndex].position
                    val magnetPosition = magnets[magnetIndex].position
                    arrows = arrows.toMutableList().also { values ->
                        values[arrowIndex] = values[arrowIndex].copy(position = magnetPosition)
                    }
                    magnets = magnets.toMutableList().also { values ->
                        values[magnetIndex] = values[magnetIndex].copy(position = arrowPosition)
                    }
                }
                else -> {
                    val occupied = (arrows.map { it.position } + magnets.map { it.position } + walls.map { it.position }).toSet()
                    val empty = (1..base.height).flatMap { row ->
                        (1..base.width).map { column -> Position(row, column) }
                    }.filterNot(occupied::contains)
                    when (random.nextInt(3)) {
                        0 -> if (empty.isNotEmpty()) {
                            val index = random.nextInt(magnets.size)
                            magnets = magnets.toMutableList().also { values ->
                                values[index] = values[index].copy(position = empty[random.nextInt(empty.size)])
                            }
                        }
                        1 -> if (walls.isNotEmpty()) {
                            val arrowIndex = random.nextInt(arrows.size)
                            val wallIndex = random.nextInt(walls.size)
                            val arrowPosition = arrows[arrowIndex].position
                            arrows = arrows.toMutableList().also { values ->
                                values[arrowIndex] = values[arrowIndex].copy(position = walls[wallIndex].position)
                            }
                            walls = walls.toMutableList().also { values -> values[wallIndex] = Wall(arrowPosition) }
                        }
                        else -> if (empty.isNotEmpty() && walls.isNotEmpty()) {
                            val wallIndex = random.nextInt(walls.size)
                            walls = walls.toMutableList().also { values ->
                                values[wallIndex] = Wall(empty[random.nextInt(empty.size)])
                            }
                        }
                    }
                }
            }
        }
        return base.copy(arrows = arrows, magnets = magnets, walls = walls)
    }

    private fun expertElite(request: V6GenerationRequest) = LevelDefinition(
        request.stableId, request.number, request.title, 4, 4,
        listOf(
            Arrow(arrowId(0), Position(3, 2), Direction.EAST),
            Arrow(arrowId(1), Position(3, 1), Direction.SOUTH),
            Arrow(arrowId(2), Position(4, 3), Direction.WEST),
            Arrow(arrowId(3), Position(1, 2), Direction.WEST),
            Arrow(arrowId(4), Position(4, 1), Direction.NORTH),
            Arrow(arrowId(5), Position(2, 3), Direction.EAST),
            Arrow(arrowId(6), Position(2, 2), Direction.WEST),
        ),
        listOf(
            Magnet(magnetId(0), Position(2, 4), Polarity.PULL),
            Magnet(magnetId(1), Position(4, 2), Polarity.PUSH),
        ),
        listOf(Wall(Position(1, 1)), Wall(Position(2, 1))),
        emptyList(),
    )

    private fun expertHandoffElite(request: V6GenerationRequest) = LevelDefinition(
        request.stableId, request.number, request.title, 4, 4,
        listOf(
            Arrow(arrowId(0), Position(2, 2), Direction.WEST),
            Arrow(arrowId(1), Position(4, 3), Direction.EAST),
            Arrow(arrowId(2), Position(3, 4), Direction.EAST),
            Arrow(arrowId(3), Position(4, 2), Direction.SOUTH),
            Arrow(arrowId(4), Position(2, 3), Direction.SOUTH),
            Arrow(arrowId(5), Position(1, 4), Direction.EAST),
            Arrow(arrowId(6), Position(4, 4), Direction.WEST),
            Arrow(arrowId(7), Position(2, 1), Direction.EAST),
            Arrow(arrowId(8), Position(3, 3), Direction.EAST),
        ),
        listOf(
            Magnet(magnetId(0), Position(1, 3), Polarity.PUSH),
            Magnet(magnetId(1), Position(4, 1), Polarity.PULL),
        ),
        listOf(Wall(Position(1, 1))),
        emptyList(),
    )

    private fun beginnerHandoffElite(request: V6GenerationRequest) = LevelDefinition(
        request.stableId, request.number, request.title, 3, 4,
        listOf(
            Arrow(arrowId(0), Position(1, 1), Direction.WEST),
            Arrow(arrowId(1), Position(1, 2), Direction.EAST),
            Arrow(arrowId(2), Position(3, 2), Direction.EAST),
            Arrow(arrowId(3), Position(4, 3), Direction.EAST),
        ),
        listOf(
            Magnet(magnetId(0), Position(1, 3), Polarity.PULL),
            Magnet(magnetId(1), Position(4, 1), Polarity.PULL),
        ),
        emptyList(),
        emptyList(),
    )

    private fun randomHandoffLevel(
        request: V6GenerationRequest,
        random: SeededRandom,
        bucket: Int,
    ): LevelDefinition {
        val height = if (bucket == 2) 3 else 4
        val extraArrowCount = when (bucket) { 2 -> 2; 3 -> 3; else -> 4 }
        val wallCount = if (bucket == 2) 1 else 2
        val fixed = listOf(
            Position(1, 1),
            Position(1, 2),
            Position(1, 3),
            Position(height, 1),
        ).toSet()
        val remaining = shuffledCells(4, height, random).filterNot(fixed::contains)
        return LevelDefinition(
            request.stableId, request.number, request.title, 4, height,
            listOf(
                Arrow(arrowId(0), Position(1, 1), Direction.WEST),
                Arrow(arrowId(1), Position(1, 2), Direction.EAST),
            ) + (0 until extraArrowCount).map { index ->
                Arrow(arrowId(index + 2), remaining[index], Direction.entries[random.nextInt(4)])
            },
            listOf(
                Magnet(magnetId(0), Position(1, 3), Polarity.PULL),
                Magnet(magnetId(1), Position(height, 1), Polarity.PULL),
            ),
            (0 until wallCount).map { index -> Wall(remaining[extraArrowCount + index]) },
            emptyList(),
        )
    }

    private fun randomExpandedHandoffLevel(
        request: V6GenerationRequest,
        random: SeededRandom,
    ): LevelDefinition {
        val positions = shuffledCells(4, 4, random)
        return LevelDefinition(
            request.stableId,
            request.number,
            request.title,
            4,
            4,
            (0 until 5).map { index ->
                Arrow(arrowId(index), positions[index], Direction.entries[random.nextInt(4)])
            },
            (0 until 3).map { index ->
                Magnet(
                    magnetId(index),
                    positions[5 + index],
                    Polarity.entries[random.nextInt(Polarity.entries.size)],
                )
            },
            listOf(Wall(positions[8]), Wall(positions[9])),
            emptyList(),
        )
    }

    private fun randomCancellationExpertLevel(
        request: V6GenerationRequest,
        random: SeededRandom,
    ): LevelDefinition {
        val scaffold = cancellationScaffolds[random.nextInt(cancellationScaffolds.size)]
        val fixed = setOf(scaffold.firstMagnet, scaffold.secondMagnet, scaffold.blocker, scaffold.affected)
        val remaining = shuffledCells(4, 4, random).filterNot(fixed::contains)
        return LevelDefinition(
            request.stableId, request.number, request.title, 4, 4,
            listOf(
                Arrow(arrowId(0), scaffold.blocker, scaffold.blockerDirection),
                Arrow(arrowId(1), scaffold.affected, Direction.entries[random.nextInt(4)]),
            ) + (0 until 5).map { index ->
                Arrow(arrowId(index + 2), remaining[index], Direction.entries[random.nextInt(4)])
            },
            listOf(
                Magnet(magnetId(0), scaffold.firstMagnet, Polarity.PULL),
                Magnet(magnetId(1), scaffold.secondMagnet, Polarity.entries[random.nextInt(2)]),
            ),
            listOf(Wall(remaining[5]), Wall(remaining[6])),
            emptyList(),
        )
    }

    private val cancellationScaffolds: List<CancellationScaffoldV6> by lazy {
        buildList {
            val cells = (1..4).flatMap { row -> (1..4).map { column -> Position(row, column) } }
            cells.forEach { affected ->
                Direction.entries.forEach firstDirection@{ firstDirection ->
                    val blocker = affected.move(firstDirection)
                    val firstMagnet = blocker.move(firstDirection)
                    if (firstMagnet !in cells || blocker !in cells) return@firstDirection
                    Direction.entries.filter { it != firstDirection }.forEach secondDirection@{ secondDirection ->
                        val secondMagnet = affected.move(secondDirection).move(secondDirection)
                        if (secondMagnet !in cells || secondMagnet == firstMagnet) return@secondDirection
                        add(CancellationScaffoldV6(affected, blocker, firstMagnet, secondMagnet, firstDirection))
                    }
                }
            }
        }.distinct()
    }

    private fun shuffledCells(width: Int, height: Int, random: SeededRandom): List<Position> {
        val cells = (1..height).flatMap { row -> (1..width).map { column -> Position(row, column) } }.toMutableList()
        for (index in cells.lastIndex downTo 1) {
            val other = random.nextInt(index + 1)
            val value = cells[index]
            cells[index] = cells[other]
            cells[other] = value
        }
        return cells
    }

    private data class Shape(val width: Int, val height: Int, val arrows: Int, val magnets: Int, val walls: Int)

    private data class CancellationScaffoldV6(
        val affected: Position,
        val blocker: Position,
        val firstMagnet: Position,
        val secondMagnet: Position,
        val blockerDirection: Direction,
    )

    private fun arrowId(index: Int): String = "v6-a-${index.toString().padStart(2, '0')}"
    private fun magnetId(index: Int): String = "v6-m-${index.toString().padStart(2, '0')}"
}

internal data class ExtractedCausalSpecV6(
    val spec: CausalHypergraphSpec,
    val bindings: Map<String, String>,
)

internal class ProductionCausalSpecExtractorV6(
    private val engine: GameEngine,
    private val tracer: DeterministicRouteTracer = DeterministicRouteTracer(),
) {
    fun extract(
        identity: GeneratorV6Identity,
        profile: V6Profile,
        level: LevelDefinition,
        dag: DecisionDagAnalysisV6,
    ): ExtractedCausalSpecV6? {
        val chainCount = maxOf(profile.minimumInteractingChains, 1)
        val arrows = level.arrows.sortedBy { it.id }
        val magnets = level.magnets.sortedBy { it.id }
        val walls = level.walls.sortedWith(compareBy({ it.position.row }, { it.position.column }))
        val arrowRoles = arrows.mapIndexed { index, arrow ->
            LogicalEntityRole("arrow-$index", LogicalEntityKindV6.ARROW, index % chainCount, index / chainCount)
        }
        val actionRoles = arrows.mapIndexed { index, _ ->
            LogicalActionRole("action-$index", "arrow-$index", LogicalActionKindV6.REQUIRED, index % chainCount, index / chainCount)
        }
        val magnetRoles = magnets.mapIndexed { index, _ ->
            LogicalEntityRole("magnet-$index", LogicalEntityKindV6.MAGNET, index % chainCount, 0)
        }
        val wallRoles = walls.mapIndexed { index, _ ->
            LogicalEntityRole("wall-$index", LogicalEntityKindV6.WALL, index % chainCount, 0)
        }
        val arrowIndex = arrows.withIndex().associate { it.value.id to it.index }
        val magnetIndex = magnets.withIndex().associate { it.value.id to it.index }
        fun action(arrowId: String) = "action-${requireNotNull(arrowIndex[arrowId])}"
        fun magnet(magnetId: String) = "magnet-${requireNotNull(magnetIndex[magnetId])}"
        val bindings = buildMap {
            arrows.forEachIndexed { index, arrow -> put("arrow-$index", arrow.id); put("action-$index", arrow.id) }
            magnets.forEachIndexed { index, value -> put("magnet-$index", value.id) }
            walls.forEachIndexed { index, value -> put("wall-$index", "${value.position.row},${value.position.column}") }
        }

        val observations = observations(dag)
        val target = targetEdge(identity.grammarFamily, observations, dag, ::action, ::magnet) ?: return null
        val winningActionIds = dag.nodes.values.flatMap { node ->
            node.transitions.filter { it.successful && it.futureSolvable == true }.map { it.arrowId }
        }.toSet()
        val trapObservations = observations.filter {
            it.trapDepth != null && it.affectedId == null && it.triggerId in winningActionIds
        }
            .groupBy { it.triggerId }
            .mapNotNull { (_, values) -> values.maxByOrNull { it.trapDepth ?: -1 } }
            .sortedWith(compareByDescending<ObservationV6> { it.trapDepth }.thenBy { it.triggerId })
        if (profile.minimumPersistentTraps > 0 && trapObservations.isEmpty()) return null
        val declaredTraps = trapObservations.take(profile.minimumPersistentTraps)
        val trapActions = declaredTraps.map { action(it.triggerId) }.toSet()
        val typedActions = actionRoles.map { role ->
            if (role.key in trapActions) role.copy(kind = LogicalActionKindV6.SUCCESSFUL_TRAP) else role
        }
        val edges = mutableListOf(target)
        val contracts = mutableListOf<SuccessfulTrapContract>()
        declaredTraps.forEachIndexed { index, observation ->
            val actionKey = action(observation.triggerId)
            contracts += SuccessfulTrapContract(actionKey, observation.trapDepth ?: 0)
            if (target.effect != CausalEffectV6.SUCCESSFUL_TRAP || target.triggeringActionRoleKey != actionKey) {
                edges += CausalHyperedge(
                    "verified-trap-$index",
                    listOf(StateCondition(StateConditionTypeV6.STATE_SOLVABLE, actionKey)),
                    actionKey,
                    CausalEffectV6.SUCCESSFUL_TRAP,
                    listOf(actionKey),
                )
            }
        }
        // A declared chain interaction must actually cross two assigned logical chains. The prior
        // code took the first non-commuting pairs by ID and could therefore emit only same-chain
        // edges while claiming multiple interacting chains in the spec.
        fun arrowChain(arrowId: String): Int = arrowRoles[requireNotNull(arrowIndex[arrowId])].chain
        dag.commutation.filter { evidence ->
            !evidence.commutes && arrowChain(evidence.firstArrowId) != arrowChain(evidence.secondArrowId)
        }.sortedWith(
            compareBy(CommutationEvidenceV6::stateKey, CommutationEvidenceV6::firstArrowId, CommutationEvidenceV6::secondArrowId),
        ).distinctBy { evidence ->
            listOf(evidence.firstArrowId, evidence.secondArrowId).sorted().joinToString("|")
        }.take((chainCount - 1).coerceAtLeast(0)).forEachIndexed { index, evidence ->
            val trigger = action(evidence.firstArrowId)
            val affected = action(evidence.secondArrowId)
            edges += CausalHyperedge(
                "verified-chain-interaction-$index",
                emptyList(), trigger, CausalEffectV6.NON_COMMUTING_CHOICE, listOf(affected), false,
            )
        }
        val precedence = requireNotNull(dag.metrics).transitiveReduction.mapTo(linkedSetOf()) { (before, after) ->
            action(before) to action(after)
        }
        val spec = CausalHypergraphSpec(
            identity.grammarFamily,
            arrowRoles + magnetRoles + wallRoles,
            typedActions,
            edges.distinctBy { it.key },
            SolutionPartialOrder(typedActions.mapTo(linkedSetOf()) { it.key }, precedence),
            contracts,
            chainCount,
        )
        return ExtractedCausalSpecV6(spec, bindings)
    }

    private fun targetEdge(
        family: CausalGrammarFamilyV6,
        observations: List<ObservationV6>,
        dag: DecisionDagAnalysisV6,
        action: (String) -> String,
        magnet: (String) -> String,
    ): CausalHyperedge? {
        val selected = when (family) {
            CausalGrammarFamilyV6.POLARITY_LOCK_RELEASE -> observations.firstOrNull { it.flipMagnetId != null && it.signatureChanged }
            CausalGrammarFamilyV6.OCCLUSION_REVEAL_CHAIN -> observations.firstOrNull {
                it.affectedId != null && ((it.beforeController == null) != (it.afterController == null))
            }
            CausalGrammarFamilyV6.CANCELLATION_RELEASE -> observations.firstOrNull {
                it.affectedId != null && it.beforeCancellation != it.afterCancellation
            }
            CausalGrammarFamilyV6.COMPETING_CONTROLLER_HANDOFF -> observations.firstOrNull {
                it.beforeController != null && it.afterController != null && it.beforeController != it.afterController
            }
            CausalGrammarFamilyV6.FORK_JOIN_COUPLED -> observations.firstOrNull {
                it.affectedId != null && it.beforeSuccess == false && it.afterSuccess == true
            }
            CausalGrammarFamilyV6.INTERACTING_CHAINS_DELAYED_TRAPS -> observations.firstOrNull { it.trapDepth != null }
        } ?: return null
        val trigger = action(selected.triggerId)
        val affected = selected.affectedId?.let(action)
        return when (family) {
            CausalGrammarFamilyV6.POLARITY_LOCK_RELEASE -> {
                val magnetRole = magnet(requireNotNull(selected.flipMagnetId))
                val polarity = selected.preState.magnet(selected.flipMagnetId)?.polarity ?: return null
                CausalHyperedge(
                    "family-polarity-lock-release",
                    listOf(StateCondition(if (polarity == Polarity.PULL) StateConditionTypeV6.MAGNET_PULL else StateConditionTypeV6.MAGNET_PUSH, magnetRole)),
                    trigger, CausalEffectV6.FLIPS_REQUIRED_POLARITY,
                    listOf(magnetRole, requireNotNull(affected)), false,
                )
            }
            CausalGrammarFamilyV6.OCCLUSION_REVEAL_CHAIN -> {
                val reveal = selected.beforeController == null
                val precondition = if (reveal) {
                    StateCondition(StateConditionTypeV6.CONTROLLER_NONE, requireNotNull(affected))
                } else {
                    StateCondition(
                        StateConditionTypeV6.CONTROLLER_IS,
                        requireNotNull(affected),
                        magnet(requireNotNull(selected.beforeController)),
                    )
                }
                CausalHyperedge(
                    "family-occlusion-reveal", listOf(precondition), trigger,
                    if (reveal) CausalEffectV6.REVEALS_CONTROLLER else CausalEffectV6.OCCLUDES_CONTROLLER,
                    listOf(requireNotNull(affected)),
                )
            }
            CausalGrammarFamilyV6.CANCELLATION_RELEASE -> CausalHyperedge(
                "family-cancellation-transition",
                listOf(
                    StateCondition(
                        if (selected.beforeCancellation) StateConditionTypeV6.CANCELLATION_ACTIVE
                        else if (selected.beforeSuccess == true) StateConditionTypeV6.ACTION_SUCCEEDS
                        else StateConditionTypeV6.ACTION_FAILS,
                        requireNotNull(affected),
                    ),
                ),
                trigger,
                if (selected.beforeCancellation) CausalEffectV6.RELEASES_CANCELLATION else CausalEffectV6.CREATES_CANCELLATION,
                listOf(requireNotNull(affected)),
            )
            CausalGrammarFamilyV6.COMPETING_CONTROLLER_HANDOFF -> CausalHyperedge(
                "family-controller-handoff",
                listOf(
                    StateCondition(
                        StateConditionTypeV6.CONTROLLER_IS,
                        requireNotNull(affected),
                        magnet(requireNotNull(selected.beforeController)),
                    ),
                ),
                trigger, CausalEffectV6.CHANGES_CONTROLLER, listOf(requireNotNull(affected)),
            )
            CausalGrammarFamilyV6.FORK_JOIN_COUPLED -> CausalHyperedge(
                "family-fork-join",
                listOf(
                    StateCondition(StateConditionTypeV6.ACTION_FAILS, requireNotNull(affected)),
                    StateCondition(StateConditionTypeV6.STATE_SOLVABLE, trigger),
                ),
                trigger, CausalEffectV6.FORK_JOIN_DEPENDENCY, listOf(requireNotNull(affected)),
            )
            CausalGrammarFamilyV6.INTERACTING_CHAINS_DELAYED_TRAPS -> CausalHyperedge(
                "family-delayed-successful-trap",
                listOf(StateCondition(StateConditionTypeV6.STATE_SOLVABLE, trigger)),
                trigger, CausalEffectV6.SUCCESSFUL_TRAP, listOf(trigger),
            )
        }
    }

    private fun observations(dag: DecisionDagAnalysisV6): List<ObservationV6> = buildList {
        dag.nodes.values.sortedBy { it.stateKey }.forEach { node ->
            node.transitions.filter { it.successful }.sortedBy { it.arrowId }.forEach { transition ->
                val child = transition.childStateKey?.let(dag.nodes::get) ?: return@forEach
                if (node.solvable == true && transition.futureSolvable == false) {
                    add(
                        ObservationV6(
                            node.state,
                            transition.arrowId,
                            null,
                            transition.polarityChange?.substringBefore(':'),
                            false,
                            null,
                            null,
                            null,
                            null,
                            false,
                            false,
                            transition.delayedDeadlockDepth ?: 0,
                        ),
                    )
                }
                child.state.arrows.sortedBy { it.id }.forEach { affected ->
                    val before = engine.resolve(node.state, PlayerAction(affected.id))
                    val after = engine.resolve(child.state, PlayerAction(affected.id))
                    add(
                        ObservationV6(
                            node.state,
                            transition.arrowId,
                            affected.id,
                            transition.polarityChange?.substringBefore(':'),
                            resolutionSignature(before) != resolutionSignature(after),
                            before.controllingMagnetId,
                            after.controllingMagnetId,
                            before.success,
                            after.success,
                            cancellation(node.state, affected.id),
                            cancellation(child.state, affected.id),
                            null,
                        ),
                    )
                }
            }
        }
    }

    private fun cancellation(state: BoardState, arrowId: String): Boolean = state.arrow(arrowId)?.let { arrow ->
        tracer.explainControl(state, arrow).cancelledByEqualNearestMagnets
    } ?: false

    private fun resolutionSignature(result: ResolutionResult): String = listOf(
        result.success,
        result.effectiveDirection,
        result.controllingMagnetId,
        result.traversedCells,
        result.terminalEvent::class.simpleName,
        result.polarityChange,
    ).joinToString("|")

    private data class ObservationV6(
        val preState: BoardState,
        val triggerId: String,
        val affectedId: String?,
        val flipMagnetId: String?,
        val signatureChanged: Boolean,
        val beforeController: String?,
        val afterController: String?,
        val beforeSuccess: Boolean?,
        val afterSuccess: Boolean?,
        val beforeCancellation: Boolean,
        val afterCancellation: Boolean,
        val trapDepth: Int?,
    )
}
