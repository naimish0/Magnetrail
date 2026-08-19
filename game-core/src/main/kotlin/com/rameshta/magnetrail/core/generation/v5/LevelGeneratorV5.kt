package com.rameshta.magnetrail.core.generation.v5

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.difficulty.DifficultyV3Gate
import com.rameshta.magnetrail.core.difficulty.DifficultyV3Scorer
import com.rameshta.magnetrail.core.difficulty.PuzzleDifficultyTarget
import com.rameshta.magnetrail.core.difficulty.PuzzleQualityAnalyzerV2
import com.rameshta.magnetrail.core.difficulty.PuzzleQualityScoreV2
import com.rameshta.magnetrail.core.difficulty.PuzzleQualityStatusV2
import com.rameshta.magnetrail.core.difficulty.PuzzleSearchAnalyzer
import com.rameshta.magnetrail.core.difficulty.PuzzleSearchConfig
import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4Analyzer
import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4Config
import com.rameshta.magnetrail.core.difficulty.v4.defaultDifficultyV4Seeds
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.engine.ResolutionResult
import com.rameshta.magnetrail.core.generation.SeededRandom
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.BoardState
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
import com.rameshta.magnetrail.core.solver.StateKey
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.roundToInt

@Serializable
data class GenerationRequestV5(
    val stableId: String,
    val sequenceNumber: Int,
    val title: String,
    val seed: Long,
    val profile: GenerationProfileV5,
    val packId: String = "d2-staging",
    val maxAttempts: Int = profile.candidateAttemptCap,
)

sealed interface GenerationResultV5 {
    data class Generated(
        val level: LevelDefinition,
        val diagnostics: StructuralDiagnosticsV5,
        val quality: PuzzleQualityScoreV2,
        val attemptsUsed: Int,
        val rejectedReasons: Map<String, Int>,
        val telemetry: GenerationTelemetryV5 = GenerationTelemetryV5(),
    ) : GenerationResultV5

    data class Exhausted(
        val attemptsUsed: Int,
        val rejectedReasons: Map<String, Int>,
        val telemetry: GenerationTelemetryV5 = GenerationTelemetryV5(),
    ) : GenerationResultV5
}

@Serializable
data class GenerationTelemetryV5(
    val candidateAttempts: Int = 0,
    val successfulConstructions: Int = 0,
    val repairAttempts: Int = 0,
    val repairRollbacks: Int = 0,
    val solverFailures: Int = 0,
    val v4Truncations: Int = 0,
    val orderingFailures: Int = 0,
    val wallParticipationFailures: Int = 0,
    val relevanceFailures: Int = 0,
    val safeChoiceFailures: Int = 0,
    val commutationFailures: Int = 0,
    val consequenceFailures: Int = 0,
    val duplicateFailures: Int = 0,
    val difficultyFailures: Int = 0,
    val certifiedCandidates: Int = 0,
)

sealed interface CertificationResultV5 {
    data class Accepted(
        val level: LevelDefinition,
        val diagnostics: StructuralDiagnosticsV5,
        val quality: PuzzleQualityScoreV2,
    ) : CertificationResultV5

    data class Rejected(val reasons: List<String>) : CertificationResultV5
}

