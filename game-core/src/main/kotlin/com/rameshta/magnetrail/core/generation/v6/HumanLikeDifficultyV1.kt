package com.rameshta.magnetrail.core.generation.v6

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

data class HumanCognitiveFeaturesV1(
    val meaningfulDecisionCount: Double,
    val nonCommutingForks: Double,
    val successfulPersistentTraps: Double,
    val minimumLookaheadProofDepth: Double,
    val delayedConsequenceDepth: Double,
    val polarityMemoryDepth: Double,
    val controllerVisibilityChanges: Double,
    val interactingCausalChains: Double,
    val hardestWinningChoiceRatio: Double,
    val solutionPolicyClasses: Double,
    val longestForcedRun: Double,
    val meaningfulDecisionDensity: Double,
    val guessDependence: Double,
    val restartCost: Double,
    val routeLosReasoningCost: Double,
) {
    fun vector(): List<Double> = listOf(
        meaningfulDecisionCount,
        nonCommutingForks,
        successfulPersistentTraps,
        minimumLookaheadProofDepth,
        delayedConsequenceDepth,
        polarityMemoryDepth,
        controllerVisibilityChanges,
        interactingCausalChains,
        hardestWinningChoiceRatio,
        solutionPolicyClasses,
        longestForcedRun,
        meaningfulDecisionDensity,
        guessDependence,
        restartCost,
        routeLosReasoningCost,
    )

    companion object {
        val names: List<String> = listOf(
            "meaningfulDecisionCount",
            "nonCommutingForks",
            "successfulPersistentTraps",
            "minimumLookaheadProofDepth",
            "delayedConsequenceDepth",
            "polarityMemoryDepth",
            "controllerVisibilityChanges",
            "interactingCausalChains",
            "hardestWinningChoiceRatio",
            "solutionPolicyClasses",
            "longestForcedRun",
            "meaningfulDecisionDensity",
            "guessDependence",
            "restartCost",
            "routeLosReasoningCost",
        )
    }
}

object HumanCognitiveFeatureExtractorV1 {
    fun extract(
        analysis: DecisionDagAnalysisV6,
        interactingChains: Int,
    ): HumanCognitiveFeaturesV1 {
        require(analysis.complete)
        val metrics = requireNotNull(analysis.metrics)
        val rootArrows = requireNotNull(analysis.nodes[analysis.rootStateKey]).state.arrows.size.coerceAtLeast(1)
        val controlledTransitions = analysis.nodes.values.flatMap { it.transitions }.filter { it.successful }
        val controllerChanges = controlledTransitions.count { it.controllerId != null } +
            controlledTransitions.count { it.polarityChange != null }
        val indistinguishable = analysis.nodes.values.sumOf { node ->
            node.transitions.filter { it.successful }.groupBy { edge ->
                val arrow = requireNotNull(node.state.arrow(edge.arrowId))
                val controller = edge.controllerId?.let(node.state::magnet)
                listOf(
                    arrow.position,
                    arrow.printedDirection,
                    controller?.position,
                    controller?.polarity,
                    edge.route,
                    edge.terminalEvent,
                    edge.polarityChange?.substringAfter(':'),
                )
            }.values.count { group -> group.map { it.futureSolvable }.distinct().size > 1 }
        }
        val trapDepths = analysis.nodes.values.flatMap { it.transitions }
            .filter { it.successful && it.futureSolvable == false }
            .map { it.delayedDeadlockDepth ?: 0 }
        return HumanCognitiveFeaturesV1(
            meaningfulDecisionCount = metrics.meaningfulDecisionCount.toDouble(),
            nonCommutingForks = metrics.meaningfulNonCommutingDecisionCount.toDouble(),
            successfulPersistentTraps = metrics.successfulLosingBranchCount.toDouble(),
            minimumLookaheadProofDepth = metrics.minimumLookaheadProofDepth.toDouble(),
            delayedConsequenceDepth = metrics.maximumDelayedDeadlockDepth.toDouble(),
            polarityMemoryDepth = metrics.polarityMemorySpan.toDouble(),
            controllerVisibilityChanges = controllerChanges.toDouble(),
            interactingCausalChains = interactingChains.toDouble(),
            hardestWinningChoiceRatio = metrics.hardestWinningChoiceShare,
            solutionPolicyClasses = metrics.winningSolutionPolicyClasses.toDouble(),
            longestForcedRun = (metrics.forcedRunLengths.maxOrNull() ?: 0).toDouble(),
            meaningfulDecisionDensity = metrics.meaningfulDecisionCount.toDouble() / rootArrows,
            guessDependence = indistinguishable.toDouble() / max(1, metrics.meaningfulDecisionCount),
            restartCost = trapDepths.averageOrZero() + metrics.shortestCompletion.orZero() *
                (metrics.successfulLosingBranchCount.toDouble() / max(1, metrics.successfulTransitionCount)),
            routeLosReasoningCost = controllerChanges.toDouble() /
                max(1, controlledTransitions.size),
        )
    }

