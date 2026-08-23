package com.rameshta.magnetrail.core.generation.v6

import com.rameshta.magnetrail.core.engine.DeterministicRouteTracer
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.engine.ResolutionResult
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.Polarity

data class CausalVerificationV6(
    val witnesses: List<CausalWitness>,
    val complete: Boolean,
    val rejections: List<V6Rejection>,
)

class ProductionCausalVerifierV6(
    private val engine: GameEngine,
    private val tracer: DeterministicRouteTracer = DeterministicRouteTracer(),
) {
    fun verify(
        spec: CausalHypergraphSpec,
        roleBindings: Map<String, String>,
        decisionDag: DecisionDagAnalysisV6,
    ): CausalVerificationV6 {
        if (!decisionDag.complete || decisionDag.metrics == null) {
            return CausalVerificationV6(
                emptyList(),
                false,
                listOf(V6Rejection(V6RejectionCode.DECISION_ANALYSIS_TRUNCATED, decisionDag.truncationReasons.joinToString())),
            )
        }
        val witnesses = mutableListOf<CausalWitness>()
        val rejections = mutableListOf<V6Rejection>()
        spec.hyperedges.sortedBy { it.key }.forEach { edge ->
            val witness = decisionDag.nodes.values.sortedBy { it.stateKey }.firstNotNullOfOrNull { node ->
                if (!edge.preconditions.all { condition -> conditionMatches(condition, spec, roleBindings, node, decisionDag) }) {
                    return@firstNotNullOfOrNull null
                }
                witnessFor(edge, spec, roleBindings, node, decisionDag)
            }
            if (witness == null) {
                rejections += V6Rejection(
                    V6RejectionCode.CAUSAL_WITNESS_MISSING,
                    "No reachable production-engine witness for ${edge.key}:${edge.effect}",
                    edge.key,
                )
            } else {
                witnesses += witness
            }
        }
        spec.trapContracts.sortedBy { it.actionRoleKey }.forEach { contract ->
            val arrowId = actionArrowId(contract.actionRoleKey, spec, roleBindings)
            val trapEdges = decisionDag.nodes.values.flatMap { node ->
                node.transitions.filter { it.arrowId == arrowId && it.successful && it.futureSolvable == false }
            }
            val correctEdges = decisionDag.nodes.values.flatMap { node ->
                node.transitions.filter { it.arrowId == arrowId && it.successful && it.futureSolvable == true }
            }
            when {
                trapEdges.isEmpty() -> rejections += V6Rejection(
                    V6RejectionCode.SUCCESSFUL_TRAP_INVALID,
                    "Trap ${contract.actionRoleKey} has no successful losing transition",
                    contract.actionRoleKey,
                )
                trapEdges.none { (it.delayedDeadlockDepth ?: 0) >= contract.minimumDelayedConsequenceDepth } ->
                    rejections += V6Rejection(
                        V6RejectionCode.SUCCESSFUL_TRAP_INVALID,
                        "Trap ${contract.actionRoleKey} consequence depth is below ${contract.minimumDelayedConsequenceDepth}",
                        contract.actionRoleKey,
                    )
                contract.requiredAtCorrectPoint && correctEdges.isEmpty() -> rejections += V6Rejection(
                    V6RejectionCode.SUCCESSFUL_TRAP_INVALID,
                    "Trap action ${contract.actionRoleKey} is not part of any winning policy at the correct point",
                    contract.actionRoleKey,
                )
            }
        }
        return CausalVerificationV6(witnesses, rejections.isEmpty(), rejections)
    }

    private fun witnessFor(
        edge: CausalHyperedge,
        spec: CausalHypergraphSpec,
        bindings: Map<String, String>,
        node: DecisionNodeV6,
        dag: DecisionDagAnalysisV6,
    ): CausalWitness? {
        val triggerId = actionArrowId(edge.triggeringActionRoleKey, spec, bindings)
        if (node.state.arrow(triggerId) == null) return null
        val trigger = engine.resolve(node.state, PlayerAction(triggerId))
        if (!trigger.success) return null
        val child = dag.nodes[exactStateKeyV6(trigger.resultingState)] ?: return null
        val affectedActionRole = edge.affectedRoleKeys.firstOrNull { key -> spec.actions.any { it.key == key } }
        val affectedArrowId = affectedActionRole?.let { actionArrowId(it, spec, bindings) }
        val before = affectedArrowId?.takeIf { node.state.arrow(it) != null }?.let {
            engine.resolve(node.state, PlayerAction(it))
        }
        val after = affectedArrowId?.takeIf { child.state.arrow(it) != null }?.let {
            engine.resolve(child.state, PlayerAction(it))
        }
        val effectObserved = when (edge.effect) {
            CausalEffectV6.MUST_PRECEDE,
            CausalEffectV6.ENABLES_SUCCESS -> before?.success == false && after?.success == true
            CausalEffectV6.DISABLES_SUCCESS -> before?.success == true && after?.success == false
            CausalEffectV6.REVEALS_CONTROLLER -> before?.controllingMagnetId == null && after?.controllingMagnetId != null
            CausalEffectV6.OCCLUDES_CONTROLLER -> before?.controllingMagnetId != null && after?.controllingMagnetId == null
            CausalEffectV6.CHANGES_CONTROLLER -> before != null && after != null &&
                before.controllingMagnetId != after.controllingMagnetId
            CausalEffectV6.FLIPS_REQUIRED_POLARITY -> trigger.polarityChange != null && (
                affectedArrowId == null || before != null && after != null && resolutionSignature(before) != resolutionSignature(after)
                )
            CausalEffectV6.RELEASES_CANCELLATION -> affectedArrowId != null &&
                cancellation(node.state, affectedArrowId) && !cancellation(child.state, affectedArrowId)
            CausalEffectV6.CREATES_CANCELLATION -> affectedArrowId != null &&
                !cancellation(node.state, affectedArrowId) && cancellation(child.state, affectedArrowId)
            CausalEffectV6.SUCCESSFUL_TRAP -> node.transitions.any {
                it.arrowId == triggerId && it.successful && it.futureSolvable == false
            }
            CausalEffectV6.FORK_JOIN_DEPENDENCY -> before?.success == false && after?.success == true &&
                edge.preconditions.size >= 2
            CausalEffectV6.NON_COMMUTING_CHOICE -> affectedArrowId != null && dag.commutation.any { evidence ->
                evidence.stateKey == node.stateKey && !evidence.commutes &&
                    setOf(evidence.firstArrowId, evidence.secondArrowId) == setOf(triggerId, affectedArrowId)
            }
        }
        if (!effectObserved) return null
        val counterfactual = when {
            before != null && after != null -> "affected:${resolutionSignature(before)} -> ${resolutionSignature(after)}"
            edge.effect == CausalEffectV6.SUCCESSFUL_TRAP -> {
                val transition = node.transitions.first { it.arrowId == triggerId && it.successful }
                "successful-losing:delayed-depth=${transition.delayedDeadlockDepth ?: 0}"
            }
            edge.effect == CausalEffectV6.NON_COMMUTING_CHOICE -> "both orders tested against exact production states"
            else -> null
        }
        if (edge.requiredCounterfactual && counterfactual == null) return null
        return CausalWitness(
            hyperedgeKey = edge.key,
            preStateKey = node.stateKey,
            preState = node.state,
            triggerArrowId = triggerId,
            resolution = trigger,
            postStateKey = child.stateKey,
            observedControllerId = trigger.controllingMagnetId,
            observedRoute = trigger.traversedCells.map { "${it.row},${it.column}" },
            observedTerminalEvent = trigger.terminalEvent::class.simpleName.orEmpty(),
            observedPolarityChange = trigger.polarityChange?.let { "${it.magnetId}:${it.from}>${it.to}" },
            claimedEffect = edge.effect,
            affectedArrowId = affectedArrowId,
            beforeAffectedResolution = before,
            afterAffectedResolution = after,
            counterfactualEvidence = counterfactual,
        )
    }

    private fun conditionMatches(
        condition: StateCondition,
        spec: CausalHypergraphSpec,
        bindings: Map<String, String>,
        node: DecisionNodeV6,
        dag: DecisionDagAnalysisV6,
    ): Boolean {
        val subjectEntity = bindings[condition.subjectRoleKey]
        val subjectAction = spec.actions.firstOrNull { it.key == condition.subjectRoleKey }
        val actionArrow = subjectAction?.let { actionArrowId(it.key, spec, bindings) }
        return when (condition.type) {
            StateConditionTypeV6.ARROW_PRESENT -> node.state.arrow(requireNotNull(subjectEntity)) != null
            StateConditionTypeV6.ARROW_ABSENT -> node.state.arrow(requireNotNull(subjectEntity)) == null
            StateConditionTypeV6.MAGNET_PULL -> node.state.magnet(requireNotNull(subjectEntity))?.polarity == Polarity.PULL
            StateConditionTypeV6.MAGNET_PUSH -> node.state.magnet(requireNotNull(subjectEntity))?.polarity == Polarity.PUSH
            StateConditionTypeV6.CONTROLLER_IS -> {
                val result = resolveAction(node.state, requireNotNull(actionArrow)) ?: return false
                result.controllingMagnetId == bindings[condition.valueRoleKey]
            }
            StateConditionTypeV6.CONTROLLER_NONE -> resolveAction(node.state, requireNotNull(actionArrow))
                ?.controllingMagnetId == null
            StateConditionTypeV6.CANCELLATION_ACTIVE -> cancellation(node.state, requireNotNull(actionArrow))
            StateConditionTypeV6.STATE_SOLVABLE -> node.solvable == true
            StateConditionTypeV6.STATE_UNSOLVABLE -> node.solvable == false
            StateConditionTypeV6.ACTION_SUCCEEDS -> resolveAction(node.state, requireNotNull(actionArrow))?.success == true
            StateConditionTypeV6.ACTION_FAILS -> resolveAction(node.state, requireNotNull(actionArrow))?.success == false
        }
    }

    private fun actionArrowId(
        actionRoleKey: String,
        spec: CausalHypergraphSpec,
        bindings: Map<String, String>,
    ): String {
        bindings[actionRoleKey]?.let { return it }
        val action = spec.actions.single { it.key == actionRoleKey }
        return requireNotNull(bindings[action.arrowRoleKey]) { "Missing role binding for ${action.arrowRoleKey}" }
    }

    private fun resolveAction(state: BoardState, arrowId: String): ResolutionResult? =
        if (state.arrow(arrowId) == null) null else engine.resolve(state, PlayerAction(arrowId))

    private fun cancellation(state: BoardState, arrowId: String): Boolean = state.arrow(arrowId)?.let { arrow ->
        tracer.explainControl(state, arrow).cancelledByEqualNearestMagnets
    } ?: false

    private fun resolutionSignature(result: ResolutionResult): String = listOf(
        result.success,
        result.effectiveDirection,
        result.controllingMagnetId,
        result.traversedCells,
        result.terminalEvent::class.simpleName,
        result.polarityChange,
    ).joinToString("|")
}