/** Production-engine certification for staging candidates. */
class CertificationPipelineV5(
    private val engine: GameEngine = DefaultGameEngine(),
    private val solver: Solver = Solver(engine),
    private val structuralAnalyzer: StructuralAnalyzerV5 = StructuralAnalyzerV5(engine),
) {
    fun certify(
        level: LevelDefinition,
        profile: GenerationProfileV5,
        seed: Long,
        packId: String = "d2-staging",
        contentVersion: Int = D2_STAGING_CONTENT_VERSION,
        previousContentFingerprint: String? = null,
    ): CertificationResultV5 {
        require(contentVersion > 0) { "contentVersion must be positive" }
        val reasons = mutableListOf<String>()
        if (level.width != level.height || level.width !in profile.gridSizes) reasons += "grid-out-of-profile"
        if (level.arrows.size !in profile.minArrows..profile.maxArrows) reasons += "arrow-count-out-of-profile"
        if (level.magnets.size !in profile.minMagnets..profile.maxMagnets) reasons += "magnet-count-out-of-profile"
        if (level.walls.size !in profile.minWalls..profile.maxWalls) reasons += "wall-count-out-of-profile"
        val density = (level.arrows.size + level.magnets.size + level.walls.size).toDouble() /
            (level.width * level.height)
        if (density !in profile.objectDensityRange) reasons += "object-density-out-of-profile"
        profile.spatialDensityProfile?.let { reasons += SpatialDensityGateV5.evaluateAuthored(level, it) }
        level.arrows.forEach { arrow ->
            val state = level.initialState()
            val result = engine.resolve(state, PlayerAction(arrow.id))
            if (!result.success && (result.originalState !== state || result.resultingState != state)) {
                reasons += "failed-action-mutated-state:${arrow.id}"
            }
        }
        if (reasons.isNotEmpty()) return CertificationResultV5.Rejected(reasons.distinct())

        val solved = solver.solve(
            level.initialState(),
            solutionLimit = 100_000,
            maxExploredStates = profile.solverStateCap,
        )
        if (!solved.searchComplete) reasons += solved.terminationReason ?: "solver-incomplete"
        if (!solved.solvable) reasons += "unsolvable"
        val solution = solved.oneCleanSolution
        if (solution == null) reasons += "missing-solution"
        if (reasons.isNotEmpty()) return CertificationResultV5.Rejected(reasons.distinct())
        var replay = level.initialState()
        requireNotNull(solution).forEach { action ->
            val result = engine.resolve(replay, action)
            if (!result.success) reasons += "solution-replay-failed:${action.arrowId}"
            replay = result.resultingState
        }
        if (replay.arrows.isNotEmpty()) reasons += "solution-replay-did-not-clear"
        if (reasons.isNotEmpty()) return CertificationResultV5.Rejected(reasons.distinct())

        val v4 = DifficultyV4Analyzer(
            engine,
            DifficultyV4Config(
                maxExpandedStates = profile.analysisStateCap,
                maxActionResolutions = profile.analysisStateCap * 12,
                maxCounterfactualStates = profile.analysisStateCap,
                maxCounterfactualActionResolutions = profile.analysisStateCap * 16,
                maxObjectCounterfactuals = profile.counterfactualCap,
                randomPolicySeeds = defaultDifficultyV4Seeds(32),
            ),
        ).analyze(level)
        reasons += v4PreGate(profile, v4)
        if (reasons.isNotEmpty()) return CertificationResultV5.Rejected(reasons.distinct())
        val diagnostics = structuralAnalyzer.analyze(
            level = level,
            band = profile.difficultyBand,
            limits = StructuralAnalysisLimitsV5(
                maxStates = profile.analysisStateCap,
                maxActionResolutions = profile.analysisStateCap * 12,
                maxCounterfactualObjects = profile.counterfactualCap,
                solverStateCap = profile.solverStateCap,
            ),
            difficultyV4 = v4,
        )
        reasons += structuralGate(profile, diagnostics)
        if (reasons.isNotEmpty()) return CertificationResultV5.Rejected(reasons.distinct())

        val v3Metrics = PuzzleSearchAnalyzer(
            engine,
            PuzzleSearchConfig(
                maxExpandedStates = profile.analysisStateCap,
                maxActionResolutions = profile.analysisStateCap * 12,
                magneticCounterfactualCap = profile.counterfactualCap * 64,
            ),
        ).analyze(level)
        val v3 = DifficultyV3Scorer.score(v3Metrics)
        val qualityGate = DifficultyV3Gate.evaluate(
            v3,
            PuzzleDifficultyTarget(
                id = "d2-quality-safety",
                minimumScore = 0,
                maximumScore = 100,
                maxGuessDependentRatio = 0.0,
            ),
        )
        val quality = PuzzleQualityAnalyzerV2().analyze(v3, qualityGate)
        if (quality.status == PuzzleQualityStatusV2.REJECT) {
            return CertificationResultV5.Rejected(listOf("quality-reject") + quality.reasonCodes)
        }

        val par = requireNotNull(solved.shortestDepth)
        val twoStar = par + maxOf(2, ceil(par * 0.25).toInt())
        val raw = level.copy(metadata = null, designedSolutions = listOf(solution.map { it.arrowId }))
        val fingerprint = ContentFingerprint.of(raw)
        val certified = raw.copy(
            metadata = LevelMetadata(
                contentVersion = contentVersion,
                origin = LevelOrigin.GENERATOR_ASSISTED,
                generatorVersion = GENERATOR_VERSION_V5,
                generatorSeed = seed,
                generationProfile = profile.id,
                difficultyBand = when (profile.difficultyBand) {
                    StructuralDifficultyBandV5.TUTORIAL -> DifficultyBand.INTRO
                    StructuralDifficultyBandV5.EASY, StructuralDifficultyBandV5.MEDIUM -> DifficultyBand.DEVELOPING
                    else -> DifficultyBand.ADVANCED
                },
                certifiedSolutionLength = par,
                solutionCount = solved.solutionCount,
                solutionCountCapped = solved.solutionCountCapped,
                validFirstActionCount = solved.validFirstActions.size,
                exploredStateCount = solved.exploredStateCount,
                grading = GradingThresholds(par, twoStar),
                packId = packId,
                mechanicTags = mechanicTags(diagnostics),
                contentFingerprint = fingerprint,
                previousContentFingerprint = previousContentFingerprint,
            ),
        )
        return CertificationResultV5.Accepted(certified, diagnostics, quality)
    }

    /** Cheap rejection before object-by-object counterfactual analysis. */
    private fun v4PreGate(
        profile: GenerationProfileV5,
        v4: com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4Score,
    ): List<String> = buildList {
        val metrics = v4.metrics
        if (!v4.searchComplete || v4.searchTruncated) add("incomplete-v4-analysis")
        if (metrics.safeChoiceRatio > profile.maxSafeChoiceRatio) add("safe-choice-ratio-above-profile")
        if (metrics.randomPolicy.completionRate > profile.maxRandomSuccessRate) {
            add("random-success-rate-above-profile")
        }
        if (metrics.meaningfulFailureRate < profile.minMeaningfulFailureRate) {
            add("meaningful-failure-below-profile")
        }
        if (metrics.meaningfulSuccessfulChoiceRatio < profile.minStrategicChoiceDensity) {
            add("strategic-choice-density-below-profile")
        }
        if ((metrics.ordering.mandatoryOrderingChainDepth ?: 0) < profile.minMandatoryOrderingDepth) {
            add("ordering-depth-below-profile")
        }
        if ((metrics.consequencePersistence.maximumDepth ?: 0) < profile.minConsequenceDepth) {
            add("consequence-depth-below-profile")
        }
        if (metrics.polarity.orderingImpactCount < profile.minPolarityImpactDepth) {
            add("polarity-impact-below-profile")
        }
        metrics.strategy.permutationRedundancy?.let {
            if (it > profile.maxPermutationRedundancy) add("permutation-redundancy-above-profile")
        }
        val strategies = metrics.strategy.meaningfulStrategyFamilyCount ?: metrics.strategy.canonicalStrategyCount ?: 0
        if (strategies < profile.minCanonicalStrategies) add("strategy-diversity-below-profile")
    }

    private fun structuralGate(
        profile: GenerationProfileV5,
        diagnostics: StructuralDiagnosticsV5,
    ): List<String> = buildList {
        if (!diagnostics.searchComplete || diagnostics.truncated) add("incomplete-structural-analysis")
        if (diagnostics.interactionGraph.interactionDensity !in profile.interactionDensityRange) {
            add("interaction-density-out-of-profile")
        }
        if (diagnostics.dependencyDepth < profile.minArrowDependencyDepth) add("dependency-depth-below-profile")
        if (diagnostics.polarityImpactDepth < profile.minPolarityImpactDepth) add("polarity-impact-below-profile")
        if (diagnostics.cancellationTransitionCount < profile.minCancellationTransitions) {
            add("cancellation-transition-below-profile")
        }
        if (diagnostics.mandatoryOrderingDepth < profile.minMandatoryOrderingDepth) add("ordering-depth-below-profile")
        if (diagnostics.consequenceDepth < profile.minConsequenceDepth) add("consequence-depth-below-profile")
        if (diagnostics.objectRelevance.relevantObjectRatio < profile.minRelevantObjectRatio) {
            add("object-participation-below-profile")
        }
        if (diagnostics.safeChoiceRatio > profile.maxSafeChoiceRatio) add("safe-choice-ratio-above-profile")
        if (diagnostics.greedySolveRate > profile.maxGreedySolveRate) add("greedy-solve-rate-above-profile")
        if (diagnostics.randomSuccessRate > profile.maxRandomSuccessRate) add("random-success-rate-above-profile")
        if (diagnostics.meaningfulFailureRate < profile.minMeaningfulFailureRate) {
            add("meaningful-failure-below-profile")
        }
        if (diagnostics.recoveryPressure < profile.minRecoveryPressure) {
            add("recovery-pressure-below-profile")
        }
        if (diagnostics.strategicChoiceDensity < profile.minStrategicChoiceDensity) {
            add("strategic-choice-density-below-profile")
        }
        if (diagnostics.exposureRevealCount < profile.minExposureEvents) add("exposure-below-profile")
        if (diagnostics.alternativePathCount < profile.minAlternativePathCount) add("alternative-path-below-profile")
        if ((diagnostics.commutationQuotient ?: 0) < profile.minCanonicalStrategies) {
            add("strategy-diversity-below-profile")
        }
        diagnostics.permutationRedundancy?.let {
            if (it > profile.maxPermutationRedundancy) add("permutation-redundancy-above-profile")
        }
        profile.spatialDensityProfile?.let { addAll(SpatialDensityGateV5.evaluateMeaningful(diagnostics, it)) }
    }

    private fun mechanicTags(diagnostics: StructuralDiagnosticsV5): List<String> = buildList {
        if (diagnostics.magneticRelationshipCount > 0) add("MAGNET_CONTROL")
        if (diagnostics.polarityDependentDecisionCount > 0) add("POLARITY_DEPENDENCY")
        if (diagnostics.lineOfSightInteractionCount > 0) add("OCCLUSION")
        if (diagnostics.magnetCancellationCount > 0) add("CANCELLATION")
        if (diagnostics.orderingConstraintCount > 0) add("ORDER_DEPENDENCY")
        if (diagnostics.exposureRevealCount > 0) add("EXPOSURE_REVEAL")
        if (diagnostics.wallCount > 0) add("WALLS")
    }.ifEmpty { listOf("MOVEMENT") }
}

