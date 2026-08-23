package com.rameshta.magnetrail.core.generation.v6

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.difficulty.DifficultyV3Gate
import com.rameshta.magnetrail.core.difficulty.DifficultyV3Scorer
import com.rameshta.magnetrail.core.difficulty.PuzzleDifficultyTarget
import com.rameshta.magnetrail.core.difficulty.PuzzleQualityAnalyzerV2
import com.rameshta.magnetrail.core.difficulty.PuzzleQualityStatusV2
import com.rameshta.magnetrail.core.difficulty.PuzzleSearchAnalyzer
import com.rameshta.magnetrail.core.difficulty.PuzzleSearchConfig
import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4Analyzer
import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4Config
import com.rameshta.magnetrail.core.difficulty.v4.defaultDifficultyV4Seeds
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.DifficultyBand
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.GradingThresholds
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.LevelMetadata
import com.rameshta.magnetrail.core.model.LevelOrigin
import com.rameshta.magnetrail.core.model.Magnet
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall
import com.rameshta.magnetrail.core.solver.Solver
import kotlin.math.ceil

const val V6_STAGING_CONTENT_VERSION = 12

data class V6GenerationRequest(
    val identity: GeneratorV6Identity,
    val stableId: String,
    val number: Int,
    val title: String,
    val packId: String,
    val knownFingerprints: List<V6FingerprintBundle> = emptyList(),
    val knownFingerprintIndex: V61FingerprintIndex? = null,
    val analysisCache: V61AnalysisCache? = null,
) {
    init {
        require(stableId.isNotBlank() && number > 0 && title.isNotBlank() && packId.isNotBlank())
    }
}

sealed interface V6GenerationResult {
    data class Generated(val candidate: V6Candidate, val certificate: V6TechnicalCertificate) : V6GenerationResult
    data class Rejected(val candidate: V6Candidate, val attemptsUsed: Int) : V6GenerationResult
}

class V6GeometryCompiler {
    fun problem(spec: CausalHypergraphSpec, profile: V6Profile): V6RealizationProblem {
        val dimensions = chooseDimensions(spec, profile)
        val width = dimensions.first
        val height = dimensions.second
        val cells = (1..height).flatMap { row -> (1..width).map { column -> Position(row, column) } }
        val chains = spec.entities.map { it.chain }.distinct().sorted()
        val laneByChain = chains.withIndex().associate { (index, chain) ->
            val lane = if (chains.size == 1) 1 else 1 + index * (height - 1) / (chains.size - 1)
            chain to lane
        }
        val variables = spec.entities.map { role ->
            val lane = requireNotNull(laneByChain[role.chain])
            val roleCells = when (role.kind) {
                LogicalEntityKindV6.ARROW,
                LogicalEntityKindV6.MAGNET -> cells.filter { it.row == lane }
                LogicalEntityKindV6.WALL -> cells.filter { kotlin.math.abs(it.row - lane) == 1 }
                    .ifEmpty { cells.filter { it.row != lane } }
            }
            val values = when (role.kind) {
                LogicalEntityKindV6.ARROW -> roleCells.flatMap { cell ->
                    Direction.entries.map { direction -> V6EntityValue(cell, printedDirection = direction) }
                }
                LogicalEntityKindV6.MAGNET -> roleCells.flatMap { cell ->
                    listOf(V6EntityValue(cell, polarity = Polarity.PULL))
                }
                LogicalEntityKindV6.WALL -> roleCells.map(::V6EntityValue)
            }
            V6RealizationVariable(role, values)
        }
        val constraints = mutableListOf<V6RealizationConstraint>()
        val firstArrow = spec.entities.firstOrNull { it.kind == LogicalEntityKindV6.ARROW }
        firstArrow?.let { constraints += V6Constraints.symmetryBreak(it.key, width, height) }
        val byChain = spec.entities.groupBy { it.chain }
        byChain.toSortedMap().forEach { (_, roles) ->
            val magnet = roles.firstOrNull { it.kind == LogicalEntityKindV6.MAGNET } ?: return@forEach
            val arrows = roles.filter { it.kind == LogicalEntityKindV6.ARROW }.sortedBy { it.ordinal }
            arrows.forEach { arrow ->
                constraints += V6Constraints.aligned(arrow.key, magnet.key)
                constraints += V6Constraints.minimumDistance(arrow.key, magnet.key, 1)
                constraints += directionTowardOrAway(arrow.key, magnet.key)
            }
            arrows.zipWithNext().forEach { (inner, outer) ->
                constraints += V6Constraints.between(inner.key, outer.key, magnet.key)
            }
            roles.filter { it.kind == LogicalEntityKindV6.WALL }.zip(arrows).forEach { (wall, arrow) ->
                constraints += adjacentToRouteWithoutBlocking(wall.key, arrow.key, magnet.key)
            }
        }
        return V6RealizationProblem(width, height, variables, constraints)
    }

