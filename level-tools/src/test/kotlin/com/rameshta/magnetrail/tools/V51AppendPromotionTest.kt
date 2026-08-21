package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.level.LevelParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V51AppendPromotionTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun `content v8 appends five unique non-Master levels and preserves content v7`() {
        val source = LevelParser().parseCatalog(resource("/content/v5_1_append/promotion/SOURCE_CONTENT_V7.json"))
        val promoted = LevelParser().parseCatalog(resource("/content/v9_expansion/SOURCE_CONTENT_V8.json"))
        val report = json.decodeFromString<V51AppendPromotionReport>(
            resource("/content/v5_1_append/promotion/V5_1_APPEND_PROMOTION_MANIFEST.json"),
        )

        assertEquals(200, source.levels.size)
        assertEquals(205, promoted.levels.size)
        assertEquals(8, promoted.contentVersion)
        assertEquals(source.levels, promoted.levels.take(200))
        assertEquals((1..205).toList(), promoted.levels.map { it.number })
        assertEquals(205, promoted.levels.map { it.id }.toSet().size)
        assertEquals(205, promoted.levels.map(ContentFingerprint::exact).toSet().size)
        assertEquals(205, promoted.levels.map(ContentFingerprint::symmetryNormalized).toSet().size)
        assertTrue(promoted.levels.drop(200).none { it.metadata?.generationProfile?.contains("master") == true })

        assertEquals("OWNER_DIRECTED_APPENDED_WITH_EXPERT_CERTIFICATION_WAIVER", report.status)
        assertEquals(0, report.automatedHumanApprovalCount)
        assertEquals(listOf("v5-repair-master-0001"), report.excludedCandidateIds)
        assertEquals(4, report.rows.count { it.structuralCertificationStatus == "CURRENT_V5_CERTIFIED" })
        assertEquals(1, report.rows.count { it.structuralCertificationStatus == "OWNER_WAIVED_UNCERTIFIED_EXPERT" })
        assertTrue(report.rows.all { it.solverCertified && it.v4Complete })
        assertFalse(report.rows.single { it.productionLevelId == "campaign-205" }.structuralRejectionReasons.isEmpty())
    }

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)).readText()
}
