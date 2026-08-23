package com.rameshta.magnetrail.core.generation.v6

import com.rameshta.magnetrail.core.content.BoardSymmetry
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Position
import java.util.ArrayDeque
import kotlin.math.max

const val GENERATOR_IDENTITY_V61 = "Generator V6.1 — Semantic Novelty plus Cued Multi-Phase Adversarial Difficulty"
const val V61_DIFFICULTY_SCHEMA_VERSION = 2
const val V61_MAX_BOARD_SIZE = 8

enum class AutomatedDifficultyBandV61(
    val rank: Int,
    val displayName: String,
    val minimumSolutionActions: Int,
    val minimumCriticalDecisions: Int,
    val minimumPersistentTraps: Int,
    val minimumCausalPhases: Int,
    val minimumVisibleLookahead: Int,
    val maximumForcedRun: Int,
    val maximumCheapPolicySolveRate: Double,
    val maximumForcedCleanupTailRatio: Double,
) {
    EASY(1, "Easy", 3, 0, 0, 1, 0, 7, 1.00, 1.00),
    MEDIUM(2, "Medium", 6, 1, 0, 1, 1, 6, 0.75, 1.00),
    HARD(3, "Hard", 10, 3, 1, 2, 2, 4, 0.40, 0.35),
    SUPER_HARD(4, "Super Hard", 14, 4, 2, 3, 3, 3, 0.20, 0.30),
    EXPERT(5, "Expert", 18, 6, 3, 3, 4, 3, 0.10, 0.25),
    MASTER(6, "Master", 22, 8, 4, 4, 5, 2, 0.05, 0.20),
    ;

    companion object {
        fun atRank(rank: Int): AutomatedDifficultyBandV61 = entries.single { it.rank == rank }
    }
}

/** Owner-authorized production/Auto Journey bands. Master remains parseable for old artifacts only. */
val AUTOMATED_CAMPAIGN_BANDS_V61: List<AutomatedDifficultyBandV61> =
    AutomatedDifficultyBandV61.entries.filter { it != AutomatedDifficultyBandV61.MASTER }

data class VisibleTransitionStepV61(
    val stateFingerprint: String,
    val arrowCell: Position,
    val printedDirection: Direction,
    val effectiveDirection: Direction,
    val controllerCell: Position?,
    val controllerPolarity: String?,
    val route: List<Position>,
    val terminalEvent: String,
    val polarityChange: String?,
)

data class VisibleDecisionProofV61(
    val decisionStateFingerprint: String,
    val safeArrowId: String,
    val trapArrowId: String,
    val safeArrowCell: Position,
    val trapArrowCell: Position,
    val safeEffectiveDirection: Direction,
    val trapEffectiveDirection: Direction,
    val safeControllerCell: Position?,
    val safeControllerPolarity: String?,
    val trapControllerCell: Position?,
    val trapControllerPolarity: String?,
    val safeRoute: List<Position>,
    val trapRoute: List<Position>,
    val downstreamObject: String,
    val firstFutureDivergence: String,
    val delayedConsequenceDepth: Int,
    val minimumVisibleLookahead: Int,
    val trapTrace: List<VisibleTransitionStepV61>,
    val explanation: String,
    val valid: Boolean,
    val invalidReasons: List<String>,
)

data class CriticalDecisionEpisodeV61(
    val stateKey: String,
    val safeActionId: String,
    val successfulTrapActionId: String,
    val delayedConsequenceDepth: Int,
    val nonCommuting: Boolean,
    val trapActionWinsAtAnotherReachableState: Boolean,
    val proof: VisibleDecisionProofV61,
)

enum class AdversarialPolicyKindV61 {
    ROW_MAJOR_SCAN,
    REVERSE_ROW_MAJOR_SCAN,
    EDGE_FIRST,
    CENTER_FIRST,
    NEAREST_EXIT_FIRST,
    SHORTEST_ROUTE_FIRST,
    UNAFFECTED_ARROW_FIRST,
    NEAREST_CONTROLLER_FIRST,
    POLARITY_PRESERVING_FIRST,
    STABLE_ORDERING,
    RANDOM_SUCCESSFUL_ACTION,
    FAILED_ACTION_PROBE,
    RESTART_WITH_MEMORY,
    VISIBLE_LOOKAHEAD_1,
    VISIBLE_LOOKAHEAD_2,
    VISIBLE_LOOKAHEAD_3,
    VISIBLE_LOOKAHEAD_4,
    VISIBLE_LOOKAHEAD_5,
    LIMITED_MAGNET_MEMORY,
}

data class AdversarialPolicyResultV61(
    val policy: AdversarialPolicyKindV61,
    val orientation: String,
    val lookaheadDepth: Int,
    val solvedRuns: Int,
    val runCount: Int,
    val solveRate: Double,
    val minimumActions: Int?,
    val maximumRestarts: Int,
)

data class AdversarialPolicyReportV61(
    val results: List<AdversarialPolicyResultV61>,
    val blindSingleAttemptSuccessProbability: Double,
    val randomSuccessfulActionCompletionProbability: Double,
    val bestCheapPolicySolveRate: Double,
    val expectedGuessingRestarts: Double?,
    val minimumReliableLookahead: Int?,
    val dominatingCheapPolicies: List<String>,
)

