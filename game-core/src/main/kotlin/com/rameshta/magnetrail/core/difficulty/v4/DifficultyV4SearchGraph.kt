package com.rameshta.magnetrail.core.difficulty.v4

import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.engine.ResolutionResult
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.solver.StateKey

internal class V4SearchBudget(
    private val maxStates: Int,
    private val maxActionResolutions: Int,
    private val maxDepth: Int,
    private val reasonPrefix: String = "",
) {
    var expandedStates: Int = 0
        private set
    var actionResolutions: Int = 0
        private set
    val truncationReasons: MutableSet<String> = linkedSetOf()

    fun reserveState(depth: Int): Boolean {
        if (depth > maxDepth) {
            truncate("SEARCH_DEPTH_CAP")
            return false
        }
        if (expandedStates >= maxStates) {
            truncate("EXPANDED_STATE_CAP")
            return false
        }
        expandedStates += 1
        return true
    }

    fun reserveResolution(): Boolean {
        if (actionResolutions >= maxActionResolutions) {
            truncate("ACTION_RESOLUTION_CAP")
            return false
        }
        actionResolutions += 1
        return true
    }

    fun truncate(reason: String) {
        truncationReasons += reasonPrefix + reason
    }
}

internal class V4SearchGraph(
    private val engine: GameEngine,
    private val budget: V4SearchBudget,
) {
    val nodes: LinkedHashMap<StateKey, V4Node> = linkedMapOf()

    fun expand(state: BoardState, depth: Int = 0): V4Node? {
        val key = StateKey.from(state)
        nodes[key]?.let { return it }
        if (!budget.reserveState(depth)) return null
        val node = V4Node(state = state, depth = depth)
        nodes[key] = node
        if (state.arrows.isEmpty()) return node
        state.arrows.sortedBy { it.id }.forEach { arrow ->
            if (!budget.reserveResolution()) {
                node.edges += V4Edge(arrow.id, null, null)
                return@forEach
            }
            val result = engine.resolve(state, PlayerAction(arrow.id))
            val child = if (result.success) expand(result.resultingState, depth + 1) else null
            node.edges += V4Edge(arrow.id, result, child)
        }
        return node
    }

    fun facts(node: V4Node): V4Facts {
        node.facts?.let { return it }
        if (node.state.arrows.isEmpty()) {
            return V4Facts(
                solvable = true,
                complete = true,
                minimumSolutionDepth = 0,
                winningSequenceCount = 1,
                winningSequenceCountCapped = false,
                deadEndProofDepth = null,
            ).also { node.facts = it }
        }
        val successful = node.edges.filter { it.result?.success == true }
        val childFacts = successful.associateWith { edge -> edge.child?.let(::facts) }
        val complete = node.edges.none { it.result == null } && childFacts.values.all { it?.complete == true }
        val viable = successful.filter { childFacts[it]?.solvable == true }
        val solvable = when {
            viable.isNotEmpty() -> true
            complete -> false
            else -> null
        }
        val minimumDepth = viable.mapNotNull { childFacts[it]?.minimumSolutionDepth?.plus(1) }.minOrNull()
        var count = 0L
        var capped = false
        viable.forEach { edge ->
            val child = requireNotNull(childFacts[edge])
            count = saturatingAdd(count, child.winningSequenceCount)
            capped = capped || child.winningSequenceCountCapped || count == Long.MAX_VALUE
        }
        val deadEndProofDepth = if (solvable == false && complete) {
            if (successful.isEmpty()) {
                0
            } else {
                successful.mapNotNull { edge -> childFacts[edge]?.deadEndProofDepth?.plus(1) }.minOrNull()
            }
        } else {
            null
        }
        return V4Facts(
            solvable = solvable,
            complete = complete,
            minimumSolutionDepth = minimumDepth,
            winningSequenceCount = count,
            winningSequenceCountCapped = capped,
            deadEndProofDepth = deadEndProofDepth,
        ).also { node.facts = it }
    }

    fun successful(node: V4Node): List<V4Edge> = node.edges.filter { it.result?.success == true }

    fun viable(node: V4Node): List<V4Edge> = successful(node).filter { edge ->
        edge.child?.let(::facts)?.solvable == true
    }

    fun fatal(node: V4Node): List<V4Edge> = successful(node).filter { edge ->
        edge.child?.let(::facts)?.let { it.complete && it.solvable == false } == true
    }

    fun winningSequences(root: V4Node, limit: Int): SequenceEnumeration {
        val sequences = mutableListOf<List<String>>()
        var truncated = false
        fun visit(node: V4Node, prefix: MutableList<String>) {
            if (truncated) return
            if (node.state.arrows.isEmpty()) {
                if (sequences.size >= limit) {
                    truncated = true
                } else {
                    sequences += prefix.toList()
                }
                return
            }
            viable(node).sortedBy { it.actionId }.forEach { edge ->
                if (truncated) return@forEach
                prefix += edge.actionId
                visit(requireNotNull(edge.child), prefix)
                prefix.removeAt(prefix.lastIndex)
            }
        }
        visit(root, mutableListOf())
        return SequenceEnumeration(sequences, truncated)
    }

    fun node(state: BoardState): V4Node? = nodes[StateKey.from(state)]

    private fun saturatingAdd(first: Long, second: Long): Long =
        if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second
}

internal class V4Node(
    val state: BoardState,
    val depth: Int,
    val edges: MutableList<V4Edge> = mutableListOf(),
    var facts: V4Facts? = null,
)

internal class V4Edge(
    val actionId: String,
    val result: ResolutionResult?,
    val child: V4Node?,
)

internal data class V4Facts(
    val solvable: Boolean?,
    val complete: Boolean,
    val minimumSolutionDepth: Int?,
    val winningSequenceCount: Long,
    val winningSequenceCountCapped: Boolean,
    val deadEndProofDepth: Int?,
)

internal data class SequenceEnumeration(
    val sequences: List<List<String>>,
    val truncated: Boolean,
)