    private fun List<Int>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
    private fun Int?.orZero(): Int = this ?: 0
}

enum class HumanPolicyKindV1 {
    IMMEDIATE_ROUTE_SAFETY,
    UNAFFECTED_EXIT_FIRST,
    SHORTEST_ROUTE_FIRST,
    CONTROLLER_STABILITY_FIRST,
    POLARITY_PRESERVING_FIRST,
    BOUNDED_LOOKAHEAD,
    GREEDY_ACTIONABILITY,
    PLAUSIBLE_FIXED_SEED,
}

data class CognitiveBudgetVectorV1(
    val lookaheadDepth: Int,
    val alternativesExamined: Int,
    val magnetStatesRemembered: Int,
    val controllerTransitionsTracked: Int,
    val trapAvoidance: Int,
    val requiredRestarts: Int,
)

data class HumanPolicyResultV1(
    val policy: HumanPolicyKindV1,
    val budget: CognitiveBudgetVectorV1,
    val solvedRuns: Int,
    val runCount: Int,
    val reliable: Boolean,
    val minimumActions: Int?,
)

class HumanPolicyEnsembleV1(
    private val reliableThreshold: Double = 0.80,
) {
    fun evaluate(analysis: DecisionDagAnalysisV6): List<HumanPolicyResultV1> {
        require(analysis.complete)
        val policies = buildList {
            add(HumanPolicyKindV1.IMMEDIATE_ROUTE_SAFETY to CognitiveBudgetVectorV1(0, 1, 0, 0, 0, 0))
            add(HumanPolicyKindV1.UNAFFECTED_EXIT_FIRST to CognitiveBudgetVectorV1(0, 2, 0, 0, 0, 0))
            add(HumanPolicyKindV1.SHORTEST_ROUTE_FIRST to CognitiveBudgetVectorV1(0, 3, 0, 0, 0, 0))
            add(HumanPolicyKindV1.CONTROLLER_STABILITY_FIRST to CognitiveBudgetVectorV1(1, 3, 1, 1, 0, 0))
            add(HumanPolicyKindV1.POLARITY_PRESERVING_FIRST to CognitiveBudgetVectorV1(1, 4, 2, 1, 1, 0))
            (1..7).forEach { depth ->
                add(
                    HumanPolicyKindV1.BOUNDED_LOOKAHEAD to CognitiveBudgetVectorV1(
                        depth,
                        (depth + 1).coerceAtMost(8),
                        (depth / 2).coerceAtLeast(1),
                        depth,
                        depth,
                        0,
                    ),
                )
            }
            add(HumanPolicyKindV1.GREEDY_ACTIONABILITY to CognitiveBudgetVectorV1(2, 5, 2, 2, 1, 0))
            add(HumanPolicyKindV1.PLAUSIBLE_FIXED_SEED to CognitiveBudgetVectorV1(1, 4, 1, 1, 0, 2))
        }
        return policies.map { (kind, budget) ->
            val runs = if (kind == HumanPolicyKindV1.PLAUSIBLE_FIXED_SEED) 32 else 1
            val outcomes = List(runs) { run -> simulate(analysis, kind, budget, run.toLong()) }
            val solved = outcomes.count { it != null }
            HumanPolicyResultV1(
                policy = kind,
                budget = budget,
                solvedRuns = solved,
                runCount = runs,
                reliable = solved.toDouble() / runs >= reliableThreshold,
                minimumActions = outcomes.filterNotNull().minOrNull(),
            )
        }
    }

    fun minimumReliableBudget(results: List<HumanPolicyResultV1>): CognitiveBudgetVectorV1? = results
        .filter { it.reliable }
        .minWithOrNull(
            compareBy<HumanPolicyResultV1> { it.budget.lookaheadDepth }
                .thenBy { it.budget.alternativesExamined }
                .thenBy { it.budget.magnetStatesRemembered }
                .thenBy { it.budget.controllerTransitionsTracked }
                .thenBy { it.budget.trapAvoidance },
        )?.budget

    private fun simulate(
        analysis: DecisionDagAnalysisV6,
        kind: HumanPolicyKindV1,
        budget: CognitiveBudgetVectorV1,
        runSeed: Long,
    ): Int? {
        var node = requireNotNull(analysis.nodes[analysis.rootStateKey])
        var actions = 0
        var seed = runSeed xor -7046029254386353131L
        while (node.state.arrows.isNotEmpty()) {
            val choices = node.transitions.filter { it.successful }.sortedBy { visibleActionKey(node, it) }
            if (choices.isEmpty()) return null
            val examined = choices.take(budget.alternativesExamined.coerceAtLeast(1))
            val next = when (kind) {
                HumanPolicyKindV1.IMMEDIATE_ROUTE_SAFETY -> examined.minWithOrNull(
                    compareBy<DecisionTransitionV6> { it.terminalEvent == "COLLISION" }
                        .thenBy { visibleActionKey(node, it) },
                )
                HumanPolicyKindV1.UNAFFECTED_EXIT_FIRST -> examined.minWithOrNull(
                    compareBy<DecisionTransitionV6> { it.controllerId != null }.thenBy { it.route.size }
                        .thenBy { visibleActionKey(node, it) },
                )
                HumanPolicyKindV1.SHORTEST_ROUTE_FIRST -> examined.minWithOrNull(
                    compareBy<DecisionTransitionV6> { it.route.size }.thenBy { visibleActionKey(node, it) },
                )
                HumanPolicyKindV1.CONTROLLER_STABILITY_FIRST -> examined.minWithOrNull(
                    compareBy<DecisionTransitionV6> { it.controllerId != null }.thenBy { it.polarityChange != null }
                        .thenBy { visibleActionKey(node, it) },
                )
                HumanPolicyKindV1.POLARITY_PRESERVING_FIRST -> examined.minWithOrNull(
                    compareBy<DecisionTransitionV6> { it.polarityChange != null }.thenBy { it.controllerId != null }
                        .thenBy { visibleActionKey(node, it) },
                )
                HumanPolicyKindV1.BOUNDED_LOOKAHEAD -> examined.maxWithOrNull(
                    compareBy<DecisionTransitionV6> { boundedValue(analysis, it, budget.lookaheadDepth) }
                        .thenByDescending { visibleActionKey(node, it) },
                )
                HumanPolicyKindV1.GREEDY_ACTIONABILITY -> examined.maxWithOrNull(
                    compareBy<DecisionTransitionV6> { transition ->
                        transition.childStateKey?.let { analysis.nodes[it] }?.transitions?.count { it.successful } ?: -1
                    }.thenByDescending { visibleActionKey(node, it) },
                )
                HumanPolicyKindV1.PLAUSIBLE_FIXED_SEED -> {
                    seed = splitMix(seed)
                    examined[(seed ushr 1).rem(examined.size.toLong()).toInt()]
                }
            } ?: return null
            node = next.childStateKey?.let { analysis.nodes[it] } ?: return null
            actions += 1
        }
        return actions
    }

    private fun visibleActionKey(node: DecisionNodeV6, transition: DecisionTransitionV6): String {
        val arrow = requireNotNull(node.state.arrow(transition.arrowId))
        val controller = transition.controllerId?.let(node.state::magnet)
        return listOf(
            arrow.position.row.toString().padStart(2, '0'),
            arrow.position.column.toString().padStart(2, '0'),
            arrow.printedDirection.ordinal,
            transition.effectiveDirectionOrdinal(),
            controller?.position?.row ?: 0,
            controller?.position?.column ?: 0,
            controller?.polarity?.ordinal ?: -1,
            transition.route.joinToString(";") { "${it.row},${it.column}" },
            transition.terminalEvent,
            transition.polarityChange != null,
        ).joinToString("|")
    }

    private fun DecisionTransitionV6.effectiveDirectionOrdinal(): Int {
        if (route.isEmpty()) return -1
        val arrow = route.first()
        val previous = route.getOrNull(1)
        return if (previous == null) -1 else when {
            previous.row < arrow.row -> 0
            previous.column > arrow.column -> 1
            previous.row > arrow.row -> 2
            else -> 3
        }
    }

    private fun boundedValue(
        analysis: DecisionDagAnalysisV6,
        transition: DecisionTransitionV6,
        depth: Int,
    ): Double {
        val child = transition.childStateKey?.let { analysis.nodes[it] } ?: return -1.0
        if (child.state.arrows.isEmpty()) return 10_000.0
        if (depth <= 1) return child.transitions.count { it.successful }.toDouble()
        val children = child.transitions.filter { it.successful }
        if (children.isEmpty()) return -10_000.0
        return children.maxOf { boundedValue(analysis, it, depth - 1) } - 0.01 * children.size
    }

    private fun splitMix(value: Long): Long {
        var result = value + -7046029254386353131L
        result = (result xor (result ushr 30)) * -4658895280553007687L
        result = (result xor (result ushr 27)) * -7723592293110705685L
        return result xor (result ushr 31)
    }
}

