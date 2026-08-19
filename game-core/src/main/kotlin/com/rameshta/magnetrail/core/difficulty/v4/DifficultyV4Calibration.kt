package com.rameshta.magnetrail.core.difficulty.v4

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.sqrt

@Serializable
data class DifficultyV4HumanCalibrationDataset(
    val schemaVersion: Int = 1,
    val calibrationVersion: Int = DIFFICULTY_V4_CALIBRATION_VERSION,
    val status: String = "AWAITING_HUMAN_CALIBRATION",
    val ratingScale: List<DifficultyV4HumanRatingScalePoint> = defaultDifficultyV4RatingScale(),
    val groups: Map<String, List<Int>>,
    val raters: List<DifficultyV4HumanRater> = emptyList(),
    val levels: List<DifficultyV4HumanLevelRating>,
    val notes: List<String> = listOf(
        "Reference groups are sampling categories, not objective difficulty labels.",
        "Automated analysis is not human approval.",
    ),
)

@Serializable
data class DifficultyV4HumanRatingScalePoint(
    val value: Int,
    val label: String,
)

fun defaultDifficultyV4RatingScale(): List<DifficultyV4HumanRatingScalePoint> = listOf(
    DifficultyV4HumanRatingScalePoint(1, "Trivial"),
    DifficultyV4HumanRatingScalePoint(2, "Very Easy"),
    DifficultyV4HumanRatingScalePoint(3, "Easy"),
    DifficultyV4HumanRatingScalePoint(4, "Moderate"),
    DifficultyV4HumanRatingScalePoint(5, "Challenging"),
    DifficultyV4HumanRatingScalePoint(6, "Hard"),
    DifficultyV4HumanRatingScalePoint(7, "Very Hard"),
    DifficultyV4HumanRatingScalePoint(8, "Expert"),
)

@Serializable
data class DifficultyV4HumanRater(
    val raterId: String,
    val displayName: String? = null,
)

@Serializable
data class DifficultyV4HumanLevelRating(
    val levelId: String,
    val levelNumber: Int,
    val group: String,
    val ratings: List<DifficultyV4HumanRating> = emptyList(),
    /** Board identity at the time of play. Ratings never transfer across board revisions. */
    val boardFingerprint: String? = null,
)

@Serializable
data class DifficultyV4HumanRating(
    val raterId: String,
    val difficultyRating: Int? = null,
    val firstMoveObvious: Boolean? = null,
    val meaningfulMistakeMade: Boolean? = null,
    val requiredOrderingReasoning: Boolean? = null,
    val requiredPolarityReasoning: Boolean? = null,
    val interchangeableMovesObserved: Boolean? = null,
    val neededUndo: Boolean? = null,
    val neededRestart: Boolean? = null,
    val perceivedFairness: Boolean? = null,
    val comments: String? = null,
) {
    init {
        require(difficultyRating == null || difficultyRating in 1..8)
    }
}

@Serializable
data class DifficultyV4CalibrationScoreRow(
    val levelId: String,
    val levelNumber: Int,
    val v3Score: Int,
    val v4Score: Int?,
    val v4Confidence: Double,
    val boardFingerprint: String? = null,
    val metrics: Map<String, Double?> = emptyMap(),
)

@Serializable
data class DifficultyV4CalibrationObservation(
    val levelId: String,
    val levelNumber: Int,
    val raterId: String,
    val humanRating: Int,
    val humanScore100: Double,
    val v3Score: Int,
    val v4Score: Int?,
    val v3Error: Double,
    val v4Error: Double?,
)

@Serializable
data class DifficultyV4CalibrationComparison(
    val sampleCount: Int,
    val pearson: Double?,
    val spearman: Double?,
    val meanAbsoluteError: Double?,
    val meanRankDisagreement: Double?,
)

@Serializable
data class DifficultyV4CalibrationOutlier(
    val levelId: String,
    val levelNumber: Int,
    val humanScore100: Double,
    val predictedScore: Int,
    val signedError: Double,
)