    private fun chooseDimensions(spec: CausalHypergraphSpec, profile: V6Profile): Pair<Int, Int> {
        val objectCount = spec.entities.size
        val chainCount = spec.entities.map { it.chain }.distinct().size
        val longestChain = spec.actions.groupingBy { it.chain }.eachCount().values.maxOrNull() ?: 1
        val minimumWidth = longestChain + 1
        val minimumHeight = (chainCount * 2 - 1).coerceAtLeast(2)
        val target = (profile.occupancy.start + profile.occupancy.endInclusive) / 2.0
        return buildList {
            for (height in minimumHeight..8) for (width in minimumWidth..8) {
                val occupancy = objectCount.toDouble() / (width * height)
                if (occupancy in profile.occupancy) add(width to height)
            }
        }.minWithOrNull(
            compareBy<Pair<Int, Int>> { kotlin.math.abs(objectCount.toDouble() / (it.first * it.second) - target) }
                .thenBy { it.first * it.second }
                .thenBy { kotlin.math.abs(it.first - it.second) }
                .thenBy { it.first }
                .thenBy { it.second },
        ) ?: error(
            "No <=8x8 purposeful-density dimensions for ${spec.entities.size} objects, " +
                "$chainCount chains and longest chain $longestChain in ${profile.occupancy}",
        )
    }

    fun materialize(
        request: V6GenerationRequest,
        spec: CausalHypergraphSpec,
        problem: V6RealizationProblem,
        assignment: Map<String, V6EntityValue>,
    ): Pair<LevelDefinition, Map<String, String>> {
        val arrows = spec.entities.filter { it.kind == LogicalEntityKindV6.ARROW }.sortedBy { it.key }.mapIndexed { index, role ->
            val value = requireNotNull(assignment[role.key])
            Arrow("v6-a-${index.toString().padStart(2, '0')}", value.position, requireNotNull(value.printedDirection))
        }
        val magnets = spec.entities.filter { it.kind == LogicalEntityKindV6.MAGNET }.sortedBy { it.key }.mapIndexed { index, role ->
            val value = requireNotNull(assignment[role.key])
            Magnet("v6-m-${index.toString().padStart(2, '0')}", value.position, requireNotNull(value.polarity))
        }
        val walls = spec.entities.filter { it.kind == LogicalEntityKindV6.WALL }.sortedBy { it.key }.map { role ->
            Wall(requireNotNull(assignment[role.key]).position)
        }
        val entityBindings = buildMap {
            spec.entities.filter { it.kind == LogicalEntityKindV6.ARROW }.sortedBy { it.key }.zip(arrows).forEach { (role, arrow) ->
                put(role.key, arrow.id)
            }
            spec.entities.filter { it.kind == LogicalEntityKindV6.MAGNET }.sortedBy { it.key }.zip(magnets).forEach { (role, magnet) ->
                put(role.key, magnet.id)
            }
            spec.entities.filter { it.kind == LogicalEntityKindV6.WALL }.sortedBy { it.key }.zip(walls).forEach { (role, wall) ->
                put(role.key, "${wall.position.row},${wall.position.column}")
            }
        }
        val bindings = buildMap {
            putAll(entityBindings)
            spec.actions.forEach { action -> put(action.key, requireNotNull(entityBindings[action.arrowRoleKey])) }
        }
        val designed = spec.solutionPartialOrder.deterministicTopologicalOrder().map { requireNotNull(bindings[it]) }
        return LevelDefinition(
            id = request.stableId,
            number = request.number,
            title = request.title,
            width = problem.width,
            height = problem.height,
            arrows = arrows,
            magnets = magnets,
            walls = walls,
            designedSolutions = listOf(designed),
        ) to bindings
    }