data class DifficultyEvidenceBandsV61(
    val lengthEligibilityBand: AutomatedDifficultyBandV61?,
    val criticalDecisionBand: AutomatedDifficultyBandV61?,
    val interactionBand: AutomatedDifficultyBandV61?,
    val delayedTrapBand: AutomatedDifficultyBandV61?,
    val policyResistanceBand: AutomatedDifficultyBandV61?,
    val lookaheadBand: AutomatedDifficultyBandV61?,
    val forcedRunBand: AutomatedDifficultyBandV61?,
)

data class AutomatedDifficultyAssessmentV61(
    val analyzerVersion: String,
    val maximumEligibleBand: AutomatedDifficultyBandV61?,
    val evidenceBands: DifficultyEvidenceBandsV61,
    val criticalEpisodes: List<CriticalDecisionEpisodeV61>,
    val solutionActions: Int,
    val persistentSuccessfulTrapCount: Int,
    val causalPhaseCount: Int,
    val crossChainDependencyCount: Int,
    val maximumForcedRun: Int,
    val forcedCleanupTailRatio: Double,
    val firstCriticalDecisionPosition: Int?,
    val criticalDecisionPositions: List<Int>,
    val inferableCriticalDecisionRatio: Double,
    val policyReport: AdversarialPolicyReportV61,
    val visibleInferabilityPass: Boolean,
    val guessDependencePass: Boolean,
    val semanticNoveltyPass: Boolean,
    val purposefulOccupancyPass: Boolean,
    val analysisCompletenessPass: Boolean,
    val accepted: Boolean,
    val rejectionReasons: List<String>,
)

/**
 * V6.1 assigns only the highest band supported independently by every evidence family. It consumes
 * a complete DAG produced by [CompleteDecisionDagAnalyzerV6], whose transitions are production
 * [com.rameshta.magnetrail.core.engine.GameEngine] results. No authored profile, board dimension,
 * occupancy, object count, raw legacy score, or intended solution can raise the result.
 */
