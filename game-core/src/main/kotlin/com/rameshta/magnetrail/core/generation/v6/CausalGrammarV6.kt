package com.rameshta.magnetrail.core.generation.v6

import com.rameshta.magnetrail.core.generation.SeededRandom

/** Builds the logical solution policy before any coordinate or printed direction exists. */
class ReverseLogicalConstructorV6 {
    fun construct(identity: GeneratorV6Identity): CausalHypergraphSpec {
        val profile = V6Profiles.forBucket(identity.bucket)
        val random = SeededRandom(identity.seed xor identity.graphInstance.toLong())
        val chainCount = maxOf(
            profile.minimumInteractingChains,
            when (identity.grammarFamily) {
                CausalGrammarFamilyV6.POLARITY_LOCK_RELEASE,
                CausalGrammarFamilyV6.OCCLUSION_REVEAL_CHAIN,
                CausalGrammarFamilyV6.CANCELLATION_RELEASE,
                CausalGrammarFamilyV6.COMPETING_CONTROLLER_HANDOFF -> 1
                CausalGrammarFamilyV6.FORK_JOIN_COUPLED -> 2
                CausalGrammarFamilyV6.INTERACTING_CHAINS_DELAYED_TRAPS -> 2
            },
        )
        val targetActions = when (identity.bucket) {
            1 -> 2 + identity.graphInstance % 2
            2 -> 3 + identity.graphInstance % 2
            3 -> 5 + identity.graphInstance % 2
            4 -> 7 + identity.graphInstance % 2
            5 -> 9 + identity.graphInstance % 3
            else -> 12 + identity.graphInstance % 3
        }
        val chainLengths = distribute(targetActions, chainCount, random)
        val entities = mutableListOf<LogicalEntityRole>()
        val actions = mutableListOf<LogicalActionRole>()
        val precedence = linkedSetOf<Pair<String, String>>()
        val edges = mutableListOf<CausalHyperedge>()
        val traps = mutableListOf<SuccessfulTrapContract>()

        repeat(chainCount) { chain ->
            val magnet = "m-$chain"
            entities += LogicalEntityRole(magnet, LogicalEntityKindV6.MAGNET, chain, 0)
            val length = chainLengths[chain]
            repeat(length) { ordinal ->
                val arrow = "arrow-$chain-$ordinal"
                val action = "action-$chain-$ordinal"
                entities += LogicalEntityRole(arrow, LogicalEntityKindV6.ARROW, chain, ordinal)
                val isTrapAction = ordinal > 0 && ordinal == length - 1 &&
                    identity.bucket >= 3 && chain < profile.minimumPersistentTraps.coerceAtMost(chainCount)
                actions += LogicalActionRole(
                    action,
                    arrow,
                    if (isTrapAction) LogicalActionKindV6.SUCCESSFUL_TRAP else LogicalActionKindV6.REQUIRED,
                    chain,
                    ordinal,
                )
                if (ordinal > 0) precedence += "action-$chain-${ordinal - 1}" to action
            }
            val wallCount = if (identity.bucket == 1) 0 else 1
            repeat(wallCount) { ordinal ->
                entities += LogicalEntityRole("wall-$chain-$ordinal", LogicalEntityKindV6.WALL, chain, ordinal)
            }
        }

        when (identity.grammarFamily) {
            CausalGrammarFamilyV6.POLARITY_LOCK_RELEASE -> buildPolarity(edges, actions, entities)
            CausalGrammarFamilyV6.OCCLUSION_REVEAL_CHAIN -> buildOcclusion(edges, actions, entities)
            CausalGrammarFamilyV6.CANCELLATION_RELEASE -> buildCancellation(edges, actions, entities)
            CausalGrammarFamilyV6.COMPETING_CONTROLLER_HANDOFF -> buildHandoff(edges, actions, entities)
            CausalGrammarFamilyV6.FORK_JOIN_COUPLED -> buildForkJoin(edges, actions, precedence)
            CausalGrammarFamilyV6.INTERACTING_CHAINS_DELAYED_TRAPS ->
                buildInteractingTraps(edges, actions, precedence, traps, profile)
        }
        addChainInteractions(edges, actions, precedence, chainCount, identity.graphInstance)
        if (profile.minimumPersistentTraps > traps.size) {
            val candidates = actions.filter { it.ordinal > 0 }.sortedWith(
                compareByDescending<LogicalActionRole> { it.ordinal }.thenBy { it.key },
            )
            candidates.take(profile.minimumPersistentTraps - traps.size).forEachIndexed { index, action ->
                traps += SuccessfulTrapContract(
                    actionRoleKey = action.key,
                    minimumDelayedConsequenceDepth = (profile.minimumLookahead - 1 - index).coerceAtLeast(0),
                )
                edges += CausalHyperedge(
                    key = "trap-${edges.size}",
                    preconditions = listOf(StateCondition(StateConditionTypeV6.STATE_SOLVABLE, action.arrowRoleKey)),
                    triggeringActionRoleKey = action.key,
                    effect = CausalEffectV6.SUCCESSFUL_TRAP,
                    affectedRoleKeys = listOf(action.arrowRoleKey),
                )
            }
        }
        return CausalHypergraphSpec(
            family = identity.grammarFamily,
            entities = entities,
            actions = actions,
            hyperedges = edges,
            solutionPartialOrder = SolutionPartialOrder(actions.mapTo(linkedSetOf()) { it.key }, precedence),
            trapContracts = traps.distinctBy { it.actionRoleKey },
            interactingChainCount = chainCount,
        )
    }