    private fun directionTowardOrAway(arrowRole: String, magnetRole: String): V6RealizationConstraint =
        V6RealizationConstraint { assignment ->
            val arrow = assignment[arrowRole]
            val magnet = assignment[magnetRole]
            if (arrow == null || magnet == null) true else {
                val toward = Direction.between(arrow.position, magnet.position)
                arrow.printedDirection == toward
            }
        }

    private fun adjacentToRouteWithoutBlocking(
        wallRole: String,
        arrowRole: String,
        magnetRole: String,
    ): V6RealizationConstraint = V6RealizationConstraint { assignment ->
        val wall = assignment[wallRole]?.position
        val arrow = assignment[arrowRole]?.position
        val magnet = assignment[magnetRole]?.position
        if (wall == null || arrow == null || magnet == null) true else {
            val alignedRow = arrow.row == magnet.row
            val adjacent = if (alignedRow) abs(wall.row - arrow.row) == 1 else abs(wall.column - arrow.column) == 1
            val withinSpan = if (alignedRow) {
                wall.column in minOf(arrow.column, magnet.column)..maxOf(arrow.column, magnet.column)
            } else {
                wall.row in minOf(arrow.row, magnet.row)..maxOf(arrow.row, magnet.row)
            }
            adjacent && withinSpan
        }
    }

    private fun abs(value: Int): Int = kotlin.math.abs(value)
}

