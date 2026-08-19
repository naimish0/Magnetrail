package com.rameshta.magnetrail.core.difficulty.v4

import kotlin.math.pow
import kotlin.math.roundToInt

object DifficultyV4Scorer {
    fun score(
        metrics: DifficultyV4Metrics,
        config: DifficultyV4Config = DifficultyV4Config(),
    ): DifficultyV4Score {
        val ordering = metrics.ordering.mandatoryOrderingRatio ?: 0.0
        val orderingDepth = metrics.ordering.mandatoryOrderingChainDepth ?: 0
        val consequenceAverage = metrics.consequencePersistence.averageDepth ?: 0.0
        val decisionDensity = metrics.forcedDecision.decisionDensity ?: 0.0
        val permutation = metrics.strategy.permutationRedundancy ?: 0.0
        val forcedRatio = if (
            metrics.forcedDecision.totalSolutionLength != null &&
            metrics.forcedDecision.longestForcedRun != null &&
            metrics.forcedDecision.totalSolutionLength > 0
        ) {
            metrics.forcedDecision.longestForcedRun.toDouble() / metrics.forcedDecision.totalSolutionLength
        } else {
            0.0
        }
        val irrelevantRatio = irrelevantStructureRatio(metrics.objectRelevance)
        val normalized = DifficultyV4NormalizedMetrics(
            meaningfulFailure = curve(metrics.meaningfulFailureRate, 0.25),
            harmfulDecisionDensity = curve(metrics.harmfulDecisionDensity, 0.60),
            consequencePersistence = curve(consequenceAverage, 3.0),
            mandatoryOrdering = (
                curve(ordering, 0.40) * 0.65 + curve(orderingDepth.toDouble(), 4.0) * 0.35
                ).coerceIn(0.0, 1.0),
            polarityActionability = curve(metrics.polarity.polarityImpactRatio, 0.50),
            greedyResistance = if (metrics.greedyPolicy.solved) 0.0 else 1.0,
            randomResistance = metrics.randomPolicy.deadlockRate.coerceIn(0.0, 1.0),
            recoveryPressure = curve(metrics.recovery.normalizedRecoveryPressure, 0.35),
            decisionDensity = curve(decisionDensity, 0.50),
            safeChoicePenalty = metrics.safeChoiceRatio.coerceIn(0.0, 1.0),
            permutationRedundancyPenalty = permutation.coerceIn(0.0, 1.0),
            forcedRunPenalty = forcedRatio.coerceIn(0.0, 1.0),
            irrelevantStructurePenalty = irrelevantRatio,
        ).rounded()
        val weights = config.scoreWeights
        val positive = linkedMapOf(
            "meaningfulFailure" to normalized.meaningfulFailure * weights.meaningfulFailure,
            "harmfulDecisionDensity" to normalized.harmfulDecisionDensity * weights.harmfulDecisionDensity,
            "consequencePersistence" to normalized.consequencePersistence * weights.consequencePersistence,
            "mandatoryOrdering" to normalized.mandatoryOrdering * weights.mandatoryOrdering,
            "polarityActionability" to normalized.polarityActionability * weights.polarityActionability,
            "greedyResistance" to normalized.greedyResistance * weights.greedyResistance,
            "randomResistance" to normalized.randomResistance * weights.randomResistance,
            "recoveryPressure" to normalized.recoveryPressure * weights.recoveryPressure,
            "decisionDensity" to normalized.decisionDensity * weights.decisionDensity,
        ).mapValues { round4(it.value) }
        val negative = linkedMapOf(
            "safeChoiceRatio" to normalized.safeChoicePenalty * weights.safeChoicePenalty,
            "permutationRedundancy" to
                normalized.permutationRedundancyPenalty * weights.permutationRedundancyPenalty,
            "forcedRunExcess" to normalized.forcedRunPenalty * weights.forcedRunPenalty,
            "irrelevantStructure" to normalized.irrelevantStructurePenalty * weights.irrelevantStructurePenalty,
        ).mapValues { round4(it.value) }
        val positiveTotal = positive.values.sum()
        val negativeTotal = negative.values.sum()
        val essentialComplete = metrics.searchComplete &&
            metrics.strategy.analysisComplete &&
            metrics.ordering.analysisComplete
        val confidenceReasons = buildList {
            addAll(metrics.truncationReasons)
            if (!metrics.strategy.analysisComplete) add("STRATEGY_ANALYSIS_INCOMPLETE")
            if (!metrics.ordering.analysisComplete) add("ORDERING_ANALYSIS_INCOMPLETE")
            if (!metrics.polarity.analysisComplete) add("POLARITY_ANALYSIS_INCOMPLETE")
            if (!metrics.objectRelevance.analysisComplete) add("OBJECT_RELEVANCE_INCOMPLETE")
            if (metrics.randomPolicy.seedCount != config.randomPolicySeeds.size) add("RANDOM_POLICY_INCOMPLETE")
        }.distinct().sorted()
        var confidence = 1.0
        if (!metrics.searchComplete) confidence -= 0.55
        if (!metrics.strategy.analysisComplete) confidence -= 0.15
        if (!metrics.ordering.analysisComplete) confidence -= 0.10
        if (!metrics.polarity.analysisComplete) confidence -= 0.08
        if (!metrics.objectRelevance.analysisComplete) confidence -= 0.08
        if (metrics.randomPolicy.seedCount != config.randomPolicySeeds.size) confidence -= 0.04
        return DifficultyV4Score(
            metrics = metrics,
            normalized = normalized,
            contributions = DifficultyV4Contributions(
                positive = positive,
                negative = negative,
                positiveTotal = round4(positiveTotal),
                negativeTotal = round4(negativeTotal),
            ),
            score = if (essentialComplete) {
                (positiveTotal - negativeTotal).roundToInt().coerceIn(0, 100)
            } else {
                null
            },
            confidence = round4(confidence.coerceIn(0.0, 1.0)),
            confidenceReasons = confidenceReasons,
            searchComplete = metrics.searchComplete,
            searchTruncated = metrics.searchTruncated,
            truncationReasons = metrics.truncationReasons,
            calibrationVersion = config.calibrationVersion,
            analyzerVersion = config.analyzerVersion,
        )
    }

