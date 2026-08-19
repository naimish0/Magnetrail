package com.rameshta.magnetrail.core.difficulty

import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.MagneticDiagnostics
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.solver.Solver
import com.rameshta.magnetrail.core.solver.SolverResult
import com.rameshta.magnetrail.core.solver.StateKey
import kotlin.math.abs

data class DifficultyAnalysis(
    val metrics: DifficultyMetrics,
    val score: DifficultyScoreV2,
    val certifiedSolution: List<PlayerAction>?,
    val solutionReplayValid: Boolean,
    val searchComplete: Boolean,
)

/**
 * Deterministic build-time analyzer. It follows one certified solution and memoizes every
 * alternative state by the complete production [StateKey]. Unknown capped branches stay unknown.
 */
class DifficultyAnalyzer(
    private val engine: GameEngine = DefaultGameEngine(),
    private val config: DifficultyConfig = DifficultyConfig(),
    private val diagnostics: MagneticDiagnostics = MagneticDiagnostics(engine),
) {
    fun analyze(level: LevelDefinition, certified: SolverResult? = null): DifficultyAnalysis {
        val solved = certified ?: Solver(engine).solve(
            level.initialState(),
            solutionLimit = config.solutionCountCap,
            maxExploredStates = config.solverStateCap,
        )
        val solution = solved.oneCleanSolution
        if (!solved.solvable || solution == null) {
            val metrics = emptyMetrics(level, solved)
            return DifficultyAnalysis(metrics, DifficultyScorer.score(metrics, config), null, false, solved.searchComplete)
        }

        val stateFacts = BoundedStateFacts(engine, config.solutionCountCap, config.solverStateCap)
        var state = level.initialState()
        var replayValid = true
        var successfulOpening = 0
        var plausibleOpening = state.arrows.size
        var branchingTotal = 0
        var maximumBranching = 0
        var forcedStates = 0
        var fatalAlternatives = 0
        var provenAlternatives = 0
        var unknownAlternatives = 0
        var criticalConstraints = 0
        var divergenceDepth: Int? = null
        var magnetControlled = 0
        var pull = 0
        var push = 0
        var flips = 0
        var controllingChanges = 0
        var previousController: String? = null
        var occlusionDependencies = 0
        var cancellationDependencies = 0
        var wallDependencies = 0
        var recoveryWindows = 0
        var immediateFailures = 0
        var counterfactualChecks = 0
        var counterfactualCapped = false

        solution.forEachIndexed { depth, chosen ->
            if (!replayValid) return@forEachIndexed
            val actions = state.arrows.map { PlayerAction(it.id) }.sortedBy { it.arrowId }
            val preserving = mutableListOf<PlayerAction>()
            var fatalAtState = 0
            actions.forEach { action ->
                val resolution = engine.resolve(state, action)
                if (!resolution.success) {
                    immediateFailures += 1
                    return@forEach
                }
                val facts = stateFacts.analyze(resolution.resultingState)
                when (facts) {
                    is StateSolvability.Known -> if (facts.solvable) {
                        preserving += action
                        if (action != chosen) {
                            recoveryWindows += 1
                            if (divergenceDepth == null) divergenceDepth = depth
                        }
                    } else if (action != chosen) {
                        fatalAlternatives += 1
                        fatalAtState += 1
                    }
                    StateSolvability.Unknown -> if (action != chosen) unknownAlternatives += 1
                }
                if (action != chosen && facts !is StateSolvability.Unknown) provenAlternatives += 1
            }
            if (depth == 0) successfulOpening = preserving.size
            branchingTotal += preserving.size
            maximumBranching = maxOf(maximumBranching, preserving.size)
            if (preserving.size == 1) forcedStates += 1
            if (fatalAtState > 0) criticalConstraints += 1

            val arrow = state.arrow(chosen.arrowId)
            if (arrow == null) {
                replayValid = false
                return@forEachIndexed
            }
            val result = engine.resolve(state, chosen)
            if (!result.success) {
                replayValid = false
                return@forEachIndexed
            }
            result.polarityChange?.let { change ->
                magnetControlled += 1
                flips += 1
                if (change.from == Polarity.PULL) pull += 1 else push += 1
                if (previousController != null && previousController != change.magnetId) controllingChanges += 1
                previousController = change.magnetId
            }
            if (counterfactualChecks < config.counterfactualCheckCap) {
                val explanation = diagnostics.explain(
                    state,
                    arrow,
                    maxCounterfactualChecks = config.counterfactualCheckCap - counterfactualChecks,
                )
                counterfactualChecks += explanation.counterfactualChecksPerformed
                counterfactualCapped = counterfactualCapped || explanation.counterfactualCapped
                if (explanation.occludingEntityKeys.isNotEmpty()) occlusionDependencies += 1
                if (explanation.cancellationUsed) cancellationDependencies += 1
                if (explanation.wallEntityKeys.isNotEmpty()) wallDependencies += 1
            } else {
                counterfactualCapped = true
            }
            state = result.resultingState
        }
        replayValid = replayValid && state.arrows.isEmpty()
        val steps = solution.size.coerceAtLeast(1)
        val congestion = visualCongestion(level)
        val metrics = DifficultyMetrics(
            cleanSolutionLength = solution.size,
            successfulOpeningActions = successfulOpening,
            plausibleOpeningActions = plausibleOpening,
            averageSuccessfulBranching = branchingTotal.toDouble() / steps,
            maximumSuccessfulBranching = maximumBranching,
            forcedMoveRatio = forcedStates.toDouble() / steps,
            fatalChoiceRatio = if (provenAlternatives == 0) 0.0 else fatalAlternatives.toDouble() / provenAlternatives,
            criticalOrderConstraintCount = criticalConstraints,
            solutionDivergenceDepth = divergenceDepth,
            magnetControlledSolutionActions = magnetControlled,
            pullSolutionActions = pull,
            pushSolutionActions = push,
            polarityFlipCount = flips,
            controllingMagnetChangeCount = controllingChanges,
            occlusionDependencyCount = occlusionDependencies,
            cancellationDependencyCount = cancellationDependencies,
            wallDependencyCount = wallDependencies,
            solverStatesExplored = solved.exploredStateCount,
            solutionCountUpToCap = solved.solutionCount,
            boardDensity = ((level.arrows.size + level.magnets.size + level.walls.size).toDouble() /
                (level.width * level.height)).coerceIn(0.0, 1.0),
            visualCongestionScore = congestion,
            solutionCountCapped = solved.solutionCountCapped,
            stateAnalysisCapped = !solved.searchComplete || stateFacts.capped,
            counterfactualAnalysisCapped = counterfactualCapped,
            unknownAlternativeCount = unknownAlternatives,
            recoveryWindowCount = recoveryWindows,
            immediatelyFailingChoiceCount = immediateFailures,
            analyzerVersion = config.analyzerVersion,
        )
        return DifficultyAnalysis(
            metrics = metrics,
            score = DifficultyScorer.score(metrics, config),
            certifiedSolution = solution,
            solutionReplayValid = replayValid,
            searchComplete = solved.searchComplete && !stateFacts.capped,
        )
    }

    private fun emptyMetrics(level: LevelDefinition, solved: SolverResult): DifficultyMetrics = DifficultyMetrics(
        cleanSolutionLength = 0,
        successfulOpeningActions = 0,
        plausibleOpeningActions = level.arrows.size,
        averageSuccessfulBranching = 0.0,
        maximumSuccessfulBranching = 0,
        forcedMoveRatio = 0.0,
        fatalChoiceRatio = 0.0,
        criticalOrderConstraintCount = 0,
        solutionDivergenceDepth = null,
        magnetControlledSolutionActions = 0,
        pullSolutionActions = 0,
        pushSolutionActions = 0,
        polarityFlipCount = 0,
        controllingMagnetChangeCount = 0,
        occlusionDependencyCount = 0,
        cancellationDependencyCount = 0,
        wallDependencyCount = 0,
        solverStatesExplored = solved.exploredStateCount,
        solutionCountUpToCap = solved.solutionCount,
        boardDensity = ((level.arrows.size + level.magnets.size + level.walls.size).toDouble() /
            (level.width * level.height)).coerceIn(0.0, 1.0),
        visualCongestionScore = visualCongestion(level),
        solutionCountCapped = solved.solutionCountCapped,
        stateAnalysisCapped = !solved.searchComplete,
        counterfactualAnalysisCapped = false,
        unknownAlternativeCount = 0,
        recoveryWindowCount = 0,
        immediatelyFailingChoiceCount = 0,
        analyzerVersion = config.analyzerVersion,
    )

    private fun visualCongestion(level: LevelDefinition): Double {
        val positions = level.arrows.map { it.position } + level.magnets.map { it.position } + level.walls.map { it.position }
        if (positions.isEmpty()) return 0.0
        val density = positions.size.toDouble() / (level.width * level.height)
        val adjacentPairs = positions.indices.sumOf { first ->
            ((first + 1)..positions.lastIndex).count { second ->
                val a = positions[first]
                val b = positions[second]
                abs(a.row - b.row) + abs(a.column - b.column) == 1
            }
        }
        val possiblePairs = (positions.size * (positions.size - 1) / 2).coerceAtLeast(1)
        val adjacency = adjacentPairs.toDouble() / possiblePairs
        val fieldOverlap = level.arrows.count { arrow ->
            level.magnets.count { magnet -> aligned(arrow.position, magnet.position) } > 1
        }.toDouble() / level.arrows.size.coerceAtLeast(1)
        return (density * 0.5 + adjacency * 0.25 + fieldOverlap * 0.25).coerceIn(0.0, 1.0)
    }

    private fun aligned(a: Position, b: Position): Boolean = a.row == b.row || a.column == b.column
}

private sealed interface StateSolvability {
    data class Known(val solvable: Boolean, val solutionCount: Int) : StateSolvability
    data object Unknown : StateSolvability
}

private class BoundedStateFacts(
    private val engine: GameEngine,
    private val solutionCap: Int,
    private val stateCap: Int,
) {
    private val memo = mutableMapOf<StateKey, StateSolvability.Known>()
    private var explored = 0
    var capped: Boolean = false
        private set

    fun analyze(state: BoardState): StateSolvability {
        val key = StateKey.from(state)
        memo[key]?.let { return it }
        if (capped || explored >= stateCap) {
            capped = true
            return StateSolvability.Unknown
        }
        explored += 1
        if (state.arrows.isEmpty()) return StateSolvability.Known(true, 1).also { memo[key] = it }
        var count = 0
        for (action in engine.validActions(state).sortedBy { it.arrowId }) {
            when (val child = analyze(engine.resolve(state, action).resultingState)) {
                is StateSolvability.Known -> count = (count + child.solutionCount).coerceAtMost(solutionCap)
                StateSolvability.Unknown -> return StateSolvability.Unknown
            }
            if (count >= solutionCap) break
        }
        return StateSolvability.Known(count > 0, count).also { memo[key] = it }
    }
}
