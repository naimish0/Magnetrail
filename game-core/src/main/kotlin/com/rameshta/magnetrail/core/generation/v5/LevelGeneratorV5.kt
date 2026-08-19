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
import com.rameshta.magnetrail.core.generation.SeededRandom
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
    ) : GenerationResultV5

    data class Exhausted(
        val attemptsUsed: Int,
        val rejectedReasons: Map<String, Int>,
    ) : GenerationResultV5
}

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
) {
    fun generate(request: GenerationRequestV5): GenerationResultV5 {
        require(request.stableId.isNotBlank() && request.title.isNotBlank())
        require(request.sequenceNumber > 0 && request.maxAttempts > 0)
        val rejected = linkedMapOf<String, Int>()
        repeat(request.maxAttempts) { attempt ->
            val attemptSeed = request.seed + attempt * ATTEMPT_GAMMA
            val raw = generateRaw(request, attemptSeed)
            when (val result = certification.certify(raw, request.profile, attemptSeed, request.packId)) {
                is CertificationResultV5.Accepted -> return GenerationResultV5.Generated(
                    result.level,
                    result.diagnostics,
                    result.quality,
                    attempt + 1,
                    rejected.toMap(),
                )
                is CertificationResultV5.Rejected -> result.reasons.forEach { reason ->
                    rejected[reason] = (rejected[reason] ?: 0) + 1
                }
            }
        }
        return GenerationResultV5.Exhausted(request.maxAttempts, rejected.toMap())
    }

    fun generateRaw(request: GenerationRequestV5, seed: Long = request.seed): LevelDefinition {
        val random = SeededRandom(seed)
        val profile = request.profile
        val size = profile.gridSizes[random.nextInt(profile.gridSizes.size)]
        val cells = allCells(size).shuffled(random).toMutableList()
        val targetDensity = profile.objectDensityRange.minimum +
            nextUnit(random) * (profile.objectDensityRange.maximum - profile.objectDensityRange.minimum)
        val targetObjects = (size * size * targetDensity).roundToInt()
            .coerceAtLeast(profile.minArrows + profile.minMagnets + profile.minWalls)
            .coerceAtMost(profile.maxArrows + profile.maxMagnets + profile.maxWalls)
        val magnetCount = randomBetween(random, profile.minMagnets, profile.maxMagnets)
        val arrowCount = randomBetween(random, profile.minArrows, profile.maxArrows)
        val wallCount = (targetObjects - magnetCount - arrowCount).coerceIn(profile.minWalls, profile.maxWalls)

        val magnetPositions = mutableListOf<Position>()
        repeat(magnetCount) { magnetPositions += cells.removeAt(0) }
        val arrowPositions = mutableListOf<Position>()
        repeat(arrowCount) { index ->
            val aligned = if (magnetPositions.isNotEmpty() && random.nextInt(100) < 82) {
                chooseAlignedFreeCell(random, magnetPositions[index % magnetPositions.size], cells, profile)
            } else null
            val selected = aligned ?: cells.first()
            cells.remove(selected)
            arrowPositions += selected
        }

        // Harder profiles deliberately place equal-distance visible magnets around an arrow.
        if (profile.minCancellationTransitions > 0 && arrowPositions.isNotEmpty() && magnetPositions.size >= 2) {
            val center = arrowPositions.first()
            cancellationPair(center, size, arrowPositions + magnetPositions, random, minimumDistance = 2)?.let { (first, second) ->
                val occupied = (arrowPositions + magnetPositions).toMutableSet()
                if (first !in occupied && second !in occupied) {
                    cells += magnetPositions[0]
                    cells += magnetPositions[1]
                    magnetPositions[0] = first
                    magnetPositions[1] = second
                    cells.remove(first)
                    cells.remove(second)
                    // A removable arrow hides one side initially; its removal reveals equal-nearest
                    // cancellation for the center arrow. Certification decides whether this is
                    // strategically relevant and solvable.
                    if (arrowPositions.size >= 2) {
                        val blocker = center.move(Direction.between(center, second))
                        if (blocker != second && blocker in cells) {
                            cells += arrowPositions[1]
                            arrowPositions[1] = blocker
                            cells.remove(blocker)
                        }
                    }
                }
            }
        }

        val walls = mutableListOf<Position>()
        repeat(wallCount) {
            val corridor = if (random.nextInt(100) < 72) {
                chooseCorridorCell(random, arrowPositions, magnetPositions, cells)
            } else null
            val selected = corridor ?: cells.first()
            cells.remove(selected)
            walls += selected
        }
        val arrows = arrowPositions.mapIndexed { index, position ->
            Arrow("a${index + 1}", position, chooseDirection(random, position, size))
        }
        val magnets = magnetPositions.mapIndexed { index, position ->
            Magnet(
                "m${index + 1}",
                position,
                if ((index + random.nextInt(2)) % 2 == 0) Polarity.PULL else Polarity.PUSH,
            )
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
    ): Position? {
        val aligned = free.filter { it.row == magnet.row || it.column == magnet.column }
        if (aligned.isEmpty()) return null
        val desired = when (profile.magneticDistanceProfile) {
            MagneticDistanceProfileV5.SHORT -> 1..2
            MagneticDistanceProfileV5.MEDIUM -> 2..4
            MagneticDistanceProfileV5.LONG -> 4..9
            MagneticDistanceProfileV5.MIXED -> 1..9
        }
        val preferred = aligned.filter { distance(it, magnet) in desired }.ifEmpty { aligned }
        return preferred[random.nextInt(preferred.size)]
    }

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
    }
}
