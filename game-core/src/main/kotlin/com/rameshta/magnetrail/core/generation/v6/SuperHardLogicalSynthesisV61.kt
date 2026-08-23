package com.rameshta.magnetrail.core.generation.v6

import java.security.MessageDigest

const val SUPER_HARD_LOGICAL_SYNTHESIS_VERSION_V61 = "super-hard-state-conditioned-logical-graph-v1"

enum class SuperHardSpatialKernelV61 {
    CONTROLLER_INTERLOCK,
    NESTED_CANCELLATION,
    CANCELLATION_HANDOFF,
}

data class SuperHardLogicalPlanV61(
    val spec: CausalHypergraphSpec,
    val canonicalGraphFingerprint: String,
    val kernel: SuperHardSpatialKernelV61,
    val primaryRefinementIndex: Int,
    val secondaryRefinementIndex: Int,
    val directionPhase: Int,
    val phaseByActionRole: Map<String, Int>,
    val decisionPairs: List<Pair<String, String>>,
    val crossChainDependencies: Set<Pair<String, String>>,
) {
    init {
        require(spec.actions.size == 17)
        require(spec.trapContracts.size >= 2)
        require(spec.interactingChainCount >= 3)
        require(phaseByActionRole.keys == spec.solutionPartialOrder.actionRoleKeys)
        require(phaseByActionRole.values.toSet() == setOf(0, 1, 2))
        require(decisionPairs.size >= 4)
        require(crossChainDependencies.size >= 2)
        require(canonicalGraphFingerprint.startsWith("sha256:"))
    }
}

/**
 * Constructs the Super Hard policy graph before geometry. The graph varies the three chain
 * partitions, phase-local decisions, delayed trap depths, topology effect, and a deterministic
 * subset of phase-forward cross-chain dependencies. Only the independently extracted production
 * graph and complete decision DAG can certify the subsequent spatial proposal.
 */