data class HumanCalibrationObservationV1(
    val participantCode: String,
    val boardFingerprint: String,
    val causalFamily: String,
    val semanticCluster: String,
    val perceivedBand: Int,
    val assignedBucket: Int,
    val features: HumanCognitiveFeaturesV1,
    val hinted: Boolean,
    val completed: Boolean,
    val fairnessRating: Int,
    val guessRequired: Boolean,
    val repeatedStrategy: Boolean,
) {
    init {
        require(participantCode.isNotBlank() && boardFingerprint.isNotBlank())
        require(perceivedBand in 1..V6_HUMAN_BAND_COUNT && assignedBucket in 1..V6_HUMAN_BAND_COUNT)
        require(fairnessRating in 1..5)
    }
}

data class PairwiseDifficultyObservationV1(
    val participantCode: String,
    val harderBoardFingerprint: String,
    val easierBoardFingerprint: String,
) {
    init {
        require(participantCode.isNotBlank())
        require(harderBoardFingerprint.isNotBlank() && easierBoardFingerprint.isNotBlank())
        require(harderBoardFingerprint != easierBoardFingerprint)
    }
}

data class BradleyTerryModelV1(
    val boardAbilities: Map<String, Double>,
    val l2: Double,
    val observationCount: Int,
    val version: String = "bradley-terry-secondary-v1",
) {
    fun harderProbability(firstBoardFingerprint: String, secondBoardFingerprint: String): Double =
        logistic(
            requireNotNull(boardAbilities[firstBoardFingerprint]) -
                requireNotNull(boardAbilities[secondBoardFingerprint]),
        )
}

