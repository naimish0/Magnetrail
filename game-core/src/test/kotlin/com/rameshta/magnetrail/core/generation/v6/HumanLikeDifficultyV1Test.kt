package com.rameshta.magnetrail.core.generation.v6

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HumanLikeDifficultyV1Test {
    @Test
    fun `V6 exposes five bands and rejects removed Master bucket`() {
        assertEquals(listOf(1, 2, 3, 4, 5), V6Profiles.all.map { it.bucket })
        assertEquals(V6_HUMAN_BAND_COUNT, V6Profiles.all.size)
        assertTrue(runCatching {
            GeneratorV6Identity(1L, 6, CausalGrammarFamilyV6.POLARITY_LOCK_RELEASE, 0)
        }.isFailure)
    }

    @Test
    fun `ordinal fit is deterministic and fingerprint bound`() {
        val observations = calibrationObservations()
        val calibrator = CumulativeLinkOrdinalCalibratorV1(iterations = 600, learningRate = 0.02)
        val first = calibrator.fit(observations) as HumanModelFitResultV1.Fitted
        val second = calibrator.fit(observations) as HumanModelFitResultV1.Fitted

        assertEquals(first.model.coefficients, second.model.coefficients)
        assertEquals(first.model.thresholds, second.model.thresholds)
        assertEquals(first.finalNegativeLogLikelihood, second.finalNegativeLogLikelihood, 0.0)
        assertEquals(10, first.model.trainingParticipantCount)
        assertEquals(V6_PILOT_BOARD_COUNT, first.model.trainingBoardCount)
        assertTrue(first.model.thresholds.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `underpowered owner evidence cannot certify a human model`() {
        val oneParticipant = calibrationObservations().filter { it.participantCode == "P00" }
        val result = CumulativeLinkOrdinalCalibratorV1(iterations = 10).fit(oneParticipant)
        assertTrue(result is HumanModelFitResultV1.Insufficient)
        val reasons = (result as HumanModelFitResultV1.Insufficient).reasons
        assertTrue(reasons.any { it.startsWith("requires-at-least-10-participants") })
        assertTrue(reasons.any { it.startsWith("participant-share-above-20-percent") })
    }

    @Test
    fun `model rejects out of distribution features`() {
        val model = (CumulativeLinkOrdinalCalibratorV1(iterations = 300).fit(calibrationObservations())
            as HumanModelFitResultV1.Fitted).model
        val prediction = model.predict(features(100.0))
        assertTrue(prediction.outOfDistribution)
        assertEquals("OUT_OF_DISTRIBUTION", prediction.rejectionReason)
        assertEquals(V6_HUMAN_BAND_COUNT, prediction.bandProbabilities.size)
        assertEquals(1.0, prediction.bandProbabilities.sum(), 1e-9)

        val lowConfidence = model.copy(trainingBoardCount = 1).predict(features(3.0))
        assertFalse(lowConfidence.outOfDistribution)
        assertEquals("LOW_CALIBRATION_CONFIDENCE", lowConfidence.rejectionReason)
    }

    @Test
    fun `sealed split detects every protected leakage dimension`() {
        val calibration = calibrationObservations()
        val validation = listOf(calibration.first())
        val leakage = HumanModelValidatorV1.assertNoLeakage(calibration, validation)
        assertTrue(leakage.any { it.startsWith("board-leakage") })
        assertFalse(leakage.any { it.startsWith("causal-family-leakage") })
        assertTrue(leakage.any { it.startsWith("semantic-cluster-leakage") })
        assertTrue(leakage.any { it.startsWith("participant-leakage") })
    }

    @Test
    fun `Bradley Terry secondary fit is deterministic and orders harder boards`() {
        val observations = buildList {
            repeat(20) { participant ->
                add(PairwiseDifficultyObservationV1("P$participant", "expert", "hard"))
                add(PairwiseDifficultyObservationV1("P$participant", "hard", "easy"))
            }
        }
        val calibrator = BradleyTerryCalibratorV1(iterations = 500)
        val first = calibrator.fit(observations)
        val second = calibrator.fit(observations)
        assertEquals(first, second)
        assertTrue(first.harderProbability("expert", "hard") > 0.5)
        assertTrue(first.harderProbability("expert", "easy") > first.harderProbability("hard", "easy"))
    }

    @Test
    fun `perfectly ordered sealed evidence passes statistical point gates`() {
        val observations = calibrationObservations().map { observation ->
            observation.copy(
                perceivedBand = observation.assignedBucket,
                completed = true,
                hinted = false,
                fairnessRating = 5,
                guessRequired = false,
                repeatedStrategy = false,
            )
        }
        val metrics = HumanModelValidatorV1.validateSealed(observations)
        assertTrue(metrics.failureReasons.joinToString(), metrics.passes)
        assertEquals(1.0, metrics.spearmanAssignedPerceived, 0.0)
        assertFalse(metrics.medianByBucket.isEmpty())
    }

    private fun calibrationObservations(): List<HumanCalibrationObservationV1> = buildList {
        repeat(10) { participant ->
            repeat(V6_PILOT_BOARD_COUNT) { board ->
                val bucket = board / 6 + 1
                add(
                    HumanCalibrationObservationV1(
                        participantCode = "P${participant.toString().padStart(2, '0')}",
                        boardFingerprint = "sha256:${board.toString(16).padStart(64, '0')}",
                        causalFamily = "family-${board % 6}",
                        semanticCluster = "cluster-$board",
                        perceivedBand = bucket,
                        assignedBucket = bucket,
                        features = features(bucket.toDouble()),
                        hinted = false,
                        completed = true,
                        fairnessRating = 5,
                        guessRequired = false,
                        repeatedStrategy = false,
                    ),
                )
            }
        }
    }

    private fun features(value: Double) = HumanCognitiveFeaturesV1(
        meaningfulDecisionCount = value,
        nonCommutingForks = value,
        successfulPersistentTraps = (value - 1).coerceAtLeast(0.0),
        minimumLookaheadProofDepth = value,
        delayedConsequenceDepth = value,
        polarityMemoryDepth = value / 2,
        controllerVisibilityChanges = value,
        interactingCausalChains = (value / 2).coerceAtLeast(1.0),
        hardestWinningChoiceRatio = 1.0 / value,
        solutionPolicyClasses = value,
        longestForcedRun = 1.0,
        meaningfulDecisionDensity = value / 10,
        guessDependence = 0.0,
        restartCost = value,
        routeLosReasoningCost = value / 6,
    )
}
