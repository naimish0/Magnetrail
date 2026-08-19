package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.difficulty.PuzzleQualityStatusV2
import com.rameshta.magnetrail.core.difficulty.PuzzleSearchAnalyzer
import com.rameshta.magnetrail.core.level.LevelParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase1ProposalArtifactsTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun `oversized Phase 1 pool is deterministic gated and unapproved`() {
        val report = json.decodeFromString<Phase1CandidatePoolReport>(
            resource("/content/M5_3_CANDIDATE_POOL.json"),
        )
        val candidates = LevelParser().parseCatalog(resource("/content/M5_3_STAGED_CANDIDATES.json"))

        assertEquals("STAGED_NOT_APPROVED_OR_PROMOTED", report.status)
        assertTrue(report.constructionComplete)
        assertEquals(200, report.candidates.size)
        assertEquals(200, candidates.levels.size)
        assertEquals(report.targetQuotas, report.producedByTarget)
        assertEquals(200, report.exactFingerprintCount)
        assertEquals(200, report.symmetryFingerprintCount)
        assertEquals(0, report.humanApprovalCount)
        assertTrue(report.candidates.all { it.width in 6..7 && it.height in 6..7 })
        assertTrue(report.candidates.all { it.gate.accepted && it.difficulty.certifiable })
        assertTrue(report.candidates.none { it.quality.status == PuzzleQualityStatusV2.REJECT })
        assertTrue(report.candidates.all { it.manualReviewStatus == "PENDING" })
        assertEquals(200, candidates.levels.map(ContentFingerprint::exact).toSet().size)
        assertEquals(200, candidates.levels.map(ContentFingerprint::symmetryNormalized).toSet().size)
    }

    @Test
    fun `Phase 1 proposal preserves first 150 and does not claim approval`() {
        val source = LevelParser().parseCatalog(resource("/content/M5_3_SOURCE_CONTENT_V5.json"))
        val proposed = LevelParser().parseCatalog(
            resource("/content/M5_3_PROPOSED_CAMPAIGN_NOT_PROMOTED.json"),
        )
        val report = json.decodeFromString<Phase1ProposalReport>(
            resource("/content/M5_3_PROPOSED_PROMOTION_MANIFEST.json"),
        )

        assertEquals("PROPOSED_NOT_APPROVED_OR_PROMOTED", report.status)
        assertEquals(0, report.automatedApprovalCount)
        assertEquals(0, report.humanApprovalCount)
        assertEquals("PENDING", report.ownerApprovalStatus)
        assertEquals("NOT_PERFORMED", report.humanPlaytestStatus)
        assertEquals(50, report.assignments.size)
        assertEquals(200, proposed.levels.size)
        assertEquals((1..200).toList(), proposed.levels.map { it.number })
        assertEquals(
            source.levels.map(ContentFingerprint::exact),
            proposed.levels.take(150).map(ContentFingerprint::exact),
        )
        assertEquals((151..200).map { "campaign-${it.toString().padStart(3, '0')}" }, report.assignments.map { it.levelId })
        assertEquals(150, report.existingLevelsPreserved)
        assertEquals(200, report.exactFingerprintCount)
        assertEquals(200, report.symmetryFingerprintCount)
        assertEquals(50, report.targetGateAcceptedCount)
        assertEquals(50, report.certifiableCount)
        assertEquals(0, report.guessDependentChoiceCount)
        assertTrue(report.assignments.none { it.quality.status == PuzzleQualityStatusV2.REJECT })
        assertTrue(report.assignments.all { it.origin == "GENERATOR_ASSISTED" })
        assertTrue(report.assignments.all { it.manualReviewStatus == "PENDING" })
        assertEquals(10, report.curationModeCounts["HEAVY_TUNING_REVIEW_SLOT"])
        assertEquals(40, report.curationModeCounts["GENERATOR_ASSISTED_SELECTION"])
        assertTrue(report.assignments.single { it.campaignNumber == 200 }.gate.accepted)
    }

    @Test
    fun `approved Phase 1 promotion is distinct from human playtesting`() {
        val source = LevelParser().parseCatalog(resource("/content/M5_3_SOURCE_CONTENT_V5.json"))
        val shipped = LevelParser().parseCatalog(resource("/content/d2/promotion/D2_SOURCE_CONTENT_V6.json"))
        val approved = json.decodeFromString<Phase1ProposalReport>(
            resource("/content/M5_3_APPROVED_PROMOTION.json"),
        )
        val migration = json.decodeFromString<Phase1ContentMigrationReport>(
            resource("/content/M5_3_CONTENT_MIGRATION.json"),
        )

        assertEquals("OWNER_APPROVED_AND_PROMOTED", approved.status)
        assertEquals("APPROVED_BY_PROJECT_OWNER", approved.ownerApprovalStatus)
        assertEquals("NOT_PERFORMED", approved.humanPlaytestStatus)
        assertEquals(50, approved.humanApprovalCount)
        assertEquals(0, approved.automatedApprovalCount)
        assertTrue(approved.assignments.all { it.manualReviewStatus == "OWNER_APPROVED_NOT_PLAYTESTED" })
        assertEquals(200, shipped.levels.size)
        assertEquals(6, shipped.contentVersion)
        assertEquals(4, shipped.generatorVersion)
        assertEquals(
            source.levels.map(ContentFingerprint::exact),
            shipped.levels.take(150).map(ContentFingerprint::exact),
        )
        assertEquals(150, migration.preservedExistingLevelIds)
        assertEquals(150, migration.preservedExistingFingerprints)
        assertEquals((151..200).map { "campaign-${it.toString().padStart(3, '0')}" }, migration.addedLevelIds)
    }

    @Test
    fun `final Phase 1 certification covers promoted content and leaves playtest pending`() {
        val shipped = LevelParser().parseCatalog(resource("/content/d2/promotion/D2_SOURCE_CONTENT_V6.json"))
        val report = json.decodeFromString<Phase1FinalCertificationReport>(
            resource("/content/M5_3_FINAL_DIAGNOSTICS.json"),
        )

        assertEquals("COMPLETE_HUMAN_PLAYTEST_PENDING", report.status)
        assertEquals(6, report.contentVersion)
        assertEquals(4, report.generatorVersion)
        assertEquals(200, report.campaignLevelCount)
        assertEquals(50, report.levels.size)
        assertEquals(200, report.fullCampaignExactFingerprintCount)
        assertEquals(200, report.fullCampaignSymmetryFingerprintCount)
        assertEquals(200, report.fullCampaignDifficultyCertifiableCount)
        assertEquals(50, report.productionCertificationAcceptedCount)
        assertEquals(50, report.targetGateAcceptedCount)
        assertEquals(0, report.fullCampaignGuessDependentChoiceCount)
        assertEquals(150, report.preservedExistingStableIdCount)
        assertEquals(150, report.preservedExistingFingerprintCount)
        assertEquals(50, report.addedStableIdCount)
        assertTrue(report.progressionGatePassed)
        assertTrue(report.level200FinaleGatePassed)
        assertEquals(0, report.automatedApprovalCount)
        assertEquals(50, report.ownerApprovedCount)
        assertEquals(0, report.humanPlaytestedCount)
        assertTrue(report.levels.all { it.productionCertificationAccepted && it.gate.accepted })
        assertTrue(report.levels.none { it.quality.status == PuzzleQualityStatusV2.REJECT })
        assertTrue(report.levels.all { it.humanPlaytestStatus == "PENDING" })
        assertEquals(
            shipped.levels.drop(150).map(ContentFingerprint::exact),
            report.levels.map { it.contentFingerprint },
        )
    }

    @Test
    fun `golden campaign rows retain advanced mechanics recovery and finale evidence`() {
        val shipped = LevelParser().parseCatalog(resource("/content/d2/promotion/D2_SOURCE_CONTENT_V6.json"))
        val report = json.decodeFromString<Phase1FinalCertificationReport>(
            resource("/content/M5_3_FINAL_DIAGNOSTICS.json"),
        )
        val rows = report.levels.associateBy { it.levelId }

        assertEquals(2, rows.getValue("campaign-151").difficulty.rawMetrics.controllingMagnetChangeCount)
        assertEquals(3, rows.getValue("campaign-151").difficulty.rawMetrics.occlusionDependencyCount)
        assertEquals(6, rows.getValue("campaign-156").difficulty.rawMetrics.polarityFlipCount)
        assertEquals(2, rows.getValue("campaign-166").difficulty.rawMetrics.deadEndActionCount)
        assertEquals(1, rows.getValue("campaign-166").difficulty.rawMetrics.canonicalChoiceMetrics.deceptiveButFairChoices)
        assertEquals("phase1-recovery", rows.getValue("campaign-166").target.id)
        assertTrue(PuzzleSearchAnalyzer().analyze(shipped.level("campaign-109")).cancellationDependencyCount > 0)
        val finale = rows.getValue("campaign-200")
        assertTrue(finale.difficulty.rawMetrics.meaningfulDecisionPoints >= 4)
        assertTrue(finale.difficulty.rawMetrics.dependencyDepth >= 3)
        assertEquals(0, finale.difficulty.rawMetrics.canonicalChoiceMetrics.guessDependentChoices)
    }

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)).readText()
}