class CuedAdversarialDifficultyAnalyzerV61(
    private val stochasticRuns: Int = 256,
    private val reliablePolicyThreshold: Double = 0.80,
) {
    init {
        require(stochasticRuns >= 32)
        require(reliablePolicyThreshold in 0.5..1.0)
    }

    fun analyze(
        level: LevelDefinition,
        decisionDag: DecisionDagAnalysisV6,
        causalSpec: CausalHypergraphSpec? = null,
        semanticNoveltyPass: Boolean,
        purposefulOccupancyPass: Boolean,
        knownGuessRequired: Boolean = false,
    ): AutomatedDifficultyAssessmentV61 {
        val incompleteReasons = buildList {
            if (!decisionDag.complete || decisionDag.metrics == null) add("REJECT_ANALYSIS_INCOMPLETE")
            if (level.width !in 1..V61_MAX_BOARD_SIZE || level.height !in 1..V61_MAX_BOARD_SIZE) {
                add("REJECT_UNSUPPORTED_BOARD_SIZE")
            }
        }
        if (incompleteReasons.isNotEmpty()) {
            return rejectedIncomplete(semanticNoveltyPass, purposefulOccupancyPass, incompleteReasons)
        }

        val nodes = decisionDag.nodes
        val allEpisodes = nodes.values.associate { node -> node.stateKey to episodesAt(node, decisionDag) }
        val witness = easiestWinningWitness(decisionDag, allEpisodes)
        // A deferred choice is not a new decision every time the player removes an unrelated,
        // commuting arrow. Count the episode only at the witness edge that actually resolves it.
        // This prevents long cleanup prefixes from manufacturing critical-decision credit.
        val witnessEpisodes = witness.flatMap { (node, chosenEdge) ->
            allEpisodes[node.stateKey].orEmpty().filter { it.safeActionId == chosenEdge.arrowId }
        }
            .distinctBy { Triple(it.stateKey, it.safeActionId, it.successfulTrapActionId) }
        val solutionActions = witness.size
        val forcedRun = maximumForcedRun(witness)
        val decisionPositions = witness.mapIndexedNotNull { index, (node, chosenEdge) ->
            index.takeIf { allEpisodes[node.stateKey].orEmpty().any { it.safeActionId == chosenEdge.arrowId } }
        }
        val phaseSlots = AutomatedDifficultyBandV61.MASTER.minimumCausalPhases
        val causalPhases = if (decisionPositions.isEmpty()) 1 else decisionPositions.map { index ->
            ((index * phaseSlots) / max(1, solutionActions)).coerceIn(0, phaseSlots - 1)
        }.toSet().size
        val lastDecision = decisionPositions.maxOrNull()
        val cleanupRatio = if (lastDecision == null || solutionActions == 0) 1.0 else {
            (solutionActions - lastDecision - 1).toDouble() / solutionActions
        }
        val crossChains = crossChainDependencies(causalSpec)
        val policy = AdversarialPolicyEnsembleV61(stochasticRuns, reliablePolicyThreshold).evaluate(decisionDag)
        val persistentTraps = witnessEpisodes.count { it.proof.valid }
        val minimumLookahead = witnessEpisodes.maxOfOrNull { it.proof.minimumVisibleLookahead } ?: 0
        val inferableRatio = if (witnessEpisodes.isEmpty()) 1.0 else {
            witnessEpisodes.count { it.proof.valid }.toDouble() / witnessEpisodes.size
        }

        val bands = DifficultyEvidenceBandsV61(
            lengthEligibilityBand = highestBand { solutionActions >= it.minimumSolutionActions },
            criticalDecisionBand = highestBand { witnessEpisodes.size >= it.minimumCriticalDecisions },
            interactionBand = highestBand { band ->
                causalPhases >= band.minimumCausalPhases && when (band) {
                    AutomatedDifficultyBandV61.SUPER_HARD, AutomatedDifficultyBandV61.EXPERT -> crossChains >= 2
                    AutomatedDifficultyBandV61.MASTER -> crossChains >= 3
                    AutomatedDifficultyBandV61.HARD -> crossChains >= 1
                    else -> true
                }
            },
            delayedTrapBand = highestBand { persistentTraps >= it.minimumPersistentTraps },
            policyResistanceBand = highestBand { policy.bestCheapPolicySolveRate <= it.maximumCheapPolicySolveRate },
            lookaheadBand = highestBand { minimumLookahead >= it.minimumVisibleLookahead },
            forcedRunBand = highestBand { forcedRun <= it.maximumForcedRun },
        )
        var maximum = bands.minimum()
        val reasons = linkedSetOf<String>()
        if (bands.asList().any { it == null }) reasons += "REJECT_BELOW_EASY_ELIGIBILITY"
        val visiblePass = inferableRatio == 1.0
        if (!visiblePass) reasons += "REJECT_GUESS_DEPENDENT"
        if (knownGuessRequired) reasons += "REJECT_GUESS_REPORTED_REGRESSION"
        if (!semanticNoveltyPass) reasons += "REJECT_SEMANTIC_DUPLICATE"
        if (!purposefulOccupancyPass) reasons += "REJECT_PURPOSEFUL_OCCUPANCY"

        if (maximum != null) {
            while (requireNotNull(maximum).rank >= AutomatedDifficultyBandV61.HARD.rank &&
                cleanupRatio > requireNotNull(maximum).maximumForcedCleanupTailRatio
            ) maximum = AutomatedDifficultyBandV61.atRank(requireNotNull(maximum).rank - 1)
            val firstDecision = decisionPositions.minOrNull()
            while (requireNotNull(maximum).rank >= AutomatedDifficultyBandV61.SUPER_HARD.rank &&
                (firstDecision == null || firstDecision > max(2, solutionActions / 4))
            ) maximum = AutomatedDifficultyBandV61.atRank(requireNotNull(maximum).rank - 1)
            if (requireNotNull(maximum).rank >= AutomatedDifficultyBandV61.EXPERT.rank &&
                policy.results.any { it.lookaheadDepth in 0..3 && it.solveRate >= reliablePolicyThreshold }
            ) maximum = AutomatedDifficultyBandV61.SUPER_HARD
            if (maximum == AutomatedDifficultyBandV61.MASTER &&
                policy.results.any { it.lookaheadDepth in 0..4 && it.solveRate >= reliablePolicyThreshold }
            ) maximum = AutomatedDifficultyBandV61.EXPERT
        }

        val accepted = reasons.isEmpty() && maximum != null
        return AutomatedDifficultyAssessmentV61(
            analyzerVersion = "cued-adversarial-difficulty-v6.1-schema-$V61_DIFFICULTY_SCHEMA_VERSION",
            maximumEligibleBand = maximum,
            evidenceBands = bands,
            criticalEpisodes = witnessEpisodes,
            solutionActions = solutionActions,
            persistentSuccessfulTrapCount = persistentTraps,
            causalPhaseCount = causalPhases,
            crossChainDependencyCount = crossChains,
            maximumForcedRun = forcedRun,
            forcedCleanupTailRatio = cleanupRatio,
            firstCriticalDecisionPosition = decisionPositions.minOrNull(),
            criticalDecisionPositions = decisionPositions,
            inferableCriticalDecisionRatio = inferableRatio,
            policyReport = policy,
            visibleInferabilityPass = visiblePass,
            guessDependencePass = !knownGuessRequired && visiblePass,
            semanticNoveltyPass = semanticNoveltyPass,
            purposefulOccupancyPass = purposefulOccupancyPass,
            analysisCompletenessPass = true,
            accepted = accepted,
            rejectionReasons = reasons.toList(),
        )
    }

    private fun rejectedIncomplete(
        semanticPass: Boolean,
        occupancyPass: Boolean,
        reasons: List<String>,
    ): AutomatedDifficultyAssessmentV61 {
        val emptyPolicy = AdversarialPolicyReportV61(emptyList(), 0.0, 0.0, 1.0, null, null, emptyList())
        val emptyBands = DifficultyEvidenceBandsV61(null, null, null, null, null, null, null)
        return AutomatedDifficultyAssessmentV61(
            analyzerVersion = "cued-adversarial-difficulty-v6.1-schema-$V61_DIFFICULTY_SCHEMA_VERSION",
            maximumEligibleBand = null,
            evidenceBands = emptyBands,
            criticalEpisodes = emptyList(),
            solutionActions = 0,
            persistentSuccessfulTrapCount = 0,
            causalPhaseCount = 0,
            crossChainDependencyCount = 0,
            maximumForcedRun = 0,
            forcedCleanupTailRatio = 1.0,
            firstCriticalDecisionPosition = null,
            criticalDecisionPositions = emptyList(),
            inferableCriticalDecisionRatio = 0.0,
            policyReport = emptyPolicy,
            visibleInferabilityPass = false,
            guessDependencePass = false,
            semanticNoveltyPass = semanticPass,
            purposefulOccupancyPass = occupancyPass,
            analysisCompletenessPass = false,
            accepted = false,
            rejectionReasons = reasons,
        )
    }

    private fun episodesAt(
        node: DecisionNodeV6,
        analysis: DecisionDagAnalysisV6,
    ): List<CriticalDecisionEpisodeV61> {
        if (node.solvable != true) return emptyList()
        val successful = node.transitions.filter { it.successful }
        if (successful.size < 2) return emptyList()
        val safe = successful.filter { it.futureSolvable == true }
        val traps = successful.filter { it.futureSolvable == false && (it.delayedDeadlockDepth ?: 0) >= 1 }
        if (safe.isEmpty() || traps.isEmpty()) return emptyList()
        return buildList {
            safe.sortedBy { it.arrowId }.forEach { safeEdge ->
                traps.sortedBy { it.arrowId }.forEach { trapEdge ->
                    val commute = analysis.commutation.firstOrNull { evidence ->
                        evidence.stateKey == node.stateKey && setOf(evidence.firstArrowId, evidence.secondArrowId) ==
                            setOf(safeEdge.arrowId, trapEdge.arrowId)
                    }?.commutes ?: false
                    if (commute) return@forEach
                    val winsElsewhere = analysis.nodes.values.any { candidate ->
                        candidate.transitions.any { edge ->
                            edge.arrowId == trapEdge.arrowId && edge.successful && edge.futureSolvable == true
                        }
                    }
                    val proof = visibleProof(node, safeEdge, trapEdge, analysis, winsElsewhere)
                    add(
                        CriticalDecisionEpisodeV61(
                            stateKey = node.stateKey,
                            safeActionId = safeEdge.arrowId,
                            successfulTrapActionId = trapEdge.arrowId,
                            delayedConsequenceDepth = trapEdge.delayedDeadlockDepth ?: 0,
                            nonCommuting = true,
                            trapActionWinsAtAnotherReachableState = winsElsewhere,
                            proof = proof,
                        ),
                    )
                }
            }
        }
    }

    private fun visibleProof(
        node: DecisionNodeV6,
        safe: DecisionTransitionV6,
        trap: DecisionTransitionV6,
        analysis: DecisionDagAnalysisV6,
        winsElsewhere: Boolean,
    ): VisibleDecisionProofV61 {
        val safeArrow = requireNotNull(node.state.arrow(safe.arrowId))
        val trapArrow = requireNotNull(node.state.arrow(trap.arrowId))
        val safeController = safe.controllerId?.let(node.state::magnet)
        val trapController = trap.controllerId?.let(node.state::magnet)
        val trace = traceToDeadlock(trap, analysis)
        val invalid = buildList {
            if (!winsElsewhere) add("trap action is never part of a winning reachable policy")
            if ((trap.delayedDeadlockDepth ?: 0) < 1) add("consequence is immediate")
            if (trace.isEmpty()) add("no complete visible trace to a deadlock")
            if (safeArrow.position == trapArrow.position && safeArrow.printedDirection == trapArrow.printedDirection) {
                add("strategically different actions are visually indistinguishable")
            }
            if (safe.terminalEvent.isBlank() || trap.terminalEvent.isBlank()) {
                add("production terminal event is not available")
            }
        }.toMutableList()
        val downstream = firstDownstreamDifference(safe, trap, analysis)
        if (downstream == null) invalid += "no visible downstream divergence"
        val controllerExplanation = listOfNotNull(
            safeController?.let { "safe controller ${it.position} is ${it.polarity}" },
            trapController?.let { "trap controller ${it.position} is ${it.polarity}" },
        ).ifEmpty { listOf("both arrows are uncontrolled and retain their printed directions") }
        return VisibleDecisionProofV61(
            decisionStateFingerprint = "sha256:${sha256V6(node.stateKey)}",
            safeArrowId = safe.arrowId,
            trapArrowId = trap.arrowId,
            safeArrowCell = safeArrow.position,
            trapArrowCell = trapArrow.position,
            safeEffectiveDirection = effectiveDirection(safeArrow.printedDirection, safeArrow.position, safeController),
            trapEffectiveDirection = effectiveDirection(trapArrow.printedDirection, trapArrow.position, trapController),
            safeControllerCell = safeController?.position,
            safeControllerPolarity = safeController?.polarity?.name,
            trapControllerCell = trapController?.position,
            trapControllerPolarity = trapController?.polarity?.name,
            safeRoute = safe.route,
            trapRoute = trap.route,
            downstreamObject = downstream ?: "unproven",
            firstFutureDivergence = downstream ?: "unproven",
            delayedConsequenceDepth = trap.delayedDeadlockDepth ?: 0,
            minimumVisibleLookahead = (trap.delayedDeadlockDepth ?: 0) + 1,
            trapTrace = trace,
            explanation = "From visible cells ${safeArrow.position} and ${trapArrow.position}, " +
                controllerExplanation.joinToString("; ") + ". Their effective directions are " +
                "${effectiveDirection(safeArrow.printedDirection, safeArrow.position, safeController)} and " +
                "${effectiveDirection(trapArrow.printedDirection, trapArrow.position, trapController)}, ending in " +
                "${safe.terminalEvent} and ${trap.terminalEvent}. The production routes first diverge at " +
                "${downstream ?: "an unproven state"}; following the trap route for " +
                "${(trap.delayedDeadlockDepth ?: 0) + 1} successful moves reaches a state with arrows but no successful action.",
            valid = invalid.isEmpty(),
            invalidReasons = invalid,
        )
    }

    private fun firstDownstreamDifference(
        safe: DecisionTransitionV6,
        trap: DecisionTransitionV6,
        analysis: DecisionDagAnalysisV6,
    ): String? {
        val safeNode = safe.childStateKey?.let(analysis.nodes::get) ?: return null
        val trapNode = trap.childStateKey?.let(analysis.nodes::get) ?: return null
        val ids = (safeNode.state.arrows.map { it.id } + trapNode.state.arrows.map { it.id }).distinct().sorted()
        ids.forEach { id ->
            val left = safeNode.transitions.firstOrNull { it.arrowId == id }
            val right = trapNode.transitions.firstOrNull { it.arrowId == id }
            if (left?.visibleEffect() != right?.visibleEffect()) {
                val position = safeNode.state.arrow(id)?.position ?: trapNode.state.arrow(id)?.position
                return "arrow at $position changes actionability, controller, route, or polarity effect"
            }
        }
        val polarities = (safeNode.state.magnets + trapNode.state.magnets).groupBy { it.id }
        polarities.toSortedMap().forEach { (_, values) ->
            if (values.map { it.polarity }.distinct().size > 1) return "magnet at ${values.first().position} changes polarity"
        }
        return if (safeNode.state.arrows.map { it.position }.toSet() != trapNode.state.arrows.map { it.position }.toSet()) {
            "the visible remaining-arrow set changes"
        } else null
    }

    private fun DecisionTransitionV6.visibleEffect(): String = listOf(
        successful,
        controllerId,
        route,
        terminalEvent,
        polarityChange,
    ).joinToString("|")

    private fun traceToDeadlock(
        first: DecisionTransitionV6,
        analysis: DecisionDagAnalysisV6,
    ): List<VisibleTransitionStepV61> {
        val start = first.childStateKey?.let(analysis.nodes::get) ?: return emptyList()
        val queue = ArrayDeque<Pair<DecisionNodeV6, List<DecisionTransitionV6>>>()
        val seen = hashSetOf<String>()
        queue += start to listOf(first)
        var transitions: List<DecisionTransitionV6>? = null
        while (queue.isNotEmpty()) {
            val (node, path) = queue.removeFirst()
            if (!seen.add(node.stateKey)) continue
            val successes = node.transitions.filter { it.successful }
            if (node.state.arrows.isNotEmpty() && successes.isEmpty()) {
                transitions = path
                break
            }
            successes.sortedBy { it.arrowId }.forEach { edge ->
                val child = edge.childStateKey?.let(analysis.nodes::get)
                if (child != null) queue += child to (path + edge)
            }
        }
        val path = transitions ?: return emptyList()
        val steps = mutableListOf<VisibleTransitionStepV61>()
        var stateNode: DecisionNodeV6 = requireNotNull(analysis.nodes[analysis.rootStateKey])
        path.forEach { edge ->
            val actualNode = analysis.nodes.values.firstOrNull { candidate ->
                candidate.transitions.any { it === edge }
            } ?: stateNode
            val arrow = actualNode.state.arrow(edge.arrowId) ?: return@forEach
            val controller = edge.controllerId?.let(actualNode.state::magnet)
            steps += VisibleTransitionStepV61(
                stateFingerprint = "sha256:${sha256V6(actualNode.stateKey)}",
                arrowCell = arrow.position,
                printedDirection = arrow.printedDirection,
                effectiveDirection = effectiveDirection(arrow.printedDirection, arrow.position, controller),
                controllerCell = controller?.position,
                controllerPolarity = controller?.polarity?.name,
                route = edge.route,
                terminalEvent = edge.terminalEvent,
                polarityChange = edge.polarityChange,
            )
            stateNode = edge.childStateKey?.let(analysis.nodes::get) ?: stateNode
        }
        return steps
    }

    private fun effectiveDirection(
        printedDirection: Direction,
        arrowPosition: Position,
        controller: com.rameshta.magnetrail.core.model.Magnet?,
    ): Direction = when (controller?.polarity) {
        com.rameshta.magnetrail.core.model.Polarity.PULL -> Direction.between(arrowPosition, controller.position)
        com.rameshta.magnetrail.core.model.Polarity.PUSH -> Direction.between(arrowPosition, controller.position).opposite()
        null -> printedDirection
    }

    private fun easiestWinningWitness(
        analysis: DecisionDagAnalysisV6,
        episodes: Map<String, List<CriticalDecisionEpisodeV61>>,
    ): List<Pair<DecisionNodeV6, DecisionTransitionV6>> {
        data class Cost(val decisions: Int, val lookahead: Int, val length: Int)
        val costs = hashMapOf<String, Cost>()
        fun plusDecisionCost(
            node: DecisionNodeV6,
            edge: DecisionTransitionV6,
            child: Cost,
        ): Cost {
            val resolved = episodes[node.stateKey].orEmpty().filter { it.safeActionId == edge.arrowId }
            return Cost(
                decisions = child.decisions + if (resolved.isEmpty()) 0 else 1,
                lookahead = max(child.lookahead, resolved.maxOfOrNull { it.proof.minimumVisibleLookahead } ?: 0),
                length = child.length + 1,
            )
        }
        fun cost(node: DecisionNodeV6): Cost {
            costs[node.stateKey]?.let { return it }
            if (node.state.arrows.isEmpty()) return Cost(0, 0, 0).also { costs[node.stateKey] = it }
            val best = node.transitions.filter { it.successful && it.futureSolvable == true }.map { edge ->
                edge to plusDecisionCost(node, edge, cost(requireNotNull(analysis.nodes[edge.childStateKey])))
            }.minWithOrNull(
                compareBy<Pair<DecisionTransitionV6, Cost>> { it.second.decisions }
                    .thenBy { it.second.lookahead }
                    .thenBy { it.second.length }
                    .thenBy { it.first.arrowId },
            )?.second ?: Cost(Int.MAX_VALUE / 4, Int.MAX_VALUE / 4, Int.MAX_VALUE / 4)
            return best.also { costs[node.stateKey] = it }
        }
        var node = requireNotNull(analysis.nodes[analysis.rootStateKey])
        cost(node)
        return buildList {
            while (node.state.arrows.isNotEmpty()) {
                val edge = node.transitions.filter { it.successful && it.futureSolvable == true }.minWithOrNull(
                    compareBy<DecisionTransitionV6> {
                        plusDecisionCost(node, it, cost(requireNotNull(analysis.nodes[it.childStateKey]))).decisions
                    }.thenBy {
                        plusDecisionCost(node, it, cost(requireNotNull(analysis.nodes[it.childStateKey]))).lookahead
                    }.thenBy {
                        plusDecisionCost(node, it, cost(requireNotNull(analysis.nodes[it.childStateKey]))).length
                    }
                        .thenBy { it.arrowId },
                ) ?: break
                add(node to edge)
                node = requireNotNull(analysis.nodes[edge.childStateKey])
            }
        }
    }

    private fun maximumForcedRun(witness: List<Pair<DecisionNodeV6, DecisionTransitionV6>>): Int {
        var current = 0
        var maximum = 0
        witness.forEach { (node, _) ->
            val viable = node.transitions.count { it.successful && it.futureSolvable == true }
            if (viable == 1) current += 1 else current = 0
            maximum = max(maximum, current)
        }
        return maximum
    }

    private fun crossChainDependencies(spec: CausalHypergraphSpec?): Int {
        spec ?: return 0
        val chains = spec.entities.associate { it.key to it.chain } + spec.actions.associate { it.key to it.chain }
        return spec.hyperedges.count { edge ->
            (edge.affectedRoleKeys + edge.triggeringActionRoleKey).mapNotNull(chains::get).toSet().size >= 2
        }
    }

    private fun highestBand(predicate: (AutomatedDifficultyBandV61) -> Boolean): AutomatedDifficultyBandV61? =
        AUTOMATED_CAMPAIGN_BANDS_V61.filter(predicate).maxByOrNull { it.rank }

    private fun DifficultyEvidenceBandsV61.asList(): List<AutomatedDifficultyBandV61?> = listOf(
        lengthEligibilityBand,
        criticalDecisionBand,
        interactionBand,
        delayedTrapBand,
        policyResistanceBand,
        lookaheadBand,
        forcedRunBand,
    )

    private fun DifficultyEvidenceBandsV61.minimum(): AutomatedDifficultyBandV61? {
        val values = asList()
        if (values.any { it == null }) return null
        return values.filterNotNull().minBy { it.rank }
    }
}