@Serializable
data class DifficultyV4CalibrationReport(
    val schemaVersion: Int = 1,
    val calibrationVersion: Int = DIFFICULTY_V4_CALIBRATION_VERSION,
    val status: String,
    val ratedObservationCount: Int,
    val distinctRatedLevelCount: Int,
    val excludedStaleBoardRatingCount: Int = 0,
    val v3: DifficultyV4CalibrationComparison,
    val v4: DifficultyV4CalibrationComparison,
    val metricPearsonCorrelations: Map<String, Double?>,
    val v3StrongestOverestimates: List<DifficultyV4CalibrationOutlier>,
    val v3StrongestUnderestimates: List<DifficultyV4CalibrationOutlier>,
    val v4StrongestOverestimates: List<DifficultyV4CalibrationOutlier>,
    val v4StrongestUnderestimates: List<DifficultyV4CalibrationOutlier>,
    val alignmentConclusion: String,
    val warnings: List<String>,
    val observations: List<DifficultyV4CalibrationObservation>,
)

object DifficultyV4Calibrator {
    fun compare(
        dataset: DifficultyV4HumanCalibrationDataset,
        scores: List<DifficultyV4CalibrationScoreRow>,
    ): DifficultyV4CalibrationReport {
        val byId = scores.associateBy { it.levelId }
        val excludedStaleBoardRatings = dataset.levels.sumOf { level ->
            val score = byId[level.levelId] ?: return@sumOf 0
            if (score.boardFingerprint != null && level.boardFingerprint != score.boardFingerprint) {
                level.ratings.count { it.difficultyRating != null }
            } else {
                0
            }
        }
        val observations = dataset.levels.flatMap { level ->
            val score = byId[level.levelId] ?: return@flatMap emptyList()
            if (score.boardFingerprint != null && level.boardFingerprint != score.boardFingerprint) {
                return@flatMap emptyList()
            }
            level.ratings.mapNotNull { rating ->
                val human = rating.difficultyRating ?: return@mapNotNull null
                val human100 = humanScore100(human)
                DifficultyV4CalibrationObservation(
                    levelId = level.levelId,
                    levelNumber = level.levelNumber,
                    raterId = rating.raterId,
                    humanRating = human,
                    humanScore100 = round4(human100),
                    v3Score = score.v3Score,
                    v4Score = score.v4Score,
                    v3Error = round4(score.v3Score - human100),
                    v4Error = score.v4Score?.let { round4(it - human100) },
                )
            }
        }.sortedWith(compareBy<DifficultyV4CalibrationObservation> { it.levelNumber }.thenBy { it.raterId })
        val human = observations.map { it.humanScore100 }
        val v3Values = observations.map { it.v3Score.toDouble() }
        val v4Pairs = observations.mapNotNull { observation ->
            observation.v4Score?.toDouble()?.let { observation.humanScore100 to it }
        }
        val v3 = comparison(human.zip(v3Values))
        val v4 = comparison(v4Pairs)
        val metricNames = scores.flatMap { it.metrics.keys }.toSortedSet()
        val observationsByLevel = observations.groupBy { it.levelId }
        val metricCorrelations = metricNames.associateWith { metric ->
            val pairs = scores.flatMap { row ->
                val metricValue = row.metrics[metric] ?: return@flatMap emptyList()
                observationsByLevel[row.levelId].orEmpty().map { it.humanScore100 to metricValue }
            }
            pearson(pairs)
        }
        val v3Over = outliers(observations, useV4 = false, over = true)
        val v3Under = outliers(observations, useV4 = false, over = false)
        val v4Over = outliers(observations, useV4 = true, over = true)
        val v4Under = outliers(observations, useV4 = true, over = false)
        val conclusion = when {
            observations.isEmpty() -> "EVIDENCE_INCONCLUSIVE_AWAITING_HUMAN_CALIBRATION"
            v3.spearman == null || v4.spearman == null -> "EVIDENCE_INCONCLUSIVE"
            v4.spearman > v3.spearman && (v4.meanAbsoluteError ?: Double.MAX_VALUE) <=
                (v3.meanAbsoluteError ?: Double.MAX_VALUE) -> "V4_APPEARS_BETTER_ALIGNED"
            v4.spearman < v3.spearman && (v4.meanAbsoluteError ?: 0.0) >=
                (v3.meanAbsoluteError ?: 0.0) -> "V4_APPEARS_WORSE_ALIGNED"
            else -> "EVIDENCE_INCONCLUSIVE"
        }
        return DifficultyV4CalibrationReport(
            status = if (observations.isEmpty()) "AWAITING_HUMAN_CALIBRATION" else "CALIBRATION_PRELIMINARY",
            ratedObservationCount = observations.size,
            distinctRatedLevelCount = observations.map { it.levelId }.distinct().size,
            excludedStaleBoardRatingCount = excludedStaleBoardRatings,
            v3 = v3,
            v4 = v4,
            metricPearsonCorrelations = metricCorrelations,
            v3StrongestOverestimates = v3Over,
            v3StrongestUnderestimates = v3Under,
            v4StrongestOverestimates = v4Over,
            v4StrongestUnderestimates = v4Under,
            alignmentConclusion = conclusion,
            warnings = buildList {
                add("SAMPLE SIZE LIMITED")
                add("CALIBRATION PRELIMINARY")
                if (excludedStaleBoardRatings > 0) add("STALE BOARD RATINGS EXCLUDED")
            },
            observations = observations,
        )
    }

