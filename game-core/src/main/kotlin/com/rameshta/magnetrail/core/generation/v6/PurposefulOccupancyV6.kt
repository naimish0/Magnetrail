package com.rameshta.magnetrail.core.generation.v6

import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.engine.ResolutionResult
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Position

enum class SemanticParticipationV6 {
    SELECTED_IN_WINNING_POLICY,
    MOVEMENT_ROUTE,
    COLLISION,
    CONTROLLER_SELECTION,
    EFFECTIVE_DIRECTION,
    LINE_OF_SIGHT,
    OCCLUSION,
    CANCELLATION,
    POLARITY_MUTATION,
    MANDATORY_ORDERING,
    SUCCESSFUL_PERSISTENT_TRAP,
    NON_COMMUTING_DECISION,
    COUNTERFACTUAL_OUTCOME_CHANGE,
    EMPTY_ROUTE_OR_SEPARATION,
}

data class ObjectSemanticWitnessV6(
    val objectKey: String,
    val participation: SemanticParticipationV6,
    val stateKey: String,
    val actionArrowId: String,
    val evidence: String,
)

data class PurposefulOccupancyV6(
    val occupiedRatio: Double,
    val purposefulOccupiedRatio: Double,
    val inertOccupiedRatio: Double,
    val purposefulArrowRatio: Double,
    val purposefulMagnetRatio: Double,
    val purposefulWallRatio: Double,
    val objectWitnesses: Map<String, List<ObjectSemanticWitnessV6>>,
    val purposefulEmptyCells: Map<Position, String>,
    val analysisComplete: Boolean,
    val counterfactualChecks: Int,
    val rejectionReasons: List<String>,
)