class GeneratorV6(
    private val engine: GameEngine = DefaultGameEngine(),
    private val logicalConstructor: ReverseLogicalConstructorV6 = ReverseLogicalConstructorV6(),
    private val geometryCompiler: V6GeometryCompiler = V6GeometryCompiler(),
    private val engineGuidedRealizer: EngineGuidedSpatialRealizerV6 = EngineGuidedSpatialRealizerV6(engine),
    private val humanDifficultyModel: HumanDifficultyModelV1? = null,
) {
    fun generate(request: V6GenerationRequest): V6GenerationResult {
        var lastCandidate: V6Candidate? = null
        val profile = V6Profiles.forBucket(request.identity.bucket)
        repeat(profile.budgets.constructionAttempts) { attempt ->
            val identity = request.identity.copy(
                graphInstance = request.identity.graphInstance + attempt,
                refinementIteration = (attempt / maxOf(1, profile.budgets.constructionAttempts / 3)).coerceAtMost(2),
            )
            val (engineGuided, guidedFailure) = engineGuidedRealizer.realize(request, identity, profile, attempt)
            if (engineGuided != null) {
                val evaluated = evaluate(
                    identity,
                    profile,
                    engineGuided.spec,
                    engineGuided.level,
                    engineGuided.bindings,
                    request.knownFingerprints,
                )
                lastCandidate = evaluated.first
                evaluated.second?.let { certificate -> return V6GenerationResult.Generated(evaluated.first, certificate) }
                return@repeat
            }
            val spec = runCatching { logicalConstructor.construct(identity) }.getOrElse { error ->
                lastCandidate = V6Candidate(
                    identity,
                    profile,
                    emptySpec(identity.grammarFamily),
                    null,
                    emptyMap(),
                    rejections = listOf(
                        V6Rejection(V6RejectionCode.CEGIS_REFINEMENT_EXHAUSTED, guidedFailure.detail()),
                        V6Rejection(V6RejectionCode.INVALID_LOGICAL_SPEC, error.message.orEmpty()),
                    ),
                )
                return@repeat
            }
            val problem = runCatching { geometryCompiler.problem(spec, profile) }.getOrElse { error ->
                lastCandidate = V6Candidate(
                    identity,
                    profile,
                    spec,
                    null,
                    emptyMap(),
                    rejections = listOf(
                        V6Rejection(V6RejectionCode.OCCUPANCY_OUT_OF_RANGE, error.message.orEmpty()),
                    ),
                )
                return@repeat
            }
            val realization = DeterministicConstraintRealizerV6(
                seed = identity.seed xor attempt.toLong(),
                maxStates = profile.budgets.realizationStates,
                maxNogoods = profile.budgets.realizationNogoods,
                maxMillis = profile.budgets.realizationMillis,
            ).realize(problem)
            if (realization is V6RealizationResult.Rejected) {
                lastCandidate = V6Candidate(
                    identity,
                    profile,
                    spec,
                    null,
                    emptyMap(),
                    rejections = listOf(V6Rejection(realization.reason, "realizer explored ${realization.exploredStates}")),
                )
                return@repeat
            }
            realization as V6RealizationResult.Realized
            val (level, bindings) = geometryCompiler.materialize(request, spec, problem, realization.assignment)
            val evaluated = evaluate(identity, profile, spec, level, bindings, request.knownFingerprints)
            lastCandidate = if (evaluated.second == null) {
                evaluated.first.copy(
                    rejections = listOf(
                        V6Rejection(V6RejectionCode.CEGIS_REFINEMENT_EXHAUSTED, guidedFailure.detail()),
                    ) + evaluated.first.rejections,
                )
            } else {
                evaluated.first
            }
            evaluated.second?.let { certificate -> return V6GenerationResult.Generated(evaluated.first, certificate) }
        }
        return V6GenerationResult.Rejected(
            requireNotNull(lastCandidate).let { candidate ->
                if (candidate.rejections.isEmpty()) candidate.copy(
                    rejections = listOf(V6Rejection(V6RejectionCode.CEGIS_REFINEMENT_EXHAUSTED, "bounded refinement exhausted")),
                ) else candidate
            },
            profile.budgets.constructionAttempts,
        )
    }

    private fun evaluate(
        identity: GeneratorV6Identity,
        profile: V6Profile,
        spec: CausalHypergraphSpec,
        rawLevel: LevelDefinition,
        bindings: Map<String, String>,
        archive: List<V6FingerprintBundle>,
    ): Pair<V6Candidate, V6TechnicalCertificate?> {
        val rejections = mutableListOf<V6Rejection>()
        var replay = rawLevel.initialState()
        rawLevel.designedSolutions.single().forEach { arrowId ->
            val result = engine.resolve(replay, PlayerAction(arrowId))
            if (!result.success) {
                rejections += V6Rejection(
                    V6RejectionCode.DESIGNED_REPLAY_FAILED,
                    "Designed replay failed at $arrowId:${result.terminalEvent}",
                    exactStateKeyV6(replay),
                )
                return candidate(identity, profile, spec, rawLevel, bindings, rejections) to null
            }
            replay = result.resultingState
        }
        if (replay.arrows.isNotEmpty()) {
            rejections += V6Rejection(V6RejectionCode.DESIGNED_REPLAY_FAILED, "Designed replay did not clear")
            return candidate(identity, profile, spec, rawLevel, bindings, rejections) to null
        }
        val solver = Solver(engine).solve(
            rawLevel.initialState(),
            solutionLimit = 100_000,
            maxExploredStates = profile.budgets.decisionDagStates,
        )
        if (!solver.searchComplete || !solver.solvable || solver.oneCleanSolution == null) {
            rejections += V6Rejection(
                V6RejectionCode.SOLVER_DISAGREEMENT,
                solver.terminationReason ?: "independent solver did not prove solvable",
            )
            return candidate(identity, profile, spec, rawLevel, bindings, rejections) to null
        }
        replay = rawLevel.initialState()
        requireNotNull(solver.oneCleanSolution).forEach { action -> replay = engine.resolve(replay, action).resultingState }
        if (replay.arrows.isNotEmpty()) {
            rejections += V6Rejection(V6RejectionCode.SOLVER_DISAGREEMENT, "independent solution replay failed")
            return candidate(identity, profile, spec, rawLevel, bindings, rejections) to null
        }
        val dag = CompleteDecisionDagAnalyzerV6(
            engine,
            profile.budgets.decisionDagStates,
            profile.budgets.decisionDagResolutions,
        ).analyze(rawLevel.initialState())
        if (!dag.complete) {
            rejections += V6Rejection(V6RejectionCode.DECISION_ANALYSIS_TRUNCATED, dag.truncationReasons.joinToString())
            return candidate(identity, profile, spec, rawLevel, bindings, rejections, decision = dag) to null
        }
        val failedImmutable = dag.nodes.values.all { node ->
            node.transitions.filter { !it.successful }.all { transition ->
                val result = engine.resolve(node.state, PlayerAction(transition.arrowId))
                result.originalState == node.state && result.resultingState == node.state
            }
        }
        if (!failedImmutable) rejections += V6Rejection(V6RejectionCode.SOLVER_DISAGREEMENT, "failed action mutated state")
        val causal = ProductionCausalVerifierV6(engine).verify(spec, bindings, dag)
        rejections += causal.rejections
        val occupancy = PurposefulOccupancyAnalyzerV6(engine, profile.budgets.counterfactualChecks)
            .analyze(rawLevel, dag, profile)
        occupancy.rejectionReasons.forEach { reason ->
            rejections += V6Rejection(
                when {
                    reason.startsWith("occupancy") -> V6RejectionCode.OCCUPANCY_OUT_OF_RANGE
                    reason.startsWith("purposeful") -> V6RejectionCode.PURPOSEFUL_RATIO_LOW
                    reason.startsWith("inert") -> V6RejectionCode.INERT_RATIO_HIGH
                    reason.startsWith("magnet") -> V6RejectionCode.MAGNET_WITHOUT_WITNESS
                    reason.startsWith("wall") -> V6RejectionCode.WALL_WITHOUT_WITNESS
                    else -> V6RejectionCode.PURPOSEFUL_RATIO_LOW
                },
                reason,
            )
        }
        val metrics = requireNotNull(dag.metrics)
        if (metrics.meaningfulDecisionCount !in profile.meaningfulDecisionRange ||
            metrics.successfulLosingBranchCount < profile.minimumPersistentTraps ||
            metrics.minimumLookaheadProofDepth < profile.minimumLookahead ||
            profile.maximumHardestWinningShare?.let { metrics.hardestWinningChoiceShare > it } == true ||
            spec.interactingChainCount < profile.minimumInteractingChains
        ) {
            rejections += V6Rejection(
                V6RejectionCode.BEHAVIOURAL_GATE_FAILED,
                "decisions=${metrics.meaningfulDecisionCount};traps=${metrics.successfulLosingBranchCount};" +
                    "lookahead=${metrics.minimumLookaheadProofDepth};winningShare=${metrics.hardestWinningChoiceShare};" +
                    "chains=${spec.interactingChainCount}",
            )
        }
        val features = HumanCognitiveFeatureExtractorV1.extract(dag, spec.interactingChainCount)
        val policies = HumanPolicyEnsembleV1().evaluate(dag)
        val minimumBudget = HumanPolicyEnsembleV1().minimumReliableBudget(policies)
        if (profile.bucket >= 5 && minimumBudget != null && minimumBudget.lookaheadDepth <= 2) {
            rejections += V6Rejection(
                V6RejectionCode.SIMPLE_POLICY_SOLVES_HIGH_BUCKET,
                "minimum reliable policy budget=$minimumBudget",
            )
        }
        if (features.guessDependence > 0.0) {
            rejections += V6Rejection(V6RejectionCode.REQUIRED_GUESSING, "guessDependence=${features.guessDependence}")
        }
        val humanPrediction = humanDifficultyModel?.predict(features)
        if (humanPrediction?.rejectionReason != null) {
            rejections += V6Rejection(
                if (humanPrediction.outOfDistribution) V6RejectionCode.OOD_DIFFICULTY else V6RejectionCode.LOW_CONFIDENCE_DIFFICULTY,
                humanPrediction.rejectionReason,
            )
        } else if (humanPrediction != null &&
            humanPrediction.bandProbabilities.indices.maxBy { humanPrediction.bandProbabilities[it] } + 1 != profile.bucket
        ) {
            rejections += V6Rejection(
                V6RejectionCode.LOW_CONFIDENCE_DIFFICULTY,
                "calibrated modal band does not match requested bucket ${profile.bucket}",
            )
        }
        val v4 = DifficultyV4Analyzer(
            engine,
            DifficultyV4Config(
                maxExpandedStates = profile.budgets.decisionDagStates,
                maxActionResolutions = profile.budgets.decisionDagResolutions,
                maxCounterfactualStates = profile.budgets.decisionDagStates,
                maxCounterfactualActionResolutions = profile.budgets.decisionDagResolutions,
                maxObjectCounterfactuals = rawLevel.magnets.size + rawLevel.walls.size,
                randomPolicySeeds = defaultDifficultyV4Seeds(16),
            ),
        ).analyze(rawLevel)
        if (!v4.searchComplete || v4.searchTruncated) {
            rejections += V6Rejection(V6RejectionCode.V4_REJECTED, "V4 incomplete:${v4.metrics.truncationReasons}")
        }
        val v3 = DifficultyV3Scorer.score(
            PuzzleSearchAnalyzer(
                engine,
                PuzzleSearchConfig(
                    maxExpandedStates = profile.budgets.decisionDagStates,
                    maxActionResolutions = profile.budgets.decisionDagResolutions,
                    magneticCounterfactualCap = profile.budgets.counterfactualChecks,
                ),
            ).analyze(rawLevel),
        )
        val quality = PuzzleQualityAnalyzerV2().analyze(
            v3,
            DifficultyV3Gate.evaluate(
                v3,
                PuzzleDifficultyTarget("v6-supplementary", 0, 100, maxGuessDependentRatio = 0.0),
            ),
        )
        if (quality.status == PuzzleQualityStatusV2.REJECT) {
            rejections += V6Rejection(V6RejectionCode.QUALITY_REJECTED, quality.reasonCodes.joinToString())
        }
        if (rejections.isNotEmpty()) {
            return V6Candidate(
                identity, profile, spec, rawLevel, bindings, causal.witnesses, dag, occupancy,
                humanDifficulty = humanPrediction,
                rejections = rejections.distinct(),
            ) to null
        }
        val provisionalFingerprints = SemanticFingerprintBuilderV6(
            ExactColoredGraphCanonicalizerV6(profile.budgets.canonicalBacktrackingStates),
        ).build(rawLevel, spec, dag, occupancy, archive).getOrElse { error ->
            rejections += V6Rejection(V6RejectionCode.CANONICALIZATION_CAP, error.message.orEmpty())
            return V6Candidate(
                identity, profile, spec, rawLevel, bindings, causal.witnesses, dag, occupancy, rejections = rejections,
            ) to null
        }
        val duplicate = archive.firstOrNull { existing ->
            existing.exactLayout == provisionalFingerprints.exactLayout ||
                existing.d4Layout == provisionalFingerprints.d4Layout ||
                existing.causalHypergraph == provisionalFingerprints.causalHypergraph ||
                existing.quotientDecisionDag == provisionalFingerprints.quotientDecisionDag ||
                existing.solutionPolicy == provisionalFingerprints.solutionPolicy
        }
        if (duplicate != null) {
            val code = when {
                duplicate.exactLayout == provisionalFingerprints.exactLayout -> V6RejectionCode.EXACT_DUPLICATE
                duplicate.d4Layout == provisionalFingerprints.d4Layout -> V6RejectionCode.D4_DUPLICATE
                duplicate.causalHypergraph == provisionalFingerprints.causalHypergraph -> V6RejectionCode.CAUSAL_DUPLICATE
                duplicate.quotientDecisionDag == provisionalFingerprints.quotientDecisionDag -> V6RejectionCode.DECISION_DAG_DUPLICATE
                else -> V6RejectionCode.SOLUTION_POLICY_DUPLICATE
            }
            rejections += V6Rejection(code, "semantic collision with ${duplicate.exactLayout}")
            return V6Candidate(
                identity,
                profile,
                spec,
                rawLevel,
                bindings,
                causal.witnesses,
                dag,
                occupancy,
                provisionalFingerprints,
                rejections = rejections,
            ) to null
        }
        if ((provisionalFingerprints.nearestSemanticSimilarity ?: 0.0) > 0.92) {
            rejections += V6Rejection(
                V6RejectionCode.NEAR_SEMANTIC_CLONE,
                "nearest similarity=${provisionalFingerprints.nearestSemanticSimilarity}",
            )
            return V6Candidate(
                identity, profile, spec, rawLevel, bindings, causal.witnesses, dag, occupancy,
                provisionalFingerprints, rejections = rejections,
            ) to null
        }
        val solution = requireNotNull(solver.oneCleanSolution).map { it.arrowId }
        val par = requireNotNull(solver.shortestDepth)
        val fingerprint = ContentFingerprint.of(rawLevel)
        val level = rawLevel.copy(
            designedSolutions = listOf(solution),
            metadata = LevelMetadata(
                contentVersion = V6_STAGING_CONTENT_VERSION,
                origin = LevelOrigin.GENERATOR_ASSISTED,
                generatorVersion = GENERATOR_VERSION_V6,
                generatorSeed = identity.seed,
                generationProfile = "v6-bucket-${profile.bucket}",
                difficultyBand = if (profile.bucket <= 2) DifficultyBand.DEVELOPING else DifficultyBand.ADVANCED,
                certifiedSolutionLength = par,
                solutionCount = solver.solutionCount,
                solutionCountCapped = solver.solutionCountCapped,
                validFirstActionCount = solver.validFirstActions.size,
                exploredStateCount = solver.exploredStateCount,
                grading = GradingThresholds(par, par + maxOf(2, ceil(par * 0.25).toInt())),
                packId = requestPackId(rawLevel.id),
                mechanicTags = (
                    listOf(
                        "V6_FAMILY_${spec.family.name}",
                        "V6_CAUSAL_${provisionalFingerprints.causalHypergraph}",
                        "V6_DAG_${provisionalFingerprints.quotientDecisionDag}",
                        "V6_POLICY_${provisionalFingerprints.solutionPolicy}",
                    ) + spec.hyperedges.map { it.effect.name }
                )
                    .distinct().sorted(),
                contentFingerprint = fingerprint,
            ),
        )
        val candidate = V6Candidate(
            identity,
            profile,
            spec,
            level,
            bindings,
            causal.witnesses,
            dag,
            occupancy,
            provisionalFingerprints,
            humanPrediction,
        )
        val payload = listOf(
            identity.stableKey,
            V6_REALIZER_VERSION,
            V6_DECISION_ANALYZER_VERSION,
            fingerprint,
            causal.witnesses.size,
            dag.nodes.size,
            failedImmutable,
            provisionalFingerprints.causalHypergraph,
            provisionalFingerprints.quotientDecisionDag,
        ).joinToString("|")
        val certificate = V6TechnicalCertificate(
            identity,
            fingerprint,
            causal.witnesses.size,
            dag.nodes.size,
            productionReplayVerified = true,
            independentSolverVerified = true,
            failedActionImmutabilityVerified = failedImmutable,
            deterministicRegenerationVerified = true,
            certificatePayloadSha256 = sha256V6(payload),
        )
        return candidate to certificate
    }

    private fun candidate(
        identity: GeneratorV6Identity,
        profile: V6Profile,
        spec: CausalHypergraphSpec,
        level: LevelDefinition?,
        bindings: Map<String, String>,
        rejections: List<V6Rejection>,
        decision: DecisionDagAnalysisV6? = null,
    ) = V6Candidate(identity, profile, spec, level, bindings, decisionAnalysis = decision, rejections = rejections)

    private fun emptySpec(family: CausalGrammarFamilyV6): CausalHypergraphSpec = CausalHypergraphSpec(
        family,
        emptyList(),
        emptyList(),
        emptyList(),
        SolutionPartialOrder(emptySet(), emptySet()),
        emptyList(),
        0,
    )

    private fun requestPackId(levelId: String): String = when {
        levelId.startsWith("v6-calibration") -> "v6-calibration"
        levelId.startsWith("v6-validation") -> "v6-sealed-validation"
        else -> "v6-staging"
    }
}
