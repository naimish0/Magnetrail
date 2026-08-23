package com.rameshta.magnetrail.tools

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CombinedV10V11StagingTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `legacy V10 waiver is deterministic while incomplete V11 evidence stays excluded`() {
        val first = temporary.newFolder("first")
        val second = temporary.newFolder("second")
        stage(first)
        stage(second)

        val artifacts = listOf(
            "COMBINED_V10_CERTIFIED_V11_CATALOG.json",
            "COMBINED_V10_V11_MERGE_MANIFEST.json",
            "COMBINED_V10_V11_CERTIFICATE.json",
        )
        artifacts.forEach { name ->
            assertArrayEquals(File(first, name).readBytes(), File(second, name).readBytes())
        }
        assertArrayEquals(resource("/Magnetrail_Campaign_Levels_v3.json").readBytes(), File(first, artifacts[0]).readBytes())

        val certificate = Json.parseToJsonElement(File(first, artifacts[2]).readText()).jsonObject
        assertEquals("LEGACY_MECHANICAL_OWNER_APPROVED", certificate.getValue("certificationType").jsonPrimitive.content)
        assertEquals("2205", certificate.getValue("boardCount").jsonPrimitive.content)
        assertEquals("2205", certificate.getValue("v10CertifiedBoardCount").jsonPrimitive.content)
        assertEquals("2205", certificate.getValue("certifiedBoardCount").jsonPrimitive.content)
        assertEquals("53", certificate.getValue("discoveredV11BoardCount").jsonPrimitive.content)
        assertEquals("0", certificate.getValue("certifiedV11BoardCount").jsonPrimitive.content)
        assertEquals("0", certificate.getValue("appendedV11BoardCount").jsonPrimitive.content)
        assertEquals("53", certificate.getValue("excludedV11BoardCount").jsonPrimitive.content)
        assertTrue(certificate.getValue("deterministicMergePassed").jsonPrimitive.content.toBoolean())
        assertTrue(certificate.getValue("v10LegacyOwnerWaiverReused").jsonPrimitive.content.toBoolean())
        assertTrue(certificate.getValue("combinedCampaignLegacyApproved").jsonPrimitive.content.toBoolean())
        assertTrue(certificate.getValue("humanDifficultyRejectionRetained").jsonPrimitive.content.toBoolean())
        assertFalse(certificate.getValue("automatedCampaignCertified").jsonPrimitive.content.toBoolean())
        assertFalse(certificate.getValue("difficultyLabelsCertified").jsonPrimitive.content.toBoolean())
        assertFalse(certificate.getValue("productionContentModified").jsonPrimitive.content.toBoolean())

        val entries = Json.parseToJsonElement(File(first, artifacts[1]).readText())
            .jsonObject.getValue("entries").jsonArray
        assertEquals(2_258, entries.size)
        assertEquals(2_205, entries.count {
            it.jsonObject.getValue("decision").jsonPrimitive.content == "PRESERVED_V10"
        })
        assertEquals(53, entries.count {
            it.jsonObject.getValue("decision").jsonPrimitive.content == "EXCLUDED_UNCERTIFIED_V11"
        })
    }

    @Test
    fun `legacy waiver is invalidated when its catalog binding changes`() {
        val output = temporary.newFolder("invalid-waiver-output")
        val invalidWaiver = temporary.newFile("invalid-waiver.json")
        invalidWaiver.writeText(
            resource("/content/v10_density_remediation/LEGACY_V10_OWNER_WAIVER.json").readText()
                .replace(
                    "8ad274b5dcf39f69db006c87bc0861e9b7037516755f79d8560206b4f2577de9",
                    "${"0".repeat(64)}",
                ),
        )

        stage(output, invalidWaiver)

        val certificate = Json.parseToJsonElement(
            File(output, "COMBINED_V10_V11_CERTIFICATE.json").readText(),
        ).jsonObject
        assertEquals("AUTOMATED_CAMPAIGN_REJECTED", certificate.getValue("certificationType").jsonPrimitive.content)
        assertFalse(certificate.getValue("v10LegacyOwnerWaiverReused").jsonPrimitive.content.toBoolean())
    }

    private fun stage(
        output: File,
        waiver: File = resource("/content/v10_density_remediation/LEGACY_V10_OWNER_WAIVER.json"),
    ) = stageCertifiedV11Merge(
        mapOf(
            "v10" to resource("/Magnetrail_Campaign_Levels_v3.json").path,
            "v10-audit" to resource("/content/v10_density_remediation/CAMPAIGN_V10_GENERATION_AUDIT.json").path,
            "v10-promotion" to resource("/content/v10_density_remediation/CAMPAIGN_V10_PROMOTION_RESULT.md").path,
            "v10-waiver" to waiver.path,
            "v10-human-csv" to resource("/magnetrail-playtest-pet-52c263fb.csv").path,
            "v10-rejection" to resource("/content/v11_pilot/V10_HUMAN_PLAYTEST_REJECTION.md").path,
            "v11" to resource("/content/v11_pilot/V11_PILOT_CATALOG.json").path,
            "v11-audit" to resource("/content/v11_pilot/V11_PILOT_AUDIT.json").path,
            "v11-certificate" to File(output, "missing-v11-certificate.json").path,
            "output" to output.path,
        ),
    )

    private fun resource(path: String): File = File(checkNotNull(javaClass.getResource(path)).toURI())
}
