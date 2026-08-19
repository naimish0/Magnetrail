package com.rameshta.magnetrail.core.difficulty.v4

import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.engine.ResolutionResult
import com.rameshta.magnetrail.core.engine.TerminalEvent
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.solver.StateKey
import kotlin.math.ln

class DifficultyV4Analyzer(
    private val engine: GameEngine = DefaultGameEngine(),
    private val config: DifficultyV4Config = DifficultyV4Config(),
) {
    fun analyze(level: LevelDefinition): DifficultyV4Score {
        val baseBudget = V4SearchBudget(
            maxStates = config.maxExpandedStates,
            maxActionResolutions = config.maxActionResolutions,
            maxDepth = config.maxSearchDepth,
        )
        val counterBudget = V4SearchBudget(
            maxStates = config.maxCounterfactualStates,
            maxActionResolutions = config.maxCounterfactualActionResolutions,
            maxDepth = config.maxSearchDepth,
            reasonPrefix = "COUNTERFACTUAL_",
        )
        val graph = V4SearchGraph(engine, baseBudget)
        val root = requireNotNull(graph.expand(level.initialState()))
        val rootFacts = graph.facts(root)
        graph.nodes.values.forEach(graph::facts)
        val solvableNodes = graph.nodes.values.filter { graph.facts(it).solvable == true }
        val commuteCache = mutableMapOf<CommutationKey, Boolean?>()
        fun commute(state: BoardState, first: String, second: String): Boolean? {
            val ordered = if (first <= second) first to second else second to first
            val key = CommutationKey(StateKey.from(state), ordered.first, ordered.second)
            return commuteCache.getOrPut(key) {
                actionsCommute(state, ordered.first, ordered.second, counterBudget)
            }
        }

        val nodeDiagnostics = solvableNodes.associateWith { node ->
            diagnoseNode(graph, node, ::commute)
        }
        val meaningfulKeys = nodeDiagnostics.filterValues { it.meaningful }.keys
            .mapTo(mutableSetOf()) { StateKey.from(it.state) }
        val actionCounts = actionCounts(nodeDiagnostics.values)
        val consequence = consequenceMetrics(graph, nodeDiagnostics, meaningfulKeys)
        val recovery = recoveryMetrics(graph, nodeDiagnostics)

        val sequences = graph.winningSequences(root, config.maxWinningSequences)
        if (sequences.truncated) baseBudget.truncate("WINNING_SEQUENCE_ENUMERATION_CAP")
        val strategy = strategyMetrics(
            graph = graph,
            rootFacts = rootFacts,
            sequences = sequences,
            commute = ::commute,
            nodeDiagnostics = nodeDiagnostics,
        )
        val ordering = orderingMetrics(level, sequences, strategy, nodeDiagnostics)
        val canonical = forcedDecisionMetrics(graph, root, meaningfulKeys)
        val greedy = greedyPolicy(level, graph, root)
        val random = randomPolicy(graph, root)
        val polarityResult = polarityMetrics(graph, solvableNodes, counterBudget)
        val polarity = polarityResult.metrics
        val objects = objectRelevanceMetrics(
            level,
            rootFacts,
            sequences,
            polarity,
            polarityResult.impactfulMagnetIds,
            counterBudget,
        )

        val allTruncationReasons = (baseBudget.truncationReasons + counterBudget.truncationReasons)
            .distinct().sorted()
        val baseComplete = rootFacts.complete && baseBudget.truncationReasons.none {
            it == "EXPANDED_STATE_CAP" || it == "ACTION_RESOLUTION_CAP" || it == "SEARCH_DEPTH_CAP"
        }
        val metricStatus = linkedMapOf(
            "stateGraph" to if (baseComplete) "COMPLETE" else "INCOMPLETE",
            "strategy" to if (strategy.analysisComplete) "COMPLETE" else "INCOMPLETE",
            "ordering" to if (ordering.analysisComplete) "COMPLETE" else "INCOMPLETE",
            "polarity" to if (polarity.analysisComplete) "COMPLETE" else "INCOMPLETE",
            "objectRelevance" to if (objects.analysisComplete) "COMPLETE" else "INCOMPLETE",
            "humanPerceivedObviousness" to "NOT_MEASURABLE_WITH_CURRENT_IMPLEMENTATION",
            "visualFairness" to "NOT_MEASURABLE_WITH_CURRENT_IMPLEMENTATION",
        )
        val metrics = DifficultyV4Metrics(
            levelId = level.id,
            levelNumber = level.number,
            boardWidth = level.width,
            boardHeight = level.height,
            arrowCount = level.arrows.size,
            magnetCount = level.magnets.size,
            wallCount = level.walls.size,
            plausibleChoiceCount = actionCounts.plausible,
            immediatelyInvalidChoiceCount = actionCounts.invalid,
            successfulChoiceCount = actionCounts.successful,
            safeSuccessfulChoiceCount = actionCounts.safe,
            meaningfulSuccessfulChoiceCount = actionCounts.meaningful,
            capabilityChangingSuccessfulChoiceCount = actionCounts.capabilityChanging,
            solutionReducingSuccessfulChoiceCount = actionCounts.solutionReducing,
            futureDeadEndChoiceCount = actionCounts.fatal,
            harmfulDecisionCount = nodeDiagnostics.values.count { it.harmful },
            meaningfulDecisionStateCount = nodeDiagnostics.values.count { it.meaningful },
            meaningfulFailureRate = ratio(actionCounts.fatal, actionCounts.successful),
            harmfulDecisionDensity = ratio(
                nodeDiagnostics.values.count { it.harmful },
                nodeDiagnostics.values.count { it.meaningful },
            ),
            safeChoiceRatio = ratio(actionCounts.safe, actionCounts.successful),
            meaningfulSuccessfulChoiceRatio = ratio(actionCounts.meaningful, actionCounts.successful),
            consequencePersistence = consequence,
            ordering = ordering,
            polarity = polarity,
            strategy = strategy,
            greedyPolicy = greedy,
            randomPolicy = random,
            recovery = recovery,
            forcedDecision = canonical,
            objectRelevance = objects,
            solvable = rootFacts.solvable,
            searchComplete = baseComplete,
            searchStateCount = baseBudget.expandedStates,
            actionResolutionCount = baseBudget.actionResolutions,
            searchTruncated = allTruncationReasons.isNotEmpty(),
            truncationReasons = allTruncationReasons,
            metricStatus = metricStatus,
            analyzerVersion = config.analyzerVersion,
        )
        return DifficultyV4Scorer.score(metrics, config)
    }

    private fun diagnoseNode(
        graph: V4SearchGraph,
        node: V4Node,
        commute: (BoardState, String, String) -> Boolean?,
    ): NodeDiagnostic {
        val successful = graph.successful(node)
        val viable = graph.viable(node)
        val fatal = graph.fatal(node)
        val viablePairs = mutableListOf<Pair<V4Edge, V4Edge>>()
        val nonCommutingPairs = mutableListOf<Pair<V4Edge, V4Edge>>()
        var commutationUnknown = false
        viable.forEachIndexed { firstIndex, first ->
            for (secondIndex in firstIndex + 1..<viable.size) {
                val second = viable[secondIndex]
                val pair = first to second
                viablePairs += pair
                when (commute(node.state, first.actionId, second.actionId)) {
                    true -> Unit
                    false -> nonCommutingPairs += pair
                    null -> commutationUnknown = true
                }
            }
        }
        val signatures = viable.map(::capabilitySignature).toSet()
        val harmful = viable.isNotEmpty() && fatal.isNotEmpty()
        val meaningful = successful.size >= 2 && (
            harmful || nonCommutingPairs.isNotEmpty() || signatures.size >= 2
            )
        val maximumSolutions = viable.maxOfOrNull { edge -> graph.facts(requireNotNull(edge.child)).winningSequenceCount }
        val reducing = if (maximumSolutions == null) {
            emptySet()
        } else {
            viable.filterTo(mutableSetOf()) { edge ->
                graph.facts(requireNotNull(edge.child)).winningSequenceCount < maximumSolutions
            }
        }
        val changing = if (!meaningful) {
            emptySet()
        } else {
            viable.filterTo(mutableSetOf()) { edge ->
                viable.any { other ->
                    other !== edge && (
                        capabilitySignature(other) != capabilitySignature(edge) ||
                            commute(node.state, edge.actionId, other.actionId) == false
                        )
                }
            }
        }
        return NodeDiagnostic(
            invalidCount = node.edges.count { it.result?.success == false },
            successful = successful,
            viable = viable,
            fatal = fatal,
            meaningful = meaningful,
            harmful = harmful,
            capabilityChanging = changing,
            solutionReducing = reducing,
            viablePairCount = viablePairs.size,
            nonCommutingPairs = nonCommutingPairs,
            commutationUnknown = commutationUnknown,
        )
    }

    private fun capabilitySignature(edge: V4Edge): CapabilitySignature {
        val child = requireNotNull(edge.child)
        val facts = requireNotNull(child.facts)
        val successful = child.edges.count { it.result?.success == true }
        val viable = child.edges.count { it.child?.facts?.solvable == true && it.result?.success == true }
        val fatal = child.edges.count {
            it.result?.success == true && it.child?.facts?.complete == true && it.child.facts?.solvable == false
        }
        return CapabilitySignature(
            solvable = facts.solvable,
            successfulActionCount = successful,
            viableActionCount = viable,
            fatalActionCount = fatal,
            solutionFreedomBucket = solutionFreedomBucket(facts.winningSequenceCount),
        )
    }

    private fun actionCounts(diagnostics: Collection<NodeDiagnostic>): ActionCounts {
        var plausible = 0
        var invalid = 0
        var successful = 0
        var safe = 0
        var meaningful = 0
        var changing = 0
        var reducing = 0
        var fatal = 0
        diagnostics.forEach { diagnostic ->
            plausible += diagnostic.successful.size + diagnostic.successfulStateInvalidCount
            invalid += diagnostic.successfulStateInvalidCount
            successful += diagnostic.successful.size
            safe += diagnostic.viable.size
            fatal += diagnostic.fatal.size
            val meaningfulEdges = buildSet {
                addAll(diagnostic.fatal)
                addAll(diagnostic.capabilityChanging)
                addAll(diagnostic.solutionReducing)
            }
            meaningful += meaningfulEdges.size
            changing += diagnostic.capabilityChanging.size
            reducing += diagnostic.solutionReducing.size
        }
        return ActionCounts(plausible, invalid, successful, safe, meaningful, changing, reducing, fatal)
    }

    private fun consequenceMetrics(
        graph: V4SearchGraph,
        diagnostics: Map<V4Node, NodeDiagnostic>,
        meaningfulKeys: Set<StateKey>,
    ): ConsequencePersistenceMetrics {
        val depths = mutableListOf<Int>()
        val affectedDecisions = mutableListOf<Int>()
        val decisionMemo = mutableMapOf<StateKey, Int>()
        diagnostics.forEach { (_, diagnostic) ->
            diagnostic.fatal.forEach { edge ->
                val child = requireNotNull(edge.child)
                val proof = graph.facts(child).deadEndProofDepth ?: return@forEach
                depths += proof + 1
                affectedDecisions += decisionsOnShortestDeadEndPath(
                    graph,
                    child,
                    meaningfulKeys,
                    decisionMemo,
                )
            }
        }
        if (depths.isEmpty()) {
            return ConsequencePersistenceMetrics(0, null, null, null, null, null, null)
        }
        val sorted = depths.sorted()
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2].toDouble()
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }
        return ConsequencePersistenceMetrics(
            sampleCount = depths.size,
            minimumDepth = sorted.first(),
            maximumDepth = sorted.last(),
            averageDepth = round4(depths.average()),
            medianDepth = round4(median),
            averageMeaningfulDecisionsAffected = round4(affectedDecisions.average()),
            maximumMeaningfulDecisionsAffected = affectedDecisions.maxOrNull(),
        )
    }

    private fun decisionsOnShortestDeadEndPath(
        graph: V4SearchGraph,
        node: V4Node,
        meaningfulKeys: Set<StateKey>,
        memo: MutableMap<StateKey, Int>,
    ): Int {
        val key = StateKey.from(node.state)
        memo[key]?.let { return it }
        val proof = graph.facts(node).deadEndProofDepth ?: return 0
        val here = if (key in meaningfulKeys) 1 else 0
        if (proof == 0) return here.also { memo[key] = it }
        val childValues = graph.successful(node).mapNotNull { edge ->
            val child = edge.child ?: return@mapNotNull null
            val childProof = graph.facts(child).deadEndProofDepth ?: return@mapNotNull null
            child.takeIf { childProof + 1 == proof }?.let {
                decisionsOnShortestDeadEndPath(graph, it, meaningfulKeys, memo)
            }
        }
        return (here + (childValues.maxOrNull() ?: 0)).also { memo[key] = it }
    }

    private fun recoveryMetrics(
        graph: V4SearchGraph,
        diagnostics: Map<V4Node, NodeDiagnostic>,
    ): RecoveryPressureMetrics {
        val depths = diagnostics.values.flatMap { diagnostic ->
            diagnostic.fatal.mapNotNull { edge ->
                edge.child?.let(graph::facts)?.deadEndProofDepth?.plus(1)
            }
        }
        val successful = diagnostics.values.sumOf { it.successful.size }
        val average = depths.averageOrZero()
        val restartPressure = ratio(depths.size, successful)
        val normalized = (restartPressure * (1.0 + average / 3.0)).coerceIn(0.0, 1.0)
        return RecoveryPressureMetrics(
            recoverableBadDecisionCount = depths.size,
            irreversibleBadDecisionCount = 0,
            averageRecoveryDepth = round4(average),
            maximumRecoveryDepth = depths.maxOrNull() ?: 0,
            averageDeadEndDepth = round4(average),
            maximumDeadEndDepth = depths.maxOrNull() ?: 0,
            restartPressure = restartPressure,
            normalizedRecoveryPressure = round4(normalized),
        )
    }

    private fun strategyMetrics(
        graph: V4SearchGraph,
        rootFacts: V4Facts,
        sequences: SequenceEnumeration,
        commute: (BoardState, String, String) -> Boolean?,
        nodeDiagnostics: Map<V4Node, NodeDiagnostic>,
    ): StrategyMetricsV4 {
        val commutative = nodeDiagnostics.values.sumOf { diagnostic ->
            diagnostic.viablePairCount - diagnostic.nonCommutingPairs.size
        }
        val nonCommuting = nodeDiagnostics.values.sumOf { it.nonCommutingPairs.size }
        val tested = commutative + nonCommuting
        val pairComplete = nodeDiagnostics.values.none { it.commutationUnknown }
        val sequenceComplete = !sequences.truncated && rootFacts.complete
        if (!sequenceComplete) {
            return StrategyMetricsV4(
                rawWinningSequenceCount = rootFacts.winningSequenceCount,
                rawWinningSequenceCountCapped = true,
                canonicalStrategyCount = null,
                meaningfulStrategyFamilyCount = null,
                permutationRedundancy = null,
                commutativeActionPairCount = commutative,
                nonCommutingActionPairCount = nonCommuting,
                testedViableActionPairCount = tested,
                commutationRatio = ratio(commutative, tested),
                analysisComplete = false,
            )
        }
        val index = sequences.sequences.withIndex().associate { (position, sequence) ->
            sequenceKey(sequence) to position
        }
        val union = UnionFind(sequences.sequences.size)
        var quotientComplete = pairComplete
        sequences.sequences.forEachIndexed { sequenceIndex, sequence ->
            var state = graph.nodes.values.first { it.depth == 0 }.state
            for (position in 0..<sequence.lastIndex) {
                val first = sequence[position]
                val second = sequence[position + 1]
                when (commute(state, first, second)) {
                    true -> {
                        val swapped = sequence.toMutableList().also {
                            it[position] = second
                            it[position + 1] = first
                        }
                        index[sequenceKey(swapped)]?.let { union.union(sequenceIndex, it) }
                    }
                    false -> Unit
                    null -> quotientComplete = false
                }
                val result = engine.resolve(state, PlayerAction(first))
                if (!result.success) {
                    quotientComplete = false
                    break
                }
                state = result.resultingState
            }
        }
        val strategies = sequences.sequences.indices.map(union::find).toSet().size
        if (strategies > config.maxCanonicalStrategyRepresentatives) quotientComplete = false
        val raw = rootFacts.winningSequenceCount
        val redundancy = if (raw <= 0L) 0.0 else (raw - strategies).toDouble() / raw.toDouble()
        return StrategyMetricsV4(
            rawWinningSequenceCount = raw,
            rawWinningSequenceCountCapped = rootFacts.winningSequenceCountCapped,
            canonicalStrategyCount = strategies.takeIf { quotientComplete },
            meaningfulStrategyFamilyCount = strategies.takeIf { quotientComplete },
            permutationRedundancy = round4(redundancy).takeIf { quotientComplete },
            commutativeActionPairCount = commutative,
            nonCommutingActionPairCount = nonCommuting,
            testedViableActionPairCount = tested,
            commutationRatio = ratio(commutative, tested),
            analysisComplete = quotientComplete,
        )
    }

    private fun orderingMetrics(
        level: LevelDefinition,
        sequences: SequenceEnumeration,
        strategy: StrategyMetricsV4,
        nodeDiagnostics: Map<V4Node, NodeDiagnostic>,
    ): OrderingMetricsV4 {
        val ids = level.arrows.map { it.id }.sorted()
        val totalPairs = ids.size * (ids.size - 1) / 2
        val independent = nodeDiagnostics.values.flatMap { diagnostic ->
            val nonCommuting = diagnostic.nonCommutingPairs.map { pair ->
                setOf(pair.first.actionId, pair.second.actionId)
            }.toSet()
            diagnostic.viable.indices.flatMap { firstIndex ->
                (firstIndex + 1..<diagnostic.viable.size).mapNotNull { secondIndex ->
                    val pair = setOf(
                        diagnostic.viable[firstIndex].actionId,
                        diagnostic.viable[secondIndex].actionId,
                    )
                    pair.takeIf { it !in nonCommuting }
                }
            }
        }.toSet().size
        if (sequences.truncated || !strategy.analysisComplete || sequences.sequences.isEmpty()) {
            return OrderingMetricsV4(totalPairs, null, null, null, null, independent, null, false)
        }
        val mandatoryEdges = linkedSetOf<Pair<String, String>>()
        var flexible = 0
        for (firstIndex in ids.indices) {
            for (secondIndex in firstIndex + 1..<ids.size) {
                val first = ids[firstIndex]
                val second = ids[secondIndex]
                var firstBefore = false
                var secondBefore = false
                sequences.sequences.forEach { sequence ->
                    if (sequence.indexOf(first) < sequence.indexOf(second)) firstBefore = true else secondBefore = true
                }
                when {
                    firstBefore && secondBefore -> flexible += 1
                    firstBefore -> mandatoryEdges += first to second
                    secondBefore -> mandatoryEdges += second to first
                }
            }
        }
        val depth = longestOrderingChain(ids, mandatoryEdges)
        return OrderingMetricsV4(
            totalActionPairs = totalPairs,
            mandatoryOrderingPairCount = mandatoryEdges.size,
            mandatoryOrderingRatio = ratio(mandatoryEdges.size, totalPairs),
            mandatoryOrderingChainDepth = depth,
            dependencyGraphDepth = depth,
            independentActionCount = independent,
            flexibleOrderingPairCount = flexible,
            analysisComplete = true,
        )
    }

    private fun longestOrderingChain(ids: List<String>, edges: Set<Pair<String, String>>): Int {
        if (edges.isEmpty()) return 0
        val depth = ids.associateWith { 1 }.toMutableMap()
        repeat(ids.size) {
            edges.sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second }).forEach { edge ->
                depth[edge.second] = maxOf(requireNotNull(depth[edge.second]), requireNotNull(depth[edge.first]) + 1)
            }
        }
        return depth.values.maxOrNull() ?: 0
    }

    private fun forcedDecisionMetrics(
        graph: V4SearchGraph,
        root: V4Node,
        meaningfulKeys: Set<StateKey>,
    ): ForcedDecisionMetrics {
        if (graph.facts(root).solvable != true) {
            return ForcedDecisionMetrics(null, null, null, 0, null, null, null, null, null)
        }
        var cursor = root
        var depth = 0
        var forcedCount = 0
        var forcedRun = 0
        var longestForcedRun = 0
        val decisions = mutableListOf<Int>()
        while (cursor.state.arrows.isNotEmpty()) {
            val viable = graph.viable(cursor).sortedBy { it.actionId }
            if (viable.isEmpty()) break
            if (viable.size == 1) {
                forcedCount += 1
                forcedRun += 1
                longestForcedRun = maxOf(longestForcedRun, forcedRun)
            } else {
                forcedRun = 0
            }
            if (StateKey.from(cursor.state) in meaningfulKeys) decisions += depth
            cursor = requireNotNull(viable.first().child)
            depth += 1
        }
        val gaps = decisionGaps(depth, decisions)
        return ForcedDecisionMetrics(
            totalSolutionLength = depth,
            forcedSequenceLength = forcedCount,
            longestForcedRun = longestForcedRun,
            meaningfulDecisionCount = decisions.size,
            decisionDensity = ratio(decisions.size, depth),
            firstMeaningfulDecisionDepth = decisions.firstOrNull(),
            lastMeaningfulDecisionDepth = decisions.lastOrNull(),
            maximumDecisionGap = gaps.maxOrNull(),
            averageDecisionGap = gaps.averageOrZero().let(::round4),
        )
    }

    private fun decisionGaps(length: Int, decisions: List<Int>): List<Int> {
        if (length == 0) return emptyList()
        if (decisions.isEmpty()) return listOf(length)
        return buildList {
            add(decisions.first())
            decisions.zipWithNext().forEach { (first, second) -> add((second - first - 1).coerceAtLeast(0)) }
            add((length - decisions.last() - 1).coerceAtLeast(0))
        }
    }

    private fun greedyPolicy(level: LevelDefinition, graph: V4SearchGraph, root: V4Node): GreedyPolicyMetrics {
        val authoredOrder = level.arrows.map { it.id }
        var node = root
        var actions = 0
        var divergence: Int? = null
        while (node.state.arrows.isNotEmpty()) {
            val successful = graph.successful(node)
            if (successful.isEmpty()) break
            val selected = authoredOrder.asSequence().mapNotNull { id ->
                successful.firstOrNull { it.actionId == id }
            }.first()
            val child = selected.child
            if (divergence == null && child?.let(graph::facts)?.solvable != true) divergence = actions
            if (child == null) break
            node = child
            actions += 1
        }
        val solved = node.state.arrows.isEmpty()
        val recoveryDepth = divergence?.let { actions - it }
        return GreedyPolicyMetrics(
            solved = solved,
            actionsBeforeFailure = actions,
            firstDivergenceDepth = divergence,
            recoveryRequired = !solved,
            recoverable = if (solved) null else true,
            recoveryDepth = recoveryDepth,
        )
    }

    private fun randomPolicy(graph: V4SearchGraph, root: V4Node): RandomPolicyMetrics {
        var completions = 0
        var deadlocks = 0
        val actionCounts = mutableListOf<Int>()
        val recoveryDepths = mutableListOf<Int>()
        config.randomPolicySeeds.forEach { seed ->
            val random = FrozenSplitMix64(seed)
            var node = root
            var actions = 0
            var divergence: Int? = null
            while (node.state.arrows.isNotEmpty()) {
                val successful = graph.successful(node).sortedBy { it.actionId }
                if (successful.isEmpty()) break
                val selected = successful[random.nextInt(successful.size)]
                val child = selected.child
                if (divergence == null && child?.let(graph::facts)?.solvable != true) divergence = actions
                if (child == null) break
                node = child
                actions += 1
            }
            if (node.state.arrows.isEmpty()) {
                completions += 1
            } else {
                deadlocks += 1
                recoveryDepths += divergence?.let { actions - it } ?: 0
            }
            actionCounts += actions
        }
        val mean = actionCounts.averageOrZero()
        val variance = if (actionCounts.isEmpty()) 0.0 else {
            actionCounts.sumOf { count -> (count - mean) * (count - mean) } / actionCounts.size
        }
        return RandomPolicyMetrics(
            seedCount = config.randomPolicySeeds.size,
            completionCount = completions,
            deadlockCount = deadlocks,
            completionRate = ratio(completions, config.randomPolicySeeds.size),
            deadlockRate = ratio(deadlocks, config.randomPolicySeeds.size),
            averageActions = round4(mean),
            averageFailures = ratio(deadlocks, config.randomPolicySeeds.size),
            averageRecoveryDepth = round4(recoveryDepths.averageOrZero()),
            actionCountVariance = round4(variance),
        )
    }

    private fun polarityMetrics(
        graph: V4SearchGraph,
        solvableNodes: List<V4Node>,
        counterBudget: V4SearchBudget,
    ): PolarityMetricResult {
        val counterGraph = V4SearchGraph(engine, counterBudget)
        var flips = 0
        var impactful = 0
        var routeOnly = 0
        var actionability = 0
        var solvability = 0
        var ordering = 0
        var complete = true
        val impactfulMagnets = mutableSetOf<String>()
        outer@ for (node in solvableNodes) {
            for (edge in graph.successful(node)) {
                val change = edge.result?.polarityChange ?: continue
                if (flips >= config.maxPolarityCounterfactuals) {
                    counterBudget.truncate("POLARITY_COUNTERFACTUAL_CAP")
                    complete = false
                    break@outer
                }
                flips += 1
                val flipped = requireNotNull(edge.child)
                val unflippedState = edge.result.resultingState.copy(
                    magnets = edge.result.resultingState.magnets.map { magnet ->
                        if (magnet.id == change.magnetId) magnet.copy(polarity = change.from) else magnet
                    },
                )
                val unflipped = counterGraph.expand(unflippedState)
                if (unflipped == null) {
                    complete = false
                    continue
                }
                val unflippedFacts = counterGraph.facts(unflipped)
                if (!unflippedFacts.complete) complete = false
                val flippedSuccessful = graph.successful(flipped).mapTo(sortedSetOf()) { it.actionId }
                val unflippedSuccessful = counterGraph.successful(unflipped).mapTo(sortedSetOf()) { it.actionId }
                val flippedViable = graph.viable(flipped).mapTo(sortedSetOf()) { it.actionId }
                val unflippedViable = counterGraph.viable(unflipped).mapTo(sortedSetOf()) { it.actionId }
                val actionChanged = flippedSuccessful != unflippedSuccessful || flippedViable != unflippedViable
                val solvabilityChanged = graph.facts(flipped).solvable != unflippedFacts.solvable
                val routeChanged = routeSignaturesDiffer(flipped.state, unflipped.state, counterBudget)
                if (actionChanged || solvabilityChanged) {
                    impactful += 1
                    impactfulMagnets += change.magnetId
                } else if (routeChanged == true) {
                    routeOnly += 1
                } else if (routeChanged == null) {
                    complete = false
                }
                if (actionChanged) {
                    actionability += 1
                    ordering += 1
                }
                if (solvabilityChanged) solvability += 1
            }
        }
        return PolarityMetricResult(
            metrics = PolarityActionabilityMetrics(
                polarityFlipCount = flips,
                strategicallyImpactfulPolarityFlipCount = impactful,
                routeOnlyPolarityFlipCount = routeOnly,
                polarityImpactRatio = ratio(impactful, flips),
                actionabilityChangeCount = actionability,
                solvabilityChangeCount = solvability,
                orderingImpactCount = ordering,
                analysisComplete = complete,
            ),
            impactfulMagnetIds = impactfulMagnets,
        )
    }

    private fun routeSignaturesDiffer(
        first: BoardState,
        second: BoardState,
        budget: V4SearchBudget,
    ): Boolean? {
        val ids = first.arrows.map { it.id }.intersect(second.arrows.map { it.id }.toSet()).sorted()
        for (id in ids) {
            val firstResult = diagnosticResolve(first, id, budget) ?: return null
            val secondResult = diagnosticResolve(second, id, budget) ?: return null
            if (resolutionSignature(firstResult) != resolutionSignature(secondResult)) return true
        }
        return false
    }

    private fun objectRelevanceMetrics(
        level: LevelDefinition,
        rootFacts: V4Facts,
        baseSequences: SequenceEnumeration,
        polarity: PolarityActionabilityMetrics,
        impactfulMagnetIds: Set<String>,
        counterBudget: V4SearchBudget,
    ): ObjectRelevanceMetrics {
        if (!rootFacts.complete) {
            counterBudget.truncate("BASE_SEARCH_INCOMPLETE_FOR_OBJECT_RELEVANCE")
            return ObjectRelevanceMetrics(
                level.walls.size, null, null, null,
                level.magnets.size, null, null, null,
                false,
            )
        }
        if (level.walls.size + level.magnets.size > config.maxObjectCounterfactuals || baseSequences.truncated) {
            counterBudget.truncate("OBJECT_COUNTERFACTUAL_CAP")
            return ObjectRelevanceMetrics(
                level.walls.size, null, null, null,
                level.magnets.size, null, null, null,
                false,
            )
        }
        val baseSet = baseSequences.sequences.mapTo(mutableSetOf(), ::sequenceKey)
        var relevantWalls = 0
        var relevantMagnets = 0
        var complete = true
        level.walls.forEach { wall ->
            val variant = level.copy(walls = level.walls.filterNot { it.position == wall.position })
            val comparison = variantDiffers(variant, rootFacts, baseSet, counterBudget)
            if (comparison == null) complete = false else if (comparison) relevantWalls += 1
        }
        level.magnets.forEach { magnet ->
            val variant = level.copy(magnets = level.magnets.filterNot { it.id == magnet.id })
            val comparison = variantDiffers(variant, rootFacts, baseSet, counterBudget)
            if (comparison == null) {
                complete = false
            } else if (comparison || (polarity.analysisComplete && magnet.id in impactfulMagnetIds)) {
                relevantMagnets += 1
            }
        }
        return if (complete) {
            ObjectRelevanceMetrics(
                totalWallCount = level.walls.size,
                relevantWallCount = relevantWalls,
                irrelevantWallCount = level.walls.size - relevantWalls,
                wallStrategicRelevanceRatio = ratio(relevantWalls, level.walls.size),
                totalMagnetCount = level.magnets.size,
                relevantMagnetCount = relevantMagnets,
                irrelevantMagnetCount = level.magnets.size - relevantMagnets,
                magnetStrategicRelevanceRatio = ratio(relevantMagnets, level.magnets.size),
                analysisComplete = true,
            )
        } else {
            ObjectRelevanceMetrics(
                level.walls.size, null, null, null,
                level.magnets.size, null, null, null,
                false,
            )
        }
    }

    private fun variantDiffers(
        variant: LevelDefinition,
        baseFacts: V4Facts,
        baseSequences: Set<String>,
        budget: V4SearchBudget,
    ): Boolean? {
        val graph = V4SearchGraph(engine, budget)
        val root = graph.expand(variant.initialState()) ?: return null
        val facts = graph.facts(root)
        if (!facts.complete) return null
        if (facts.solvable != baseFacts.solvable) return true
        val sequences = graph.winningSequences(root, config.maxWinningSequences)
        if (sequences.truncated) {
            budget.truncate("OBJECT_SEQUENCE_ENUMERATION_CAP")
            return null
        }
        return sequences.sequences.mapTo(mutableSetOf(), ::sequenceKey) != baseSequences
    }

    private fun actionsCommute(
        state: BoardState,
        first: String,
        second: String,
        budget: V4SearchBudget,
    ): Boolean? {
        val firstResult = diagnosticResolve(state, first, budget) ?: return null
        val secondResult = diagnosticResolve(state, second, budget) ?: return null
        if (!firstResult.success || !secondResult.success) return false
        if (firstResult.resultingState.arrow(second) == null || secondResult.resultingState.arrow(first) == null) {
            return false
        }
        val firstThenSecond = diagnosticResolve(firstResult.resultingState, second, budget) ?: return null
        val secondThenFirst = diagnosticResolve(secondResult.resultingState, first, budget) ?: return null
        return firstThenSecond.success && secondThenFirst.success &&
            StateKey.from(firstThenSecond.resultingState) == StateKey.from(secondThenFirst.resultingState)
    }

    private fun diagnosticResolve(state: BoardState, arrowId: String, budget: V4SearchBudget): ResolutionResult? {
        if (!budget.reserveResolution()) return null
        return engine.resolve(state, PlayerAction(arrowId))
    }

    private fun resolutionSignature(result: ResolutionResult): List<Any?> = listOf(
        result.success,
        result.effectiveDirection,
        result.controllingMagnetId,
        when (result.terminalEvent) {
            is TerminalEvent.Exit -> "EXIT"
            is TerminalEvent.PullCapture -> "PULL_CAPTURE"
            is TerminalEvent.Collision -> "COLLISION"
            is TerminalEvent.InvalidPullExit -> "INVALID_PULL_EXIT"
        },
        result.polarityChange?.from,
        result.traversedCells.size,
    )

    private fun solutionFreedomBucket(count: Long): Int = when {
        count <= 0L -> 0
        count == 1L -> 1
        else -> (ln(count.toDouble()) / ln(2.0)).toInt().coerceAtMost(30) + 2
    }

    private fun sequenceKey(sequence: List<String>): String = sequence.joinToString(">")

    private fun List<Int>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

    private data class CapabilitySignature(
        val solvable: Boolean?,
        val successfulActionCount: Int,
        val viableActionCount: Int,
        val fatalActionCount: Int,
        val solutionFreedomBucket: Int,
    )

    private data class NodeDiagnostic(
        val invalidCount: Int,
        val successful: List<V4Edge>,
        val viable: List<V4Edge>,
        val fatal: List<V4Edge>,
        val meaningful: Boolean,
        val harmful: Boolean,
        val capabilityChanging: Set<V4Edge>,
        val solutionReducing: Set<V4Edge>,
        val viablePairCount: Int,
        val nonCommutingPairs: List<Pair<V4Edge, V4Edge>>,
        val commutationUnknown: Boolean,
    ) {
        val successfulStateInvalidCount: Int
            get() = invalidCount
    }

    private data class PolarityMetricResult(
        val metrics: PolarityActionabilityMetrics,
        val impactfulMagnetIds: Set<String>,
    )

    private data class ActionCounts(
        val plausible: Int,
        val invalid: Int,
        val successful: Int,
        val safe: Int,
        val meaningful: Int,
        val capabilityChanging: Int,
        val solutionReducing: Int,
        val fatal: Int,
    )

    private data class CommutationKey(
        val state: StateKey,
        val first: String,
        val second: String,
    )

    private class UnionFind(size: Int) {
        private val parent = IntArray(size) { it }
        fun find(value: Int): Int {
            if (parent[value] != value) parent[value] = find(parent[value])
            return parent[value]
        }
        fun union(first: Int, second: Int) {
            val firstRoot = find(first)
            val secondRoot = find(second)
            if (firstRoot != secondRoot) parent[secondRoot] = firstRoot
        }
    }

    private class FrozenSplitMix64(seed: Long) {
        private var state = seed
        fun nextInt(bound: Int): Int {
            require(bound > 0)
            state += -7046029254386353131L
            var value = state
            value = (value xor (value ushr 30)) * -4658895280553007687L
            value = (value xor (value ushr 27)) * -7723592293110705685L
            value = value xor (value ushr 31)
            return java.lang.Long.remainderUnsigned(value, bound.toLong()).toInt()
        }
    }
}
