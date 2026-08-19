package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.generation.v5.GenerationProfilesD21
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class D21ArtifactsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun checkedInAuditIsInternallyConsistentAndNeverTreatsBlockedAsCertified() {
        val audit = audit()
        assertTrue(audit.status in setOf("PASS", "PASS_WITH_LIMITATIONS", "BLOCKED"))
        assertEquals(audit.profileConfigurations.size, audit.profileResults.size)
        assertTrue(audit.profileConfigurations.isNotEmpty())
        if (audit.status == "BLOCKED") {
            assertTrue(audit.profileResults.any { it.exhaustedRequests > 0 })
            assertTrue(audit.rejectionReasons.isNotEmpty())
        } else {
            assertTrue(audit.profileResults.all { it.validCandidates >= 1 && it.exhaustedRequests == 0 })
        }
        assertTrue(audit.levels.all {
            it.deterministicReproduction && it.diagnostics.searchComplete && !it.diagnostics.truncated
        })
    }

    @Test
    fun acceptedCandidatesMeetSpatialCountsDensityAndParticipationGates() {
        audit().levels.forEach { row ->
            val profile = GenerationProfilesD21.benchmarkProfiles.single { it.id == row.profileId }
            val spatial = requireNotNull(profile.spatialDensityProfile)
            val d = row.diagnostics
            assertTrue(row.levelId, d.spatialDensity.occupancyRatio in
                spatial.minimumOccupancyRatio..spatial.maximumOccupancyRatio)
            assertTrue(row.levelId, d.arrowCount in spatial.arrowCount)
            assertTrue(row.levelId, d.magnetCount in spatial.magnetCount)
            assertTrue(row.levelId, d.wallCount in spatial.wallCount)
            assertTrue(row.levelId, d.objectRelevance.relevantObjectRatio >= profile.minRelevantObjectRatio)
            assertTrue(row.levelId, d.objectRelevance.averageScore >= spatial.minimumAverageObjectRelevance)
            val irrelevantRatio = d.objectRelevance.irrelevantObjectCount.toDouble() /
                d.spatialDensity.totalObjectCount
            assertTrue(row.levelId, irrelevantRatio <= spatial.maximumIrrelevantObjectRatio)
            assertEquals(0, d.spatialDensity.overlappingAuthoredCellCount)
        }
    }

    @Test
    fun mediumAndHigherAreDenseAndNotDenseButTrivial() {
        audit().levels.filter { it.difficulty !in setOf("TUTORIAL", "EASY") }.forEach { row ->
            val profile = GenerationProfilesD21.benchmarkProfiles.single { it.id == row.profileId }
            val spatial = requireNotNull(profile.spatialDensityProfile)
            val d = row.diagnostics
            assertTrue(row.levelId, d.spatialDensity.occupancyRatio >= spatial.minimumOccupancyRatio)
            assertFalse(
                row.levelId,
                d.greedySolveRate >= 1.0 && d.safeChoiceRatio >= 0.95 && d.meaningfulFailureRate < 0.02,
            )
        }
    }

    @Test
    fun higherProfilesContainLongRangeLosBlockingPolarityAndPersistentConsequences() {
        audit().levels.filter { it.difficulty in setOf("HARD", "VERY HARD", "EXPERT", "MASTER") }
            .forEach { row ->
                val profile = GenerationProfilesD21.benchmarkProfiles.single { it.id == row.profileId }
                val spatial = requireNotNull(profile.spatialDensityProfile)
                val d = row.diagnostics
                assertTrue(row.levelId, d.magneticRelationshipDistances.count {
                    it >= spatial.longRangeDistance
                } >= spatial.minimumLongRangeMagneticRelationships)
                assertTrue(row.levelId, d.meaningfulLineOfSightInteractionCount >=
                    spatial.minimumMeaningfulLineOfSightInteractions)
                assertTrue(row.levelId, d.meaningfulArrowBlockerRelationshipCount >=
                    spatial.minimumArrowBlockerRelationships)
                assertTrue(row.levelId, d.polarityImpactDepth >= profile.minPolarityImpactDepth)
                assertTrue(row.levelId, d.dependencyDepth >= profile.minArrowDependencyDepth)
                assertTrue(row.levelId, d.persistentConsequenceCount >=
                    spatial.minimumPersistentConsequenceCount)
            }
    }

    @Test
    fun exactSymmetryAndDependencyFingerprintsAreUniqueAndPresent() {
        val levels = audit().levels
        assertEquals(levels.size, levels.map { it.diagnostics.exactFingerprint }.toSet().size)
        assertEquals(levels.size, levels.map { it.diagnostics.symmetryFingerprint }.toSet().size)
        assertTrue(levels.all {
            it.diagnostics.interactionFingerprint.isNotBlank() &&
                it.diagnostics.dependencyFingerprint.isNotBlank()
        })
    }

    @Test
    fun campaignRemainsByteIdenticalToRecordedHash() {
        val audit = audit()
        val campaignUrl = requireNotNull(javaClass.getResource("/Magnetrail_Campaign_Levels_v3.json"))
        val bytes = File(campaignUrl.toURI()).readBytes()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertTrue(audit.campaignByteIdentical)
        assertEquals(audit.sourceCampaignSha256Before, audit.sourceCampaignSha256After)
        assertEquals(audit.sourceCampaignSha256Before, digest)
        assertEquals(200, audit.sourceCampaignLevelCount)
    }

    private fun audit(): D21SpatialDensityAudit {
        val url = requireNotNull(javaClass.getResource("/development/MAGNETRAIL_D2_1_AUDIT.json"))
        return json.decodeFromString(File(url.toURI()).readText())
    }
}