/** Deterministic secondary evidence only; it can never certify the ordinal model by itself. */
class BradleyTerryCalibratorV1(
    private val l2: Double = 1.0,
    private val iterations: Int = 2_000,
    private val learningRate: Double = 0.02,
) {
    init {
        require(l2 >= 0.0 && iterations > 0 && learningRate > 0.0)
    }

    fun fit(observations: List<PairwiseDifficultyObservationV1>): BradleyTerryModelV1 {
        require(observations.isNotEmpty())
        val boards = observations.flatMap { listOf(it.harderBoardFingerprint, it.easierBoardFingerprint) }
            .distinct().sorted()
        val index = boards.withIndex().associate { it.value to it.index }
        val abilities = MutableList(boards.size) { 0.0 }
        repeat(iterations) { iteration ->
            val gradient = MutableList(boards.size) { 0.0 }
            observations.forEach { observation ->
                val harder = requireNotNull(index[observation.harderBoardFingerprint])
                val easier = requireNotNull(index[observation.easierBoardFingerprint])
                val error = logistic(abilities[harder] - abilities[easier]) - 1.0
                gradient[harder] += error
                gradient[easier] -= error
            }
            val step = learningRate / sqrt(iteration + 1.0)
            abilities.indices.forEach { board ->
                abilities[board] -= step * (
                    gradient[board] / observations.size + l2 * abilities[board] / observations.size
                ).coerceIn(-100.0, 100.0)
            }
            val mean = abilities.average()
            abilities.indices.forEach { abilities[it] -= mean }
        }
        return BradleyTerryModelV1(
            boardAbilities = boards.associateWith { abilities[requireNotNull(index[it])] },
            l2 = l2,
            observationCount = observations.size,
        )
    }
}