    private fun buildPolarity(
        edges: MutableList<CausalHyperedge>,
        actions: List<LogicalActionRole>,
        entities: List<LogicalEntityRole>,
    ) {
        val first = actions.first()
        val second = actions.getOrElse(1) { first }
        val magnet = entities.first { it.kind == LogicalEntityKindV6.MAGNET }
        edges += CausalHyperedge(
            "polarity-flip",
            listOf(StateCondition(StateConditionTypeV6.MAGNET_PULL, magnet.key)),
            first.key,
            CausalEffectV6.FLIPS_REQUIRED_POLARITY,
            listOf(magnet.key, second.key),
        )
        edges += CausalHyperedge(
            "polarity-release",
            listOf(StateCondition(StateConditionTypeV6.ACTION_FAILS, second.key)),
            first.key,
            CausalEffectV6.ENABLES_SUCCESS,
            listOf(second.key),
        )
    }

    private fun buildOcclusion(
        edges: MutableList<CausalHyperedge>,
        actions: List<LogicalActionRole>,
        entities: List<LogicalEntityRole>,
    ) {
        val first = actions.first()
        val second = actions.getOrElse(1) { first }
        val magnet = entities.first { it.kind == LogicalEntityKindV6.MAGNET }
        edges += CausalHyperedge(
            "reveal-controller",
            listOf(StateCondition(StateConditionTypeV6.CONTROLLER_NONE, second.key)),
            first.key,
            CausalEffectV6.REVEALS_CONTROLLER,
            listOf(second.key, magnet.key),
        )
        edges += CausalHyperedge(
            "must-reveal",
            listOf(StateCondition(StateConditionTypeV6.ACTION_FAILS, second.key)),
            first.key,
            CausalEffectV6.MUST_PRECEDE,
            listOf(second.key),
        )
    }

    private fun buildCancellation(
        edges: MutableList<CausalHyperedge>,
        actions: List<LogicalActionRole>,
        entities: List<LogicalEntityRole>,
    ) {
        val first = actions.first()
        val second = actions.getOrElse(1) { first }
        val magnets = entities.filter { it.kind == LogicalEntityKindV6.MAGNET }
        edges += CausalHyperedge(
            "release-cancellation",
            listOf(StateCondition(StateConditionTypeV6.CANCELLATION_ACTIVE, second.key)),
            first.key,
            CausalEffectV6.RELEASES_CANCELLATION,
            listOf(second.key) + magnets.take(2).map { it.key },
        )
        edges += CausalHyperedge(
            "cancel-enables",
            listOf(StateCondition(StateConditionTypeV6.ACTION_FAILS, second.key)),
            first.key,
            CausalEffectV6.ENABLES_SUCCESS,
            listOf(second.key),
        )
    }

    private fun buildHandoff(
        edges: MutableList<CausalHyperedge>,
        actions: List<LogicalActionRole>,
        entities: List<LogicalEntityRole>,
    ) {
        val first = actions.first()
        val second = actions.getOrElse(1) { first }
        val magnets = entities.filter { it.kind == LogicalEntityKindV6.MAGNET }
        edges += CausalHyperedge(
            "controller-handoff",
            listOf(StateCondition(StateConditionTypeV6.CONTROLLER_IS, second.key, magnets.first().key)),
            first.key,
            CausalEffectV6.CHANGES_CONTROLLER,
            listOf(second.key, magnets.last().key),
        )
        edges += CausalHyperedge(
            "handoff-noncommuting",
            emptyList(),
            first.key,
            CausalEffectV6.NON_COMMUTING_CHOICE,
            listOf(second.key),
            requiredCounterfactual = false,
        )
    }

