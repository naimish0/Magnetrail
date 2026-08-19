package com.rameshta.magnetrail.core.difficulty

import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.MagneticDependencyExplanation
import com.rameshta.magnetrail.core.engine.MagneticDiagnostics
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.engine.ResolutionResult
import com.rameshta.magnetrail.core.engine.TerminalEvent
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.solver.StateKey
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import kotlin.math.roundToInt

const val PUZZLE_SEARCH_ANALYZER_VERSION = "magnetrail-search-v3.0"

@Serializable
data class PuzzleSearchConfig(
    val analyzerVersion: String = PUZZLE_SEARCH_ANALYZER_VERSION,
    val maxExpandedStates: Int = 100_000,
    val maxActionResolutions: Int = 1_000_000,
    val shortestSolutionCountCap: Int = 100_000,
    val solutionFamilyCap: Int = 512,
    val magneticCounterfactualCap: Int = 10_000,
    val certifiableConfidenceFloor: Double = 0.95,
) {
    init {
        require(maxExpandedStates > 0)
        require(maxActionResolutions > 0)
        require(shortestSolutionCountCap > 0)
        require(solutionFamilyCap > 0)
        require(magneticCounterfactualCap > 0)
        require(certifiableConfidenceFloor in 0.0..1.0)
    }
}

@Serializable
enum class PlayerChoiceClassification {
    IMMEDIATELY_INVALID,
    STRATEGICALLY_VIABLE,
    DECEPTIVE_BUT_FAIR,
    GUESS_DEPENDENT,
    UNKNOWN,
}

@Serializable
data class PlayerChoiceMetrics(
    val plausibleChoices: Int,
    val immediatelyInvalidChoices: Int,
    val strategicallyViableChoices: Int,
    val deceptiveButFairChoices: Int,
    val guessDependentChoices: Int,
    val unknownChoices: Int,
)

@Serializable
data class PurposefulSpaceMetrics(
    val boardCellCount: Int,
    val authoredEntityCellCount: Int,
    val purposefulEntityCellCount: Int,
    val irrelevantEntityCellCount: Int,
    val authoredEmptyCellCount: Int,
    val purposefulCellCount: Int,
    val purposefulEmptyCellCount: Int,
    val unusedEmptyCellCount: Int,
    val rawOccupancyRatio: Double,
    val rawEmptySpaceRatio: Double,
    val purposefulEntityRatio: Double,
    val irrelevantEntityRatio: Double,
    val purposefulCellRatio: Double,
    val purposefulEmptySpaceRatio: Double,
    val unusedEmptySpaceRatio: Double,
    val authoredEmptyCellsPurposefulRatio: Double,
)

@Serializable
data class PuzzleSearchMetrics(
    val solvable: Boolean,
    val minimumSolutionLength: Int,
    val shortestSolutionCount: Int,
    val shortestSolutionCountCapped: Boolean,
    val solutionFamilyCount: Int,
    val solutionFamilyCountCapped: Boolean,
    val canonicalSolutionArrowIds: List<String>,
    val openingChoiceMetrics: PlayerChoiceMetrics,
    val canonicalChoiceMetrics: PlayerChoiceMetrics,
    val exploredChoiceMetrics: PlayerChoiceMetrics,
    val meaningfulDecisionPoints: Int,
    val averageEffectiveBranchingFactor: Double,
    val maximumEffectiveBranchingFactor: Int,
    val forcedSequenceLength: Int,
    val forcedMoveRatio: Double,
    val obviousNextActionRatio: Double,
    val averageDecisionSpacing: Double,
    val maximumForcedRunLength: Int,
    val deadEndActionCount: Int,
    val deadEndStateCount: Int,
    val averageDeadEndProofDepth: Double,
    val maximumDeadEndProofDepth: Int,
    val backtrackingPressure: Double,
    val dependencyDepth: Int,
    val dependencyEdgeCount: Int,
    val multiStageInteractionDepth: Int,
    val mechanicRelevantSolutionActions: Int,
    val mechanicRelevanceRatio: Double,
    val pullSolutionActions: Int,
    val pushSolutionActions: Int,
    val polarityFlipCount: Int,
    val controllingMagnetChangeCount: Int,
    val occlusionDependencyCount: Int,
    val cancellationDependencyCount: Int,
    val wallDependencyCount: Int,
    val averageRouteLength: Double,
    val uniqueStatesExpanded: Int,
    val actionResolutions: Int,
    val depthDistribution: Map<String, Int>,
    val searchComplete: Boolean,
    val truncationReasons: List<String>,
    val confidence: Double,
    val confidenceReasons: List<String>,
    val purposefulSpace: PurposefulSpaceMetrics,
    val structuralPatternSignature: String,
    val analyzerVersion: String = PUZZLE_SEARCH_ANALYZER_VERSION,
) {
    init {
        require(forcedMoveRatio in 0.0..1.0)
        require(obviousNextActionRatio in 0.0..1.0)
        require(backtrackingPressure in 0.0..1.0)
        require(mechanicRelevanceRatio in 0.0..1.0)
        require(confidence in 0.0..1.0)
    }
}