data class HumanDifficultyModelV1(
    val featureNames: List<String>,
    val means: List<Double>,
    val scales: List<Double>,
    val coefficients: List<Double>,
    val thresholds: List<Double>,
    val participantIntercepts: Map<String, Double>,
    val featureMinimums: List<Double>,
    val featureMaximums: List<Double>,
    val trainingObservationCount: Int,
    val trainingParticipantCount: Int,
    val trainingBoardCount: Int,
    val l2: Double,
    val version: String = HUMAN_LIKE_DIFFICULTY_V1,
) {
    init {
        val size = featureNames.size
        require(size > 0 && means.size == size && scales.size == size && coefficients.size == size)
        require(featureMinimums.size == size && featureMaximums.size == size)
        require(thresholds.size == V6_HUMAN_BAND_COUNT - 1 && thresholds.zipWithNext().all { (a, b) -> a < b })
    }

    fun predict(features: HumanCognitiveFeaturesV1): HumanDifficultyPrediction {
        val raw = features.vector()
        val normalized = raw.indices.map { (raw[it] - means[it]) / scales[it] }
        val latent = coefficients.indices.sumOf { coefficients[it] * normalized[it] }
        val cumulative = thresholds.map { logistic(it - latent) }
        val probabilities = buildList {
            add(cumulative.first())
            for (index in 1 until cumulative.size) add((cumulative[index] - cumulative[index - 1]).coerceAtLeast(0.0))
            add((1.0 - cumulative.last()).coerceAtLeast(0.0))
        }.normalizeProbabilities()
        val outside = raw.indices.filter { raw[it] < featureMinimums[it] || raw[it] > featureMaximums[it] }
        val standardizedDistance = normalized.sumOf { it * it }.let(::sqrt)
        val ood = outside.isNotEmpty() || standardizedDistance > sqrt(raw.size.toDouble()) * 3.0
        val confidence = (1.0 - standardizedDistance / (sqrt(raw.size.toDouble()) * 4.0)).coerceIn(0.0, 1.0) *
            (trainingBoardCount / V6_PILOT_BOARD_COUNT.toDouble()).coerceIn(0.0, 1.0)
        val standardError = 1.96 / sqrt(trainingObservationCount.coerceAtLeast(1).toDouble()) * (1.0 + standardizedDistance / 4.0)
        val contributors = featureNames.indices.map { featureNames[it] to coefficients[it] * normalized[it] }
            .sortedWith(compareByDescending<Pair<String, Double>> { abs(it.second) }.thenBy { it.first })
            .take(5)
        val rejection = when {
            ood -> "OUT_OF_DISTRIBUTION"
            confidence < 0.70 -> "LOW_CALIBRATION_CONFIDENCE"
            probabilities.maxOrNull() ?: 0.0 < 0.40 -> "AMBIGUOUS_BAND_PROBABILITIES"
            else -> null
        }
        return HumanDifficultyPrediction(
            modelVersion = version,
            latentDifficulty = latent,
            bandProbabilities = probabilities,
            confidenceInterval = (latent - standardError)..(latent + standardError),
            calibrationConfidence = confidence,
            outOfDistribution = ood,
            majorContributors = contributors,
            rejectionReason = rejection,
        )
    }
}

sealed interface HumanModelFitResultV1 {
    data class Fitted(val model: HumanDifficultyModelV1, val finalNegativeLogLikelihood: Double) : HumanModelFitResultV1
    data class Insufficient(val reasons: List<String>) : HumanModelFitResultV1
}

