package com.rameshta.magnetrail.core.generation

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.difficulty.DifficultyAnalyzer
import com.rameshta.magnetrail.core.difficulty.DifficultyMetrics
import com.rameshta.magnetrail.core.difficulty.DifficultyScorer
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.model.GradingThresholds
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.LevelMetadata
import com.rameshta.magnetrail.core.model.LevelOrigin
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.solver.Solver
import kotlin.math.ceil

sealed interface CertificationResult {
    data class Accepted(
        val level: LevelDefinition,
        val metrics: DifficultyMetrics,
    ) : CertificationResult

    data class Rejected(val reasons: List<String>) : CertificationResult
}

data class CertificationRequest(
    val profile: GenerationProfile,
    val origin: LevelOrigin,
    val packId: String,
    val generatorVersion: Int? = null,
    val generatorSeed: Long? = null,
    val generationProfile: String? = null,
    val contentVersion: Int = CONTENT_VERSION,
    val previousContentFingerprint: String? = null,
)

class CertificationPipeline(
    private val engine: GameEngine = DefaultGameEngine(),
    private val solver: Solver = Solver(engine),
) {
    fun certify(level: LevelDefinition, request: CertificationRequest): CertificationResult {
        val profile = request.profile
        val reasons = mutableListOf<String>()
        if (level.arrows.size !in profile.minArrows..profile.maxArrows) reasons += "arrow-count-out-of-profile"
        if (level.magnets.size !in profile.minMagnets..profile.maxMagnets) reasons += "magnet-count-out-of-profile"
        if (level.walls.size > profile.maxWalls) reasons += "wall-count-out-of-profile"
        if (level.width !in profile.minBoardSize..profile.maxBoardSize ||
            level.height !in profile.minBoardSize..profile.maxBoardSize
        ) reasons += "board-size-out-of-profile"
        if (level.initialState().arrows.isEmpty()) reasons += "already-won"

        level.arrows.forEach { arrow ->
            val state = level.initialState()
            val resolution = engine.resolve(state, PlayerAction(arrow.id))
            if (!resolution.success &&
                (resolution.originalState !== state || resolution.resultingState != state)
            ) reasons += "failed-action-mutated-state:${arrow.id}"
        }

        val solved = solver.solve(
            initialState = level.initialState(),
            solutionLimit = CERTIFICATION_SOLUTION_LIMIT,
            maxExploredStates = profile.solverStateCap,
        )
        if (!solved.searchComplete) reasons += solved.terminationReason ?: "solver-incomplete"
        if (!solved.solvable) reasons += "unsolvable"
        if (solved.validFirstActions.isEmpty()) reasons += "no-valid-opening"
        if (solved.validFirstActions.size > profile.maxOpeningActions) reasons += "excessive-opening-ambiguity"
        val solution = solved.oneCleanSolution
        if (solution == null) reasons += "missing-certified-solution"
        if (reasons.isNotEmpty()) return CertificationResult.Rejected(reasons.distinct())

        val replay = replay(level, requireNotNull(solution))
            ?: return CertificationResult.Rejected(listOf("certified-solution-replay-failed"))
        if (replay.finalArrowCount != 0) {
            return CertificationResult.Rejected(listOf("certified-solution-did-not-clear"))
        }
        if (replay.magnetControlledActions < profile.minMagnetControlledActions) {
            return CertificationResult.Rejected(listOf("required-magnetic-mechanic-unused"))
        }

        val metrics = DifficultyAnalyzer(engine).analyze(level, solved).metrics
        val difficultyScore = DifficultyScorer.score(metrics).score
        if (metrics.polarityFlipCount < profile.minPolarityFlips) {
            return CertificationResult.Rejected(listOf("required-polarity-flips-unused"))
        }
        if (metrics.criticalOrderConstraintCount < profile.minCriticalOrderConstraints) {
            return CertificationResult.Rejected(listOf("required-order-constraints-unused"))
        }
        if (difficultyScore !in profile.minDifficultyScoreV2..profile.maxDifficultyScoreV2) {
            return CertificationResult.Rejected(listOf("difficulty-v2-out-of-profile"))
        }
        val par = requireNotNull(solved.shortestDepth)
        val twoStar = par + maxOf(2, ceil(par * 0.25).toInt())
        val tags = mechanicTags(level, metrics)
        val fingerprint = ContentFingerprint.of(level)
        val certified = level.copy(
            designedSolutions = listOf(requireNotNull(solution).map { it.arrowId }),
            metadata = LevelMetadata(
                contentVersion = request.contentVersion,
                origin = request.origin,
                generatorVersion = request.generatorVersion,
                generatorSeed = request.generatorSeed,
                generationProfile = request.generationProfile,
                difficultyBand = profile.difficultyBand,
                certifiedSolutionLength = par,
                solutionCount = solved.solutionCount,
                solutionCountCapped = solved.solutionCountCapped,
                validFirstActionCount = solved.validFirstActions.size,
                exploredStateCount = solved.exploredStateCount,
                grading = GradingThresholds(par, twoStar),
                packId = request.packId,
                mechanicTags = tags,
                contentFingerprint = fingerprint,
                previousContentFingerprint = request.previousContentFingerprint,
            ),
        )
        return CertificationResult.Accepted(certified, metrics)
    }

    private fun replay(level: LevelDefinition, solution: List<PlayerAction>): ReplayMetrics? {
        var state = level.initialState()
        var branching = 0
        var controlled = 0
        var flips = 0
        var pull = false
        var push = false
        solution.forEach { action ->
            branching += engine.validActions(state).size
            val result = engine.resolve(state, action)
            if (!result.success) return null
            result.polarityChange?.let { change ->
                controlled += 1
                flips += 1
                pull = pull || change.from == Polarity.PULL
                push = push || change.from == Polarity.PUSH
            }
            state = result.resultingState
        }
        return ReplayMetrics(
            finalArrowCount = state.arrows.size,
            branchingTotal = branching,
            steps = solution.size,
            magnetControlledActions = controlled,
            polarityFlips = flips,
            usedPull = pull,
            usedPush = push,
        )
    }

    private fun mechanicTags(level: LevelDefinition, metrics: DifficultyMetrics): List<String> = buildList {
        if (level.magnets.isEmpty()) add("MOVEMENT")
        if (level.magnets.any { it.polarity == Polarity.PULL } || metrics.pullSolutionActions > 0) add("PULL")
        if (level.magnets.any { it.polarity == Polarity.PUSH } || metrics.pushSolutionActions > 0) add("PUSH")
        if (metrics.polarityFlipCount > 0) add("POLARITY_FLIP")
        if (level.walls.isNotEmpty()) add("WALLS")
        if (hasOcclusionOpportunity(level)) add("OCCLUSION")
        if (hasCancellationOpportunity(level)) add("CANCELLATION")
        if (level.magnets.size > 1) add("MULTIPLE_MAGNETS")
        if (metrics.successfulOpeningActions < metrics.plausibleOpeningActions) add("ORDER_DEPENDENCY")
    }.ifEmpty { listOf("MOVEMENT") }

    private fun hasOcclusionOpportunity(level: LevelDefinition): Boolean {
        val occupied = level.arrows.map { it.position } + level.magnets.map { it.position } + level.walls.map { it.position }
        return level.arrows.any { arrow ->
            level.magnets.any { magnet ->
                aligned(arrow.position, magnet.position) && occupied.any { blocker ->
                    blocker != arrow.position && blocker != magnet.position && between(arrow.position, blocker, magnet.position)
                }
            }
        }
    }

    private fun hasCancellationOpportunity(level: LevelDefinition): Boolean = level.arrows.any { arrow ->
        val result = engine.resolve(level.initialState(), PlayerAction(arrow.id))
        if (result.controllingMagnetId != null) return@any false
        val aligned = level.magnets.filter { aligned(arrow.position, it.position) }
        aligned.groupBy { distance(arrow.position, it.position) }.values.any { it.size > 1 }
    }

    private fun aligned(a: Position, b: Position): Boolean = a.row == b.row || a.column == b.column

    private fun between(start: Position, candidate: Position, end: Position): Boolean = when {
        start.row == end.row && candidate.row == start.row -> candidate.column in
            (minOf(start.column, end.column) + 1)..<maxOf(start.column, end.column)
        start.column == end.column && candidate.column == start.column -> candidate.row in
            (minOf(start.row, end.row) + 1)..<maxOf(start.row, end.row)
        else -> false
    }

    private fun distance(a: Position, b: Position): Int =
        kotlin.math.abs(a.row - b.row) + kotlin.math.abs(a.column - b.column)

    private data class ReplayMetrics(
        val finalArrowCount: Int,
        val branchingTotal: Int,
        val steps: Int,
        val magnetControlledActions: Int,
        val polarityFlips: Int,
        val usedPull: Boolean,
        val usedPush: Boolean,
    )
}
