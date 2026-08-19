package com.rameshta.magnetrail.core.generation.v5

import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.CollisionTargetType
import com.rameshta.magnetrail.core.engine.DeterministicRouteTracer
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.MagneticDiagnostics
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.generation.SeededRandom
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Magnet
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall
import com.rameshta.magnetrail.core.solver.Solver
import com.rameshta.magnetrail.core.solver.StateKey
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Generator V5's construction contract. Edges are construction obligations, not difficulty
 * metadata: [SolutionFirstConstructorV5] verifies the canonical replay with the production engine
 * before returning a board.
 */
@Serializable
data class SolutionContractV5(
    val version: Int = 1,
    val nodes: List<SolutionContractNodeV5>,
    val edges: List<SolutionContractEdgeV5>,
    val canonicalActionIds: List<String>,
    val semanticWitnessActionIds: List<List<String>> = emptyList(),
) {
    init {
        require(nodes.map { it.id }.distinct().size == nodes.size) { "Contract node IDs must be unique" }
        require(canonicalActionIds.isNotEmpty() && canonicalActionIds.distinct().size == canonicalActionIds.size)
        val actionIds = nodes.mapNotNull { it.actionId }.toSet()
        require(canonicalActionIds.all { it in actionIds }) { "Every canonical action needs a contract node" }
        require(semanticWitnessActionIds.flatten().all { it in actionIds }) {
            "Every semantic witness action needs a contract node"
        }
        val nodeIds = nodes.map { it.id }.toSet()
        require(edges.all { it.fromNodeId in nodeIds && it.toNodeId in nodeIds })
        require(isAcyclic(nodeIds, edges)) { "Solution contract dependencies must be acyclic" }
    }

    private fun isAcyclic(ids: Set<String>, edges: List<SolutionContractEdgeV5>): Boolean {
        val incoming = ids.associateWith { id -> edges.count { it.toNodeId == id } }.toMutableMap()
        val queue = ArrayDeque(incoming.filterValues { it == 0 }.keys.sorted())
        var visited = 0
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            visited += 1
            edges.filter { it.fromNodeId == id }.sortedBy { it.toNodeId }.forEach { edge ->
                val remaining = requireNotNull(incoming[edge.toNodeId]) - 1
                incoming[edge.toNodeId] = remaining
                if (remaining == 0) queue.addLast(edge.toNodeId)
            }
        }
        return visited == ids.size
    }
}

@Serializable
data class SolutionContractNodeV5(
    val id: String,
    val actionId: String? = null,
    val objectKey: String,
    val role: SolutionObjectRoleV5,
)

@Serializable
enum class SolutionObjectRoleV5 { ENTRY, BLOCKER, CONTROLLER, POLARITY_SWITCH, CANCELLATION, TRAP, ROUTE_GUARD }

@Serializable
data class SolutionContractEdgeV5(
    val fromNodeId: String,
    val toNodeId: String,
    val relationship: ConstructedRelationshipV5,
    val evidence: String,
)

@Serializable
enum class ConstructedRelationshipV5 {
    ARROW_BLOCKS_ARROW,
    ARROW_OCCLUDES_MAGNET,
    MAGNET_CONTROLS_ARROW,
    MAGNET_CANCELLATION,
    POLARITY_DEPENDENCY,
    WALL_BLOCKS_ROUTE,
    EXPOSURE,
    ORDER_DEPENDENCY,
    STATE_DEPENDENCY,
    LONG_RANGE_MAGNET_CONTROL,
}

data class PhysicalContractVerificationV5(
    val declaredEdgeCount: Int,
    val verifiedEdgeCount: Int,
    val missingEdges: List<SolutionContractEdgeV5>,
    val physicalSemantics: PhysicalSemanticsV5,
) {
    val passed: Boolean get() = physicalSemantics.complete && missingEdges.isEmpty()
}

data class ConstructedCandidateV5(
    val level: LevelDefinition,
    val contract: SolutionContractV5,
    val canonicalReplayVerified: Boolean,
)

data class RepairOutcomeV5(
    val level: LevelDefinition,
    val applied: Boolean,
    val rolledBack: Boolean,
    val operator: String,
)

/**
 * Deterministic dependency-first construction. Medium+ starts from a connected cancellation,
 * exposure, and polarity scaffold. Remaining cells are occupied only after the solution structure
 * exists. This avoids the old dense-random-placement/solver-lottery path.
 */