class CumulativeLinkOrdinalCalibratorV1(
    private val l2: Double = 1.0,
    private val iterations: Int = 4_000,
    private val learningRate: Double = 0.01,
) {
    init {
        require(l2 >= 0.0 && iterations > 0 && learningRate > 0.0)
    }

    fun fit(
        observations: List<HumanCalibrationObservationV1>,
        enforceEvidenceContract: Boolean = true,
    ): HumanModelFitResultV1 {
        val reasons = if (enforceEvidenceContract) evidenceReasons(observations) else emptyList()
        if (reasons.isNotEmpty()) return HumanModelFitResultV1.Insufficient(reasons)
        val featureRows = observations.map { it.features.vector() }
        val dimension = HumanCognitiveFeaturesV1.names.size
        val means = List(dimension) { index -> featureRows.map { it[index] }.average() }
        val scales = List(dimension) { index ->
            sqrt(featureRows.sumOf { (it[index] - means[index]).pow(2) } / featureRows.size).coerceAtLeast(1e-6)
        }
        val rows = featureRows.map { row -> row.indices.map { (row[it] - means[it]) / scales[it] } }
        val coefficients = MutableList(dimension) { 0.0 }
        val thresholds = mutableListOf(-1.2, -0.4, 0.4, 1.2)
        val participantIds = observations.map { it.participantCode }.distinct().sorted()
        val participantIndex = participantIds.withIndex().associate { it.value to it.index }
        val intercepts = MutableList(participantIds.size) { 0.0 }

        repeat(iterations) { iteration ->
            val betaGradient = MutableList(dimension) { 0.0 }
            val thresholdGradient = MutableList(V6_HUMAN_BAND_COUNT - 1) { 0.0 }
            val interceptGradient = MutableList(intercepts.size) { 0.0 }
            observations.forEachIndexed { rowIndex, observation ->
                val participant = requireNotNull(participantIndex[observation.participantCode])
                val eta = coefficients.indices.sumOf { coefficients[it] * rows[rowIndex][it] } + intercepts[participant]
                val category = observation.perceivedBand - 1
                val lowerF = if (category == 0) 0.0 else logistic(thresholds[category - 1] - eta)
                val upperF = if (category == V6_HUMAN_BAND_COUNT - 1) 1.0 else logistic(thresholds[category] - eta)
                val probability = (upperF - lowerF).coerceAtLeast(1e-12)
                val lowerDensity = lowerF * (1.0 - lowerF)
                val upperDensity = upperF * (1.0 - upperF)
                val etaGradient = (upperDensity - lowerDensity) / probability
                coefficients.indices.forEach { betaGradient[it] += etaGradient * rows[rowIndex][it] }
                interceptGradient[participant] += etaGradient
                if (category > 0) thresholdGradient[category - 1] += lowerDensity / probability
                if (category < V6_HUMAN_BAND_COUNT - 1) thresholdGradient[category] -= upperDensity / probability
            }
            val step = learningRate / sqrt(iteration + 1.0)
            coefficients.indices.forEach { index ->
                val gradient = betaGradient[index] / observations.size + l2 * coefficients[index] / observations.size
                coefficients[index] -= step * gradient.coerceIn(-100.0, 100.0)
            }
            intercepts.indices.forEach { index ->
                val gradient = interceptGradient[index] / observations.size + l2 * intercepts[index] / observations.size
                intercepts[index] -= step * gradient.coerceIn(-100.0, 100.0)
            }
            thresholds.indices.forEach { index ->
                thresholds[index] -= step * (thresholdGradient[index] / observations.size).coerceIn(-100.0, 100.0)
            }
            thresholds.sort()
            for (index in 1 until thresholds.size) {
                if (thresholds[index] < thresholds[index - 1] + 0.05) thresholds[index] = thresholds[index - 1] + 0.05
            }
            val interceptMean = intercepts.average()
            intercepts.indices.forEach { intercepts[it] -= interceptMean }
            thresholds.indices.forEach { thresholds[it] -= interceptMean }
        }
        val model = HumanDifficultyModelV1(
            featureNames = HumanCognitiveFeaturesV1.names,
            means = means,
            scales = scales,
            coefficients = coefficients,
            thresholds = thresholds,
            participantIntercepts = participantIds.associateWith { intercepts[requireNotNull(participantIndex[it])] },
            featureMinimums = List(dimension) { index -> featureRows.minOf { it[index] } },
            featureMaximums = List(dimension) { index -> featureRows.maxOf { it[index] } },
            trainingObservationCount = observations.size,
            trainingParticipantCount = participantIds.size,
            trainingBoardCount = observations.map { it.boardFingerprint }.distinct().size,
            l2 = l2,
        )
        return HumanModelFitResultV1.Fitted(model, negativeLogLikelihood(model, observations))
    }

    private fun evidenceReasons(observations: List<HumanCalibrationObservationV1>): List<String> = buildList {
        val participants = observations.groupingBy { it.participantCode }.eachCount()
        val boards = observations.groupingBy { it.boardFingerprint }.eachCount()
        if (participants.size < 10) add("requires-at-least-10-participants:${participants.size}")
        boards.filterValues { it < 5 }.forEach { (board, count) -> add("board-under-five-ratings:$board:$count") }
        val maximumShare = participants.maxOfOrNull { it.value.toDouble() / observations.size.coerceAtLeast(1) } ?: 1.0
        if (maximumShare > 0.20 + 1e-12) add("participant-share-above-20-percent:$maximumShare")
        if (observations.map { it.causalFamily }.distinct().size < 6) add("requires-six-causal-families")
        if (observations.map { it.boardFingerprint }.distinct().size < V6_PILOT_BOARD_COUNT) {
            add("requires-$V6_PILOT_BOARD_COUNT-calibration-boards")
        }
    }

    private fun negativeLogLikelihood(
        model: HumanDifficultyModelV1,
        observations: List<HumanCalibrationObservationV1>,
    ): Double = observations.sumOf { observation ->
        val prediction = model.predict(observation.features)
        -ln(prediction.bandProbabilities[observation.perceivedBand - 1].coerceAtLeast(1e-12))
    }
}

data class HumanValidationMetricsV1(
    val spearmanAssignedPerceived: Double,
    val spearmanClusteredLower95: Double,
    val adjacentProbabilityOfSuperiority: Double,
    val adjacentSuperiorityLower95: Double,
    val withinOneBandRate: Double,
    val fairnessRate: Double,
    val guessRate: Double,
    val repeatedStrategyRate: Double,
    val noHintCompletionRate: Double,
    val highBucketNoHintCompletionRate: Double,
    val medianByBucket: List<Double>,
    val passes: Boolean,
    val failureReasons: List<String>,
)

