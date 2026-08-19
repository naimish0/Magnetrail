package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.generation.v5.GenerationProfilesV5
import com.rameshta.magnetrail.core.generation.v5.InteractionTypeV5
import com.rameshta.magnetrail.core.level.LevelParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class D2ArtifactsTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun `D2 catalog remains isolated while its boards are promoted under stable IDs`() {
        val shipped = LevelParser().parseCatalog(resource("/Magnetrail_Campaign_Levels_v3.json"))
        val source = LevelParser().parseCatalog(resource("/content/d2/promotion/D2_SOURCE_CONTENT_V6.json"))
        val staged = LevelParser().parseCatalog(resource("/content/d2/staging/D2_CAMPAIGN_V5_CANDIDATES.json"))
        val audit = json.decodeFromString<D2CampaignGenerationAudit>(
            resource("/development/D2_CAMPAIGN_GENERATION_AUDIT.json"),
        )

        assertEquals(200, shipped.levels.size)
        assertEquals(200, staged.levels.size)
        assertEquals(200, audit.aggregate.certifiedCount)
        assertEquals(0, audit.aggregate.truncatedCandidateCount)
        assertFalse(audit.sourceCampaignChangedByTask)
        assertEquals(5, staged.generatorVersion)
        assertEquals(7, staged.contentVersion)
        assertEquals(200, staged.levels.map(ContentFingerprint::exact).toSet().size)
        assertEquals(200, staged.levels.map(ContentFingerprint::symmetryNormalized).toSet().size)
        assertTrue(staged.levels.none { level -> shipped.levels.any { it.id == level.id } })
        assertEquals(7, shipped.contentVersion)
        assertEquals(5, shipped.generatorVersion)
        assertEquals(source.levels.map { it.id }, shipped.levels.map { it.id })
        assertEquals(
            staged.levels.map(ContentFingerprint::exact),
            shipped.levels.map(ContentFingerprint::exact),
        )
        assertEquals(
            source.levels.map(ContentFingerprint::exact),
            shipped.levels.map { requireNotNull(it.metadata).previousContentFingerprint },
        )

        audit.levels.forEach { row ->
            val d = row.diagnostics
            val profile = GenerationProfilesV5.forBand(d.difficultyBand)
            assertTrue(row.levelId, row.certified)
            assertTrue(row.levelId, d.searchComplete)
            assertFalse(row.levelId, d.truncated)
            assertTrue(row.levelId, d.objectDensity in profile.objectDensityRange)
            assertTrue(row.levelId, d.interactionGraph.interactionDensity in profile.interactionDensityRange)
            assertTrue(row.levelId, d.dependencyDepth >= profile.minArrowDependencyDepth)
            assertTrue(row.levelId, d.polarityImpactDepth >= profile.minPolarityImpactDepth)
            assertTrue(row.levelId, d.polarityImpactDepth <= d.arrowCount)
            assertTrue(row.levelId, d.cancellationTransitionCount >= profile.minCancellationTransitions)
            assertTrue(row.levelId, d.mandatoryOrderingDepth >= profile.minMandatoryOrderingDepth)
            assertTrue(row.levelId, d.consequenceDepth >= profile.minConsequenceDepth)
            assertTrue(row.levelId, d.objectRelevance.relevantObjectRatio >= profile.minRelevantObjectRatio)
            assertTrue(row.levelId, d.safeChoiceRatio <= profile.maxSafeChoiceRatio)
            assertTrue(row.levelId, d.greedySolveRate <= profile.maxGreedySolveRate)
            assertTrue(row.levelId, d.randomSuccessRate <= profile.maxRandomSuccessRate)
            assertTrue(row.levelId, d.meaningfulFailureRate >= profile.minMeaningfulFailureRate)
            assertTrue(row.levelId, d.recoveryPressure >= profile.minRecoveryPressure)
            assertTrue(row.levelId, d.strategicChoiceDensity >= profile.minStrategicChoiceDensity)
            assertTrue(row.levelId, d.exposureRevealCount >= profile.minExposureEvents)
            assertTrue(row.levelId, d.alternativePathCount >= profile.minAlternativePathCount)
            assertTrue(row.levelId, (d.commutationQuotient ?: 0) >= profile.minCanonicalStrategies)
            assertTrue(row.levelId, (d.permutationRedundancy ?: 0.0) <= profile.maxPermutationRedundancy)
            assertTrue(row.levelId, d.objectRelevance.analysisComplete)
        }
    }

    @Test
    fun `D2 interaction evidence includes every versioned relationship type`() {
        val audit = json.decodeFromString<D2CampaignGenerationAudit>(
            resource("/development/D2_CAMPAIGN_GENERATION_AUDIT.json"),
        )
        val observed = audit.levels.flatMap { it.diagnostics.interactionGraph.edges }.map { it.type }.toSet()
        assertEquals(InteractionTypeV5.entries.toSet(), observed)
        assertTrue(audit.levels.any { it.diagnostics.rows == 8 && it.diagnostics.difficultyBand.name == "EASY" })
        assertTrue(audit.levels.any { it.diagnostics.rows <= 5 && it.diagnostics.difficultyBand.rank >= 3 })
    }

    @Test
    fun `D2 promotion is owner directed without fabricating human ratings`() {
        val calibration = json.decodeFromString<D2CalibrationReport>(resource("/development/D2_CALIBRATION.json"))
        val manifest = json.decodeFromString<D2PromotionManifest>(
            resource("/development/D2_PROMOTION_MANIFEST.json"),
        )

        assertEquals("AWAITING_PROJECT_OWNER_RATINGS", calibration.status)
        assertEquals(0, calibration.ratingsAvailable)
        assertTrue(calibration.levels.all { it.humanRating == null })
        assertTrue(calibration.levels.size >= 45)
        assertEquals("OWNER_DIRECTED_PROMOTED", manifest.status)
        assertTrue(manifest.campaignModified)
        assertTrue(manifest.promotionAllowed)
        assertTrue(manifest.migrationProvenSafe)
        assertTrue(manifest.existingIdsReused)
        assertEquals(200, manifest.replace.size)
        assertTrue(manifest.keep.isEmpty())
        assertTrue(manifest.tune.isEmpty())
        assertTrue(manifest.requiredBeforePromotion.isEmpty())
    }

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)).readText()
}