class SuperHardLogicalGraphSynthesizerV61 {
    fun synthesize(identity: GeneratorV61Identity): SuperHardLogicalPlanV61 {
        require(identity.band == AutomatedDifficultyBandV61.SUPER_HARD)
        val structuralCode = structuralCode(identity)
        val variantKey = Math.floorMod(
            (structuralCode xor (structuralCode ushr 32)).toInt(),
            Int.MAX_VALUE,
        )
        val chainLengths = chainLengthTemplates[variantKey % chainLengthTemplates.size]
        val entities = mutableListOf<LogicalEntityRole>()
        val actions = mutableListOf<LogicalActionRole>()
        val phaseByAction = linkedMapOf<String, Int>()
        val byChain = linkedMapOf<Int, List<LogicalActionRole>>()

        chainLengths.forEachIndexed { chain, length ->
            entities += LogicalEntityRole("super-hard-magnet-$chain", LogicalEntityKindV6.MAGNET, chain, 0)
            val chainActions = (0 until length).map { ordinal ->
                val arrowKey = "super-hard-arrow-$chain-$ordinal"
                val actionKey = "super-hard-action-$chain-$ordinal"
                entities += LogicalEntityRole(arrowKey, LogicalEntityKindV6.ARROW, chain, ordinal)
                phaseByAction[actionKey] = phaseFor(ordinal, length)
                LogicalActionRole(
                    key = actionKey,
                    arrowRoleKey = arrowKey,
                    kind = if (ordinal == length - 1) {
                        LogicalActionKindV6.SUCCESSFUL_TRAP
                    } else {
                        LogicalActionKindV6.REQUIRED
                    },
                    chain = chain,
                    ordinal = ordinal,
                )
            }
            actions += chainActions
            byChain[chain] = chainActions
        }
        entities += LogicalEntityRole("super-hard-wall-0", LogicalEntityKindV6.WALL, 0, 0)

        val precedence = linkedSetOf<Pair<String, String>>()
        byChain.values.forEach { chain ->
            chain.zipWithNext().forEach { (before, after) -> precedence += before.key to after.key }
        }
        val possibleCrossDependencies = buildList {
            for (fromChain in 0..2) for (toChain in 0..2) {
                if (fromChain == toChain) continue
                for (fromPhase in 0..1) for (toPhase in (fromPhase + 1)..2) {
                    requireNotNull(byChain[fromChain]).filter { phaseByAction.getValue(it.key) == fromPhase }
                        .forEach { before ->
                            requireNotNull(byChain[toChain]).filter { phaseByAction.getValue(it.key) == toPhase }
                                .forEach { after -> add(before.key to after.key) }
                        }
                }
            }
        }.distinct().sortedWith(compareBy<Pair<String, String>>({ it.first }, { it.second }))
        val crossDependencies = linkedSetOf<Pair<String, String>>()
        val targetCrossCount = 2 + Math.floorMod((structuralCode ushr 7).toInt(), 6)
        possibleCrossDependencies
            .sortedWith(compareBy<Pair<String, String>>(
                { edgeSelectionKey(structuralCode, it) },
                { it.first },
                { it.second },
            ))
            .take(targetCrossCount)
            .forEach(crossDependencies::add)
        precedence += crossDependencies

        val rotation = variantKey % 3
        val decisionPairs = buildList {
            repeat(4) { decision ->
                val phase = minOf(2, decision * 3 / 4)
                val leftChain = (rotation + decision) % 3
                val rightChain = (leftChain + 1 + decision % 2) % 3
                add(
                    actionInPhase(requireNotNull(byChain[leftChain]), phase).key to
                        actionInPhase(requireNotNull(byChain[rightChain]), phase).key,
                )
            }
        }.distinct().let { pairs ->
            if (pairs.size >= 4) pairs else {
                buildList {
                    addAll(pairs)
                    for (phase in 0..2) {
                        val candidates = actions.filter { phaseByAction.getValue(it.key) == phase }
                            .sortedBy { it.key }
                        candidates.indices.forEach { index ->
                            val left = candidates[index]
                            val right = candidates[(index + 1) % candidates.size]
                            if (left.chain != right.chain) add(left.key to right.key)
                        }
                    }
                }.distinct().take(4)
            }
        }
        require(decisionPairs.size == 4)

        val hyperedges = mutableListOf<CausalHyperedge>()
        decisionPairs.forEachIndexed { index, (left, right) ->
            hyperedges += CausalHyperedge(
                key = "super-hard-decision-$index",
                preconditions = listOf(StateCondition(StateConditionTypeV6.STATE_SOLVABLE, left)),
                triggeringActionRoleKey = left,
                effect = CausalEffectV6.NON_COMMUTING_CHOICE,
                affectedRoleKeys = listOf(right),
                requiredCounterfactual = false,
            )
        }
        crossDependencies.forEachIndexed { index, (before, after) ->
            hyperedges += CausalHyperedge(
                key = "super-hard-cross-chain-$index",
                preconditions = listOf(StateCondition(StateConditionTypeV6.ACTION_FAILS, after)),
                triggeringActionRoleKey = before,
                effect = if ((variantKey + index) % 2 == 0) {
                    CausalEffectV6.FORK_JOIN_DEPENDENCY
                } else {
                    CausalEffectV6.MUST_PRECEDE
                },
                affectedRoleKeys = listOf(after),
            )
        }
        val traps = byChain.values.mapIndexed { chain, chainActions ->
            val trap = chainActions.last()
            SuccessfulTrapContract(trap.key, minimumDelayedConsequenceDepth = 2 + (variantKey + chain) % 4)
                .also {
                    hyperedges += CausalHyperedge(
                        key = "super-hard-trap-$chain",
                        preconditions = listOf(StateCondition(StateConditionTypeV6.STATE_SOLVABLE, trap.key)),
                        triggeringActionRoleKey = trap.key,
                        effect = CausalEffectV6.SUCCESSFUL_TRAP,
                        affectedRoleKeys = listOf(trap.key),
                    )
                }
        }
        addTopologyEdge(identity, variantKey, byChain, hyperedges)

        val spec = CausalHypergraphSpec(
            family = causalGrammar(identity.topologyFamily),
            entities = entities,
            actions = actions,
            hyperedges = hyperedges,
            solutionPartialOrder = SolutionPartialOrder(actions.mapTo(linkedSetOf()) { it.key }, precedence),
            trapContracts = traps,
            interactingChainCount = 3,
        )
        val fingerprint = SemanticFingerprintBuilderV6().causalSignature(spec)
        val digest = fingerprint.removePrefix("sha256:").chunked(2).take(8)
            .fold(0L) { value, byte -> (value shl 8) or byte.toLong(16) }
        return SuperHardLogicalPlanV61(
            spec = spec,
            canonicalGraphFingerprint = fingerprint,
            kernel = SuperHardSpatialKernelV61.entries[Math.floorMod((digest xor identity.seed).toInt(), 3)],
            primaryRefinementIndex = Math.floorMod((digest ushr 7).toInt() + variantKey, 2_003),
            secondaryRefinementIndex = Math.floorMod((digest ushr 19).toInt() + variantKey * 7, 2_011),
            directionPhase = Math.floorMod((digest ushr 31).toInt() + identity.attempt, 4),
            phaseByActionRole = phaseByAction,
            decisionPairs = decisionPairs,
            crossChainDependencies = crossDependencies,
        )
    }