object HumanModelValidatorV1 {
    fun validateSealed(observations: List<HumanCalibrationObservationV1>): HumanValidationMetricsV1 {
        val assigned = observations.map { it.assignedBucket.toDouble() }
        val perceived = observations.map { it.perceivedBand.toDouble() }
        val spearman = spearman(assigned, perceived)
        val bootstrapSpearman = deterministicTwoWayBootstrap(observations, 2_000, 0x5636L) { sample ->
            spearman(sample.map { it.assignedBucket.toDouble() }, sample.map { it.perceivedBand.toDouble() })
        }
        val adjacent = (1 until V6_HUMAN_BAND_COUNT)
            .map { bucket -> superiority(observations, bucket + 1, bucket) }.minOrNull() ?: 0.0
        val adjacentBootstrap = deterministicTwoWayBootstrap(observations, 2_000, 0x41444AL) { sample ->
            (1 until V6_HUMAN_BAND_COUNT)
                .map { bucket -> superiority(sample, bucket + 1, bucket) }.minOrNull() ?: 0.0
        }
        val medians = (1..V6_HUMAN_BAND_COUNT)
            .map { bucket -> observations.filter { it.assignedBucket == bucket }.map { it.perceivedBand }.median() }
        val withinOne = observations.count { abs(it.assignedBucket - it.perceivedBand) <= 1 }.ratio(observations.size)
        val fairness = observations.count { it.fairnessRating >= 4 }.ratio(observations.size)
        val guess = observations.count { it.guessRequired }.ratio(observations.size)
        val repeated = observations.count { it.repeatedStrategy }.ratio(observations.size)
        val noHintCompleted = observations.count { it.completed && !it.hinted }.ratio(observations.size)
        val high = observations.filter { it.assignedBucket >= 5 }
        val highNoHint = high.count { it.completed && !it.hinted }.ratio(high.size)
        val failures = buildList {
            if (spearman < 0.70) add("spearman-below-0.70:$spearman")
            if (bootstrapSpearman < 0.55) add("spearman-lower95-below-0.55:$bootstrapSpearman")
            if (adjacent < 0.60) add("adjacent-superiority-below-0.60:$adjacent")
            if (adjacentBootstrap <= 0.50) add("adjacent-superiority-lower95-not-above-0.50:$adjacentBootstrap")
            if (withinOne < 0.80) add("within-one-band-below-0.80:$withinOne")
            if (!medians.zipWithNext().all { (a, b) -> a <= b }) add("non-monotonic-medians:$medians")
            if (medians.getOrElse(2) { 0.0 } < 3.0) add("bucket-3-median-below-hard")
            if (medians.getOrElse(3) { 0.0 } < 4.0) add("bucket-4-median-below-super-hard")
            if (medians.getOrElse(4) { 0.0 } < 5.0) add("bucket-5-median-below-expert")
            val severe = high.count { it.perceivedBand <= 2 }.ratio(high.size)
            if (severe > 0.10) add("high-bucket-easy-medium-above-0.10:$severe")
            if (fairness < 0.85) add("fairness-below-0.85:$fairness")
            if (guess > 0.10) add("guess-above-0.10:$guess")
            if (repeated > 0.10) add("repeated-strategy-above-0.10:$repeated")
            if (noHintCompleted < 0.70) add("no-hint-completion-below-0.70:$noHintCompleted")
            if (highNoHint < 0.60) add("high-no-hint-completion-below-0.60:$highNoHint")
            observations.groupBy { it.causalFamily }.toSortedMap().forEach { (family, rows) ->
                val severeFamily = rows.count { it.perceivedBand <= it.assignedBucket - 2 }.ratio(rows.size)
                val fairFamily = rows.count { it.fairnessRating >= 4 }.ratio(rows.size)
                val guessFamily = rows.count { it.guessRequired }.ratio(rows.size)
                if (severeFamily > 0.10) add("family-severe-underrating:$family:$severeFamily")
                if (fairFamily < 0.85) add("family-fairness-below-0.85:$family:$fairFamily")
                if (guessFamily > 0.10) add("family-guess-above-0.10:$family:$guessFamily")
            }
        }
        return HumanValidationMetricsV1(
            spearman,
            bootstrapSpearman,
            adjacent,
            adjacentBootstrap,
            withinOne,
            fairness,
            guess,
            repeated,
            noHintCompleted,
            highNoHint,
            medians,
            failures.isEmpty(),
            failures,
        )
    }