    private fun buildForkJoin(
        edges: MutableList<CausalHyperedge>,
        actions: List<LogicalActionRole>,
        precedence: MutableSet<Pair<String, String>>,
    ) {
        val byChain = actions.groupBy { it.chain }.toSortedMap()
        val first = requireNotNull(byChain[0]).first()
        val second = requireNotNull(byChain[1]).first()
        val join = requireNotNull(byChain[0]).last()
        precedence += first.key to join.key
        precedence += second.key to join.key
        edges += CausalHyperedge(
            "fork-join",
            listOf(
                StateCondition(StateConditionTypeV6.ARROW_ABSENT, first.arrowRoleKey),
                StateCondition(StateConditionTypeV6.ARROW_PRESENT, join.arrowRoleKey),
            ),
            second.key,
            CausalEffectV6.FORK_JOIN_DEPENDENCY,
            listOf(first.key, join.key),
        )
        edges += CausalHyperedge(
            "fork-noncommuting",
            emptyList(),
            first.key,
            CausalEffectV6.NON_COMMUTING_CHOICE,
            listOf(second.key),
            requiredCounterfactual = false,
        )
    }

    private fun buildInteractingTraps(
        edges: MutableList<CausalHyperedge>,
        actions: List<LogicalActionRole>,
        precedence: MutableSet<Pair<String, String>>,
        traps: MutableList<SuccessfulTrapContract>,
        profile: V6Profile,
    ) {
        val byChain = actions.groupBy { it.chain }.toSortedMap()
        val chainKeys = byChain.keys.toList()
        chainKeys.zipWithNext().forEachIndexed { index, (left, right) ->
            val trigger = requireNotNull(byChain[left]).first()
            val affected = requireNotNull(byChain[right]).last()
            precedence += trigger.key to affected.key
            edges += CausalHyperedge(
                "chain-couple-$index",
                listOf(StateCondition(StateConditionTypeV6.ACTION_FAILS, affected.key)),
                trigger.key,
                CausalEffectV6.ENABLES_SUCCESS,
                listOf(affected.key),
            )
        }
        actions.filter { it.ordinal > 0 }.takeLast(profile.minimumPersistentTraps).forEachIndexed { index, action ->
            traps += SuccessfulTrapContract(action.key, (profile.minimumLookahead - index - 1).coerceAtLeast(1))
            edges += CausalHyperedge(
                "delayed-trap-$index",
                listOf(StateCondition(StateConditionTypeV6.STATE_SOLVABLE, action.arrowRoleKey)),
                action.key,
                CausalEffectV6.SUCCESSFUL_TRAP,
                listOf(action.arrowRoleKey),
            )
        }
    }

    private fun addChainInteractions(
        edges: MutableList<CausalHyperedge>,
        actions: List<LogicalActionRole>,
        precedence: MutableSet<Pair<String, String>>,
        chainCount: Int,
        graphInstance: Int,
    ) {
        val byChain = actions.groupBy { it.chain }.toSortedMap()
        repeat((graphInstance % 3).coerceAtMost(actions.size / 2)) { extra ->
            val fromChain = extra % chainCount
            val toChain = (fromChain + 1) % chainCount
            if (fromChain == toChain) return@repeat
            val before = requireNotNull(byChain[fromChain])[extra % requireNotNull(byChain[fromChain]).size]
            val after = requireNotNull(byChain[toChain]).last()
            precedence += before.key to after.key
            edges += CausalHyperedge(
                "instance-coupling-$extra",
                listOf(StateCondition(StateConditionTypeV6.ACTION_FAILS, after.key)),
                before.key,
                CausalEffectV6.MUST_PRECEDE,
                listOf(after.key),
            )
        }
    }

    private fun distribute(total: Int, chains: Int, random: SeededRandom): List<Int> {
        val result = MutableList(chains) { 1 }
        repeat((total - chains).coerceAtLeast(0)) {
            val minimum = result.minOrNull() ?: 1
            val candidates = result.indices.filter { result[it] <= minimum + 1 }
            result[candidates[random.nextInt(candidates.size)]] += 1
        }
        return result
    }
}
