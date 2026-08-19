package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.difficulty.PuzzleQualityStatusV2
import com.rameshta.magnetrail.core.level.LevelParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase0ProposalArtifactsTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun `oversized candidate report is complete but never claims human approval`() {
        val pool = json.decodeFromString<Phase0CandidatePoolReport>(
            resource("/development/PHASE0_CANDIDATE_POOL.json"),
        )
        val catalog = LevelParser().parseCatalog(resource("/development/PHASE0_STAGED_CANDIDATES.json"))

        assertTrue(pool.constructionComplete)
        assertEquals(450, pool.requestedViableCandidates)
        assertEquals(450, pool.viableCandidates.size)
        assertTrue(pool.shortfalls.isEmpty())
        assertEquals(450, catalog.levels.size)
        assertEquals(450, catalog.levels.map(ContentFingerprint::exact).toSet().size)
        assertEquals(450, catalog.levels.map(ContentFingerprint::symmetryNormalized).toSet().size)
        assertTrue(pool.viableCandidates.all { it.gate.accepted && it.difficulty.certifiable })
        assertTrue(pool.viableCandidates.all { it.manualReviewStatus == "PENDING" })
    }

    @Test
    fun `proposed campaign preserves identities and passes structural progression gates`() {
        val shipped = LevelParser().parseCatalog(resource("/content/d2/promotion/D2_SOURCE_CONTENT_V6.json"))
        val source = LevelParser().parseCatalog(resource("/development/PHASE0_SOURCE_CONTENT_V4.json"))
        val proposed = LevelParser().parseCatalog(
            resource("/development/PHASE0_PROPOSED_CAMPAIGN_NOT_PROMOTED.json"),
        )
        val report = json.decodeFromString<Phase0ProposedRemediationReport>(
            resource("/development/PHASE0_PROPOSED_REMEDIATION.json"),
        )

        assertEquals("PROPOSED_NOT_APPROVED_OR_PROMOTED", report.status)
        assertEquals(0, report.automatedApprovalCount)
        assertEquals(0, report.humanApprovalCount)
        assertEquals(150, report.assignments.size)
        assertEquals(shipped.levels.take(150).map { it.id }, proposed.levels.map { it.id })
        assertEquals(shipped.levels.take(150).map { it.number }, proposed.levels.map { it.number })
        assertEquals(proposed.levels.map(ContentFingerprint::exact), shipped.levels.take(150).map(ContentFingerprint::exact))
        assertEquals(6, shipped.contentVersion)
        assertEquals(4, shipped.generatorVersion)
        assertEquals(
            source.levels.map(ContentFingerprint::exact),
            shipped.levels.take(150).map { requireNotNull(it.metadata).previousContentFingerprint },
        )
        assertEquals(150, proposed.levels.map(ContentFingerprint::exact).toSet().size)
        assertEquals(150, proposed.levels.map(ContentFingerprint::symmetryNormalized).toSet().size)
        assertTrue(report.assignments.all { it.afterDifficulty.certifiable })
        assertTrue(report.assignments.none { it.afterQuality.status == PuzzleQualityStatusV2.REJECT })
        assertTrue(report.assignments.all { it.origin == "GENERATOR_ASSISTED" })
        assertTrue(report.assignments.all { it.manualReviewStatus == "PENDING" })
        assertEquals(
            0,
            report.assignments.sumOf {
                it.afterDifficulty.rawMetrics.canonicalChoiceMetrics.guessDependentChoices
            },
        )
        assertTrue(
            Phase0ProgressionPolicy.failures(
                report.assignments.associate { it.campaignNumber to it.afterDifficulty.score },
            ).isEmpty(),
        )
    }

    @Test
    fun `owner approval is recorded without fabricating playtest results`() {
        val approved = json.decodeFromString<Phase0ProposedRemediationReport>(
            resource("/development/PHASE0_APPROVED_REMEDIATION.json"),
        )
        val migration = json.decodeFromString<Phase0ContentMigrationReport>(
            resource("/development/PHASE0_CONTENT_MIGRATION.json"),
        )

        assertEquals("OWNER_APPROVED_AND_PROMOTED", approved.status)
        assertEquals("APPROVED_BY_PROJECT_OWNER", approved.ownerApprovalStatus)
        assertEquals("NOT_PERFORMED", approved.humanPlaytestStatus)
        assertEquals(150, approved.humanApprovalCount)
        assertEquals(0, approved.automatedApprovalCount)
        assertTrue(approved.assignments.all { it.manualReviewStatus == "OWNER_APPROVED_NOT_PLAYTESTED" })
        assertEquals(4, migration.sourceContentVersion)
        assertEquals(5, migration.targetContentVersion)
        assertEquals(150, migration.stableIdsPreserved)
        assertEquals(150, migration.fingerprintsChanged)
        assertTrue(migration.preservesCompletionStarsRewardsUnlocksAndCurrency)
        assertTrue(migration.archivesIncomparableBestRecords)
    }

    @Test
    fun `final certification is complete while human playtest remains pending`() {
        val report = json.decodeFromString<Phase0FinalCertificationReport>(
            resource("/development/PHASE0_FINAL_DIAGNOSTICS.json"),
        )
        val shipped = LevelParser().parseCatalog(resource("/content/d2/promotion/D2_SOURCE_CONTENT_V6.json"))

        assertEquals("COMPLETE_HUMAN_PLAYTEST_PENDING", report.status)
        assertEquals(5, report.contentVersion)
        assertEquals(3, report.generatorVersion)
        assertEquals(150, report.levels.size)
        assertEquals(150, report.certifiableCount)
        assertEquals(150, report.gateAcceptedCount)
        assertEquals(150, report.exactFingerprintCount)
        assertEquals(150, report.symmetryFingerprintCount)
        assertEquals(150, report.stableIdMigrationCount)
        assertEquals(150, report.previousFingerprintMappingCount)
        assertEquals(150, report.ownerApprovedCount)
        assertEquals(0, report.automatedApprovalCount)
        assertEquals(0, report.humanPlaytestedCount)
        assertEquals(0, report.guessDependentChoiceCount)
        assertTrue(report.progressionGatePassed)
        assertTrue(report.levels.all { it.productionCertificationAccepted && it.gate.accepted })
        assertTrue(report.levels.none { it.quality.status == PuzzleQualityStatusV2.REJECT })
        assertTrue(report.levels.all { it.humanPlaytestStatus == "PENDING" })
        assertEquals(
            shipped.levels.filter { it.number <= 150 }.sortedBy { it.number }.map(ContentFingerprint::exact),
            report.levels.sortedBy { it.campaignNumber }.map { it.contentFingerprint },
        )
    }

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)).readText()
}