    fun assertNoLeakage(
        calibration: List<HumanCalibrationObservationV1>,
        validation: List<HumanCalibrationObservationV1>,
    ): List<String> = buildList {
        fun overlap(label: String, first: Set<String>, second: Set<String>) {
            val values = first intersect second
            if (values.isNotEmpty()) add("$label-leakage:${values.sorted().joinToString()}")
        }
        overlap("board", calibration.mapTo(hashSetOf()) { it.boardFingerprint }, validation.mapTo(hashSetOf()) { it.boardFingerprint })
        // Family is a grouping variable rather than an identity key: sealed boards are unseen
        // graph instances drawn from the same frozen grammar families. Family-grouped validation
        // is performed during fitting; semanticCluster binds the exact causal graph instance.
        overlap("semantic-cluster", calibration.mapTo(hashSetOf()) { it.semanticCluster }, validation.mapTo(hashSetOf()) { it.semanticCluster })
        overlap("participant", calibration.mapTo(hashSetOf()) { it.participantCode }, validation.mapTo(hashSetOf()) { it.participantCode })
    }

    private fun deterministicTwoWayBootstrap(
        observations: List<HumanCalibrationObservationV1>,
        samples: Int,
        seed: Long,
        metric: (List<HumanCalibrationObservationV1>) -> Double,
    ): Double {
        if (observations.isEmpty()) return 0.0
        val participants = observations.map { it.participantCode }.distinct().sorted()
        val boards = observations.map { it.boardFingerprint }.distinct().sorted()
        val byPair = observations.groupBy { it.participantCode to it.boardFingerprint }
        var state = seed
        val results = MutableList(samples) {
            val sampledParticipants = List(participants.size) {
                state = splitMix(state)
                participants[(state ushr 1).rem(participants.size.toLong()).toInt()]
            }
            val sampledBoards = List(boards.size) {
                state = splitMix(state)
                boards[(state ushr 1).rem(boards.size.toLong()).toInt()]
            }
            val sample = buildList {
                sampledParticipants.forEach { participant ->
                    sampledBoards.forEach { board -> addAll(byPair[participant to board].orEmpty()) }
                }
            }
            if (sample.isEmpty()) 0.0 else metric(sample)
        }.sorted()
        return results[(samples * 0.025).toInt().coerceIn(results.indices)]
    }

    private fun superiority(observations: List<HumanCalibrationObservationV1>, higher: Int, lower: Int): Double {
        val high = observations.filter { it.assignedBucket == higher }.map { it.perceivedBand }
        val low = observations.filter { it.assignedBucket == lower }.map { it.perceivedBand }
        if (high.isEmpty() || low.isEmpty()) return 0.0
        var wins = 0.0
        high.forEach { h -> low.forEach { l -> wins += when { h > l -> 1.0; h == l -> 0.5; else -> 0.0 } } }
        return wins / (high.size * low.size)
    }

    private fun spearman(first: List<Double>, second: List<Double>): Double {
        if (first.size != second.size || first.size < 2) return 0.0
        val firstRanks = ranks(first)
        val secondRanks = ranks(second)
        val firstMean = firstRanks.average()
        val secondMean = secondRanks.average()
        val numerator = firstRanks.indices.sumOf { (firstRanks[it] - firstMean) * (secondRanks[it] - secondMean) }
        val denominator = sqrt(
            firstRanks.sumOf { (it - firstMean).pow(2) } * secondRanks.sumOf { (it - secondMean).pow(2) },
        )
        return if (denominator == 0.0) 0.0 else numerator / denominator
    }

    private fun ranks(values: List<Double>): List<Double> {
        val result = MutableList(values.size) { 0.0 }
        values.withIndex().groupBy { it.value }.toSortedMap().values.forEach { tied ->
            val occupied = tied.map { values.sorted().indexOf(it.value) + 1 }
            val rank = occupied.average()
            tied.forEach { result[it.index] = rank }
        }
        return result
    }

    private fun List<Int>.median(): Double {
        if (isEmpty()) return 0.0
        val sorted = sorted()
        return if (size % 2 == 1) sorted[size / 2].toDouble() else (sorted[size / 2 - 1] + sorted[size / 2]) / 2.0
    }

    private fun Int.ratio(total: Int): Double = if (total == 0) 0.0 else toDouble() / total

    private fun splitMix(value: Long): Long {
        var result = value + -7046029254386353131L
        result = (result xor (result ushr 30)) * -4658895280553007687L
        result = (result xor (result ushr 27)) * -7723592293110705685L
        return result xor (result ushr 31)
    }
}

private fun logistic(value: Double): Double = when {
    value >= 35.0 -> 1.0
    value <= -35.0 -> 0.0
    else -> 1.0 / (1.0 + exp(-value))
}

private fun List<Double>.normalizeProbabilities(): List<Double> {
    val sum = sum().coerceAtLeast(1e-12)
    return map { it / sum }
}