class AdversarialPolicyEnsembleV61(
    private val stochasticRuns: Int = 256,
    private val reliableThreshold: Double = 0.80,
) {
    fun evaluate(analysis: DecisionDagAnalysisV6): AdversarialPolicyReportV61 {
        require(analysis.complete && analysis.metrics != null)
        val deterministic = AdversarialPolicyKindV61.entries.filterNot {
            it in setOf(
                AdversarialPolicyKindV61.RANDOM_SUCCESSFUL_ACTION,
                AdversarialPolicyKindV61.RESTART_WITH_MEMORY,
            )
        }.flatMap { policy ->
            BoardSymmetry.entries.map { symmetry -> runPolicy(analysis, policy, symmetry, 0L) }
        }
        val random = runStochastic(analysis, AdversarialPolicyKindV61.RANDOM_SUCCESSFUL_ACTION)
        val restart = runStochastic(analysis, AdversarialPolicyKindV61.RESTART_WITH_MEMORY)
        val results = deterministic + random + restart
        val cheapKinds = setOf(
            AdversarialPolicyKindV61.ROW_MAJOR_SCAN,
            AdversarialPolicyKindV61.REVERSE_ROW_MAJOR_SCAN,
            AdversarialPolicyKindV61.EDGE_FIRST,
            AdversarialPolicyKindV61.CENTER_FIRST,
            AdversarialPolicyKindV61.NEAREST_EXIT_FIRST,
            AdversarialPolicyKindV61.SHORTEST_ROUTE_FIRST,
            AdversarialPolicyKindV61.UNAFFECTED_ARROW_FIRST,
            AdversarialPolicyKindV61.NEAREST_CONTROLLER_FIRST,
            AdversarialPolicyKindV61.POLARITY_PRESERVING_FIRST,
            AdversarialPolicyKindV61.STABLE_ORDERING,
            AdversarialPolicyKindV61.FAILED_ACTION_PROBE,
            AdversarialPolicyKindV61.VISIBLE_LOOKAHEAD_1,
            AdversarialPolicyKindV61.VISIBLE_LOOKAHEAD_2,
        )
        val cheap = results.filter { it.policy in cheapKinds }
        // A presentation-sensitive policy is one policy evaluated over all eight D4
        // presentations. Its reliability is the aggregate presentation success rate, not the
        // maximum of eight one-run booleans (which would call a policy 100% reliable whenever a
        // single orientation happened to match a solution).
        val cheapPolicyRates = cheap.groupBy { it.policy }.mapValues { (_, rows) ->
            rows.sumOf { it.solveRate * it.runCount } / rows.sumOf { it.runCount }.toDouble()
        }
        val bestCheap = cheapPolicyRates.values.maxOrNull() ?: 1.0
        val reliableLookahead = results.filter { it.lookaheadDepth > 0 && it.solveRate >= reliableThreshold }
            .minOfOrNull { it.lookaheadDepth }
        val root = requireNotNull(analysis.nodes[analysis.rootStateKey])
        val successfulRoot = root.transitions.filter { it.successful }
        val blind = if (successfulRoot.isEmpty()) 0.0 else {
            successfulRoot.count { it.futureSolvable == true }.toDouble() / successfulRoot.size
        }
        return AdversarialPolicyReportV61(
            results = results,
            blindSingleAttemptSuccessProbability = blind,
            randomSuccessfulActionCompletionProbability = random.solveRate,
            bestCheapPolicySolveRate = bestCheap,
            expectedGuessingRestarts = random.solveRate.takeIf { it > 0.0 }?.let { (1.0 / it) - 1.0 },
            minimumReliableLookahead = reliableLookahead,
            dominatingCheapPolicies = cheapPolicyRates.filterValues { it >= reliableThreshold }
                .keys.map { it.name }.sorted(),
        )
    }

    private fun runStochastic(
        analysis: DecisionDagAnalysisV6,
        policy: AdversarialPolicyKindV61,
    ): AdversarialPolicyResultV61 {
        val outcomes = (0 until stochasticRuns).map { run ->
            simulate(analysis, policy, BoardSymmetry.IDENTITY, run.toLong() + 0x61_000L)
        }
        val solved = outcomes.count { it != null }
        return AdversarialPolicyResultV61(
            policy = policy,
            orientation = "ALL_FIXED_SEEDS",
            lookaheadDepth = policy.lookaheadDepth(),
            solvedRuns = solved,
            runCount = stochasticRuns,
            solveRate = solved.toDouble() / stochasticRuns,
            minimumActions = outcomes.filterNotNull().minOrNull(),
            maximumRestarts = if (policy == AdversarialPolicyKindV61.RESTART_WITH_MEMORY) 8 else 0,
        )
    }

    private fun runPolicy(
        analysis: DecisionDagAnalysisV6,
        policy: AdversarialPolicyKindV61,
        symmetry: BoardSymmetry,
        seed: Long,
    ): AdversarialPolicyResultV61 {
        val actions = simulate(analysis, policy, symmetry, seed)
        return AdversarialPolicyResultV61(
            policy = policy,
            orientation = symmetry.name,
            lookaheadDepth = policy.lookaheadDepth(),
            solvedRuns = if (actions == null) 0 else 1,
            runCount = 1,
            solveRate = if (actions == null) 0.0 else 1.0,
            minimumActions = actions,
            maximumRestarts = 0,
        )
    }

    private fun simulate(
        analysis: DecisionDagAnalysisV6,
        policy: AdversarialPolicyKindV61,
        symmetry: BoardSymmetry,
        seedValue: Long,
    ): Int? {
        if (policy == AdversarialPolicyKindV61.RESTART_WITH_MEMORY) {
            return simulateRestartWithMemory(analysis, seedValue)
        }
        var seed = seedValue xor -7046029254386353131L
        var node = requireNotNull(analysis.nodes[analysis.rootStateKey])
        var actions = 0
        while (node.state.arrows.isNotEmpty()) {
            val choices = node.transitions.filter { it.successful }
            if (choices.isEmpty()) return null
            val selected = when (policy) {
                AdversarialPolicyKindV61.RANDOM_SUCCESSFUL_ACTION -> {
                    seed = splitMix(seed)
                    choices.sortedBy { it.arrowId }[(seed ushr 1).rem(choices.size.toLong()).toInt()]
                }
                else -> choices.minWithOrNull(comparator(policy, node, symmetry, analysis))
            } ?: return null
            node = selected.childStateKey?.let(analysis.nodes::get) ?: return null
            actions += 1
        }
        return actions
    }

    private fun simulateRestartWithMemory(analysis: DecisionDagAnalysisV6, seedValue: Long): Int? {
        val avoided = linkedMapOf<String, MutableSet<String>>()
        var seed = seedValue
        repeat(9) {
            var node = requireNotNull(analysis.nodes[analysis.rootStateKey])
            val path = mutableListOf<Pair<String, String>>()
            var actions = 0
            while (node.state.arrows.isNotEmpty()) {
                val available = node.transitions.filter { edge ->
                    edge.successful && edge.arrowId !in avoided[node.stateKey].orEmpty()
                }.sortedBy { it.arrowId }
                if (available.isEmpty()) break
                seed = splitMix(seed)
                val selected = available[(seed ushr 1).rem(available.size.toLong()).toInt()]
                path += node.stateKey to selected.arrowId
                node = selected.childStateKey?.let(analysis.nodes::get) ?: break
                actions += 1
            }
            if (node.state.arrows.isEmpty()) return actions
            path.lastOrNull()?.let { (state, action) -> avoided.getOrPut(state, ::linkedSetOf).add(action) }
        }
        return null
    }

    private fun comparator(
        policy: AdversarialPolicyKindV61,
        node: DecisionNodeV6,
        symmetry: BoardSymmetry,
        analysis: DecisionDagAnalysisV6,
    ): Comparator<DecisionTransitionV6> {
        fun cell(edge: DecisionTransitionV6): Position = symmetry.transform(
            requireNotNull(node.state.arrow(edge.arrowId)).position,
            node.state.width,
            node.state.height,
        )
        fun edgeDistance(position: Position): Int {
            val width = symmetry.outputWidth(node.state.width, node.state.height)
            val height = symmetry.outputHeight(node.state.width, node.state.height)
            return minOf(position.row - 1, position.column - 1, height - position.row, width - position.column)
        }
        fun centerDistance(position: Position): Int {
            val width = symmetry.outputWidth(node.state.width, node.state.height)
            val height = symmetry.outputHeight(node.state.width, node.state.height)
            return kotlin.math.abs(position.row * 2 - (height + 1)) +
                kotlin.math.abs(position.column * 2 - (width + 1))
        }
        val stable = compareBy<DecisionTransitionV6>({ cell(it).row }, { cell(it).column }, { it.arrowId })
        return when (policy) {
            AdversarialPolicyKindV61.ROW_MAJOR_SCAN,
            AdversarialPolicyKindV61.STABLE_ORDERING,
            AdversarialPolicyKindV61.FAILED_ACTION_PROBE -> stable
            AdversarialPolicyKindV61.REVERSE_ROW_MAJOR_SCAN ->
                compareByDescending<DecisionTransitionV6> { cell(it).row }
                    .thenByDescending { cell(it).column }.thenBy { it.arrowId }
            AdversarialPolicyKindV61.EDGE_FIRST -> compareBy<DecisionTransitionV6> { edgeDistance(cell(it)) }.then(stable)
            AdversarialPolicyKindV61.CENTER_FIRST -> compareBy<DecisionTransitionV6> { centerDistance(cell(it)) }.then(stable)
            AdversarialPolicyKindV61.NEAREST_EXIT_FIRST,
            AdversarialPolicyKindV61.SHORTEST_ROUTE_FIRST ->
                compareBy<DecisionTransitionV6> { it.route.size }.then(stable)
            AdversarialPolicyKindV61.UNAFFECTED_ARROW_FIRST ->
                compareBy<DecisionTransitionV6> { it.controllerId != null }.then(stable)
            AdversarialPolicyKindV61.NEAREST_CONTROLLER_FIRST ->
                compareBy<DecisionTransitionV6> { edge ->
                    val arrow = requireNotNull(node.state.arrow(edge.arrowId))
                    val magnet = edge.controllerId?.let(node.state::magnet)
                    if (magnet == null) Int.MAX_VALUE else kotlin.math.abs(arrow.position.row - magnet.position.row) +
                        kotlin.math.abs(arrow.position.column - magnet.position.column)
                }.then(stable)
            AdversarialPolicyKindV61.POLARITY_PRESERVING_FIRST ->
                compareBy<DecisionTransitionV6> { it.polarityChange != null }.then(stable)
            AdversarialPolicyKindV61.LIMITED_MAGNET_MEMORY ->
                compareBy<DecisionTransitionV6> { it.polarityChange != null }.thenBy { it.controllerId == null }.then(stable)
            AdversarialPolicyKindV61.VISIBLE_LOOKAHEAD_1,
            AdversarialPolicyKindV61.VISIBLE_LOOKAHEAD_2,
            AdversarialPolicyKindV61.VISIBLE_LOOKAHEAD_3,
            AdversarialPolicyKindV61.VISIBLE_LOOKAHEAD_4,
            AdversarialPolicyKindV61.VISIBLE_LOOKAHEAD_5 ->
                compareByDescending<DecisionTransitionV6> { visibleLookaheadValue(analysis, it, policy.lookaheadDepth()) }
                    .then(stable)
            AdversarialPolicyKindV61.RANDOM_SUCCESSFUL_ACTION,
            AdversarialPolicyKindV61.RESTART_WITH_MEMORY -> stable
        }
    }

    private fun visibleLookaheadValue(
        analysis: DecisionDagAnalysisV6,
        edge: DecisionTransitionV6,
        depth: Int,
    ): Double {
        val child = edge.childStateKey?.let(analysis.nodes::get) ?: return -10_000.0
        if (child.state.arrows.isEmpty()) return 10_000.0
        val successes = child.transitions.filter { it.successful }
        if (successes.isEmpty()) return -10_000.0
        if (depth <= 1) return successes.size.toDouble()
        return successes.maxOf { visibleLookaheadValue(analysis, it, depth - 1) } - successes.size * 0.001
    }

    private fun AdversarialPolicyKindV61.lookaheadDepth(): Int = when (this) {
        AdversarialPolicyKindV61.VISIBLE_LOOKAHEAD_1 -> 1
        AdversarialPolicyKindV61.VISIBLE_LOOKAHEAD_2 -> 2
        AdversarialPolicyKindV61.VISIBLE_LOOKAHEAD_3 -> 3
        AdversarialPolicyKindV61.VISIBLE_LOOKAHEAD_4 -> 4
        AdversarialPolicyKindV61.VISIBLE_LOOKAHEAD_5 -> 5
        AdversarialPolicyKindV61.LIMITED_MAGNET_MEMORY -> 1
        else -> 0
    }

    private fun splitMix(value: Long): Long {
        var result = value + -7046029254386353131L
        result = (result xor (result ushr 30)) * -4658895280553007687L
        result = (result xor (result ushr 27)) * -7723592293110705685L
        return result xor (result ushr 31)
    }
}
