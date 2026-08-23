package com.rameshta.magnetrail.core.generation.v6

import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.engine.ResolutionResult
import com.rameshta.magnetrail.core.engine.TerminalEvent
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.Position
import java.security.MessageDigest
import java.util.ArrayDeque

data class DecisionTransitionV6(
    val arrowId: String,
    val successful: Boolean,
    val childStateKey: String?,
    val controllerId: String?,
    val route: List<Position>,
    val terminalEvent: String,
    val polarityChange: String?,
    var futureSolvable: Boolean? = null,
    var delayedDeadlockDepth: Int? = null,
)

data class DecisionNodeV6(
    val stateKey: String,
    val state: BoardState,
    val transitions: MutableList<DecisionTransitionV6> = mutableListOf(),
    var solvable: Boolean? = null,
    var shortestCompletion: Int? = null,
    var longestCompletion: Int? = null,
)

data class CommutationEvidenceV6(
    val stateKey: String,
    val firstArrowId: String,
    val secondArrowId: String,
    val commutes: Boolean,
    val bothOrdersSucceed: Boolean,
    val identicalFinalState: Boolean,
    val equivalentEffectMultiset: Boolean,
    val equivalentFutureSolvability: Boolean,
)

data class DecisionDagMetricsV6(
    val reachableStateCount: Int,
    val successfulTransitionCount: Int,
    val failedActionAnnotationCount: Int,
    val winningStateCount: Int,
    val losingStateCount: Int,
    val shortestCompletion: Int?,
    val longestCompletion: Int?,
    val winningSolutionPolicyClasses: Int,
    val mandatoryPrecedence: Set<Pair<String, String>>,
    val transitiveReduction: Set<Pair<String, String>>,
    val partialOrderWidth: Int,
    val forcedRunLengths: List<Int>,
    val meaningfulDecisionCount: Int,
    val meaningfulNonCommutingDecisionCount: Int,
    val successfulLosingBranchCount: Int,
    val maximumDelayedDeadlockDepth: Int,
    val polarityMemorySpan: Int,
    val minimumLookaheadProofDepth: Int,
    val hardestWinningChoiceShare: Double,
    val winningChoiceShares: List<Double>,
    val meaningfulDecisionTrace: List<String>,
    val mechanicRhythm: List<String>,
)

data class DecisionDagAnalysisV6(
    val nodes: Map<String, DecisionNodeV6>,
    val rootStateKey: String,
    val complete: Boolean,
    val truncationReasons: List<String>,
    val actionResolutionCount: Int,
    val commutation: List<CommutationEvidenceV6>,
    val metrics: DecisionDagMetricsV6?,
)