    private fun comparison(pairs: List<Pair<Double, Double>>): DifficultyV4CalibrationComparison {
        val errors = pairs.map { (human, predicted) -> abs(predicted - human) }
        val ranks = if (pairs.size >= 2) {
            val humanRanks = ranks(pairs.map { it.first })
            val predictionRanks = ranks(pairs.map { it.second })
            humanRanks.zip(predictionRanks).map { (first, second) -> abs(first - second) }
        } else {
            emptyList()
        }
        return DifficultyV4CalibrationComparison(
            sampleCount = pairs.size,
            pearson = pearson(pairs),
            spearman = if (pairs.size >= 3) pearson(ranks(pairs.map { it.first }).zip(ranks(pairs.map { it.second }))) else null,
            meanAbsoluteError = errors.takeIf { it.isNotEmpty() }?.average()?.let(::round4),
            meanRankDisagreement = ranks.takeIf { it.isNotEmpty() }?.average()?.let(::round4),
        )
    }

    private fun pearson(pairs: List<Pair<Double, Double>>): Double? {
        if (pairs.size < 3) return null
        val firstMean = pairs.map { it.first }.average()
        val secondMean = pairs.map { it.second }.average()
        val numerator = pairs.sumOf { (first, second) -> (first - firstMean) * (second - secondMean) }
        val firstDenominator = pairs.sumOf { (first, _) -> (first - firstMean) * (first - firstMean) }
        val secondDenominator = pairs.sumOf { (_, second) -> (second - secondMean) * (second - secondMean) }
        if (firstDenominator <= 1e-12 || secondDenominator <= 1e-12) return null
        val denominator = sqrt(firstDenominator * secondDenominator)
        return round4((numerator / denominator).coerceIn(-1.0, 1.0))
    }

    private fun ranks(values: List<Double>): List<Double> {
        val indexed = values.withIndex().sortedWith(compareBy<IndexedValue<Double>> { it.value }.thenBy { it.index })
        val result = DoubleArray(values.size)
        var cursor = 0
        while (cursor < indexed.size) {
            var end = cursor + 1
            while (end < indexed.size && indexed[end].value == indexed[cursor].value) end += 1
            val averageRank = (cursor + 1 + end).toDouble() / 2.0
            for (position in cursor..<end) result[indexed[position].index] = averageRank
            cursor = end
        }
        return result.toList()
    }

    private fun outliers(
        observations: List<DifficultyV4CalibrationObservation>,
        useV4: Boolean,
        over: Boolean,
    ): List<DifficultyV4CalibrationOutlier> = observations.mapNotNull { observation ->
        val score = if (useV4) observation.v4Score else observation.v3Score
        score?.let {
            DifficultyV4CalibrationOutlier(
                levelId = observation.levelId,
                levelNumber = observation.levelNumber,
                humanScore100 = observation.humanScore100,
                predictedScore = it,
                signedError = round4(it - observation.humanScore100),
            )
        }
    }.filter { if (over) it.signedError > 0.0 else it.signedError < 0.0 }
        .sortedWith(
            if (over) {
                compareByDescending<DifficultyV4CalibrationOutlier> { it.signedError }.thenBy { it.levelNumber }
            } else {
                compareBy<DifficultyV4CalibrationOutlier> { it.signedError }.thenBy { it.levelNumber }
            },
        ).take(10)

    private fun humanScore100(rating: Int): Double = (rating - 1) * (100.0 / 7.0)
}
