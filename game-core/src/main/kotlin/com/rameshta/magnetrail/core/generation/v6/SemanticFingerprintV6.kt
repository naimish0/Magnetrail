package com.rameshta.magnetrail.core.generation.v6

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.content.BoardSymmetry
import com.rameshta.magnetrail.core.model.LevelDefinition

const val V61_STRATEGY_CLUSTER_SCHEMA_VERSION = 2

data class CanonicalGraphEdgeV6(
    val from: Int,
    val to: Int,
    val label: String,
    val directed: Boolean = false,
)

data class CanonicalColoredGraphV6(
    val vertexColors: List<String>,
    val edges: List<CanonicalGraphEdgeV6>,
) {
    init {
        require(edges.all { it.from in vertexColors.indices && it.to in vertexColors.indices })
    }
}

data class CanonicalGraphResultV6(
    val canonicalSerialization: String?,
    val exploredBacktrackingStates: Int,
    val complete: Boolean,
)

/**
 * Exact coloured-graph canonicalization. Colour refinement is only pruning: unresolved cells are
 * deterministically individualized until every vertex has a unique colour, and the lexicographic
 * minimum full adjacency serialization is retained. A budget hit is an explicit rejection.
 */
class ExactColoredGraphCanonicalizerV6(
    private val maxBacktrackingStates: Int = 200_000,
) {
    init {
        require(maxBacktrackingStates > 0)
    }

    fun canonicalize(graph: CanonicalColoredGraphV6): CanonicalGraphResultV6 {
        if (graph.vertexColors.isEmpty()) return CanonicalGraphResultV6("V=|E=", 1, true)
        var explored = 0
        var best: String? = null
        var complete = true

        fun visit(colors: List<String>) {
            if (!complete) return
            if (explored >= maxBacktrackingStates) {
                complete = false
                return
            }
            explored += 1
            val refined = refine(graph, colors)
            val cells = refined.indices.groupBy { refined[it] }.values.filter { it.size > 1 }
            if (cells.isEmpty()) {
                val serialization = serialize(graph, refined)
                if (best == null || serialization < requireNotNull(best)) best = serialization
                return
            }
            val cell = cells.minWith(compareBy<List<Int>> { it.size }.thenBy { group -> refined[group.first()] })
            cell.sorted().forEach { vertex ->
                val individualized = refined.toMutableList()
                individualized[vertex] = "${individualized[vertex]}|INDIVIDUAL:${cell.size}"
                visit(individualized)
            }
        }
        visit(graph.vertexColors)
        return CanonicalGraphResultV6(best.takeIf { complete }, explored, complete)
    }

    private fun refine(graph: CanonicalColoredGraphV6, initial: List<String>): List<String> {
        var colors = initial
        while (true) {
            val signatures = colors.indices.map { vertex ->
                val neighbours = graph.edges.flatMap { edge ->
                    when {
                        edge.from == vertex -> listOf(
                            "${if (edge.directed) "OUT" else "UND"}:${escape(edge.label)}:${escape(colors[edge.to])}",
                        )
                        edge.to == vertex -> listOf(
                            "${if (edge.directed) "IN" else "UND"}:${escape(edge.label)}:${escape(colors[edge.from])}",
                        )
                        else -> emptyList()
                    }
                }.sorted()
                "${escape(colors[vertex])}|${neighbours.joinToString(",")}" 
            }
            val ordered = signatures.distinct().sorted()
            val remapped = signatures.map { signature -> "C${ordered.binarySearch(signature)}" }
            if (samePartition(colors, remapped)) return remapped
            colors = remapped
        }
    }

    private fun samePartition(first: List<String>, second: List<String>): Boolean = first.indices.all { left ->
        first.indices.all { right -> (first[left] == first[right]) == (second[left] == second[right]) }
    }

    private fun serialize(graph: CanonicalColoredGraphV6, colors: List<String>): String {
        val order = colors.indices.sortedWith(compareBy<Int> { colors[it] }.thenBy { it })
        val canonicalIndex = order.withIndex().associate { (index, vertex) -> vertex to index }
        val vertices = order.joinToString(";") { escape(graph.vertexColors[it]) }
        val edges = graph.edges.map { edge ->
            val from = requireNotNull(canonicalIndex[edge.from])
            val to = requireNotNull(canonicalIndex[edge.to])
            if (edge.directed || from <= to) {
                "${if (edge.directed) "D" else "U"}:$from:$to:${escape(edge.label)}"
            } else {
                "U:$to:$from:${escape(edge.label)}"
            }
        }.sorted().joinToString(";")
        return "V=$vertices|E=$edges"
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("|", "\\|")
        .replace(";", "\\;")
        .replace(":", "\\:")
}

class SemanticFingerprintBuilderV6(
    private val canonicalizer: ExactColoredGraphCanonicalizerV6 = ExactColoredGraphCanonicalizerV6(),
) {
    fun build(
        level: LevelDefinition,
        spec: CausalHypergraphSpec,
        decisionDag: DecisionDagAnalysisV6,
        occupancy: PurposefulOccupancyV6,
        archive: Collection<V6FingerprintBundle> = emptyList(),
    ): Result<V6FingerprintBundle> = runCatching {
        require(decisionDag.complete && decisionDag.metrics != null) { "decision DAG is incomplete" }
        require(occupancy.analysisComplete) { "purposeful occupancy analysis is incomplete" }
        val causal = requireCanonical(causalGraph(spec), "causal hypergraph")
        val dag = canonicalDecisionDag(decisionDag)
        val policy = requireCanonical(solutionPolicyGraph(decisionDag), "solution policy")
        val relevancePruned = relevancePruned(level, occupancy)
        val metrics = requireNotNull(decisionDag.metrics)
        val descriptor = listOf(
            metrics.meaningfulDecisionCount.toDouble(),
            metrics.meaningfulNonCommutingDecisionCount.toDouble(),
            metrics.successfulLosingBranchCount.toDouble(),
            metrics.maximumDelayedDeadlockDepth.toDouble(),
            metrics.minimumLookaheadProofDepth.toDouble(),
            metrics.polarityMemorySpan.toDouble(),
            metrics.partialOrderWidth.toDouble(),
            metrics.hardestWinningChoiceShare,
            occupancy.purposefulOccupiedRatio,
            logarithmicBucket(metrics.reachableStateCount).toDouble(),
            logarithmicBucket(metrics.winningSolutionPolicyClasses).toDouble(),
            metrics.mandatoryPrecedence.size.toDouble(),
            metrics.transitiveReduction.size.toDouble(),
            ((metrics.longestCompletion ?: 0) - (metrics.shortestCompletion ?: 0)).toDouble(),
            metrics.forcedRunLengths.size.toDouble(),
            (metrics.forcedRunLengths.maxOrNull() ?: 0).toDouble(),
            metrics.losingStateCount.toDouble() / maxOf(1, metrics.reachableStateCount),
            metrics.mechanicRhythm.distinct().size.toDouble(),
        )
        val clusterComponents = descriptor.mapIndexed { index, value ->
            when (index) {
                7, 8, 16 -> (value * 10.0).toInt().coerceIn(0, 10)
                else -> value.toInt().coerceIn(0, 9)
            }
        }
        val base = V6FingerprintBundle(
            exactLayout = ContentFingerprint.exact(level),
            d4Layout = ContentFingerprint.symmetryNormalized(level),
            arrowLayout = ContentFingerprint.arrowLayoutSymmetryNormalized(level),
            interactiveLayout = ContentFingerprint.interactiveLayoutSymmetryNormalized(level),
            perceptualLayout = ContentFingerprint.perceptualTemplateSignature(level),
            relevancePrunedD4Layout = ContentFingerprint.symmetryNormalized(relevancePruned),
            causalHypergraph = "sha256:${sha256V6(causal)}",
            quotientDecisionDag = "sha256:${sha256V6(dag)}",
            solutionPolicy = "sha256:${sha256V6(policy)}",
            meaningfulDecisionTrace = "sha256:${sha256V6(metrics.meaningfulDecisionTrace.joinToString("||"))}",
            mechanicRhythm = "sha256:${sha256V6(metrics.mechanicRhythm.joinToString("||"))}",
            behaviouralDescriptor = descriptor,
            productionStateTransitionTrace = "sha256:${sha256V6("PRODUCTION_TRACE_V1|$dag")}",
            causalFamilyIdentifier = spec.family.name,
            strategyBehaviourClusterIdentifier = "v61b$V61_STRATEGY_CLUSTER_SCHEMA_VERSION-" +
                clusterComponents.joinToString("-"),
        )
        val nearest = archive.map { existing -> existing to semanticSimilarityV61(base, existing) }
            .maxWithOrNull(compareBy<Pair<V6FingerprintBundle, Double>> { it.second }.thenBy { it.first.exactLayout })
        base.copy(
            nearestSemanticFingerprint = nearest?.first?.exactLayout,
            nearestSemanticSimilarity = nearest?.second,
        )
    }

    fun causalSignature(spec: CausalHypergraphSpec): String =
        "sha256:${sha256V6(requireCanonical(causalGraph(spec), "causal hypergraph"))}"

    fun decisionDagSignature(analysis: DecisionDagAnalysisV6): String =
        "sha256:${sha256V6(canonicalDecisionDag(analysis))}"

    fun solutionPolicySignature(analysis: DecisionDagAnalysisV6): String =
        "sha256:${sha256V6(requireCanonical(solutionPolicyGraph(analysis), "solution policy"))}"

    private fun logarithmicBucket(value: Int): Int {
        var remaining = value.coerceAtLeast(0)
        var bucket = 0
        while (remaining > 1 && bucket < 9) {
            remaining = (remaining + 1) / 2
            bucket += 1
        }
        return bucket
    }

    private fun causalGraph(spec: CausalHypergraphSpec): CanonicalColoredGraphV6 {
        val colors = mutableListOf<String>()
        val edges = mutableListOf<CanonicalGraphEdgeV6>()
        val entityIndex = spec.entities.associate { role -> role.key to colors.addVertex("ENTITY:${role.kind}:C${role.chain}") }
        val actionIndex = spec.actions.associate { action ->
            action.key to colors.addVertex("ACTION:${action.kind}:C${action.chain}").also { index ->
                edges += CanonicalGraphEdgeV6(index, requireNotNull(entityIndex[action.arrowRoleKey]), "OPERATES")
            }
        }
        spec.hyperedges.forEach { hyperedge ->
            val hyperIndex = colors.addVertex("HYPEREDGE:${hyperedge.effect}:${hyperedge.requiredCounterfactual}")
            edges += CanonicalGraphEdgeV6(
                hyperIndex,
                requireNotNull(actionIndex[hyperedge.triggeringActionRoleKey]),
                "TRIGGER",
                directed = true,
            )
            hyperedge.affectedRoleKeys.forEach { key ->
                val affected = entityIndex[key] ?: actionIndex[key]
                    ?: error("Unknown affected causal role $key")
                edges += CanonicalGraphEdgeV6(hyperIndex, affected, "AFFECTS", directed = true)
            }
            hyperedge.preconditions.forEach { condition ->
                val conditionIndex = colors.addVertex("CONDITION:${condition.type}")
                edges += CanonicalGraphEdgeV6(conditionIndex, hyperIndex, "PRECONDITION", directed = true)
                val subject = entityIndex[condition.subjectRoleKey] ?: actionIndex[condition.subjectRoleKey]
                    ?: error("Unknown condition role ${condition.subjectRoleKey}")
                edges += CanonicalGraphEdgeV6(conditionIndex, subject, "SUBJECT", directed = true)
                condition.valueRoleKey?.let { valueKey ->
                    val value = entityIndex[valueKey] ?: actionIndex[valueKey]
                        ?: error("Unknown condition value role $valueKey")
                    edges += CanonicalGraphEdgeV6(conditionIndex, value, "VALUE", directed = true)
                }
            }
        }
        spec.solutionPartialOrder.precedence.forEach { (before, after) ->
            edges += CanonicalGraphEdgeV6(
                requireNotNull(actionIndex[before]),
                requireNotNull(actionIndex[after]),
                "PRECEDES",
                directed = true,
            )
        }
        return CanonicalColoredGraphV6(colors, edges)
    }

    private fun decisionGraph(analysis: DecisionDagAnalysisV6): CanonicalColoredGraphV6 {
        val colors = mutableListOf<String>()
        val edges = mutableListOf<CanonicalGraphEdgeV6>()
        val rootLevel = stateLevel(requireNotNull(analysis.nodes[analysis.rootStateKey]))
        val rootOrientations = BoardSymmetry.entries.map { symmetry ->
            symmetry to ContentFingerprint.canonicalBoard(transform(rootLevel, symmetry))
        }
        val canonicalRoot = rootOrientations.minOf { it.second }
        val canonicalOrientations = rootOrientations.filter { it.second == canonicalRoot }.map { it.first }
        val nodeIndex = analysis.nodes.values.sortedBy { it.stateKey }.associate { node ->
            val pull = node.state.magnets.count { it.polarity.name == "PULL" }
            val push = node.state.magnets.size - pull
            val visibleState = canonicalOrientations.minOf { symmetry ->
                ContentFingerprint.canonicalBoard(transform(stateLevel(node), symmetry))
            }
            node.stateKey to colors.addVertex(
                "STATE:A${node.state.arrows.size}:P$pull:U$push:S${node.solvable}:" +
                    "W${node.state.arrows.isEmpty()}:VISIBLE=$visibleState",
            )
        }
        analysis.nodes.values.sortedBy { it.stateKey }.forEach { node ->
            node.transitions.sortedWith(compareBy({ it.arrowId }, { it.successful })).forEach { transition ->
                val transitionIndex = colors.addVertex(
                    listOf(
                        "TRANSITION",
                        transition.successful,
                        transition.terminalEvent,
                        transition.controllerId != null,
                        transition.polarityChange != null,
                        transition.route.size,
                        transition.futureSolvable,
                        transition.delayedDeadlockDepth,
                    ).joinToString(":"),
                )
                edges += CanonicalGraphEdgeV6(requireNotNull(nodeIndex[node.stateKey]), transitionIndex, "ACTION", true)
                transition.childStateKey?.let { child ->
                    edges += CanonicalGraphEdgeV6(transitionIndex, requireNotNull(nodeIndex[child]), "RESULT", true)
                }
            }
        }
        return CanonicalColoredGraphV6(colors, edges)
    }

    /**
     * Exact decision-DAG serialization. State identity is the complete production BoardState in a
     * root-selected D4 frame; transition identity uses only visible geometry and production
     * effects. Root symmetries that tie are all serialized and the lexicographic minimum wins.
     */
    private fun canonicalDecisionDag(analysis: DecisionDagAnalysisV6): String {
        require(analysis.complete)
        val rootLevel = stateLevel(requireNotNull(analysis.nodes[analysis.rootStateKey]))
        val orientations = canonicalRootOrientations(rootLevel)
        return orientations.minOf { symmetry ->
            val stateKeys = analysis.nodes.values.associate { node ->
                node.stateKey to ContentFingerprint.canonicalBoard(transform(stateLevel(node), symmetry))
            }
            analysis.nodes.values.map { node ->
                val state = requireNotNull(stateKeys[node.stateKey])
                val transitions = node.transitions.map { transition ->
                    val arrow = requireNotNull(node.state.arrow(transition.arrowId))
                    val arrowPosition = symmetry.transform(arrow.position, node.state.width, node.state.height)
                    val arrowDirection = symmetry.transform(arrow.printedDirection)
                    val controller = transition.controllerId?.let(node.state::magnet)
                    val controllerKey = controller?.let { magnet ->
                        val position = symmetry.transform(magnet.position, node.state.width, node.state.height)
                        "${position.row},${position.column}:${magnet.polarity}"
                    } ?: "NONE"
                    val route = transition.route.joinToString(";") { position ->
                        val transformed = symmetry.transform(position, node.state.width, node.state.height)
                        "${transformed.row},${transformed.column}"
                    }
                    listOf(
                        "A=${arrowPosition.row},${arrowPosition.column}:${arrowDirection.code}",
                        "OK=${transition.successful}",
                        "C=$controllerKey",
                        "R=$route",
                        "T=${transition.terminalEvent}",
                        "P=${transition.polarityChange != null}",
                        "F=${transition.futureSolvable}",
                        "D=${transition.delayedDeadlockDepth}",
                        "N=${transition.childStateKey?.let(stateKeys::get) ?: "NONE"}",
                    ).joinToString("|")
                }.sorted().joinToString(";;")
                "$state=>$transitions"
            }.sorted().joinToString("\n")
        }
    }

    private fun canonicalRootOrientations(root: LevelDefinition): List<BoardSymmetry> {
        val serialized = BoardSymmetry.entries.map { symmetry ->
            symmetry to ContentFingerprint.canonicalBoard(transform(root, symmetry))
        }
        val minimum = serialized.minOf { it.second }
        return serialized.filter { it.second == minimum }.map { it.first }
    }

    private fun stateLevel(node: DecisionNodeV6): LevelDefinition = LevelDefinition(
        id = "semantic-state",
        number = 1,
        title = "Semantic state",
        width = node.state.width,
        height = node.state.height,
        arrows = node.state.arrows,
        magnets = node.state.magnets,
        walls = node.state.walls,
        designedSolutions = emptyList(),
    )

    private fun transform(level: LevelDefinition, symmetry: BoardSymmetry): LevelDefinition = level.copy(
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

    private fun solutionPolicyGraph(analysis: DecisionDagAnalysisV6): CanonicalColoredGraphV6 {
        val metrics = requireNotNull(analysis.metrics)
        val actionIds = analysis.nodes[analysis.rootStateKey]?.state?.arrows?.map { it.id }.orEmpty().sorted()
        val colors = actionIds.map { actionId ->
            val directIn = metrics.transitiveReduction.count { it.second == actionId }
            val directOut = metrics.transitiveReduction.count { it.first == actionId }
            val transitiveIn = metrics.mandatoryPrecedence.count { it.second == actionId }
            val transitiveOut = metrics.mandatoryPrecedence.count { it.first == actionId }
            val effects = analysis.nodes.values.flatMap { node ->
                node.transitions.filter { it.arrowId == actionId }.map { transition ->
                    listOf(
                        transition.successful,
                        transition.terminalEvent,
                        transition.controllerId != null,
                        transition.polarityChange != null,
                        transition.route.size,
                        transition.futureSolvable,
                        transition.delayedDeadlockDepth,
                    ).joinToString(":")
                }
            }.sorted().joinToString(",")
            "ACTION:DI$directIn:DO$directOut:TI$transitiveIn:TO$transitiveOut:E=$effects"
        }.toMutableList()
        val index = actionIds.withIndex().associate { it.value to it.index }
        val edges = metrics.mandatoryPrecedence.map { (before, after) ->
            CanonicalGraphEdgeV6(requireNotNull(index[before]), requireNotNull(index[after]), "PRECEDES", true)
        }
        return CanonicalColoredGraphV6(colors, edges)
    }

    private fun relevancePruned(level: LevelDefinition, occupancy: PurposefulOccupancyV6): LevelDefinition {
        val keys = occupancy.objectWitnesses.filterValues { it.isNotEmpty() }.keys
        return level.copy(
            arrows = level.arrows.filter { "arrow:${it.id}" in keys },
            magnets = level.magnets.filter { "magnet:${it.id}" in keys },
            walls = level.walls.filter { "wall:${it.position.row},${it.position.column}" in keys },
            designedSolutions = emptyList(),
            metadata = null,
        )
    }

    private fun requireCanonical(graph: CanonicalColoredGraphV6, label: String): String {
        val result = canonicalizer.canonicalize(graph)
        require(result.complete) { "$label canonicalization cap after ${result.exploredBacktrackingStates} states" }
        return requireNotNull(result.canonicalSerialization)
    }

    private fun MutableList<String>.addVertex(color: String): Int = size.also { add(color) }
}