/**
 * Deterministic from-scratch candidate generator. It biases placement toward aligned magnetic
 * corridors, blockers, cancellation, and reveal relationships, then delegates all acceptance to
 * [CertificationPipelineV5].
 */
class LevelGeneratorV5(
    private val certification: CertificationPipelineV5 = CertificationPipelineV5(),
    private val engine: GameEngine = DefaultGameEngine(),
    private val solutionFirstConstructor: SolutionFirstConstructorV5 = SolutionFirstConstructorV5(engine),
) {
    fun generate(request: GenerationRequestV5): GenerationResultV5 {
        require(request.stableId.isNotBlank() && request.title.isNotBlank())
        require(request.sequenceNumber > 0 && request.maxAttempts > 0)
        val rejected = linkedMapOf<String, Int>()
        var telemetry = GenerationTelemetryV5()
        repeat(request.maxAttempts) attemptLoop@ { attempt ->
            val attemptSeed = request.seed + attempt * ATTEMPT_GAMMA
            telemetry = telemetry.copy(candidateAttempts = telemetry.candidateAttempts + 1)
            val constructed = if (request.profile.constructionStrategy == ConstructionStrategyV5.SOLUTION_FIRST) {
                runCatching { solutionFirstConstructor.construct(request, attemptSeed) }.getOrElse {
                    rejected["construction-failed"] = (rejected["construction-failed"] ?: 0) + 1
                    return@attemptLoop
                }.also { telemetry = telemetry.copy(successfulConstructions = telemetry.successfulConstructions + 1) }
            } else null
            var raw = constructed?.level ?: generatePlacementFirstRaw(request, attemptSeed)
            var result = certification.certify(raw, request.profile, attemptSeed, request.packId)
            if (result is CertificationResultV5.Rejected && constructed != null) {
                var repairBase = requireNotNull(constructed)
                for (repairIndex in 0 until request.profile.repairAttemptCap) {
                    val rejectedResult = result as? CertificationResultV5.Rejected ?: break
                    telemetry = telemetry.copy(repairAttempts = telemetry.repairAttempts + 1)
                    val repair = solutionFirstConstructor.repair(
                        repairBase,
                        rejectedResult.reasons,
                        attemptSeed + (repairIndex + 1) * REPAIR_GAMMA,
                        request.profile.solverStateCap,
                    )
                    if (repair.rolledBack) {
                        telemetry = telemetry.copy(repairRollbacks = telemetry.repairRollbacks + 1)
                        break
                    }
                    if (!repair.applied) break
                    raw = repair.level
                    repairBase = repairBase.copy(level = raw)
                    result = certification.certify(raw, request.profile, attemptSeed, request.packId)
                    if (result is CertificationResultV5.Accepted) break
                }
            }
            when (result) {
                is CertificationResultV5.Accepted -> return GenerationResultV5.Generated(
                    result.level,
                    result.diagnostics,
                    result.quality,
                    attempt + 1,
                    rejected.toMap(),
                    telemetry.copy(certifiedCandidates = telemetry.certifiedCandidates + 1),
                )
                is CertificationResultV5.Rejected -> result.reasons.forEach { reason ->
                    rejected[reason] = (rejected[reason] ?: 0) + 1
                    telemetry = telemetry.recordRejection(reason)
                }
            }
        }
        return GenerationResultV5.Exhausted(request.maxAttempts, rejected.toMap(), telemetry)
    }

    fun generateRaw(request: GenerationRequestV5, seed: Long = request.seed): LevelDefinition {
        if (request.profile.constructionStrategy == ConstructionStrategyV5.SOLUTION_FIRST) {
            return solutionFirstConstructor.construct(request, seed).level
        }
        return generatePlacementFirstRaw(request, seed)
    }

    private fun generatePlacementFirstRaw(request: GenerationRequestV5, seed: Long): LevelDefinition {
        val random = SeededRandom(seed)
        val profile = request.profile
        val size = profile.gridSizes[random.nextInt(profile.gridSizes.size)]
        val cells = allCells(size).shuffled(random).toMutableList()
        val counts = profile.spatialDensityProfile?.let { chooseSpatialObjectCounts(random, size, it) }
        val targetDensity = profile.objectDensityRange.minimum +
            nextUnit(random) * (profile.objectDensityRange.maximum - profile.objectDensityRange.minimum)
        val targetObjects = (size * size * targetDensity).roundToInt()
            .coerceAtLeast(profile.minArrows + profile.minMagnets + profile.minWalls)
            .coerceAtMost(profile.maxArrows + profile.maxMagnets + profile.maxWalls)
        val magnetCount = counts?.magnets ?: randomBetween(random, profile.minMagnets, profile.maxMagnets)
        val arrowCount = counts?.arrows ?: randomBetween(random, profile.minArrows, profile.maxArrows)
        val wallCount = counts?.walls
            ?: (targetObjects - magnetCount - arrowCount).coerceIn(profile.minWalls, profile.maxWalls)

        val desiredTrapCount = when {
            profile.id.endsWith("master") -> 5
            profile.id.endsWith("expert") -> 4
            profile.id.endsWith("very-hard") -> 3
            profile.difficultyBand.rank >= StructuralDifficultyBandV5.HARD.rank -> 2
            else -> 0
        }.coerceAtMost(minOf(magnetCount, arrowCount / 3, wallCount))
        val polarityTraps = if (
            profile.spatialDensityProfile?.rejectDenseButTrivial == true &&
            size >= 6 && desiredTrapCount > 0
        ) polarityTraps(size, desiredTrapCount, random) else emptyList()
        val reserved = polarityTraps.flatMapTo(mutableSetOf()) { it.reservedCells }
        polarityTraps.forEach { trap ->
            cells.removeAll(trap.occupiedCells)
        }
        val magnetPositions = mutableListOf<Position>()
        magnetPositions += polarityTraps.map { it.magnet }
        repeat(magnetCount - magnetPositions.size) {
            val selected = cells.firstOrNull { cell ->
                cell !in reserved && polarityTraps.none { trap ->
                    cell.row == trap.revealFirst.row || cell.column == trap.revealFirst.column ||
                        cell.row == trap.mustMoveFirst.row || cell.column == trap.mustMoveFirst.column ||
                        cell.row == trap.safeMoveLast.row || cell.column == trap.safeMoveLast.column
                }
            } ?: cells.first()
            cells.remove(selected)
            magnetPositions += selected
        }
        val arrowPositions = mutableListOf<Position>()
        polarityTraps.forEach {
            arrowPositions += it.revealFirst
            arrowPositions += it.safeMoveLast
            arrowPositions += it.mustMoveFirst
        }
        repeat(arrowCount - arrowPositions.size) { localIndex ->
            val index = arrowPositions.size
            val available = cells.filter { cell ->
                cell !in reserved && polarityTraps.none { trap ->
                    cell.row == trap.magnet.row || cell.column == trap.magnet.column
                }
            }.ifEmpty { cells }
            val relationshipMagnets = magnetPositions.drop(polarityTraps.size)
                .ifEmpty { magnetPositions }
            val requiresLongRange = localIndex <
                (profile.spatialDensityProfile?.minimumLongRangeMagneticRelationships ?: 0)
            val aligned = if (relationshipMagnets.isNotEmpty() && (requiresLongRange || random.nextInt(100) < 82)) {
                chooseAlignedFreeCell(
                    random,
                    relationshipMagnets[index % relationshipMagnets.size],
                    available,
                    profile,
                    minimumDistance = profile.spatialDensityProfile?.let { spatial ->
                        if (requiresLongRange) spatial.longRangeDistance else null
                    },
                )
            } else null
            val selected = aligned ?: available.first()
            cells.remove(selected)
            arrowPositions += selected
        }

        profile.spatialDensityProfile?.let { spatial ->
            placeArrowBlockers(
                random = random,
                required = spatial.minimumArrowBlockerRelationships,
                arrows = arrowPositions,
                magnets = magnetPositions,
                free = cells,
                protectedArrowCount = polarityTraps.size * 3,
                forbidden = reserved,
            )
        }

        // Harder profiles deliberately place equal-distance visible magnets around an arrow.
        val cancellationMagnetIndex = polarityTraps.size
        val cancellationArrowIndex = polarityTraps.size * 3
        if (profile.minCancellationTransitions > 0 && arrowPositions.size > cancellationArrowIndex &&
            magnetPositions.size >= cancellationMagnetIndex + 2
        ) {
            val center = arrowPositions[cancellationArrowIndex]
            cancellationPair(center, size, arrowPositions + magnetPositions, random, minimumDistance = 2)?.let { (first, second) ->
                val occupied = (arrowPositions + magnetPositions + polarityTraps.map { it.trapWall }).toMutableSet()
                if (first !in occupied && second !in occupied && first !in reserved && second !in reserved) {
                    cells += magnetPositions[cancellationMagnetIndex]
                    cells += magnetPositions[cancellationMagnetIndex + 1]
                    magnetPositions[cancellationMagnetIndex] = first
                    magnetPositions[cancellationMagnetIndex + 1] = second
                    cells.remove(first)
                    cells.remove(second)
                    // A removable arrow hides one side initially; its removal reveals equal-nearest
                    // cancellation for the center arrow. Certification decides whether this is
                    // strategically relevant and solvable.
                    if (arrowPositions.size > cancellationArrowIndex + 1) {
                        val blocker = center.move(Direction.between(center, second))
                        if (blocker != second && blocker in cells && blocker !in reserved) {
                            cells += arrowPositions[cancellationArrowIndex + 1]
                            arrowPositions[cancellationArrowIndex + 1] = blocker
                            cells.remove(blocker)
                        }
                    }
                }
            }
        }

        val occupiedBeforeWalls = (arrowPositions + magnetPositions + polarityTraps.map { it.trapWall }).toSet()
        val normalizedFreeCells = cells.distinct().filterNot { it in occupiedBeforeWalls }
        cells.clear()
        cells.addAll(normalizedFreeCells)

        val printedDirections = arrowPositions.map { position ->
            polarityTraps.firstOrNull { it.mustMoveFirst == position }?.let { trap ->
                trap.mustEscapeDirection
            } ?: chooseDirection(random, position, size)
        }
        // Only authored trap corridors are reserved. Other routes remain eligible for purposeful
        // walls; chooseMeaningfulWallCell protects one production-engine-verified solution while
        // requiring each selected wall to change another reachable action.
        val protectedEscapeCells = reserved
        val provisionalArrows = arrowPositions.mapIndexed { index, position ->
            Arrow("a${index + 1}", position, printedDirections[index])
        }
        val magnetPolarities = magnetPositions.mapIndexed { index, _ ->
            if (index < polarityTraps.size) Polarity.PULL
            else if (random.nextInt(100) < 75) Polarity.PULL else Polarity.PUSH
        }
        val magnets = magnetPositions.mapIndexed { index, position ->
            Magnet("m${index + 1}", position, magnetPolarities[index])
        }
        val walls = polarityTraps.mapTo(mutableListOf()) { it.trapWall }
        val arrows = chooseSolvableArrowDirections(
            request = request,
            size = size,
            arrows = provisionalArrows,
            magnets = magnets,
            walls = walls,
        ) ?: provisionalArrows
        repeat(wallCount - walls.size) {
            val roleEligibleCells = cells.filterNot { it in protectedEscapeCells }.ifEmpty { cells }
            val meaningfulRole = if (profile.spatialDensityProfile?.rejectDenseButTrivial == true) {
                chooseMeaningfulWallCell(
                    request = request,
                    size = size,
                    arrows = arrows,
                    magnets = magnets,
                    existingWalls = walls,
                    candidates = roleEligibleCells,
                )
            } else null
            val safeFiller = if (meaningfulRole == null &&
                profile.spatialDensityProfile?.rejectDenseButTrivial == true
            ) {
                chooseSolutionPreservingWallCell(
                    request = request,
                    size = size,
                    arrows = arrows,
                    magnets = magnets,
                    existingWalls = walls,
                    candidates = roleEligibleCells,
                )
            } else null
            val corridor = if (random.nextInt(100) < 72) {
                chooseCorridorCell(
                    random,
                    arrowPositions,
                    magnetPositions,
                    roleEligibleCells,
                )
            } else null
            val routeRole = chooseRouteRoleCell(random, arrowPositions, roleEligibleCells)
            val selected = meaningfulRole ?: safeFiller ?: corridor ?: routeRole
                ?: roleEligibleCells[random.nextInt(roleEligibleCells.size)]
            cells.remove(selected)
            walls += selected
        }
        return LevelDefinition(
            id = request.stableId,
            number = request.sequenceNumber,
            title = request.title,
            width = size,
            height = size,
            arrows = arrows,
            magnets = magnets,
            walls = walls.map(::Wall),
            designedSolutions = listOf(arrows.map { it.id }),
            metadata = null,
        )
    }

    private fun chooseAlignedFreeCell(
        random: SeededRandom,
        magnet: Position,
        free: List<Position>,
        profile: GenerationProfileV5,
        minimumDistance: Int? = null,
    ): Position? {
        val aligned = free.filter { it.row == magnet.row || it.column == magnet.column }
        if (aligned.isEmpty()) return null
        val desired = when (profile.magneticDistanceProfile) {
            MagneticDistanceProfileV5.SHORT -> 1..2
            MagneticDistanceProfileV5.MEDIUM -> 2..4
            MagneticDistanceProfileV5.LONG -> 4..9
            MagneticDistanceProfileV5.MIXED -> 1..9
        }
        val minimumFiltered = minimumDistance?.let { minimum ->
            aligned.filter { distance(it, magnet) >= minimum }
        }.orEmpty()
        val preferred = minimumFiltered.ifEmpty {
            aligned.filter { distance(it, magnet) in desired }.ifEmpty { aligned }
        }
        return preferred[random.nextInt(preferred.size)]
    }

    private fun chooseSpatialObjectCounts(
        random: SeededRandom,
        size: Int,
        spatial: SpatialDensityProfileV5,
    ): ObjectCountsV5 {
        val cells = size * size
        val jitter = (nextUnit(random) - 0.5) *
            (spatial.maximumOccupancyRatio - spatial.minimumOccupancyRatio) * 0.35
        val desiredTotal = (cells * (spatial.targetOccupancyRatio + jitter)).roundToInt()
            .coerceIn(
                ceil(cells * spatial.minimumOccupancyRatio).toInt(),
                kotlin.math.floor(cells * spatial.maximumOccupancyRatio).toInt(),
            )
        val options = buildList {
            for (arrows in spatial.arrowCount.minimum..spatial.arrowCount.maximum) {
                for (magnets in spatial.magnetCount.minimum..spatial.magnetCount.maximum) {
                    for (walls in spatial.wallCount.minimum..spatial.wallCount.maximum) {
                        val total = arrows + magnets + walls
                        val occupancy = total.toDouble() / cells
                        if (total <= cells && occupancy in
                            spatial.minimumOccupancyRatio..spatial.maximumOccupancyRatio
                        ) add(ObjectCountsV5(arrows, magnets, walls))
                    }
                }
            }
        }
        require(options.isNotEmpty()) { "No valid object-count combination for ${size}x$size spatial profile" }
        val bestDistance = options.minOf { kotlin.math.abs(it.total - desiredTotal) }
        val nearest = options.filter { kotlin.math.abs(it.total - desiredTotal) == bestDistance }
        val ranked = nearest.sortedWith(
            compareBy<ObjectCountsV5> { option ->
                val total = option.total.toDouble()
                kotlin.math.abs(option.arrows / total - spatial.targetArrowShare) +
                    kotlin.math.abs(option.magnets / total - spatial.targetMagnetShare) +
                    kotlin.math.abs(option.walls / total - spatial.targetWallShare)
            }.thenByDescending { it.arrows }
                .thenByDescending { it.magnets }
                .thenBy { it.walls },
        )
        val bestShareDistance = ranked.first().let { option ->
            val total = option.total.toDouble()
            kotlin.math.abs(option.arrows / total - spatial.targetArrowShare) +
                kotlin.math.abs(option.magnets / total - spatial.targetMagnetShare) +
                kotlin.math.abs(option.walls / total - spatial.targetWallShare)
        }
        val balanced = ranked.takeWhile { option ->
            val total = option.total.toDouble()
            val distance = kotlin.math.abs(option.arrows / total - spatial.targetArrowShare) +
                kotlin.math.abs(option.magnets / total - spatial.targetMagnetShare) +
                kotlin.math.abs(option.walls / total - spatial.targetWallShare)
            distance <= bestShareDistance + 0.035
        }.take(4)
        return balanced[random.nextInt(balanced.size)]
    }

    private fun placeArrowBlockers(
        random: SeededRandom,
        required: Int,
        arrows: MutableList<Position>,
        magnets: List<Position>,
        free: MutableList<Position>,
        protectedArrowCount: Int = 0,
        forbidden: Set<Position> = emptySet(),
    ) {
        if (required <= 0 || arrows.size - protectedArrowCount < 2 || magnets.isEmpty()) return
        val mutableIndexes = protectedArrowCount until arrows.size
        var placed = 0
        var attempts = 0
        while (placed < required && attempts < arrows.size * magnets.size * 20) {
            attempts += 1
            val targetIndex = mutableIndexes.first + random.nextInt(mutableIndexes.count())
            val target = arrows[targetIndex]
            val alignedMagnets = magnets.filter { it.row == target.row || it.column == target.column }
            if (alignedMagnets.isEmpty()) continue
            val magnet = alignedMagnets[random.nextInt(alignedMagnets.size)]
            val betweenCells = free.filter { it !in forbidden && between(target, it, magnet) }
            if (betweenCells.isEmpty()) continue
            val blockerIndex = mutableIndexes.first +
                ((targetIndex - mutableIndexes.first + 1 + placed) % mutableIndexes.count())
            if (blockerIndex == targetIndex) continue
            val selected = betweenCells[random.nextInt(betweenCells.size)]
            free += arrows[blockerIndex]
            arrows[blockerIndex] = selected
            free.remove(selected)
            placed += 1
        }
    }

    private fun polarityTraps(size: Int, count: Int, random: SeededRandom): List<PolarityTrapV5> {
        val options = buildList {
            val trapDistance = minOf(4, size - 2)
            allCells(size).forEach { magnet ->
                Direction.entries.forEach { trapDirection ->
                    Direction.entries.filter {
                        it != trapDirection && it != trapDirection.opposite()
                    }.forEach { safeDirection ->
                        val revealFirst = move(magnet, trapDirection, 1)
                        val mustMoveFirst = move(magnet, trapDirection, trapDistance)
                        val trapWall = move(magnet, trapDirection, trapDistance + 1)
                        val safeMoveLast = move(magnet, safeDirection, 2)
                        val mustEscapeDirection = safeDirection.opposite()
                        val positions = listOf(magnet, revealFirst, mustMoveFirst, trapWall, safeMoveLast)
                        if (positions.all { it.row in 1..size && it.column in 1..size } &&
                            positions.distinct().size == positions.size
                        ) {
                            val reserved = buildSet {
                                for (distance in 1 until trapDistance) {
                                    add(move(magnet, trapDirection, distance))
                                }
                                add(move(magnet, safeDirection, 1))
                                addAll(rayToBoundary(safeMoveLast, safeDirection, size))
                                addAll(rayToBoundary(mustMoveFirst, mustEscapeDirection, size))
                            }
                            add(
                                PolarityTrapV5(
                                    magnet = magnet,
                                    revealFirst = revealFirst,
                                    mustMoveFirst = mustMoveFirst,
                                    safeMoveLast = safeMoveLast,
                                    trapWall = trapWall,
                                    mustEscapeDirection = mustEscapeDirection,
                                    reservedCells = reserved,
                                ),
                            )
                        }
                    }
                }
            }
        }.shuffled(random)
        val selected = mutableListOf<PolarityTrapV5>()
        options.forEach { candidate ->
            if (selected.size >= count) return@forEach
            val candidateOccupied = candidate.occupiedCells
            val compatible = selected.all { existing ->
                candidateOccupied.intersect(existing.occupiedCells).isEmpty() &&
                    candidateOccupied.intersect(existing.reservedCells).isEmpty() &&
                    existing.occupiedCells.intersect(candidate.reservedCells).isEmpty() &&
                    !aligned(candidate.magnet, existing.mustMoveFirst) &&
                    !aligned(candidate.magnet, existing.revealFirst) &&
                    !aligned(candidate.magnet, existing.safeMoveLast) &&
                    !aligned(existing.magnet, candidate.mustMoveFirst) &&
                    !aligned(existing.magnet, candidate.revealFirst) &&
                    !aligned(existing.magnet, candidate.safeMoveLast)
            }
            if (compatible) selected += candidate
        }
        return selected
    }

    private fun move(position: Position, direction: Direction, distance: Int): Position {
        var moved = position
        repeat(distance) { moved = moved.move(direction) }
        return moved
    }

    private fun aligned(first: Position, second: Position): Boolean =
        first.row == second.row || first.column == second.column

    private fun chooseCorridorCell(
        random: SeededRandom,
        arrows: List<Position>,
        magnets: List<Position>,
        free: List<Position>,
    ): Position? {
        val candidates = free.filter { cell ->
            arrows.any { arrow -> magnets.any { magnet -> between(arrow, cell, magnet) } }
        }
        return candidates.takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] }
    }

    private fun chooseRouteRoleCell(
        random: SeededRandom,
        arrows: List<Position>,
        free: List<Position>,
    ): Position? {
        val candidates = free.filter { cell ->
            arrows.any { arrow -> arrow.row == cell.row || arrow.column == cell.column }
        }
        return candidates.takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] }
    }

    private fun chooseMeaningfulWallCell(
        request: GenerationRequestV5,
        size: Int,
        arrows: List<Arrow>,
        magnets: List<Magnet>,
        existingWalls: List<Position>,
        candidates: List<Position>,
    ): Position? {
        if (candidates.isEmpty()) return null
        val level = LevelDefinition(
            id = request.stableId,
            number = request.sequenceNumber,
            title = request.title,
            width = size,
            height = size,
            arrows = arrows,
            magnets = magnets,
            walls = existingWalls.map(::Wall),
            designedSolutions = listOf(arrows.map { it.id }),
        )
        val canonicalStates = findCanonicalStates(level) ?: return null
        return candidates.asSequence().map { candidate ->
            var score = 0
            var blocksCanonicalProgress = false
            canonicalStates.forEach { (state, canonicalActionId) ->
                val withCandidate = state.copy(walls = state.walls + Wall(candidate))
                state.arrows.forEach { arrow ->
                    val baseline = engine.resolve(state, PlayerAction(arrow.id))
                    val changed = engine.resolve(withCandidate, PlayerAction(arrow.id))
                    if (wallRoleSignature(baseline) == wallRoleSignature(changed)) return@forEach
                    if (arrow.id == canonicalActionId &&
                        wallRoleSignature(baseline) != wallRoleSignature(changed)
                    ) {
                        blocksCanonicalProgress = true
                    }
                    val baselineAlreadyWallBlocked = baseline.collisionTarget?.type?.name == "WALL"
                    if (!baselineAlreadyWallBlocked) {
                        score += if (baseline.success != changed.success) 3 else 1
                        if (baseline.controllingMagnetId != changed.controllingMagnetId) score += 2
                    }
                }
            }
            candidate to if (blocksCanonicalProgress) -1 else score
        }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Position, Int>> { it.second }
                .thenBy { it.first.row }.thenBy { it.first.column })
            .firstOrNull()?.first
    }

    /**
     * Assigns printed directions against the actual production engine so the pre-filler board has
     * at least one verified removal order. Magnetic control still owns the effective direction;
     * this only selects a printed direction when an arrow has no unique controller.
     */
    private fun chooseSolvableArrowDirections(
        request: GenerationRequestV5,
        size: Int,
        arrows: List<Arrow>,
        magnets: List<Magnet>,
        walls: List<Position>,
    ): List<Arrow>? {
        val initial = LevelDefinition(
            id = request.stableId,
            number = request.sequenceNumber,
            title = request.title,
            width = size,
            height = size,
            arrows = arrows,
            magnets = magnets,
            walls = walls.map(::Wall),
            designedSolutions = listOf(arrows.map { it.id }),
        ).initialState()
        val dead = mutableSetOf<StateKey>()
        var expanded = 0

        fun find(state: BoardState): Map<String, Direction>? {
            if (state.arrows.isEmpty()) return emptyMap()
            if (expanded >= DIRECTION_ASSIGNMENT_STATE_CAP) return null
            val key = StateKey.from(state)
            if (key in dead) return null
            expanded += 1
            state.arrows.sortedBy { it.id }.forEach { arrow ->
                val baseline = engine.resolve(state, PlayerAction(arrow.id))
                val directions = if (baseline.controllingMagnetId == null) {
                    Direction.entries
                } else {
                    listOf(arrow.printedDirection)
                }
                directions.forEach { direction ->
                    val directedState = if (direction == arrow.printedDirection) state else state.copy(
                        arrows = state.arrows.map { candidate ->
                            if (candidate.id == arrow.id) candidate.copy(printedDirection = direction) else candidate
                        },
                    )
                    val result = engine.resolve(directedState, PlayerAction(arrow.id))
                    if (!result.success) return@forEach
                    find(result.resultingState)?.let { suffix ->
                        return suffix + (arrow.id to direction)
                    }
                }
            }
            dead += key
            return null
        }

        val directions = find(initial) ?: return null
        return arrows.map { arrow -> arrow.copy(printedDirection = directions[arrow.id] ?: arrow.printedDirection) }
    }

    private fun chooseSolutionPreservingWallCell(
        request: GenerationRequestV5,
        size: Int,
        arrows: List<Arrow>,
        magnets: List<Magnet>,
        existingWalls: List<Position>,
        candidates: List<Position>,
    ): Position? {
        if (candidates.isEmpty()) return null
        val level = LevelDefinition(
            id = request.stableId,
            number = request.sequenceNumber,
            title = request.title,
            width = size,
            height = size,
            arrows = arrows,
            magnets = magnets,
            walls = existingWalls.map(::Wall),
            designedSolutions = listOf(arrows.map { it.id }),
        )
        val canonicalStates = findCanonicalStates(level) ?: return null
        return candidates.firstOrNull { candidate ->
            canonicalStates.all { (state, canonicalActionId) ->
                canonicalActionId == null || run {
                    val baseline = engine.resolve(state, PlayerAction(canonicalActionId))
                    val changed = engine.resolve(
                        state.copy(walls = state.walls + Wall(candidate)),
                        PlayerAction(canonicalActionId),
                    )
                    wallRoleSignature(baseline) == wallRoleSignature(changed)
                }
            }
        }
    }

    private fun findCanonicalStates(level: LevelDefinition): List<Pair<BoardState, String?>>? {
        val dead = mutableSetOf<StateKey>()
        var expanded = 0

        fun find(state: BoardState): List<Pair<BoardState, String?>>? {
            if (state.arrows.isEmpty()) return listOf(state to null)
            if (expanded >= 10_000) return null
            val key = StateKey.from(state)
            if (key in dead) return null
            expanded += 1
            state.arrows.sortedBy { it.id }.forEach { arrow ->
                val result = engine.resolve(state, PlayerAction(arrow.id))
                if (!result.success) return@forEach
                find(result.resultingState)?.let { suffix ->
                    return listOf(state to arrow.id) + suffix
                }
            }
            dead += key
            return null
        }

        return find(level.initialState())
    }

    private fun wallRoleSignature(result: ResolutionResult): String = listOf(
        result.success,
        result.controllingMagnetId,
        result.effectiveDirection,
        result.terminalEvent::class.simpleName,
        result.collisionTarget?.type,
        result.collisionTarget?.entityId,
        result.collisionTarget?.position,
    ).joinToString("|")

    private fun cancellationPair(
        center: Position,
        size: Int,
        occupied: List<Position>,
        random: SeededRandom,
        minimumDistance: Int = 1,
    ): Pair<Position, Position>? {
        val options = mutableListOf<Pair<Position, Position>>()
        for (distance in minimumDistance..size) {
            listOf(
                Position(center.row, center.column - distance) to Position(center.row, center.column + distance),
                Position(center.row - distance, center.column) to Position(center.row + distance, center.column),
            ).forEach { pair ->
                if (pair.first.row in 1..size && pair.first.column in 1..size &&
                    pair.second.row in 1..size && pair.second.column in 1..size &&
                    pair.first !in occupied && pair.second !in occupied
                ) options += pair
            }
        }
        return options.takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] }
    }

    private fun chooseDirection(random: SeededRandom, position: Position, size: Int): Direction {
        val outward = buildList {
            if (position.row <= size / 2) add(Direction.NORTH) else add(Direction.SOUTH)
            if (position.column <= size / 2) add(Direction.WEST) else add(Direction.EAST)
        }
        return if (random.nextInt(100) < 55) outward[random.nextInt(outward.size)]
        else Direction.entries[random.nextInt(Direction.entries.size)]
    }

    private fun rayToBoundary(position: Position, direction: Direction, size: Int): List<Position> = buildList {
        var current = position.move(direction)
        while (current.row in 1..size && current.column in 1..size) {
            add(current)
            current = current.move(direction)
        }
    }

    private fun allCells(size: Int): List<Position> =
        (1..size).flatMap { row -> (1..size).map { column -> Position(row, column) } }

    private fun <T> List<T>.shuffled(random: SeededRandom): List<T> {
        val values = toMutableList()
        for (index in values.lastIndex downTo 1) {
            val other = random.nextInt(index + 1)
            val value = values[index]
            values[index] = values[other]
            values[other] = value
        }
        return values
    }

    private fun randomBetween(random: SeededRandom, minimum: Int, maximum: Int): Int =
        minimum + random.nextInt(maximum - minimum + 1)

    private fun nextUnit(random: SeededRandom): Double =
        random.nextLong().ushr(11).toDouble() / 9_007_199_254_740_992.0

    private fun distance(a: Position, b: Position): Int =
        kotlin.math.abs(a.row - b.row) + kotlin.math.abs(a.column - b.column)

    private fun between(start: Position, candidate: Position, end: Position): Boolean = when {
        start.row == end.row && candidate.row == start.row ->
            candidate.column in (minOf(start.column, end.column) + 1)..<maxOf(start.column, end.column)
        start.column == end.column && candidate.column == start.column ->
            candidate.row in (minOf(start.row, end.row) + 1)..<maxOf(start.row, end.row)
        else -> false
    }

    private companion object {
        const val ATTEMPT_GAMMA = -7046029254386353131L
        const val REPAIR_GAMMA = -3335678366873096957L
        const val DIRECTION_ASSIGNMENT_STATE_CAP = 50_000
    }

    private data class ObjectCountsV5(val arrows: Int, val magnets: Int, val walls: Int) {
        val total: Int get() = arrows + magnets + walls
    }

    private data class PolarityTrapV5(
        val magnet: Position,
        val revealFirst: Position,
        val mustMoveFirst: Position,
        val safeMoveLast: Position,
        val trapWall: Position,
        val mustEscapeDirection: Direction,
        val reservedCells: Set<Position>,
    ) {
        val occupiedCells: Set<Position>
            get() = setOf(magnet, revealFirst, mustMoveFirst, safeMoveLast, trapWall)
    }
}

private fun GenerationTelemetryV5.recordRejection(reason: String): GenerationTelemetryV5 = when {
    reason == "unsolvable" || "solver" in reason || "solution-replay" in reason ->
        copy(solverFailures = solverFailures + 1)
    reason == "incomplete-v4-analysis" -> copy(v4Truncations = v4Truncations + 1)
    "ordering" in reason -> copy(orderingFailures = orderingFailures + 1)
    "wall" in reason -> copy(wallParticipationFailures = wallParticipationFailures + 1)
    "relevance" in reason || "participation" in reason -> copy(relevanceFailures = relevanceFailures + 1)
    "safe-choice" in reason -> copy(safeChoiceFailures = safeChoiceFailures + 1)
    "commut" in reason || "permutation" in reason -> copy(commutationFailures = commutationFailures + 1)
    "consequence" in reason -> copy(consequenceFailures = consequenceFailures + 1)
    "duplicate" in reason || "similar" in reason -> copy(duplicateFailures = duplicateFailures + 1)
    "difficulty" in reason -> copy(difficultyFailures = difficultyFailures + 1)
    else -> this
}