    private fun curve(value: Double, target: Double): Double =
        (value.coerceAtLeast(0.0) / target).coerceIn(0.0, 1.0).pow(0.80)

    private fun irrelevantStructureRatio(metrics: ObjectRelevanceMetrics): Double {
        val irrelevant = listOfNotNull(metrics.irrelevantWallCount, metrics.irrelevantMagnetCount).sum()
        val total = metrics.totalWallCount + metrics.totalMagnetCount
        return ratio(irrelevant, total)
    }
}

private fun DifficultyV4NormalizedMetrics.rounded(): DifficultyV4NormalizedMetrics = copy(
    meaningfulFailure = round4(meaningfulFailure),
    harmfulDecisionDensity = round4(harmfulDecisionDensity),
    consequencePersistence = round4(consequencePersistence),
    mandatoryOrdering = round4(mandatoryOrdering),
    polarityActionability = round4(polarityActionability),
    greedyResistance = round4(greedyResistance),
    randomResistance = round4(randomResistance),
    recoveryPressure = round4(recoveryPressure),
    decisionDensity = round4(decisionDensity),
    safeChoicePenalty = round4(safeChoicePenalty),
    permutationRedundancyPenalty = round4(permutationRedundancyPenalty),
    forcedRunPenalty = round4(forcedRunPenalty),
    irrelevantStructurePenalty = round4(irrelevantStructurePenalty),
)

internal fun ratio(numerator: Int, denominator: Int): Double =
    if (denominator <= 0) 0.0 else round4(numerator.toDouble() / denominator)

internal fun round4(value: Double): Double = kotlin.math.round(value * 10_000.0) / 10_000.0