class CompleteDecisionDagAnalyzerV6(
    private val engine: GameEngine,
    private val maxStates: Int = 200_000,
    private val maxActionResolutions: Int = 2_000_000,
) {
    init {
        require(maxStates > 0 && maxActionResolutions > 0)
    }

    fun analyze(initialState: BoardState): DecisionDagAnalysisV6 {
        val nodes = linkedMapOf<String, DecisionNodeV6>()
        val truncationReasons = linkedSetOf<String>()
        var resolutions = 0

        fun expand(state: BoardState): DecisionNodeV6? {
            val key = exactStateKeyV6(state)
            nodes[key]?.let { return it }
            if (nodes.size >= maxStates) {
                truncationReasons += "DECISION_DAG_STATE_CAP:$maxStates"
                return null
            }
            val node = DecisionNodeV6(key, state)
            nodes[key] = node
            if (state.arrows.isEmpty()) return node
            state.arrows.sortedBy { it.id }.forEach { arrow ->
                if (resolutions >= maxActionResolutions) {
                    truncationReasons += "DECISION_DAG_RESOLUTION_CAP:$maxActionResolutions"
                    return@forEach
                }
                resolutions += 1
                val result = engine.resolve(state, PlayerAction(arrow.id))
                val child = if (result.success) expand(result.resultingState) else null
                if (result.success && child == null) return@forEach
                node.transitions += result.toTransition(child?.stateKey)
            }
            return node
        }

        val root = requireNotNull(expand(initialState))
        val complete = truncationReasons.isEmpty()
        if (!complete) {
            return DecisionDagAnalysisV6(
                nodes = nodes,
                rootStateKey = root.stateKey,
                complete = false,
                truncationReasons = truncationReasons.toList(),
                actionResolutionCount = resolutions,
                commutation = emptyList(),
                metrics = null,
            )
        }

        fun solve(node: DecisionNodeV6): Boolean {
            node.solvable?.let { return it }
            if (node.state.arrows.isEmpty()) {
                node.solvable = true
                node.shortestCompletion = 0
                node.longestCompletion = 0
                return true
            }
            val successful = node.transitions.filter { it.successful }
            val viableDepths = mutableListOf<Pair<Int, Int>>()
            successful.forEach { edge ->
                val child = requireNotNull(nodes[edge.childStateKey])
                val viable = solve(child)
                edge.futureSolvable = viable
                if (viable) {
                    viableDepths += requireNotNull(child.shortestCompletion) + 1 to
                        (requireNotNull(child.longestCompletion) + 1)
                } else {
                    edge.delayedDeadlockDepth = distanceToDeadlock(child, nodes)
                }
            }
            node.solvable = viableDepths.isNotEmpty()
            node.shortestCompletion = viableDepths.minOfOrNull { it.first }
            node.longestCompletion = viableDepths.maxOfOrNull { it.second }
            return node.solvable == true
        }
        solve(root)

        val commutation = nodes.values.flatMap { node -> commutationAt(node, nodes) }
        val metrics = buildMetrics(root, nodes, commutation)
        return DecisionDagAnalysisV6(
            nodes = nodes,
            rootStateKey = root.stateKey,
            complete = true,
            truncationReasons = emptyList(),
            actionResolutionCount = resolutions,
            commutation = commutation,
            metrics = metrics,
        )
    }

    private fun buildMetrics(
        root: DecisionNodeV6,
        nodes: Map<String, DecisionNodeV6>,
        commutation: List<CommutationEvidenceV6>,
    ): DecisionDagMetricsV6 {
        val successful = nodes.values.flatMap { it.transitions }.filter { it.successful }
        val strategicTransitions = nodes.values.filter { it.solvable == true }.flatMap { it.transitions }
            .filter { it.successful }
        val failed = nodes.values.sumOf { node -> node.transitions.count { !it.successful } }
        val decisions = nodes.values.filter { node ->
            val choices = node.transitions.filter { it.successful }
            node.solvable == true && choices.size >= 2 && (
                choices.map { it.futureSolvable }.distinct().size > 1 ||
                    commutation.any { evidence -> evidence.stateKey == node.stateKey && !evidence.commutes }
                )
        }
        val decisionKeys = decisions.mapTo(hashSetOf()) { it.stateKey }
        val easiestWitness = easiestWinningWitness(root, nodes, decisionKeys)
        val easiestNodes = easiestWitness.map { requireNotNull(nodes[it.first]) }
        val easiestDecisions = easiestNodes.filter { it.stateKey in decisionKeys }
        val nonCommutingDecisionCount = easiestDecisions.count { node ->
            commutation.any { evidence -> evidence.stateKey == node.stateKey && !evidence.commutes }
        }
        val shares = easiestDecisions.mapNotNull { node ->
            val choices = node.transitions.filter { it.successful }
            choices.takeIf { it.isNotEmpty() }?.let { edges ->
                edges.count { it.futureSolvable == true }.toDouble() / edges.size
            }
        }
        val precedence = mandatoryPrecedence(root, nodes)
        val reduction = transitiveReduction(precedence)
        val arrowIds = root.state.arrows.map { it.id }.sorted()
        val policySignatureCounts = nodes.values.filter { it.solvable == true }.map { node ->
            node.transitions.filter { it.successful && it.futureSolvable == true }
                .map { it.effectSignature() }
                .sorted()
                .joinToString("&")
        }.toSet().size
        val meaningfulTrace = easiestDecisions.map { node ->
            node.transitions.filter { it.successful }.sortedBy { it.arrowId }.joinToString(",") { edge ->
                val outcome = if (edge.futureSolvable == true) "W" else "L${edge.delayedDeadlockDepth ?: 0}"
                "${edge.effectSignature()}:$outcome"
            }
        }
        val rhythm = winningWitness(root, nodes).map { edge ->
            listOf(
                if (edge.controllerId == null) "FREE" else "CONTROLLED",
                edge.terminalEvent,
                if (edge.polarityChange == null) "STABLE" else "FLIP",
            ).joinToString(":")
        }
        val forcedRuns = forcedRuns(root, nodes)
        val polaritySpan = polarityMemorySpan(root, nodes)
        val easiestLosingTransitions = easiestNodes.flatMap { node ->
            node.transitions.filter { it.successful && it.futureSolvable == false }
        }
        val maxDelayed = easiestLosingTransitions.mapNotNull { it.delayedDeadlockDepth }.maxOrNull() ?: 0
        return DecisionDagMetricsV6(
            reachableStateCount = nodes.size,
            successfulTransitionCount = successful.size,
            failedActionAnnotationCount = failed,
            winningStateCount = nodes.values.count { it.solvable == true },
            losingStateCount = nodes.values.count { it.solvable == false },
            shortestCompletion = root.shortestCompletion,
            longestCompletion = root.longestCompletion,
            winningSolutionPolicyClasses = policySignatureCounts,
            mandatoryPrecedence = precedence,
            transitiveReduction = reduction,
            partialOrderWidth = posetWidth(arrowIds, precedence),
            forcedRunLengths = forcedRuns,
            meaningfulDecisionCount = easiestDecisions.size,
            meaningfulNonCommutingDecisionCount = nonCommutingDecisionCount,
            successfulLosingBranchCount = easiestLosingTransitions.size,
            maximumDelayedDeadlockDepth = maxDelayed,
            polarityMemorySpan = polaritySpan,
            minimumLookaheadProofDepth = easiestLosingTransitions
                .maxOfOrNull { (it.delayedDeadlockDepth ?: 0) + 1 } ?: 0,
            hardestWinningChoiceShare = shares.minOrNull() ?: 1.0,
            winningChoiceShares = shares,
            meaningfulDecisionTrace = meaningfulTrace,
            mechanicRhythm = rhythm,
        )
    }

    /**
     * Returns the winning witness with the fewest meaningful decisions. Ties are stable and prefer
     * the child whose remaining policy is cheapest, so independent commuting prefixes cannot make
     * a board appear harder merely because the complete DAG contains many permutations.
     */
    private fun easiestWinningWitness(
        root: DecisionNodeV6,
        nodes: Map<String, DecisionNodeV6>,
        decisionKeys: Set<String>,
    ): List<Pair<String, DecisionTransitionV6>> {
        data class Cost(val decisions: Int, val maximumLookahead: Int, val traps: Int, val length: Int)

        val costs = hashMapOf<String, Cost>()
        fun cost(node: DecisionNodeV6): Cost {
            costs[node.stateKey]?.let { return it }
            if (node.state.arrows.isEmpty()) return Cost(0, 0, 0, 0).also { costs[node.stateKey] = it }
            val localTraps = node.transitions.filter { it.successful && it.futureSolvable == false }
            val localLookahead = localTraps.maxOfOrNull { (it.delayedDeadlockDepth ?: 0) + 1 } ?: 0
            val child = node.transitions.filter { it.successful && it.futureSolvable == true }
                .map { transition -> transition to cost(requireNotNull(nodes[transition.childStateKey])) }
                .minWithOrNull(
                    compareBy<Pair<DecisionTransitionV6, Cost>> { it.second.decisions }
                        .thenBy { it.second.maximumLookahead }
                        .thenBy { it.second.traps }
                        .thenBy { it.second.length }
                        .thenBy { it.first.effectSignature() }
                        .thenBy { it.first.arrowId },
                )?.second ?: Cost(Int.MAX_VALUE / 4, Int.MAX_VALUE / 4, Int.MAX_VALUE / 4, Int.MAX_VALUE / 4)
            return Cost(
                decisions = child.decisions + if (node.stateKey in decisionKeys) 1 else 0,
                maximumLookahead = maxOf(localLookahead, child.maximumLookahead),
                traps = localTraps.size + child.traps,
                length = child.length + 1,
            ).also { costs[node.stateKey] = it }
        }

        cost(root)
        val witness = mutableListOf<Pair<String, DecisionTransitionV6>>()
        var current = root
        while (current.state.arrows.isNotEmpty()) {
            val next = current.transitions.filter { it.successful && it.futureSolvable == true }
                .minWithOrNull(
                    compareBy<DecisionTransitionV6> { cost(requireNotNull(nodes[it.childStateKey])).decisions }
                        .thenBy { cost(requireNotNull(nodes[it.childStateKey])).maximumLookahead }
                        .thenBy { cost(requireNotNull(nodes[it.childStateKey])).traps }
                        .thenBy { cost(requireNotNull(nodes[it.childStateKey])).length }
                        .thenBy { it.effectSignature() }
                        .thenBy { it.arrowId },
                ) ?: break
            witness += current.stateKey to next
            current = requireNotNull(nodes[next.childStateKey])
        }
        return witness
    }

    private fun commutationAt(
        node: DecisionNodeV6,
        nodes: Map<String, DecisionNodeV6>,
    ): List<CommutationEvidenceV6> {
        val firstEdges = node.transitions.filter { it.successful }.sortedBy { it.arrowId }
        return buildList {
            for (firstIndex in firstEdges.indices) {
                for (secondIndex in firstIndex + 1 until firstEdges.size) {
                    val first = firstEdges[firstIndex]
                    val second = firstEdges[secondIndex]
                    val afterFirst = requireNotNull(nodes[first.childStateKey])
                    val afterSecond = requireNotNull(nodes[second.childStateKey])
                    val secondThen = afterFirst.transitions.singleOrNull { it.arrowId == second.arrowId && it.successful }
                    val firstThen = afterSecond.transitions.singleOrNull { it.arrowId == first.arrowId && it.successful }
                    val both = secondThen != null && firstThen != null
                    val identical = both && secondThen?.childStateKey == firstThen?.childStateKey
                    val effects = both && listOf(first.effectSignature(), requireNotNull(secondThen).effectSignature()).sorted() ==
                        listOf(second.effectSignature(), requireNotNull(firstThen).effectSignature()).sorted()
                    val future = both && nodes[secondThen?.childStateKey]?.solvable == nodes[firstThen?.childStateKey]?.solvable
                    add(
                        CommutationEvidenceV6(
                            stateKey = node.stateKey,
                            firstArrowId = first.arrowId,
                            secondArrowId = second.arrowId,
                            commutes = both && identical && effects && future,
                            bothOrdersSucceed = both,
                            identicalFinalState = identical,
                            equivalentEffectMultiset = effects,
                            equivalentFutureSolvability = future,
                        ),
                    )
                }
            }
        }
    }

    private fun mandatoryPrecedence(
        root: DecisionNodeV6,
        nodes: Map<String, DecisionNodeV6>,
    ): Set<Pair<String, String>> {
        val ids = root.state.arrows.map { it.id }.sorted()
        val winningStates = nodes.values.filter { it.solvable == true }
        return buildSet {
            ids.forEach { first ->
                ids.forEach { second ->
                    if (first == second) return@forEach
                    val firstBeforeSecondPossible = winningStates.any { state ->
                        state.state.arrow(first) == null && state.state.arrow(second) != null
                    }
                    val secondBeforeFirstPossible = winningStates.any { state ->
                        state.state.arrow(second) == null && state.state.arrow(first) != null
                    }
                    if (firstBeforeSecondPossible && !secondBeforeFirstPossible) add(first to second)
                }
            }
        }
    }

    private fun transitiveReduction(relation: Set<Pair<String, String>>): Set<Pair<String, String>> = relation.filterTo(linkedSetOf()) {
        (from, to) ->
        relation.none { (left, middle) ->
            left == from && middle != to && relation.contains(middle to to)
        }
    }

    private fun posetWidth(ids: List<String>, relation: Set<Pair<String, String>>): Int {
        if (ids.size > 20) return ids.size
        var best = 0
        val subsetCount = 1 shl ids.size
        for (mask in 1 until subsetCount) {
            val size = Integer.bitCount(mask)
            if (size <= best) continue
            var antichain = true
            loop@ for (first in ids.indices) for (second in first + 1 until ids.size) {
                if (mask and (1 shl first) == 0 || mask and (1 shl second) == 0) continue
                if ((ids[first] to ids[second]) in relation || (ids[second] to ids[first]) in relation) {
                    antichain = false
                    break@loop
                }
            }
            if (antichain) best = size
        }
        return best.coerceAtLeast(if (ids.isEmpty()) 0 else 1)
    }

    private fun forcedRuns(root: DecisionNodeV6, nodes: Map<String, DecisionNodeV6>): List<Int> {
        val result = mutableListOf<Int>()
        var run = 0
        var current = root
        val visited = hashSetOf<String>()
        while (visited.add(current.stateKey) && current.state.arrows.isNotEmpty()) {
            val viable = current.transitions.filter { it.successful && it.futureSolvable == true }
            if (viable.size == 1) {
                run += 1
            } else {
                if (run > 0) result += run
                run = 0
            }
            val next = viable.minByOrNull { it.arrowId } ?: break
            current = requireNotNull(nodes[next.childStateKey])
        }
        if (run > 0) result += run
        return result
    }

    private fun polarityMemorySpan(root: DecisionNodeV6, nodes: Map<String, DecisionNodeV6>): Int {
        val witness = winningWitness(root, nodes)
        val lastAffected = mutableMapOf<String, Int>()
        var best = 0
        witness.forEachIndexed { index, edge ->
            edge.polarityChange?.substringBefore(':')?.let { magnetId -> lastAffected[magnetId] = index }
            edge.controllerId?.let { magnetId ->
                lastAffected[magnetId]?.let { flipIndex -> best = maxOf(best, index - flipIndex) }
            }
        }
        return best
    }

    private fun winningWitness(root: DecisionNodeV6, nodes: Map<String, DecisionNodeV6>): List<DecisionTransitionV6> {
        val result = mutableListOf<DecisionTransitionV6>()
        var node = root
        while (node.state.arrows.isNotEmpty()) {
            val next = node.transitions.filter { it.successful && it.futureSolvable == true }
                .minWithOrNull(compareBy<DecisionTransitionV6> { nodes[it.childStateKey]?.shortestCompletion }.thenBy { it.arrowId })
                ?: break
            result += next
            node = requireNotNull(nodes[next.childStateKey])
        }
        return result
    }

    private fun distanceToDeadlock(start: DecisionNodeV6, nodes: Map<String, DecisionNodeV6>): Int {
        if (start.state.arrows.isNotEmpty() && start.transitions.none { it.successful }) return 0
        val queue = ArrayDeque<Pair<DecisionNodeV6, Int>>()
        val seen = hashSetOf<String>()
        queue += start to 0
        while (queue.isNotEmpty()) {
            val (node, depth) = queue.removeFirst()
            if (!seen.add(node.stateKey)) continue
            val successful = node.transitions.filter { it.successful }
            if (node.state.arrows.isNotEmpty() && successful.isEmpty()) return depth
            successful.sortedBy { it.arrowId }.forEach { edge ->
                queue += requireNotNull(nodes[edge.childStateKey]) to depth + 1
            }
        }
        return 0
    }

    private fun ResolutionResult.toTransition(childKey: String?): DecisionTransitionV6 = DecisionTransitionV6(
        arrowId = selectedArrowId,
        successful = success,
        childStateKey = childKey,
        controllerId = controllingMagnetId,
        route = traversedCells,
        terminalEvent = when (terminalEvent) {
            is TerminalEvent.Exit -> "EXIT"
            is TerminalEvent.PullCapture -> "PULL_CAPTURE"
            is TerminalEvent.Collision -> "COLLISION"
            is TerminalEvent.InvalidPullExit -> "INVALID_PULL_EXIT"
        },
        polarityChange = polarityChange?.let { "${it.magnetId}:${it.from.name}>${it.to.name}" },
    )

    private fun DecisionTransitionV6.effectSignature(): String = listOf(
        if (controllerId == null) "NONE" else "M",
        route.joinToString("/") { "${it.row},${it.column}" },
        terminalEvent,
        if (polarityChange == null) "NO_FLIP" else "FLIP",
    ).joinToString("|")
}

fun exactStateKeyV6(state: BoardState): String = buildString {
    append(state.width).append('x').append(state.height).append('|')
    state.arrows.sortedBy { it.id }.forEach {
        append("A:").append(it.id).append('@').append(it.position.row).append(',').append(it.position.column)
            .append(':').append(it.printedDirection.name).append(';')
    }
    append('|')
    state.magnets.sortedBy { it.id }.forEach {
        append("M:").append(it.id).append('@').append(it.position.row).append(',').append(it.position.column)
            .append(':').append(it.polarity.name).append(';')
    }
    append('|')
    state.walls.map { it.position }.sortedWith(compareBy(Position::row, Position::column)).forEach {
        append("W@").append(it.row).append(',').append(it.column).append(';')
    }
}

internal fun sha256V6(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