class PuzzleSearchAnalyzer(
    private val engine: GameEngine = DefaultGameEngine(),
    private val config: PuzzleSearchConfig = PuzzleSearchConfig(),
    private val diagnostics: MagneticDiagnostics = MagneticDiagnostics(engine),
) {
    fun analyze(level: LevelDefinition): PuzzleSearchMetrics {
        val budget = SearchBudget(config)
        val nodes = linkedMapOf<StateKey, SearchNode>()
        val depthDistribution = sortedMapOf<Int, Int>()
        val purposefulCells = mutableSetOf<Position>()
        val initialEntityCells = (
            level.arrows.map { it.position } + level.magnets.map { it.position } + level.walls.map { it.position }
            ).toSet()
        // Every arrow is a visible player action. Static objects become purposeful only when
        // production resolution proves route, control, collision, cancellation, or blocking use.
        purposefulCells += level.arrows.map { it.position }

        fun expand(state: BoardState, depth: Int): SearchNode? {
            val key = StateKey.from(state)
            nodes[key]?.let { return it }
            if (nodes.size >= config.maxExpandedStates) {
                budget.truncate("EXPANDED_STATE_CAP")
                return null
            }
            val node = SearchNode(state, depth)
            nodes[key] = node
            depthDistribution[depth] = (depthDistribution[depth] ?: 0) + 1
            if (state.arrows.isEmpty()) return node

            state.arrows.sortedBy { it.id }.forEach { arrow ->
                if (budget.actionResolutions >= config.maxActionResolutions) {
                    budget.truncate("ACTION_RESOLUTION_CAP")
                    node.edges += SearchEdge(PlayerAction(arrow.id), null, null)
                    return@forEach
                }
                budget.actionResolutions += 1
                val result = engine.resolve(state, PlayerAction(arrow.id))
                purposefulCells += arrow.position
                purposefulCells += result.traversedCells
                result.collisionTarget?.position?.let { purposefulCells += it }
                result.controllingMagnetId?.let { magnetId ->
                    purposefulCells += diagnostics.relationshipCells(state, arrow, setOf(magnetId))
                }
                val child = if (result.success) expand(result.resultingState, depth + 1) else null
                node.edges += SearchEdge(PlayerAction(arrow.id), result, child)
            }
            return node
        }

        val root = requireNotNull(expand(level.initialState(), 0))
        val facts = calculateFacts(root, config, budget)
        val canonicalNodes = mutableListOf<SearchNode>()
        val canonicalEdges = mutableListOf<SearchEdge>()
        var cursor = root
        while (cursor.state.arrows.isNotEmpty() && cursor.facts?.solvable == true) {
            val depth = requireNotNull(cursor.facts).minimumDepth
            val selected = cursor.edges.asSequence()
                .filter { it.result?.success == true && it.child?.facts?.solvable == true }
                .filter { edge -> edge.child?.facts?.minimumDepth?.plus(1) == depth }
                .sortedBy { it.action.arrowId }
                .firstOrNull() ?: break
            canonicalNodes += cursor
            canonicalEdges += selected
            cursor = requireNotNull(selected.child)
        }

        val classificationCache = mutableMapOf<Pair<StateKey, String>, PlayerChoiceClassification>()
        fun classify(node: SearchNode, edge: SearchEdge): PlayerChoiceClassification {
            val cacheKey = StateKey.from(node.state) to edge.action.arrowId
            classificationCache[cacheKey]?.let { return it }
            val result = edge.result
            val classification = when {
                result == null -> PlayerChoiceClassification.UNKNOWN
                !result.success -> PlayerChoiceClassification.IMMEDIATELY_INVALID
                edge.child == null || edge.child.facts?.complete != true -> PlayerChoiceClassification.UNKNOWN
                edge.child.facts?.solvable == true -> PlayerChoiceClassification.STRATEGICALLY_VIABLE
                else -> classifyFatalChoice(node, edge, budget)
            }
            classificationCache[cacheKey] = classification
            return classification
        }

        val openingChoiceMetrics = choiceMetrics(listOf(root), ::classify)
        val canonicalChoiceMetrics = choiceMetrics(canonicalNodes, ::classify)
        val exploredNodes = nodes.values.filter { it.facts?.solvable == true }
        val exploredChoiceMetrics = choiceMetrics(exploredNodes, ::classify)

        val decisionFlags = mutableListOf<Boolean>()
        val forcedFlags = mutableListOf<Boolean>()
        val effectiveBranches = mutableListOf<Int>()
        canonicalNodes.forEach { node ->
            val relevant = node.edges.mapNotNull { edge ->
                when (classify(node, edge)) {
                    PlayerChoiceClassification.STRATEGICALLY_VIABLE -> strategicSignature(edge)
                    PlayerChoiceClassification.DECEPTIVE_BUT_FAIR -> "FAIR:${deadEndSignature(edge)}"
                    else -> null
                }
            }.toSet()
            val viableCount = node.edges.count { classify(node, it) == PlayerChoiceClassification.STRATEGICALLY_VIABLE }
            effectiveBranches += relevant.size
            decisionFlags += relevant.size >= 2
            forcedFlags += viableCount == 1
        }

        val dependencyEdges = dependencyEdges(canonicalNodes, canonicalEdges)
        val dependencyDepth = dependencyDepth(canonicalEdges.size, dependencyEdges)
        var previousController: String? = null
        var controllingChanges = 0
        var pull = 0
        var push = 0
        var flips = 0
        var occlusion = 0
        var cancellation = 0
        var walls = 0
        val mechanicRelevantFlags = mutableListOf<Boolean>()
        canonicalEdges.forEachIndexed { index, edge ->
            val result = requireNotNull(edge.result)
            when (result.polarityChange?.from) {
                Polarity.PULL -> pull += 1
                Polarity.PUSH -> push += 1
                null -> Unit
            }
            if (result.polarityChange != null) flips += 1
            result.controllingMagnetId?.let { controller ->
                if (previousController != null && previousController != controller) controllingChanges += 1
                previousController = controller
            }
            val explanation = diagnosticFor(canonicalNodes[index], edge, budget)
            if (explanation?.occludingEntityKeys?.isNotEmpty() == true) {
                occlusion += 1
                explanation.occludingEntityKeys.forEach { key ->
                    entityPosition(canonicalNodes[index].state, key)?.let { purposefulCells += it }
                }
            }
            if (explanation?.cancellationUsed == true) {
                cancellation += 1
                purposefulCells += diagnostics.relationshipCells(
                    canonicalNodes[index].state,
                    requireNotNull(canonicalNodes[index].state.arrow(edge.action.arrowId)),
                )
            }
            if (explanation?.wallEntityKeys?.isNotEmpty() == true) walls += 1
            mechanicRelevantFlags += result.controllingMagnetId != null ||
                explanation?.occludingEntityKeys?.isNotEmpty() == true ||
                explanation?.cancellationUsed == true ||
                dependencyEdges.any { it.first == index }
        }

        val knownFatalEdges = nodes.values.filter { it.facts?.solvable == true }.flatMap { node ->
            node.edges.filter { edge ->
                edge.result?.success == true && edge.child?.facts?.complete == true && edge.child.facts?.solvable == false
            }
        }
        val knownSuccessfulEdges = nodes.values.filter { it.facts?.solvable == true }
            .sumOf { node -> node.edges.count { it.result?.success == true && it.child?.facts?.complete == true } }
        val proofDepths = knownFatalEdges.mapNotNull { it.child?.facts?.deadEndProofDepth?.plus(1) }
        val deadEndStates = nodes.values.count { it.facts?.complete == true && it.facts?.solvable == false }
        val solutionLength = facts.minimumDepth ?: 0
        val decisionIndices = decisionFlags.mapIndexedNotNull { index, decision -> index.takeIf { decision } }
        val pathLength = canonicalEdges.size
        val confidenceReasons = buildList {
            addAll(budget.truncationReasons)
            if (facts.shortestCountCapped) add("SHORTEST_SOLUTION_COUNT_CAPPED")
            if (facts.familyCountCapped) add("SOLUTION_FAMILY_COUNT_CAPPED")
            if (budget.diagnosticCapped) add("MAGNETIC_COUNTERFACTUAL_CAP")
            if (!facts.complete) add("SEARCH_GRAPH_INCOMPLETE")
            if (facts.solvable && canonicalEdges.size != facts.minimumDepth) add("CANONICAL_REPLAY_INCOMPLETE")
        }.distinct().sorted()
        var confidence = 1.0
        if (!facts.complete || budget.truncationReasons.isNotEmpty()) confidence -= 0.60
        if (facts.shortestCountCapped) confidence -= 0.05
        if (facts.familyCountCapped) confidence -= 0.05
        if (budget.diagnosticCapped) confidence -= 0.15
        if (facts.solvable && canonicalEdges.size != facts.minimumDepth) confidence -= 0.50
        confidence = confidence.coerceIn(0.0, 1.0)

        val boardCells = level.width * level.height
        val purposefulOnBoard = purposefulCells.filterTo(mutableSetOf()) {
            it.row in 1..level.height && it.column in 1..level.width
        }
        val purposefulEmpty = purposefulOnBoard - initialEntityCells
        val purposefulEntities = purposefulOnBoard intersect initialEntityCells
        val irrelevantEntities = initialEntityCells - purposefulOnBoard
        val authoredEmptyCount = boardCells - initialEntityCells.size
        val unusedEmpty = (authoredEmptyCount - purposefulEmpty.size).coerceAtLeast(0)
        val purposefulSpace = PurposefulSpaceMetrics(
            boardCellCount = boardCells,
            authoredEntityCellCount = initialEntityCells.size,
            purposefulEntityCellCount = purposefulEntities.size,
            irrelevantEntityCellCount = irrelevantEntities.size,
            authoredEmptyCellCount = authoredEmptyCount,
            purposefulCellCount = purposefulOnBoard.size,
            purposefulEmptyCellCount = purposefulEmpty.size,
            unusedEmptyCellCount = unusedEmpty,
            rawOccupancyRatio = ratio(initialEntityCells.size, boardCells),
            rawEmptySpaceRatio = ratio(authoredEmptyCount, boardCells),
            purposefulEntityRatio = ratio(purposefulEntities.size, initialEntityCells.size),
            irrelevantEntityRatio = ratio(irrelevantEntities.size, initialEntityCells.size),
            purposefulCellRatio = ratio(purposefulOnBoard.size, boardCells),
            purposefulEmptySpaceRatio = ratio(purposefulEmpty.size, boardCells),
            unusedEmptySpaceRatio = ratio(unusedEmpty, boardCells),
            authoredEmptyCellsPurposefulRatio = ratio(purposefulEmpty.size, authoredEmptyCount),
        )
        val structuralSignature = structuralSignature(
            canonicalNodes = canonicalNodes,
            canonicalEdges = canonicalEdges,
            decisionFlags = decisionFlags,
            effectiveBranches = effectiveBranches,
            dependencyEdges = dependencyEdges,
            facts = facts,
        )

        return PuzzleSearchMetrics(
            solvable = facts.solvable,
            minimumSolutionLength = solutionLength,
            shortestSolutionCount = facts.shortestSolutionCount,
            shortestSolutionCountCapped = facts.shortestCountCapped,
            solutionFamilyCount = facts.solutionFamilies.size,
            solutionFamilyCountCapped = facts.familyCountCapped,
            canonicalSolutionArrowIds = canonicalEdges.map { it.action.arrowId },
            openingChoiceMetrics = openingChoiceMetrics,
            canonicalChoiceMetrics = canonicalChoiceMetrics,
            exploredChoiceMetrics = exploredChoiceMetrics,
            meaningfulDecisionPoints = decisionFlags.count { it },
            averageEffectiveBranchingFactor = effectiveBranches.map(Int::toDouble).averageOrZero(),
            maximumEffectiveBranchingFactor = effectiveBranches.maxOrNull() ?: 0,
            forcedSequenceLength = forcedFlags.count { it },
            forcedMoveRatio = ratio(forcedFlags.count { it }, pathLength),
            obviousNextActionRatio = ratio(
                canonicalNodes.count { node -> node.edges.count { it.result?.success == true } == 1 },
                pathLength,
            ),
            averageDecisionSpacing = averageDecisionSpacing(pathLength, decisionIndices),
            maximumForcedRunLength = longestRun(forcedFlags),
            deadEndActionCount = knownFatalEdges.size,
            deadEndStateCount = deadEndStates,
            averageDeadEndProofDepth = proofDepths.map(Int::toDouble).averageOrZero(),
            maximumDeadEndProofDepth = proofDepths.maxOrNull() ?: 0,
            backtrackingPressure = ratio(knownFatalEdges.size, knownSuccessfulEdges),
            dependencyDepth = dependencyDepth,
            dependencyEdgeCount = dependencyEdges.size,
            multiStageInteractionDepth = longestRun(mechanicRelevantFlags),
            mechanicRelevantSolutionActions = mechanicRelevantFlags.count { it },
            mechanicRelevanceRatio = ratio(mechanicRelevantFlags.count { it }, pathLength),
            pullSolutionActions = pull,
            pushSolutionActions = push,
            polarityFlipCount = flips,
            controllingMagnetChangeCount = controllingChanges,
            occlusionDependencyCount = occlusion,
            cancellationDependencyCount = cancellation,
            wallDependencyCount = walls,
            averageRouteLength = canonicalEdges.mapNotNull { it.result?.traversedCells?.size?.toDouble() }.averageOrZero(),
            uniqueStatesExpanded = nodes.size,
            actionResolutions = budget.actionResolutions,
            depthDistribution = depthDistribution.mapKeys { it.key.toString() },
            searchComplete = facts.complete && budget.truncationReasons.isEmpty(),
            truncationReasons = budget.truncationReasons.sorted(),
            confidence = round4(confidence),
            confidenceReasons = confidenceReasons,
            purposefulSpace = purposefulSpace,
            structuralPatternSignature = structuralSignature,
            analyzerVersion = config.analyzerVersion,
        )
    }

    private fun calculateFacts(
        node: SearchNode,
        config: PuzzleSearchConfig,
        budget: SearchBudget,
    ): SearchFacts {
        node.facts?.let { return it }
        if (node.state.arrows.isEmpty()) {
            return SearchFacts(
                solvable = true,
                complete = true,
                minimumDepth = 0,
                shortestSolutionCount = 1,
                shortestCountCapped = false,
                solutionFamilies = setOf("GOAL"),
                familyCountCapped = false,
                deadEndProofDepth = null,
            ).also { node.facts = it }
        }
        val successful = node.edges.filter { it.result?.success == true }
        val childFacts = successful.mapNotNull { it.child?.let { child -> calculateFacts(child, config, budget) } }
        val complete = successful.all { it.child != null && it.child.facts?.complete == true } &&
            node.edges.none { it.result == null }
        val viable = successful.filter { it.child?.facts?.solvable == true }
        val minimumDepth = viable.mapNotNull { it.child?.facts?.minimumDepth?.plus(1) }.minOrNull()
        var shortestCount = 0
        var shortestCapped = false
        val families = linkedSetOf<String>()
        var familyCapped = false
        viable.filter { it.child?.facts?.minimumDepth?.plus(1) == minimumDepth }.forEach { edge ->
            val child = requireNotNull(edge.child).facts ?: return@forEach
            shortestCount += child.shortestSolutionCount
            if (shortestCount > config.shortestSolutionCountCap || child.shortestCountCapped) shortestCapped = true
            shortestCount = shortestCount.coerceAtMost(config.shortestSolutionCountCap)
            child.solutionFamilies.sorted().forEach { family ->
                if (families.size < config.solutionFamilyCap) {
                    families += "${mechanicToken(requireNotNull(edge.result))}>$family"
                } else {
                    familyCapped = true
                }
            }
            familyCapped = familyCapped || child.familyCountCapped
        }
        val deadEndProofDepth = if (viable.isEmpty() && complete) {
            if (successful.isEmpty()) 0 else successful.mapNotNull { it.child?.facts?.deadEndProofDepth?.plus(1) }.minOrNull()
        } else {
            null
        }
        return SearchFacts(
            solvable = viable.isNotEmpty(),
            complete = complete,
            minimumDepth = minimumDepth,
            shortestSolutionCount = shortestCount,
            shortestCountCapped = shortestCapped,
            solutionFamilies = families,
            familyCountCapped = familyCapped,
            deadEndProofDepth = deadEndProofDepth,
        ).also {
            node.facts = it
            if (shortestCapped) budget.shortestCountCapped = true
            if (familyCapped) budget.familyCountCapped = true
        }
    }

    private fun classifyFatalChoice(
        node: SearchNode,
        edge: SearchEdge,
        budget: SearchBudget,
    ): PlayerChoiceClassification {
        val viable = node.edges.filter { it.child?.facts?.solvable == true && it.result?.success == true }
        if (viable.isEmpty()) return PlayerChoiceClassification.UNKNOWN
        val observation = observableSignature(edge)
        val futureProfile = immediateFutureProfile(edge)
        val indistinguishableViable = viable.any { viableEdge ->
            observableSignature(viableEdge) == observation && immediateFutureProfile(viableEdge) == futureProfile
        }
        if (indistinguishableViable) return PlayerChoiceClassification.GUESS_DEPENDENT
        val explanation = diagnosticFor(node, edge, budget) ?: return PlayerChoiceClassification.UNKNOWN
        val result = requireNotNull(edge.result)
        val terminalDiffers = viable.any { terminalFamily(it.result) != terminalFamily(result) }
        val nextCountDiffers = viable.any { successfulChildCount(it) != successfulChildCount(edge) }
        val routeDiffers = viable.any { routeBucket(it.result?.traversedCells?.size ?: 0) != routeBucket(result.traversedCells.size) }
        val immediateFutureDiffers = viable.any { immediateFutureProfile(it) != futureProfile }
        val fairClue = result.polarityChange != null || explanation.cancellationUsed ||
            explanation.occludingEntityKeys.isNotEmpty() || terminalDiffers || nextCountDiffers || routeDiffers ||
            immediateFutureDiffers
        return if (fairClue) {
            PlayerChoiceClassification.DECEPTIVE_BUT_FAIR
        } else {
            PlayerChoiceClassification.GUESS_DEPENDENT
        }
    }

    private fun diagnosticFor(
        node: SearchNode,
        edge: SearchEdge,
        budget: SearchBudget,
    ): MagneticDependencyExplanation? {
        edge.diagnostic?.let { return it }
        if (budget.diagnosticChecks >= config.magneticCounterfactualCap) {
            budget.diagnosticCapped = true
            return null
        }
        val arrow = node.state.arrow(edge.action.arrowId) ?: return null
        val explanation = diagnostics.explain(
            node.state,
            arrow,
            maxCounterfactualChecks = config.magneticCounterfactualCap - budget.diagnosticChecks,
        )
        budget.diagnosticChecks += explanation.counterfactualChecksPerformed
        budget.diagnosticCapped = budget.diagnosticCapped || explanation.counterfactualCapped
        edge.diagnostic = explanation
        return explanation
    }

    private fun dependencyEdges(
        nodes: List<SearchNode>,
        edges: List<SearchEdge>,
    ): Set<Pair<Int, Int>> = buildSet {
        nodes.forEachIndexed { index, node ->
            val after = edges[index].result?.resultingState ?: return@forEachIndexed
            for (future in (index + 1)..<edges.size) {
                val arrowId = edges[future].action.arrowId
                val beforeSignature = resolutionSignature(node.state, arrowId)
                val afterSignature = resolutionSignature(after, arrowId)
                if (beforeSignature != null && afterSignature != null && beforeSignature != afterSignature) {
                    add(index to future)
                }
            }
        }
    }

    private fun dependencyDepth(size: Int, edges: Set<Pair<Int, Int>>): Int {
        if (edges.isEmpty() || size == 0) return 0
        val depth = IntArray(size) { 1 }
        edges.sortedWith(compareBy<Pair<Int, Int>> { it.second }.thenBy { it.first }).forEach { (from, to) ->
            depth[to] = maxOf(depth[to], depth[from] + 1)
        }
        return depth.maxOrNull() ?: 0
    }

    private fun resolutionSignature(state: BoardState, arrowId: String): String? {
        if (state.arrow(arrowId) == null) return null
        val result = engine.resolve(state, PlayerAction(arrowId))
        return listOf(
            result.success,
            result.effectiveDirection,
            result.controllingMagnetId ?: "none",
            terminalFamily(result),
            result.polarityChange?.from ?: "none",
            result.traversedCells.size,
        ).joinToString("|")
    }

    private fun observableSignature(edge: SearchEdge): String {
        val result = requireNotNull(edge.result)
        return listOf(
            terminalFamily(result),
            result.polarityChange?.from ?: "none",
            if (result.controllingMagnetId == null) "uncontrolled" else "controlled",
            routeBucket(result.traversedCells.size),
            nextActionBucket(successfulChildCount(edge)),
        ).joinToString("|")
    }

    private fun strategicSignature(edge: SearchEdge): String {
        val child = requireNotNull(edge.child).facts ?: return "UNKNOWN"
        val families = child.solutionFamilies.sorted().joinToString(";")
        return sha256("${mechanicToken(requireNotNull(edge.result))}|${child.minimumDepth}|$families")
    }

    private fun deadEndSignature(edge: SearchEdge): String =
        "${observableSignature(edge)}|proof=${edge.child?.facts?.deadEndProofDepth ?: -1}"

    private fun structuralSignature(
        canonicalNodes: List<SearchNode>,
        canonicalEdges: List<SearchEdge>,
        decisionFlags: List<Boolean>,
        effectiveBranches: List<Int>,
        dependencyEdges: Set<Pair<Int, Int>>,
        facts: SearchFacts,
    ): String {
        val stateShape = canonicalNodes.mapIndexed { index, node ->
            val invalid = node.edges.count { it.result?.success == false }
            val viable = node.edges.count { it.child?.facts?.solvable == true }
            listOf(
                node.state.arrows.size,
                invalid,
                viable,
                effectiveBranches.getOrElse(index) { 0 },
                decisionFlags.getOrElse(index) { false },
                canonicalEdges.getOrNull(index)?.result?.let(::mechanicToken) ?: "none",
            ).joinToString(":")
        }
        return sha256(
            listOf(
                "min=${facts.minimumDepth}",
                "families=${facts.solutionFamilies.sorted().joinToString(";")}",
                "states=${stateShape.joinToString(",")}",
                "deps=${dependencyEdges.sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })}",
            ).joinToString("|"),
        )
    }

    private fun mechanicToken(result: ResolutionResult): String = listOf(
        terminalFamily(result),
        result.polarityChange?.from ?: "NONE",
        if (result.polarityChange == null) "NO_FLIP" else "FLIP",
        routeBucket(result.traversedCells.size),
    ).joinToString(":")

    private fun terminalFamily(result: ResolutionResult?): String = when (result?.terminalEvent) {
        is TerminalEvent.Exit -> if (result.controllingMagnetId == null) "EXIT" else "PUSH_EXIT"
        is TerminalEvent.PullCapture -> "PULL_CAPTURE"
        is TerminalEvent.Collision -> "COLLISION"
        is TerminalEvent.InvalidPullExit -> "INVALID_PULL_EXIT"
        null -> "UNKNOWN"
    }

    private fun successfulChildCount(edge: SearchEdge): Int =
        edge.child?.edges?.count { it.result?.success == true } ?: 0

    /** Player-visible one-step-ahead outcomes after the selected move; no solvability facts. */
    private fun immediateFutureProfile(edge: SearchEdge): List<String> = edge.child?.edges.orEmpty()
        .mapNotNull { future -> future.result }
        .map { result ->
            listOf(
                result.success,
                terminalFamily(result),
                result.effectiveDirection,
                result.polarityChange?.from ?: "NONE",
                if (result.controllingMagnetId == null) "UNCONTROLLED" else "CONTROLLED",
                routeBucket(result.traversedCells.size),
            ).joinToString(":")
        }
        .sorted()

    private fun routeBucket(length: Int): String = when (length) {
        0 -> "0"
        1 -> "1"
        2 -> "2"
        in 3..4 -> "3_4"
        else -> "5_PLUS"
    }

    private fun nextActionBucket(count: Int): String = when (count) {
        0 -> "0"
        1 -> "1"
        2 -> "2"
        else -> "3_PLUS"
    }

    private fun choiceMetrics(
        nodes: List<SearchNode>,
        classify: (SearchNode, SearchEdge) -> PlayerChoiceClassification,
    ): PlayerChoiceMetrics {
        val classified = nodes.flatMap { node -> node.edges.map { edge -> edge to classify(node, edge) } }
        val classes = classified.map { it.second }
        return PlayerChoiceMetrics(
            // A missing resolution is unknown, not plausible. A successful resolution remains
            // player-plausible even if a search cap prevents future viability classification.
            plausibleChoices = classified.count { it.first.result?.success == true },
            immediatelyInvalidChoices = classes.count { it == PlayerChoiceClassification.IMMEDIATELY_INVALID },
            strategicallyViableChoices = classes.count { it == PlayerChoiceClassification.STRATEGICALLY_VIABLE },
            deceptiveButFairChoices = classes.count { it == PlayerChoiceClassification.DECEPTIVE_BUT_FAIR },
            guessDependentChoices = classes.count { it == PlayerChoiceClassification.GUESS_DEPENDENT },
            unknownChoices = classes.count { it == PlayerChoiceClassification.UNKNOWN },
        )
    }

    private fun entityPosition(state: BoardState, key: String): Position? = when {
        key.startsWith("arrow:") -> state.arrow(key.substringAfter(':'))?.position
        key.startsWith("magnet:") -> state.magnets.firstOrNull { it.id == key.substringAfter(':') }?.position
        key.startsWith("wall:") -> key.substringAfter(':').split(',').takeIf { it.size == 2 }
            ?.let { (row, column) -> Position(row.toInt(), column.toInt()) }
        else -> null
    }

    private fun averageDecisionSpacing(length: Int, decisionIndices: List<Int>): Double {
        if (length == 0) return 0.0
        if (decisionIndices.isEmpty()) return length.toDouble()
        val gaps = mutableListOf<Int>()
        gaps += decisionIndices.first()
        decisionIndices.zipWithNext().forEach { (first, second) -> gaps += (second - first - 1).coerceAtLeast(0) }
        gaps += (length - decisionIndices.last() - 1).coerceAtLeast(0)
        return gaps.map(Int::toDouble).averageOrZero()
    }

    private fun longestRun(flags: List<Boolean>): Int {
        var longest = 0
        var current = 0
        flags.forEach { value ->
            if (value) {
                current += 1
                longest = maxOf(longest, current)
            } else {
                current = 0
            }
        }
        return longest
    }

    private fun ratio(numerator: Int, denominator: Int): Double =
        if (denominator <= 0) 0.0 else round4(numerator.toDouble() / denominator)

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else round4(average())

    private fun round4(value: Double): Double = (value * 10_000.0).roundToInt() / 10_000.0

    private fun sha256(value: String): String = "sha256:" + MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private data class SearchNode(
        val state: BoardState,
        val depth: Int,
        val edges: MutableList<SearchEdge> = mutableListOf(),
        var facts: SearchFacts? = null,
    )

    private data class SearchEdge(
        val action: PlayerAction,
        val result: ResolutionResult?,
        val child: SearchNode?,
        var diagnostic: MagneticDependencyExplanation? = null,
    )

    private data class SearchFacts(
        val solvable: Boolean,
        val complete: Boolean,
        val minimumDepth: Int?,
        val shortestSolutionCount: Int,
        val shortestCountCapped: Boolean,
        val solutionFamilies: Set<String>,
        val familyCountCapped: Boolean,
        val deadEndProofDepth: Int?,
    )

    private class SearchBudget(private val config: PuzzleSearchConfig) {
        var actionResolutions: Int = 0
        var diagnosticChecks: Int = 0
        var diagnosticCapped: Boolean = false
        var shortestCountCapped: Boolean = false
        var familyCountCapped: Boolean = false
        val truncationReasons = linkedSetOf<String>()

        fun truncate(reason: String) {
            truncationReasons += reason
        }
    }
}