    private fun addTopologyEdge(
        identity: GeneratorV61Identity,
        variantKey: Int,
        byChain: Map<Int, List<LogicalActionRole>>,
        edges: MutableList<CausalHyperedge>,
    ) {
        val trigger = actionInPhase(requireNotNull(byChain[variantKey % 3]), 0)
        val affected = actionInPhase(requireNotNull(byChain[(variantKey + 1) % 3]), 2)
        val effect = when (identity.topologyFamily) {
            TopologyFamilyV61.SHARED_MAGNET_PARITY_BRAID -> CausalEffectV6.FLIPS_REQUIRED_POLARITY
            TopologyFamilyV61.NESTED_NON_COMMUTING_CHOICES -> CausalEffectV6.FORK_JOIN_DEPENDENCY
            TopologyFamilyV61.WRONG_NOW_RIGHT_LATER -> CausalEffectV6.DISABLES_SUCCESS
            TopologyFamilyV61.CROSS_CHAIN_CONTROLLER_HANDOFF -> CausalEffectV6.CHANGES_CONTROLLER
            TopologyFamilyV61.OCCLUSION_REVEAL_FORK_JOIN -> CausalEffectV6.REVEALS_CONTROLLER
            TopologyFamilyV61.CANCELLATION_POLARITY_RESTORATION -> CausalEffectV6.RELEASES_CANCELLATION
            TopologyFamilyV61.TWO_CHAIN_INTERLOCK -> CausalEffectV6.CREATES_CANCELLATION
            TopologyFamilyV61.OPENING_TO_ENDGAME_DELAYED_TRAP -> CausalEffectV6.ENABLES_SUCCESS
        }
        edges += CausalHyperedge(
            key = "super-hard-topology-${identity.topologyFamily.name.lowercase()}",
            preconditions = listOf(StateCondition(StateConditionTypeV6.ARROW_PRESENT, affected.key)),
            triggeringActionRoleKey = trigger.key,
            effect = effect,
            affectedRoleKeys = listOf(affected.key),
            requiredCounterfactual = effect != CausalEffectV6.FLIPS_REQUIRED_POLARITY,
        )
    }

    private fun phaseFor(ordinal: Int, length: Int): Int = ((ordinal * 3) / length).coerceIn(0, 2)

    private fun actionInPhase(actions: List<LogicalActionRole>, phase: Int): LogicalActionRole {
        val matching = actions.filter { phaseFor(it.ordinal, actions.size) == phase }
        return matching[matching.size / 2]
    }

    private fun causalGrammar(topology: TopologyFamilyV61): CausalGrammarFamilyV6 = when (topology) {
        TopologyFamilyV61.SHARED_MAGNET_PARITY_BRAID -> CausalGrammarFamilyV6.POLARITY_LOCK_RELEASE
        TopologyFamilyV61.NESTED_NON_COMMUTING_CHOICES,
        TopologyFamilyV61.TWO_CHAIN_INTERLOCK -> CausalGrammarFamilyV6.FORK_JOIN_COUPLED
        TopologyFamilyV61.WRONG_NOW_RIGHT_LATER,
        TopologyFamilyV61.OPENING_TO_ENDGAME_DELAYED_TRAP -> CausalGrammarFamilyV6.INTERACTING_CHAINS_DELAYED_TRAPS
        TopologyFamilyV61.CROSS_CHAIN_CONTROLLER_HANDOFF -> CausalGrammarFamilyV6.COMPETING_CONTROLLER_HANDOFF
        TopologyFamilyV61.OCCLUSION_REVEAL_FORK_JOIN -> CausalGrammarFamilyV6.OCCLUSION_REVEAL_CHAIN
        TopologyFamilyV61.CANCELLATION_POLARITY_RESTORATION -> CausalGrammarFamilyV6.CANCELLATION_RELEASE
    }

    private fun structuralCode(identity: GeneratorV61Identity): Long {
        val bytes = MessageDigest.getInstance("SHA-256").digest(
            listOf(
                SUPER_HARD_LOGICAL_SYNTHESIS_VERSION_V61,
                identity.ordinal,
                identity.attempt,
                identity.topologyFamily,
                identity.familyVariant,
                identity.seed,
            ).joinToString("|").toByteArray(),
        )
        return bytes.take(8).fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 0xff) }
    }

    private fun edgeSelectionKey(structuralCode: Long, edge: Pair<String, String>): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$structuralCode|${edge.first}|${edge.second}".toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private val chainLengthTemplates = listOf(
        listOf(4, 5, 8), listOf(4, 6, 7), listOf(5, 4, 8), listOf(5, 5, 7),
        listOf(5, 6, 6), listOf(6, 4, 7), listOf(6, 5, 6), listOf(7, 4, 6),
        listOf(7, 5, 5), listOf(8, 4, 5), listOf(4, 7, 6), listOf(6, 7, 4),
    )
}

internal fun superHardLogicalPlanDigestV61(plan: SuperHardLogicalPlanV61): String = MessageDigest
    .getInstance("SHA-256")
    .digest(
        listOf(
            SUPER_HARD_LOGICAL_SYNTHESIS_VERSION_V61,
            plan.canonicalGraphFingerprint,
            plan.kernel,
            plan.primaryRefinementIndex,
            plan.secondaryRefinementIndex,
            plan.directionPhase,
        ).joinToString("|").toByteArray(),
    ).joinToString("") { "%02x".format(it.toInt() and 0xff) }
