package com.rameshta.magnetrail.tools

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class GeneratorV5RepairArtifactsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun benchmarkRecordsApprovedProfileCalibrationAndNeverChangesCampaign() {
        val audit = audit()
        assertTrue(audit.gatesWeakened)
        assertTrue(audit.limitations.any { "owner explicitly approved" in it })
        assertFalse(audit.productionCampaignModified)
        assertTrue(audit.campaignByteIdentical)
        assertEquals(200, audit.campaignLevelCount)
        assertEquals(audit.campaignSha256Before, audit.campaignSha256After)

        val campaignUrl = requireNotNull(
            javaClass.getResource("/content/v5_1_append/promotion/SOURCE_CONTENT_V7.json"),
        )
        val bytes = File(campaignUrl.toURI()).readBytes()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertEquals(audit.campaignSha256Before, digest)
    }

    @Test
    fun benchmarkCoversEveryRequestedDifficultyAndReportsExpertHonestly() {
        val audit = audit()
        assertEquals("PASS", audit.status)
        assertEquals(
            setOf("EASY", "MEDIUM", "HARD", "VERY_HARD", "EXPERT", "MASTER"),
            audit.profileRows.map { it.difficulty }.toSet(),
        )
        val expert = audit.profileRows.single { it.difficulty == "EXPERT" }
        assertTrue(expert.attempts > 0)
        assertTrue(expert.constructed > 0)
        assertTrue(expert.solved > 0)
        assertEquals(0, expert.v4Truncated)
        assertEquals(1, expert.certified)
        assertTrue(audit.profileRows.all { it.certified == 1 && it.rejectionReasons.isEmpty() })
    }

    private fun audit(): GeneratorV5RepairAudit {
        val url = requireNotNull(javaClass.getResource("/development/MAGNETRAIL_GENERATOR_V5_AUDIT.json"))
        return json.decodeFromString(File(url.toURI()).readText())
    }
}