class SolutionFirstConstructorV5(
    private val engine: GameEngine = DefaultGameEngine(),
    private val solver: Solver = Solver(engine),
) {
    private val tracer = DeterministicRouteTracer()
    private val magneticDiagnostics = MagneticDiagnostics(engine, tracer)
    fun construct(request: GenerationRequestV5, seed: Long): ConstructedCandidateV5 {
        val size = chooseSize(request.profile, seed)
        val candidate = if (size >= 8 && request.profile.maxArrows >= DEPENDENCY_COMPLETE_ARROW_COUNT) {
            dependencyCompleteScaffold(request, seed, size)
        } else if (size >= 6) {
            dependencyScaffold(request, seed, size)
        } else {
            compactScaffold(request, seed, size)
        }
        check(replay(candidate.level, candidate.contract.canonicalActionIds)) {
            "Solution-first materialization did not preserve its canonical production-engine replay"
        }
        val solved = solver.solve(
            candidate.level.initialState(),
            solutionLimit = 100_000,
            maxExploredStates = request.profile.solverStateCap,
        )
        check(solved.searchComplete && solved.solvable) {
            "Solution-first materialization was not completely solvable: $solved"
        }
        return candidate.copy(canonicalReplayVerified = true)
    }

    fun verifyPhysicalContract(
        level: LevelDefinition,
        contract: SolutionContractV5,
        stateCap: Int,
    ): PhysicalContractVerificationV5 {
        val physical = analyzeWitnessSemantics(level, contract, stateCap)
        val nodes = contract.nodes.associateBy { it.id }
        val missing = contract.edges.filterNot { edge ->
            val source = requireNotNull(nodes[edge.fromNodeId]).objectKey
            val target = requireNotNull(nodes[edge.toNodeId]).objectKey
            edgeIsPresent(edge.relationship, source, target, physical)
        }
        return PhysicalContractVerificationV5(
            declaredEdgeCount = contract.edges.size,
            verifiedEdgeCount = contract.edges.size - missing.size,
            missingEdges = missing,
            physicalSemantics = physical,
        )
    }

    /**
     * Bounded deterministic mutation. A mutation is committed only if the original canonical
     * replay remains valid and a complete production solver still proves the board solvable.
     */
    fun repair(
        original: ConstructedCandidateV5,
        rejectionReasons: List<String>,
        seed: Long,
        solverStateCap: Int,
    ): RepairOutcomeV5 {
        if (rejectionReasons.isEmpty()) return RepairOutcomeV5(original.level, false, false, "none")
        val random = SeededRandom(seed)
        val fillerMagnets = original.level.magnets.filter { it.id.startsWith(FILLER_PREFIX) }
        if (fillerMagnets.isEmpty()) return RepairOutcomeV5(original.level, false, false, "no-structurally-safe-operator")
        val before = verifyPhysicalContract(original.level, original.contract, solverStateCap)
        if (!before.passed) return RepairOutcomeV5(original.level, false, true, "invalid-original-structure")
        val mutated = when {
            rejectionReasons.any { "polarity" in it || "safe-choice" in it } && fillerMagnets.isNotEmpty() -> {
                val selected = fillerMagnets[random.nextInt(fillerMagnets.size)]
                original.level.copy(magnets = original.level.magnets.map { magnet ->
                    if (magnet.id == selected.id) magnet.copy(polarity = magnet.polarity.flipped()) else magnet
                }) to "flip-shielded-filler-polarity"
            }
            rejectionReasons.any { "wall" in it || "interaction" in it || "relevance" in it } &&
                fillerMagnets.isNotEmpty() && original.level.walls.isNotEmpty() -> {
                val magnet = fillerMagnets[random.nextInt(fillerMagnets.size)]
                val wall = original.level.walls[random.nextInt(original.level.walls.size)]
                original.level.copy(
                    magnets = original.level.magnets.map {
                        if (it.id == magnet.id) it.copy(position = wall.position) else it
                    },
                    walls = original.level.walls.map {
                        if (it.position == wall.position) Wall(magnet.position) else it
                    },
                ) to "swap-shielded-magnet-and-wall"
            }
            else -> return RepairOutcomeV5(original.level, false, false, "no-safe-operator")
        }
        val (candidate, operator) = mutated
        if (!replay(candidate, original.contract.canonicalActionIds)) {
            return RepairOutcomeV5(original.level, false, true, operator)
        }
        val solved = solver.solve(candidate.initialState(), 100_000, solverStateCap)
        val after = if (solved.searchComplete && solved.solvable) {
            verifyPhysicalContract(candidate, original.contract, solverStateCap)
        } else {
            null
        }
        val requiredOrdering = before.physicalSemantics.edges.filter { it.type == InteractionTypeV5.ORDER_DEPENDENCY }.toSet()
        val requiredPolarity = before.physicalSemantics.edges.filter { it.type == InteractionTypeV5.POLARITY_DEPENDENCY }.toSet()
        val requiredExposure = before.physicalSemantics.edges.filter {
            it.type == InteractionTypeV5.EXPOSURE || it.type == InteractionTypeV5.REVEAL
        }.toSet()
        val structurePreserved = after != null && after.passed &&
            after.physicalSemantics.edges.containsAll(requiredOrdering) &&
            after.physicalSemantics.edges.containsAll(requiredPolarity) &&
            after.physicalSemantics.edges.containsAll(requiredExposure) &&
            after.physicalSemantics.longRangeRelationships.containsAll(
                before.physicalSemantics.longRangeRelationships,
            ) &&
            after.physicalSemantics.participatingWalls.containsAll(before.physicalSemantics.participatingWalls)
        return if (solved.searchComplete && solved.solvable && structurePreserved) {
            RepairOutcomeV5(candidate, true, false, operator)
        } else {
            RepairOutcomeV5(original.level, false, true, operator)
        }
    }

    /**
     * Eight-by-eight dependency-complete construction. The removable L-shaped corridors are
     * authored before the surrounding route-guard shell. Clearing them exposes three genuine
     * distance-four controllers and creates cross-corridor ordering dependencies.
     */
    private fun dependencyCompleteScaffold(
        request: GenerationRequestV5,
        seed: Long,
        size: Int,
    ): ConstructedCandidateV5 {
        check(size == 8) { "Dependency-complete V5 currently supports the approved 8x8 canvas" }
        val arrows = listOf(
            Arrow("left6", Position(6, 1), Direction.WEST),
            Arrow("left7", Position(7, 1), Direction.WEST),
            Arrow("leftSwitch", Position(5, 1), Direction.WEST),
            Arrow("inner6", Position(6, 2), Direction.WEST),
            Arrow("inner7", Position(7, 2), Direction.WEST),
            Arrow("innerSwitch", Position(5, 2), Direction.WEST),
            Arrow("innerTarget", Position(8, 2), Direction.NORTH),
            Arrow("bottomFree", Position(8, 3), Direction.SOUTH),
            Arrow("rightSwitch", Position(8, 4), Direction.SOUTH),
            Arrow("cornerTarget", Position(8, 1), Direction.WEST),
        )
        val magnets = listOf(
            Magnet("longTop", Position(4, 1), Polarity.PULL),
            Magnet("longInner", Position(4, 2), Polarity.PULL),
            Magnet("longRight", Position(8, 5), Polarity.PULL),
        )
        val occupied = arrows.mapTo(mutableSetOf()) { it.position }.apply {
            addAll(magnets.map { it.position })
        }
        val walls = allCells(size).filterNot { it in occupied }.map(::Wall)
        val base = LevelDefinition(
            id = request.stableId,
            number = request.sequenceNumber,
            title = request.title,
            width = size,
            height = size,
            arrows = arrows,
            magnets = magnets,
            walls = walls,
            designedSolutions = emptyList(),
        )
        val canonical = listOf(
            "left7", "left6", "leftSwitch",
            "inner7", "inner6", "innerSwitch", "innerTarget",
            "bottomFree", "rightSwitch", "cornerTarget",
        )
        val nodes = buildList {
            arrows.forEach { arrow ->
                add(
                    SolutionContractNodeV5(
                        id = "arrow:${arrow.id}",
                        actionId = arrow.id,
                        objectKey = "arrow:${arrow.id}",
                        role = when {
                            arrow.id.contains("Switch") || arrow.id.endsWith("1") ->
                                SolutionObjectRoleV5.POLARITY_SWITCH
                            arrow.id.contains("Target") || arrow.id.endsWith("0") -> SolutionObjectRoleV5.TRAP
                            else -> SolutionObjectRoleV5.BLOCKER
                        },
                    ),
                )
            }
            magnets.forEach { magnet ->
                add(
                    SolutionContractNodeV5(
                        id = "magnet:${magnet.id}",
                        objectKey = "magnet:${magnet.id}",
                        role = SolutionObjectRoleV5.CONTROLLER,
                    ),
                )
            }
        }
        val contract = SolutionContractV5(
            nodes = nodes,
            edges = listOf(
                edge("arrow:left7", "arrow:inner7", ConstructedRelationshipV5.EXPOSURE, "left7 opens inner7's west route"),
                edge("arrow:left6", "arrow:inner6", ConstructedRelationshipV5.EXPOSURE, "left6 opens inner6's west route"),
                edge("arrow:leftSwitch", "arrow:cornerTarget", ConstructedRelationshipV5.POLARITY_DEPENDENCY, "leftSwitch exposes and flips longTop"),
                edge("arrow:innerSwitch", "arrow:innerTarget", ConstructedRelationshipV5.POLARITY_DEPENDENCY, "innerSwitch exposes and flips longInner"),
                edge("arrow:rightSwitch", "arrow:cornerTarget", ConstructedRelationshipV5.POLARITY_DEPENDENCY, "rightSwitch exposes and flips longRight"),
                edge("magnet:longTop", "arrow:cornerTarget", ConstructedRelationshipV5.LONG_RANGE_MAGNET_CONTROL, "distance-four vertical controller"),
                edge("magnet:longInner", "arrow:innerTarget", ConstructedRelationshipV5.LONG_RANGE_MAGNET_CONTROL, "distance-four vertical controller"),
                edge("magnet:longRight", "arrow:cornerTarget", ConstructedRelationshipV5.LONG_RANGE_MAGNET_CONTROL, "distance-four horizontal controller"),
            ),
            canonicalActionIds = canonical,
            semanticWitnessActionIds = listOf(
                canonical,
                listOf(
                    "left7", "left6", "inner7", "inner6", "innerSwitch", "innerTarget",
                    "bottomFree", "rightSwitch",
                ),
            ),
        )
        check(replay(base, canonical)) { "Dependency-complete base geometry broke canonical replay" }
        val beforeTransform = verifyPhysicalContract(base, contract, request.profile.analysisStateCap)
        check(beforeTransform.passed) {
            "Dependency-complete base semantic edges missing: ${beforeTransform.missingEdges} ${beforeTransform.physicalSemantics.truncationReasons}"
        }
        val transformed = transform(base, seed)
        val afterTransform = verifyPhysicalContract(transformed, contract, request.profile.analysisStateCap)
        check(afterTransform.passed) {
            "Geometry transform destroyed semantic edges: ${afterTransform.missingEdges} ${afterTransform.physicalSemantics.truncationReasons}"
        }
        return ConstructedCandidateV5(transformed, contract, canonicalReplayVerified = false)
    }

    private fun dependencyScaffold(
        request: GenerationRequestV5,
        seed: Long,
        size: Int,
    ): ConstructedCandidateV5 {
        val middle = (size + 1) / 2
        val chainEnd = if (size >= 7) size - 5 else size - 2
        val arrows = mutableListOf(
            Arrow("p0", Position(middle - 1, 1), Direction.WEST),
            Arrow("p1", Position(middle + 1, 1), Direction.WEST),
            Arrow("chain0", Position(middle, 1), Direction.WEST),
        )
        (2..chainEnd).forEach { column ->
            arrows += Arrow("chain${column - 1}", Position(middle, column), Direction.WEST)
        }
        val trapColumn = size - 2
        arrows += Arrow("trap0", Position(1, trapColumn), Direction.NORTH)
        arrows += Arrow("trap1", Position(2, trapColumn + 1), Direction.EAST)
        if (size >= 7) {
            arrows += Arrow("trapB0", Position(size, trapColumn), Direction.SOUTH)
            arrows += Arrow("trapB1", Position(size - 1, trapColumn + 1), Direction.EAST)
        }

        val primaryMagnets = listOf(
            Magnet("cancelTop", Position(middle - 2, 1), Polarity.PULL),
            Magnet("cancelBottom", Position(middle + 2, 1), Polarity.PULL),
            Magnet("trap", Position(2, trapColumn), Polarity.PULL),
        ) + if (size >= 7) listOf(Magnet("trapB", Position(size - 1, trapColumn), Polarity.PULL)) else emptyList()
        val forcedWalls = linkedSetOf(
            Position(middle - 1, 2),
            Position(middle + 1, 2),
            Position(2, size),
            Position(1, trapColumn - 1),
            Position(1, trapColumn + 1),
            Position(3, trapColumn + 1),
            Position(size - 1, size),
            Position(size, trapColumn - 1),
            Position(size, trapColumn + 1),
            Position(size - 2, trapColumn + 1),
        ).filter { it.row in 1..size && it.column in 1..size }.toMutableSet()
        val occupied = arrows.map { it.position }.toSet() + primaryMagnets.map { it.position } + forcedWalls
        val free = allCells(size).filterNot { it in occupied }
        val fillerMagnets = free.mapIndexed { index, position ->
            Magnet(
                "$FILLER_PREFIX${index + 1}",
                position,
                if (index % 3 == 0) Polarity.PUSH else Polarity.PULL,
            )
        }
        val level = LevelDefinition(
            id = request.stableId,
            number = request.sequenceNumber,
            title = request.title,
            width = size,
            height = size,
            arrows = arrows,
            magnets = primaryMagnets + fillerMagnets,
            walls = forcedWalls.map(::Wall),
            designedSolutions = emptyList(),
        )
        val canonical = buildList {
            add("p0")
            add("p1")
            addAll((0..chainEnd - 1).map { "chain$it" })
            add("trap1")
            add("trap0")
            if (size >= 7) {
                add("trapB1")
                add("trapB0")
            }
        }
        val nodes = buildList {
            arrows.forEach { arrow ->
                add(
                    SolutionContractNodeV5(
                        id = "arrow:${arrow.id}", actionId = arrow.id, objectKey = "arrow:${arrow.id}",
                        role = when {
                            arrow.id.startsWith("p") -> SolutionObjectRoleV5.POLARITY_SWITCH
                            arrow.id.startsWith("trap") -> SolutionObjectRoleV5.TRAP
                            else -> SolutionObjectRoleV5.BLOCKER
                        },
                    ),
                )
            }
            primaryMagnets.forEach { magnet ->
                add(
                    SolutionContractNodeV5(
                        id = "magnet:${magnet.id}", objectKey = "magnet:${magnet.id}",
                        role = if (magnet.id.startsWith("cancel")) SolutionObjectRoleV5.CANCELLATION
                        else SolutionObjectRoleV5.CONTROLLER,
                    ),
                )
            }
        }
        val edges = buildList {
            add(edge("arrow:p0", "arrow:chain0", ConstructedRelationshipV5.EXPOSURE, "p0 exposes cancelTop"))
            add(edge("arrow:p1", "arrow:chain0", ConstructedRelationshipV5.EXPOSURE, "p1 exposes cancelBottom"))
            (0 until chainEnd - 1).forEach { index ->
                add(
                    edge(
                        "arrow:chain$index", "arrow:chain${index + 1}",
                        ConstructedRelationshipV5.ARROW_BLOCKS_ARROW,
                        "removing the west neighbor opens the next printed route",
                    ),
                )
            }
            add(
                edge(
                    "arrow:trap1", "arrow:trap0", ConstructedRelationshipV5.POLARITY_DEPENDENCY,
                    "trap1 flips trap to PUSH so trap0 exits",
                ),
            )
            if (size >= 7) {
                add(
                    edge(
                        "arrow:trapB1", "arrow:trapB0", ConstructedRelationshipV5.POLARITY_DEPENDENCY,
                        "trapB1 flips trapB to PUSH so trapB0 exits",
                    ),
                )
            }
            add(
                edge(
                    "magnet:cancelTop", "arrow:chain0", ConstructedRelationshipV5.MAGNET_CANCELLATION,
                    "equal nearest visible magnets cancel after both blockers are removed",
                ),
            )
            add(
                edge(
                    "magnet:cancelBottom", "arrow:chain0", ConstructedRelationshipV5.MAGNET_CANCELLATION,
                    "equal nearest visible magnets cancel after both blockers are removed",
                ),
            )
        }
        return ConstructedCandidateV5(
            transform(level, seed),
            SolutionContractV5(nodes = nodes, edges = edges, canonicalActionIds = canonical),
            false,
        )
    }

    private fun compactScaffold(
        request: GenerationRequestV5,
        seed: Long,
        size: Int,
    ): ConstructedCandidateV5 {
        val magnet = Position(2, size - 1)
        val safe = Position(2, size)
        val wrong = Position(1, size - 1)
        val arrows = listOf(
            Arrow("safe", safe, Direction.EAST),
            Arrow("wrong", wrong, Direction.NORTH),
        )
        val magnets = listOf(Magnet("controller", magnet, Polarity.PULL))
        val occupied = arrows.map { it.position }.toSet() + magnet
        val targetObjects = if (request.profile.constructionStrategy == ConstructionStrategyV5.SOLUTION_FIRST) {
            size * size
        } else {
            maxOf(3, (size * size * request.profile.objectDensityRange.minimum).toInt())
        }
        val fillerCells = allCells(size).filterNot { it in occupied }.take((targetObjects - 3).coerceAtLeast(0))
        val random = SeededRandom(seed)
        val fillerMagnets = fillerCells.mapIndexed { index, position ->
            Magnet("$FILLER_PREFIX${index + 1}", position, if (random.nextInt(2) == 0) Polarity.PULL else Polarity.PUSH)
        }
        val level = LevelDefinition(
            id = request.stableId, number = request.sequenceNumber, title = request.title,
            width = size, height = size, arrows = arrows, magnets = magnets + fillerMagnets,
            walls = emptyList(), designedSolutions = emptyList(),
        )
        val nodes = listOf(
            SolutionContractNodeV5("arrow:safe", "safe", "arrow:safe", SolutionObjectRoleV5.POLARITY_SWITCH),
            SolutionContractNodeV5("arrow:wrong", "wrong", "arrow:wrong", SolutionObjectRoleV5.TRAP),
            SolutionContractNodeV5("magnet:controller", objectKey = "magnet:controller", role = SolutionObjectRoleV5.CONTROLLER),
        )
        val contract = SolutionContractV5(
            nodes = nodes,
            edges = listOf(
                edge(
                    "arrow:safe", "arrow:wrong", ConstructedRelationshipV5.POLARITY_DEPENDENCY,
                    "safe flips controller to PUSH so wrong exits",
                ),
            ),
            canonicalActionIds = listOf("safe", "wrong"),
        )
        return ConstructedCandidateV5(level, contract, false)
    }

    private fun replay(level: LevelDefinition, actionIds: List<String>): Boolean {
        var state = level.initialState()
        actionIds.forEach { id ->
            if (state.arrow(id) == null) return false
            val resolution = engine.resolve(state, PlayerAction(id))
            if (!resolution.success) return false
            state = resolution.resultingState
        }
        return state.arrows.isEmpty()
    }

    private fun analyzeWitnessSemantics(
        level: LevelDefinition,
        contract: SolutionContractV5,
        stateCap: Int,
    ): PhysicalSemanticsV5 {
        val states = linkedMapOf<StateKey, BoardState>()
        val reasons = mutableListOf<String>()
        val witnesses = (listOf(contract.canonicalActionIds) + contract.semanticWitnessActionIds).distinct()
        witnesses.forEachIndexed { witnessIndex, actions ->
            var state = level.initialState()
            states.putIfAbsent(StateKey.from(state), state)
            actions.forEach { actionId ->
                if (states.size >= stateCap) {
                    reasons += "SEMANTIC_WITNESS_STATE_CAP"
                    return@forEach
                }
                val arrow = state.arrow(actionId)
                if (arrow == null) {
                    reasons += "SEMANTIC_WITNESS_MISSING_ACTION:$witnessIndex:$actionId"
                    return@forEach
                }
                val result = engine.resolve(state, PlayerAction(actionId))
                if (!result.success) {
                    reasons += "SEMANTIC_WITNESS_FAILED:$witnessIndex:$actionId"
                    return@forEach
                }
                state = result.resultingState
                states.putIfAbsent(StateKey.from(state), state)
            }
        }

        val edges = linkedSetOf<InteractionEdgeV5>()
        val distances = linkedMapOf<String, Int>()
        states.values.forEach { state ->
            state.arrows.forEach { arrow ->
                val arrowKey = "arrow:${arrow.id}"
                val control = tracer.explainControl(state, arrow)
                control.controllingMagnet?.let { magnet ->
                    edges += InteractionEdgeV5("magnet:${magnet.id}", arrowKey, InteractionTypeV5.MAGNET_CONTROL)
                    distances["${magnet.id}>${arrow.id}"] =
                        abs(magnet.position.row - arrow.position.row) + abs(magnet.position.column - arrow.position.column)
                }
                if (control.cancelledByEqualNearestMagnets) {
                    control.equallyNearestVisibleMagnets.forEachIndexed { index, first ->
                        control.equallyNearestVisibleMagnets.drop(index + 1).forEach { second ->
                            edges += InteractionEdgeV5(
                                "magnet:${first.id}",
                                "magnet:${second.id}",
                                InteractionTypeV5.CANCELLATION,
                            )
                        }
                    }
                }
                magneticDiagnostics.explain(state, arrow, 64).occludingEntityKeys.forEach { blocker ->
                    edges += InteractionEdgeV5(blocker, arrowKey, InteractionTypeV5.OCCLUSION)
                }
                val before = state.arrows.filter { it.id != arrow.id }.associate { future ->
                    future.id to resolutionSignature(engine.resolve(state, PlayerAction(future.id)))
                }
                val result = engine.resolve(state, PlayerAction(arrow.id))
                result.collisionTarget?.let { collision ->
                    val collisionKey = when (collision.type) {
                        CollisionTargetType.ARROW -> "arrow:${collision.entityId}"
                        CollisionTargetType.MAGNET -> "magnet:${collision.entityId}"
                        CollisionTargetType.WALL -> "wall:${collision.position.row},${collision.position.column}"
                    }
                    edges += InteractionEdgeV5(arrowKey, collisionKey, InteractionTypeV5.COLLISION)
                    edges += InteractionEdgeV5(collisionKey, arrowKey, InteractionTypeV5.ROUTE_BLOCK)
                }
                if (result.success) {
                    result.resultingState.arrows.forEach { future ->
                        if (before[future.id] != resolutionSignature(
                                engine.resolve(result.resultingState, PlayerAction(future.id)),
                            )
                        ) {
                            val futureKey = "arrow:${future.id}"
                            edges += InteractionEdgeV5(arrowKey, futureKey, InteractionTypeV5.EXPOSURE)
                            edges += InteractionEdgeV5(arrowKey, futureKey, InteractionTypeV5.REVEAL)
                            edges += InteractionEdgeV5(arrowKey, futureKey, InteractionTypeV5.STATE_DEPENDENCY)
                        }
                    }
                    result.polarityChange?.let { polarity ->
                        val unflipped = result.resultingState.copy(
                            magnets = result.resultingState.magnets.map { magnet ->
                                if (magnet.id == polarity.magnetId) magnet.copy(polarity = polarity.from) else magnet
                            },
                        )
                        result.resultingState.arrows.forEach { future ->
                            val withFlip = resolutionSignature(
                                engine.resolve(result.resultingState, PlayerAction(future.id)),
                            )
                            val withoutFlip = resolutionSignature(engine.resolve(unflipped, PlayerAction(future.id)))
                            if (withFlip != withoutFlip) {
                                edges += InteractionEdgeV5(
                                    "magnet:${polarity.magnetId}",
                                    "arrow:${future.id}",
                                    InteractionTypeV5.POLARITY_DEPENDENCY,
                                )
                            }
                        }
                    }
                }
            }
        }
        return PhysicalSemanticsV5(
            edges = edges,
            magneticRelationshipDistances = distances,
            complete = reasons.isEmpty(),
            truncationReasons = reasons.distinct(),
        )
    }

    private fun resolutionSignature(result: com.rameshta.magnetrail.core.engine.ResolutionResult): String =
        listOf(
            result.success,
            result.effectiveDirection,
            result.controllingMagnetId,
            result.terminalEvent::class.simpleName,
        ).joinToString("|")

    private fun edgeIsPresent(
        relationship: ConstructedRelationshipV5,
        source: String,
        target: String,
        physical: PhysicalSemanticsV5,
    ): Boolean {
        fun contains(vararg types: InteractionTypeV5): Boolean = physical.edges.any { edge ->
            edge.source == source && edge.target == target && edge.type in types
        }
        return when (relationship) {
            ConstructedRelationshipV5.ARROW_BLOCKS_ARROW -> contains(
                InteractionTypeV5.OCCLUSION,
                InteractionTypeV5.ROUTE_BLOCK,
            )
            ConstructedRelationshipV5.ARROW_OCCLUDES_MAGNET -> contains(InteractionTypeV5.OCCLUSION)
            ConstructedRelationshipV5.MAGNET_CONTROLS_ARROW -> contains(InteractionTypeV5.MAGNET_CONTROL)
            ConstructedRelationshipV5.MAGNET_CANCELLATION ->
                contains(InteractionTypeV5.CANCELLATION) || physical.edges.any { edge ->
                    edge.source == target && edge.target == source && edge.type == InteractionTypeV5.CANCELLATION
                }
            ConstructedRelationshipV5.POLARITY_DEPENDENCY -> contains(
                InteractionTypeV5.POLARITY_DEPENDENCY,
                InteractionTypeV5.STATE_DEPENDENCY,
            )
            ConstructedRelationshipV5.WALL_BLOCKS_ROUTE -> contains(
                InteractionTypeV5.ROUTE_BLOCK,
                InteractionTypeV5.OCCLUSION,
            )
            ConstructedRelationshipV5.EXPOSURE -> contains(
                InteractionTypeV5.EXPOSURE,
                InteractionTypeV5.REVEAL,
            )
            ConstructedRelationshipV5.ORDER_DEPENDENCY -> contains(InteractionTypeV5.ORDER_DEPENDENCY)
            ConstructedRelationshipV5.STATE_DEPENDENCY -> contains(InteractionTypeV5.STATE_DEPENDENCY)
            ConstructedRelationshipV5.LONG_RANGE_MAGNET_CONTROL ->
                contains(InteractionTypeV5.MAGNET_CONTROL) &&
                    (physical.magneticRelationshipDistances["${source.substringAfter(':')}>${target.substringAfter(':')}"] ?: 0) >= 4
        }
    }

    private fun chooseSize(profile: GenerationProfileV5, seed: Long): Int {
        val random = SeededRandom(seed)
        val supported = if (profile.difficultyBand.rank >= StructuralDifficultyBandV5.MEDIUM.rank) {
            profile.gridSizes.filter { it >= 6 }.ifEmpty { profile.gridSizes }
        } else {
            profile.gridSizes
        }
        // A higher-band bounded run gets the largest approved canvas for its dependency scaffold.
        // Lower bands retain deterministic, seeded size variation.
        if (profile.difficultyBand.rank >= StructuralDifficultyBandV5.HARD.rank) {
            return supported.max()
        }
        return supported[random.nextInt(supported.size)]
    }

    private fun allCells(size: Int): List<Position> =
        (1..size).flatMap { row -> (1..size).map { column -> Position(row, column) } }

    private fun transform(level: LevelDefinition, seed: Long): LevelDefinition {
        val variant = Math.floorMod(seed, 8L).toInt()
        val reflected = variant >= 4
        val rotations = variant % 4
        fun transformPosition(original: Position): Position {
            var position = if (reflected) {
                Position(original.row, level.width + 1 - original.column)
            } else {
                original
            }
            repeat(rotations) {
                position = Position(position.column, level.width + 1 - position.row)
            }
            return position
        }
        fun transformDirection(original: Direction): Direction {
            var direction = if (reflected) when (original) {
                Direction.EAST -> Direction.WEST
                Direction.WEST -> Direction.EAST
                else -> original
            } else original
            repeat(rotations) {
                direction = when (direction) {
                    Direction.NORTH -> Direction.EAST
                    Direction.EAST -> Direction.SOUTH
                    Direction.SOUTH -> Direction.WEST
                    Direction.WEST -> Direction.NORTH
                }
            }
            return direction
        }
        return level.copy(
            arrows = level.arrows.map { arrow ->
                arrow.copy(position = transformPosition(arrow.position), printedDirection = transformDirection(arrow.printedDirection))
            },
            magnets = level.magnets.map { it.copy(position = transformPosition(it.position)) },
            walls = level.walls.map { Wall(transformPosition(it.position)) },
        )
    }

    private fun edge(
        from: String,
        to: String,
        relationship: ConstructedRelationshipV5,
        evidence: String,
    ) = SolutionContractEdgeV5(from, to, relationship, evidence)

    private companion object {
        const val FILLER_PREFIX = "shielded-filler-"
        const val DEPENDENCY_COMPLETE_ARROW_COUNT = 10
    }
}