class PurposefulOccupancyAnalyzerV6(
    private val engine: GameEngine,
    private val maxCounterfactualChecks: Int = 500_000,
) {
    init {
        require(maxCounterfactualChecks > 0)
    }

    fun analyze(
        level: LevelDefinition,
        decisionDag: DecisionDagAnalysisV6,
        profile: V6Profile,
    ): PurposefulOccupancyV6 {
        if (!decisionDag.complete || decisionDag.metrics == null) {
            return incomplete(level, "decision-analysis-incomplete")
        }
        val witnesses = linkedMapOf<String, MutableList<ObjectSemanticWitnessV6>>()
        fun witness(
            key: String,
            participation: SemanticParticipationV6,
            state: DecisionNodeV6,
            arrowId: String,
            evidence: String,
        ) {
            witnesses.getOrPut(key) { mutableListOf() }.add(
                ObjectSemanticWitnessV6(key, participation, state.stateKey, arrowId, evidence),
            )
        }

        val nodes = decisionDag.nodes.values.sortedBy { it.stateKey }
        nodes.forEach { node ->
            node.transitions.sortedBy { it.arrowId }.forEach { edge ->
                val arrowKey = "arrow:${edge.arrowId}"
                if (edge.successful && edge.futureSolvable == true) {
                    witness(arrowKey, SemanticParticipationV6.SELECTED_IN_WINNING_POLICY, node, edge.arrowId, "winning edge")
                }
                if (edge.route.isNotEmpty()) {
                    witness(arrowKey, SemanticParticipationV6.MOVEMENT_ROUTE, node, edge.arrowId, edge.route.joinToString())
                }
                edge.controllerId?.let { magnetId ->
                    witness(
                        "magnet:$magnetId",
                        SemanticParticipationV6.CONTROLLER_SELECTION,
                        node,
                        edge.arrowId,
                        "controller with ${edge.terminalEvent}",
                    )
                }
                edge.polarityChange?.substringBefore(':')?.let { magnetId ->
                    witness(
                        "magnet:$magnetId",
                        SemanticParticipationV6.POLARITY_MUTATION,
                        node,
                        edge.arrowId,
                        edge.polarityChange,
                    )
                }
                if (edge.successful && edge.futureSolvable == false) {
                    witness(
                        arrowKey,
                        SemanticParticipationV6.SUCCESSFUL_PERSISTENT_TRAP,
                        node,
                        edge.arrowId,
                        "delayedDeadlockDepth=${edge.delayedDeadlockDepth ?: 0}",
                    )
                }
                val collision = node.state.walls.firstOrNull { wall -> wall.position in edge.route && edge.terminalEvent == "COLLISION" }
                collision?.let {
                    witness(
                        wallKey(it.position),
                        SemanticParticipationV6.COLLISION,
                        node,
                        edge.arrowId,
                        "route terminal collision",
                    )
                }
            }
        }

        decisionDag.metrics.mandatoryPrecedence.forEach { (before, after) ->
            nodes.firstOrNull { it.state.arrow(before) != null }?.let { state ->
                witness(
                    "arrow:$before",
                    SemanticParticipationV6.MANDATORY_ORDERING,
                    state,
                    before,
                    "must precede $after",
                )
            }
        }
        decisionDag.commutation.filter { !it.commutes }.forEach { evidence ->
            val state = requireNotNull(decisionDag.nodes[evidence.stateKey])
            listOf(evidence.firstArrowId, evidence.secondArrowId).forEach { arrowId ->
                witness(
                    "arrow:$arrowId",
                    SemanticParticipationV6.NON_COMMUTING_DECISION,
                    state,
                    arrowId,
                    "paired with ${if (arrowId == evidence.firstArrowId) evidence.secondArrowId else evidence.firstArrowId}",
                )
            }
        }

        var counterfactualChecks = 0
        var capped = false
        val originalMagnets = level.magnets.sortedBy { it.id }
        val originalWalls = level.walls.sortedWith(compareBy({ it.position.row }, { it.position.column }))
        fun signature(result: ResolutionResult): String = listOf(
            result.success,
            result.effectiveDirection,
            result.controllingMagnetId,
            result.traversedCells,
            result.terminalEvent::class.simpleName,
            result.collisionTarget,
            result.polarityChange,
        ).joinToString("|")

        fun compareCounterfactual(
            objectKey: String,
            changed: (BoardState) -> BoardState,
        ) {
            if (witnesses[objectKey].orEmpty().isNotEmpty()) return
            loop@ for (node in nodes) {
                for (arrow in node.state.arrows.sortedBy { it.id }) {
                    if (counterfactualChecks >= maxCounterfactualChecks) {
                        capped = true
                        break@loop
                    }
                    counterfactualChecks += 1
                    val original = engine.resolve(node.state, PlayerAction(arrow.id))
                    val counterfactualState = changed(node.state)
                    val changedResult = engine.resolve(counterfactualState, PlayerAction(arrow.id))
                    if (signature(original) != signature(changedResult)) {
                        witness(
                            objectKey,
                            SemanticParticipationV6.COUNTERFACTUAL_OUTCOME_CHANGE,
                            node,
                            arrow.id,
                            "${signature(original)} -> ${signature(changedResult)}",
                        )
                        break@loop
                    }
                }
            }
        }
        originalMagnets.forEach { magnet ->
            compareCounterfactual("magnet:${magnet.id}") { state ->
                state.copy(magnets = state.magnets.filterNot { it.id == magnet.id })
            }
        }
        originalWalls.forEach { wall ->
            compareCounterfactual(wallKey(wall.position)) { state ->
                state.copy(walls = state.walls.filterNot { it.position == wall.position })
            }
        }

        val winningNodes = nodes.filter { it.solvable == true }
        level.arrows.forEach { arrow ->
            val key = "arrow:${arrow.id}"
            if (witnesses[key].isNullOrEmpty()) {
                winningNodes.firstOrNull { it.state.arrow(arrow.id) != null }?.let { node ->
                    witness(key, SemanticParticipationV6.SELECTED_IN_WINNING_POLICY, node, arrow.id, "required remaining arrow")
                }
            }
        }

        val occupied = level.arrows.size + level.magnets.size + level.walls.size
        val purposefulArrows = level.arrows.count { witnesses["arrow:${it.id}"].orEmpty().isNotEmpty() }
        val purposefulMagnets = level.magnets.count { witnesses["magnet:${it.id}"].orEmpty().isNotEmpty() }
        val purposefulWalls = level.walls.count { witnesses[wallKey(it.position)].orEmpty().isNotEmpty() }
        val purposeful = purposefulArrows + purposefulMagnets + purposefulWalls
        val inert = occupied - purposeful
        val area = level.width * level.height
        val occupiedRatio = occupied.toDouble() / area
        val purposefulRatio = if (occupied == 0) 0.0 else purposeful.toDouble() / occupied
        val inertRatio = if (occupied == 0) 0.0 else inert.toDouble() / occupied
        val emptyPurpose = purposefulEmptyCells(level, nodes)
        val reasons = buildList {
            if (occupiedRatio !in profile.occupancy) add("occupancy-out-of-range:$occupiedRatio")
            if (purposefulRatio < profile.minimumPurposefulOccupiedRatio) add("purposeful-occupied-ratio:$purposefulRatio")
            if (inertRatio > profile.maximumInertOccupiedRatio) add("inert-occupied-ratio:$inertRatio")
            level.magnets.filter { witnesses["magnet:${it.id}"].isNullOrEmpty() }
                .forEach { add("magnet-without-semantic-witness:${it.id}") }
            level.walls.filter { witnesses[wallKey(it.position)].isNullOrEmpty() }
                .forEach { add("wall-without-semantic-witness:${it.position.row},${it.position.column}") }
            if (capped) add("purposeful-counterfactual-cap:$maxCounterfactualChecks")
        }
        return PurposefulOccupancyV6(
            occupiedRatio = occupiedRatio,
            purposefulOccupiedRatio = purposefulRatio,
            inertOccupiedRatio = inertRatio,
            purposefulArrowRatio = ratio(purposefulArrows, level.arrows.size),
            purposefulMagnetRatio = ratio(purposefulMagnets, level.magnets.size),
            purposefulWallRatio = ratio(purposefulWalls, level.walls.size),
            objectWitnesses = witnesses.mapValues { it.value.distinct() }.toSortedMap(),
            purposefulEmptyCells = emptyPurpose,
            analysisComplete = !capped,
            counterfactualChecks = counterfactualChecks,
            rejectionReasons = reasons,
        )
    }

    private fun purposefulEmptyCells(
        level: LevelDefinition,
        nodes: List<DecisionNodeV6>,
    ): Map<Position, String> {
        val occupied = buildSet {
            level.arrows.forEach { add(it.position) }
            level.magnets.forEach { add(it.position) }
            level.walls.forEach { add(it.position) }
        }
        val routeCells = nodes.flatMap { node -> node.transitions.flatMap { it.route } }.toSet() - occupied
        val lineOfSightCells = buildSet {
            level.arrows.forEach { arrow ->
                level.magnets.filter { magnet ->
                    arrow.position.row == magnet.position.row || arrow.position.column == magnet.position.column
                }.forEach { magnet ->
                    val rowStep = magnet.position.row.compareTo(arrow.position.row)
                    val columnStep = magnet.position.column.compareTo(arrow.position.column)
                    var position = Position(arrow.position.row + rowStep, arrow.position.column + columnStep)
                    while (position != magnet.position) {
                        if (position !in occupied) add(position)
                        position = Position(position.row + rowStep, position.column + columnStep)
                    }
                }
            }
        }
        return (routeCells + lineOfSightCells).associateWith { cell ->
            when {
                cell in routeCells && cell in lineOfSightCells -> "route-and-line-of-sight"
                cell in routeCells -> "reachable-route"
                else -> "line-of-sight-separation"
            }
        }.toSortedMap(compareBy(Position::row, Position::column))
    }

    private fun incomplete(level: LevelDefinition, reason: String): PurposefulOccupancyV6 {
        val occupied = level.arrows.size + level.magnets.size + level.walls.size
        return PurposefulOccupancyV6(
            occupiedRatio = occupied.toDouble() / (level.width * level.height),
            purposefulOccupiedRatio = 0.0,
            inertOccupiedRatio = 1.0,
            purposefulArrowRatio = 0.0,
            purposefulMagnetRatio = 0.0,
            purposefulWallRatio = 0.0,
            objectWitnesses = emptyMap(),
            purposefulEmptyCells = emptyMap(),
            analysisComplete = false,
            counterfactualChecks = 0,
            rejectionReasons = listOf(reason),
        )
    }

    private fun wallKey(position: Position): String = "wall:${position.row},${position.column}"

    private fun ratio(numerator: Int, denominator: Int): Double =
        if (denominator == 0) 1.0 else numerator.toDouble() / denominator
}
